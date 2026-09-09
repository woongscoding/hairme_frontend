package com.example.myapplication.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.network.MyResultItem
import com.example.myapplication.repository.ApiResult
import com.example.myapplication.repository.MyResultsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "내가 만든 스타일" (합성 결과 히스토리) UI 상태
 */
sealed class MyResultsUiState {
    /** 첫 페이지 로딩 중 */
    object Loading : MyResultsUiState()

    /** 401 — 로그인 필요 (토큰 만료 포함) */
    object RequiresLogin : MyResultsUiState()

    /** 결과 0건 */
    object Empty : MyResultsUiState()

    /** 첫 페이지 로드 실패 (재시도 버튼 표시) */
    data class Error(val message: String) : MyResultsUiState()

    /** 목록 표시 중 */
    data class Content(
        val items: List<MyResultItem>,
        val isLoadingMore: Boolean,
        val endReached: Boolean
    ) : MyResultsUiState()
}

/**
 * MyResultsViewModel
 *
 * 합성 결과 히스토리 목록 + continuation_token 기반 무한 스크롤.
 * presigned URL은 24시간 만료라 화면 진입마다 refresh()로 새로 받아야 한다
 * (내비게이션 백스택 엔트리에 스코프되므로 재진입 시 새 인스턴스 → 자동으로 재로드됨).
 */
class MyResultsViewModel : ViewModel() {

    private val repository = MyResultsRepository()

    companion object {
        private const val TAG = "MyResultsViewModel"
    }

    private val _uiState = MutableStateFlow<MyResultsUiState>(MyResultsUiState.Loading)
    val uiState: StateFlow<MyResultsUiState> = _uiState.asStateFlow()

    /** 다음 페이지 토큰 (null이면 마지막 페이지) */
    private var nextToken: String? = null
    private var isRequestInFlight = false

    /**
     * 첫 페이지 (재)로드 — 화면 진입, 재시도, 로그인 성공 시 호출
     */
    fun refresh() {
        if (isRequestInFlight) return
        isRequestInFlight = true
        _uiState.value = MyResultsUiState.Loading
        nextToken = null

        viewModelScope.launch {
            when (val result = repository.getMyResults()) {
                is ApiResult.Success -> {
                    nextToken = result.data.nextToken
                    _uiState.value = if (result.data.results.isEmpty()) {
                        MyResultsUiState.Empty
                    } else {
                        MyResultsUiState.Content(
                            items = result.data.results,
                            isLoadingMore = false,
                            endReached = nextToken == null
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = if (result.code == 401) {
                        MyResultsUiState.RequiresLogin
                    } else {
                        MyResultsUiState.Error(result.message)
                    }
                }
            }
            isRequestInFlight = false
        }
    }

    /**
     * 다음 페이지 로드 (스크롤 하단 도달 시)
     * 실패해도 에러 화면으로 전환하지 않고 스피너만 내림 — 다음 스크롤에서 자동 재시도
     */
    fun loadMore() {
        val current = _uiState.value as? MyResultsUiState.Content ?: return
        if (current.isLoadingMore || current.endReached || isRequestInFlight) return
        val token = nextToken ?: return

        isRequestInFlight = true
        _uiState.value = current.copy(isLoadingMore = true)

        viewModelScope.launch {
            when (val result = repository.getMyResults(continuationToken = token)) {
                is ApiResult.Success -> {
                    nextToken = result.data.nextToken
                    // key 기준 dedup — 페이지 경계에서 중복 항목이 와도 그리드 key 충돌 방지
                    val merged = (current.items + result.data.results).distinctBy { it.key }
                    _uiState.value = MyResultsUiState.Content(
                        items = merged,
                        isLoadingMore = false,
                        endReached = nextToken == null
                    )
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "❌ 다음 페이지 로드 실패: ${result.message}")
                    if (result.code == 401) {
                        _uiState.value = MyResultsUiState.RequiresLogin
                    } else {
                        _uiState.value = current.copy(isLoadingMore = false)
                    }
                }
            }
            isRequestInFlight = false
        }
    }
}
