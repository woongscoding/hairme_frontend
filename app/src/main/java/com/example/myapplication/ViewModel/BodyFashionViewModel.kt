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
import com.example.myapplication.util.BodyResultStore
import com.example.myapplication.util.DeviceIdHelper
import com.example.myapplication.util.PrefsBodyResultStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BodyFashionUiState {
    data object Idle : BodyFashionUiState
    data object Loading : BodyFashionUiState

    /**
     * @param photo 결과 화면에 보여줄 내 사진 (Uri 또는 내부 저장소 파일 경로, 없으면 null)
     * @param restored 기기에 저장해 둔 지난 결과를 다시 보여주는 것 (분석 이벤트를 또 남기지 않는다)
     */
    data class Success(
        val result: BodyAnalysisResponse,
        val photo: Any? = null,
        val restored: Boolean = false
    ) : BodyFashionUiState

    /** @param isPhotoProblem 서버가 사진을 거절함(400: 어깨·골반이 안 보임·정면 아님 등) → "다시 찍어주세요" */
    data class Error(val message: String, val isPhotoProblem: Boolean = false) : BodyFashionUiState
}

/**
 * 체형별 아이템 추천 (헤어 분석과 완전히 별도인 기능)
 *
 * 전신 사진 → 서버 체형 분석 → 결과. 아이템 탭은 매번 /api/products/click 을
 * 거쳐 쿠팡 링크를 받는다 (클릭 로그가 서버에 남아야 해서 링크를 캐시하지 않는다).
 * 마지막 결과와 사진은 기기에 보관해, 다시 들어오거나 프로세스가 정리된 뒤에도 보여준다.
 */
class BodyFashionViewModel(
    application: Application,
    private val repository: HairstyleRepository = HairstyleRepository(application),
    private val store: BodyResultStore = PrefsBodyResultStore(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
            when (val result = repository.analyzeBody(imageUri, _gender.value, deviceId)) {
                is ApiResult.Success -> {
                    _uiState.value = BodyFashionUiState.Success(result.data, photo = imageUri)
                    val savedPath = withContext(ioDispatcher) { store.save(result.data, imageUri) }
                    // 촬영 임시 파일은 지워질 수 있어 보관한 사본으로 바꿔 둔다
                    val current = _uiState.value
                    if (savedPath != null && current is BodyFashionUiState.Success && current.result == result.data) {
                        _uiState.value = current.copy(photo = savedPath)
                    }
                }
                is ApiResult.Error -> _uiState.value =
                    BodyFashionUiState.Error(result.message, isPhotoProblem = result.code == 400)
            }
        }
    }

    /**
     * 기기에 보관한 지난 결과를 보여준다.
     * @return 보관된 결과가 있으면 true (상태가 Success 로 바뀐다)
     */
    fun showLastResult(): Boolean {
        val saved = store.load() ?: return false
        analyzeJob?.cancel()
        setGender(saved.result.gender)
        _uiState.value = BodyFashionUiState.Success(saved.result, photo = saved.photoPath, restored = true)
        return true
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
