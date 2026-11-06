package com.example.myapplication.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.network.BodyAnalysisResponse
import com.example.myapplication.network.BodyFit
import com.example.myapplication.network.HairProfile
import com.example.myapplication.network.ProductClickRequest
import com.example.myapplication.repository.ApiResult
import com.example.myapplication.repository.HairstyleRepository
import com.example.myapplication.util.DeviceIdHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BodyFashionUiState {
    data object Idle : BodyFashionUiState
    data object Loading : BodyFashionUiState
    data class Success(val result: BodyAnalysisResponse) : BodyFashionUiState
    /** @param isPhotoProblem 서버가 사진을 거절함(400: 전신이 안 보임·정면 아님 등) → "다시 찍어주세요" */
    data class Error(val message: String, val isPhotoProblem: Boolean = false) : BodyFashionUiState
}

/**
 * 체형별 아이템 추천 (헤어 분석과 완전히 별도인 기능)
 *
 * 전신 사진 → 서버 체형 분석 → 결과. 아이템 탭은 매번 /api/products/click 을
 * 거쳐 쿠팡 링크를 받는다 (클릭 로그가 서버에 남아야 해서 링크를 캐시하지 않는다).
 */
class BodyFashionViewModel(
    application: Application,
    private val repository: HairstyleRepository = HairstyleRepository(application),
    deviceIdProvider: () -> String = { DeviceIdHelper.getDeviceId(application) }
) : AndroidViewModel(application) {

    private val deviceId: String by lazy(deviceIdProvider)

    private val _uiState = MutableStateFlow<BodyFashionUiState>(BodyFashionUiState.Idle)
    val uiState: StateFlow<BodyFashionUiState> = _uiState.asStateFlow()

    private val _gender = MutableStateFlow("male")
    val gender: StateFlow<String> = _gender.asStateFlow()

    private var analyzeJob: Job? = null

    fun setGender(gender: String) {
        _gender.value = if (gender == "female") "female" else "male"
    }

    fun analyze(imageUri: Uri) {
        analyzeJob?.cancel()
        _uiState.value = BodyFashionUiState.Loading
        analyzeJob = viewModelScope.launch {
            _uiState.value = when (val result = repository.analyzeBody(imageUri, _gender.value, deviceId)) {
                is ApiResult.Success -> BodyFashionUiState.Success(result.data)
                is ApiResult.Error -> BodyFashionUiState.Error(result.message, isPhotoProblem = result.code == 400)
            }
        }
    }

    fun reset() {
        analyzeJob?.cancel()
        _uiState.value = BodyFashionUiState.Idle
    }

    /**
     * 아이템 "쿠팡에서 보기" → 서버에서 제휴 링크 발급 후 onLink 로 넘긴다.
     * 링크가 없는 항목(스타일링 팁)은 호출하지 않는다.
     */
    fun openItemLink(fit: BodyFit, onLink: (String) -> Unit, onFailure: () -> Unit) {
        val productId = fit.productId ?: return
        val result = (_uiState.value as? BodyFashionUiState.Success)?.result
        viewModelScope.launch {
            val request = ProductClickRequest(
                productId = productId,
                source = "body_analysis",
                hairProfile = HairProfile(
                    gender = result?.gender ?: _gender.value,
                    bodyType = result?.bodyType
                ),
                deviceId = deviceId
            )
            when (val click = repository.clickProduct(request)) {
                is ApiResult.Success -> {
                    val url = click.data.affiliateUrl
                    if (url.isNullOrBlank()) onFailure() else onLink(url)
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "❌ 체형 아이템 링크 발급 실패: ${click.message} (code=${click.code})")
                    onFailure()
                }
            }
        }
    }

    private companion object {
        const val TAG = "BodyFashionViewModel"
    }
}
