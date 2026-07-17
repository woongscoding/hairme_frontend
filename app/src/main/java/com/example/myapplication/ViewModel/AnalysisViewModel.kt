package com.example.myapplication.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.AnalysisResult
import com.example.myapplication.repository.ApiResult
import com.example.myapplication.repository.HairstyleRepository
import com.example.myapplication.util.DeviceIdHelper
// import dagger.hilt.android.lifecycle.HiltViewModel  // Hilt 임시 비활성화
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
// import javax.inject.Inject  // Hilt 임시 비활성화

// 1. UI 상태를 정의하는 Sealed Class
sealed class AnalysisUiState {
    object Idle : AnalysisUiState() // 초기 상태
    data class Loading(val startTime: Long = System.currentTimeMillis()) : AnalysisUiState() // 로딩 중
    // ✅ v26: DynamoDB UUID 지원 (Int → String)
    data class Success(
        val analysisId: String?,
        val data: AnalysisResult,
        val elapsedTimeMs: Long = 0 // 분석 소요 시간 (밀리초)
    ) : AnalysisUiState() // 성공
    data class Error(val message: String) : AnalysisUiState() // 실패
}

// ✅ v30: 헤어스타일 합성 UI 상태
sealed class SynthesisUiState {
    object Idle : SynthesisUiState()
    data class Loading(val hairstyleName: String) : SynthesisUiState()
    data class Success(val synthesizedImage: Bitmap, val hairstyleName: String) : SynthesisUiState()
    data class Error(val message: String) : SynthesisUiState()
}

// ✅ v33: 염색색 추천 UI 상태
sealed class HairColorUiState {
    object Idle : HairColorUiState()
    object Loading : HairColorUiState()
    data class Success(
        val personalColor: String,
        val recommended: List<com.example.myapplication.network.HairColorItem>,
        val avoid: List<com.example.myapplication.network.AvoidColorItem>
    ) : HairColorUiState()
    data class Error(val message: String) : HairColorUiState()
}

// ✅ v34: 염색색 합성 UI 상태
sealed class HairColorSynthesisUiState {
    object Idle : HairColorSynthesisUiState()
    data class Loading(val colorName: String) : HairColorSynthesisUiState()
    data class Success(val synthesizedImage: Bitmap, val colorName: String) : HairColorSynthesisUiState()
    data class Error(val message: String) : HairColorSynthesisUiState()
}

// 일일 무료 합성 횟수 상태
data class SynthesisUsageState(
    val remaining: Int = 3,
    val dailyLimit: Int = 3,
    val isLoading: Boolean = false,
    val isApiError: Boolean = false
)

/**
 * AnalysisViewModel
 * 
 * 임시로 Hilt 비활성화 - 빌드 문제 해결 후 재활성화 예정
 * AndroidViewModel을 사용하여 Application Context 접근
 */
// @HiltViewModel  // Hilt 임시 비활성화
class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    // Hilt 비활성화로 인해 Repository를 직접 생성
    // Application Context를 전달하여 Repository 생성
    private val repository: HairstyleRepository = HairstyleRepository(application)

    companion object {
        private const val TAG = "AnalysisViewModel"
    }

    // 4. UI 상태를 관리하는 StateFlow
    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    // ✅ v30: 합성 UI 상태
    private val _synthesisState = MutableStateFlow<SynthesisUiState>(SynthesisUiState.Idle)
    val synthesisState: StateFlow<SynthesisUiState> = _synthesisState.asStateFlow()

    // ✅ v33: 염색색 추천 UI 상태
    private val _hairColorState = MutableStateFlow<HairColorUiState>(HairColorUiState.Idle)
    val hairColorState: StateFlow<HairColorUiState> = _hairColorState.asStateFlow()

    // ✅ v34: 염색색 합성 UI 상태
    private val _hairColorSynthesisState = MutableStateFlow<HairColorSynthesisUiState>(HairColorSynthesisUiState.Idle)
    val hairColorSynthesisState: StateFlow<HairColorSynthesisUiState> = _hairColorSynthesisState.asStateFlow()

    // 일일 무료 합성 횟수 상태
    private val _usageState = MutableStateFlow(SynthesisUsageState())
    val usageState: StateFlow<SynthesisUsageState> = _usageState.asStateFlow()

    // 기기 식별 ID
    private val deviceId: String by lazy {
        DeviceIdHelper.getDeviceId(getApplication())
    }

    // ✅ v30: 현재 분석에 사용된 이미지 URI 저장
    private var currentImageUri: Uri? = null
    private var currentGender: String = "male"

    // 5. 히스토리 개수 (옵저빙 가능)
    val historyCount: StateFlow<Int> = repository.getHistoryCount()
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    init {
        // ViewModel 생성 시 오래된 히스토리 정리 (30일 이상)
        viewModelScope.launch {
            try {
                repository.cleanOldHistory()
            } catch (e: Exception) {
                Log.e(TAG, "오래된 히스토리 정리 실패: ${e.message}")
            }
        }
        // 초기 Usage 조회
        fetchUsage()
    }

    /**
     * Composable(UI)에서 이 함수를 호출하여 분석 시작
     * ✅ 'File'이 아닌 'Uri'를 받음
     * ✅ v26: 성별 파라미터 추가
     */
    fun analyzeImage(imageUri: Uri, gender: String = "male") {
        // ✅ v30: 이미지 URI와 성별 저장 (합성에서 사용)
        currentImageUri = imageUri
        currentGender = gender

        // ViewModel 고유의 CoroutineScope에서 실행
        viewModelScope.launch {
            // 5. 상태를 'Loading'으로 변경 (시작 시간 기록)
            val loadingState = AnalysisUiState.Loading(startTime = System.currentTimeMillis())
            _uiState.value = loadingState
            Log.d(TAG, "분석 시작: Loading 상태로 변경 (gender=$gender)")

            // 6. Repository에 API 호출 위임 (성별 전달)
            when (val result = repository.analyzeImage(imageUri, gender)) {
                is ApiResult.Success -> {
                    // 소요 시간 계산
                    val elapsedTime = System.currentTimeMillis() - loadingState.startTime

                    // ✅ v20: analysisId 로그 추가
                    Log.d(TAG, "✅ API 성공 - analysisId: ${result.analysisId}, 소요시간: ${elapsedTime}ms")

                    // 7. 성공 시 - ✅ v20: analysisId + 소요시간 포함
                    _uiState.value = AnalysisUiState.Success(
                        analysisId = result.analysisId,
                        data = result.data,
                        elapsedTimeMs = elapsedTime
                    )

                    Log.d(TAG, "✅ Success 상태로 변경 완료 - 총 소요 시간: ${elapsedTime / 1000.0}초")
                }
                is ApiResult.Error -> {
                    // 8. 실패 시
                    Log.e(TAG, "❌ API 실패: ${result.message}")
                    _uiState.value = AnalysisUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * 결과 화면에서 '뒤로가기'를 누르거나, 새로 분석을 시작할 때
     * 상태를 초기화하는 함수
     */
    fun resetState() {
        Log.d(TAG, "상태 초기화: Idle로 변경")
        _uiState.value = AnalysisUiState.Idle
    }

    /**
     * 오프라인 상태에서 가장 최근 분석 결과 로드
     * 사용자가 오프라인일 때 이전 결과를 보여줄 수 있음
     */
    fun loadLatestCachedResult() {
        viewModelScope.launch {
            val loadingState = AnalysisUiState.Loading(startTime = System.currentTimeMillis())
            _uiState.value = loadingState
            Log.d(TAG, "📂 캐시에서 최근 결과 로드 중...")

            val latestResult = repository.getLatestAnalysis()
            if (latestResult != null) {
                val elapsedTime = System.currentTimeMillis() - loadingState.startTime
                Log.d(TAG, "✅ 캐시에서 결과 로드 성공 (${elapsedTime}ms)")
                _uiState.value = AnalysisUiState.Success(
                    analysisId = latestResult.first,
                    data = latestResult.second,
                    elapsedTimeMs = elapsedTime
                )
            } else {
                Log.e(TAG, "❌ 캐시된 결과 없음")
                _uiState.value = AnalysisUiState.Error("저장된 분석 결과가 없습니다")
            }
        }
    }

    /**
     * 모든 히스토리 삭제
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                repository.clearAllHistory()
                Log.d(TAG, "✅ 모든 히스토리 삭제 완료")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 히스토리 삭제 실패: ${e.message}")
            }
        }
    }

    // ========================================
    // 일일 무료 합성 횟수 관리
    // ========================================

    fun fetchUsage() {
        viewModelScope.launch {
            _usageState.value = _usageState.value.copy(isLoading = true)
            when (val result = repository.getUsage(deviceId)) {
                is ApiResult.Success -> {
                    _usageState.value = SynthesisUsageState(
                        remaining = result.data.remaining,
                        dailyLimit = result.data.dailyLimit,
                        isLoading = false,
                        isApiError = false
                    )
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Usage 조회 실패: ${result.message}")
                    _usageState.value = SynthesisUsageState(
                        remaining = 3,
                        dailyLimit = 3,
                        isLoading = false,
                        isApiError = true
                    )
                }
            }
        }
    }

    private suspend fun consumeUsage() {
        when (val result = repository.consumeUsage(deviceId)) {
            is ApiResult.Success -> {
                _usageState.value = SynthesisUsageState(
                    remaining = result.data.remaining,
                    dailyLimit = result.data.dailyLimit,
                    isLoading = false,
                    isApiError = false
                )
            }
            is ApiResult.Error -> {
                Log.e(TAG, "Usage 소비 실패: ${result.message}")
                // 낙관적 차감
                val current = _usageState.value
                _usageState.value = current.copy(
                    remaining = (current.remaining - 1).coerceAtLeast(0),
                    isApiError = true
                )
            }
        }
    }

    private fun canSynthesize(): Boolean {
        val state = _usageState.value
        return state.remaining > 0 || state.isApiError
    }

    // ========================================
    // ✅ v30: 헤어스타일 합성 기능
    // ========================================

    /**
     * 선택한 헤어스타일을 현재 사진에 합성
     *
     * @param hairstyleName 적용할 헤어스타일 이름 (예: 투블럭컷)
     */
    fun synthesizeHairstyle(hairstyleName: String) {
        if (!canSynthesize()) {
            Log.e(TAG, "❌ 합성 불가: 일일 무료 횟수 소진")
            _synthesisState.value = SynthesisUiState.Error("오늘의 무료 합성 횟수(3회)를 모두 사용했습니다. 내일 다시 이용해주세요!")
            return
        }

        val imageUri = currentImageUri
        if (imageUri == null) {
            Log.e(TAG, "❌ 합성 실패: 이미지 URI가 없습니다")
            _synthesisState.value = SynthesisUiState.Error("이미지가 없습니다. 다시 분석을 시도해주세요.")
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "🎨 헤어스타일 합성 시작: $hairstyleName")
            _synthesisState.value = SynthesisUiState.Loading(hairstyleName)

            when (val result = repository.synthesizeHairstyle(imageUri, hairstyleName, currentGender, deviceId)) {
                is ApiResult.Success -> {
                    Log.d(TAG, "✅ 헤어스타일 합성 성공: $hairstyleName")
                    consumeUsage()
                    _synthesisState.value = SynthesisUiState.Success(
                        synthesizedImage = result.data,
                        hairstyleName = hairstyleName
                    )
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "❌ 헤어스타일 합성 실패: ${result.message}")
                    _synthesisState.value = SynthesisUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * 합성 상태 초기화
     */
    fun resetSynthesisState() {
        Log.d(TAG, "합성 상태 초기화: Idle로 변경")
        _synthesisState.value = SynthesisUiState.Idle
    }

    // ========================================
    // ✅ v33: 염색색 추천 기능
    // ========================================

    /**
     * 퍼스널컬러 기반 염색색 추천 조회
     *
     * @param personalColor 퍼스널컬러 (봄웜, 여름쿨, 가을웜, 겨울쿨)
     */
    fun fetchHairColorRecommendations(personalColor: String) {
        viewModelScope.launch {
            Log.d(TAG, "🎨 염색색 추천 조회 시작: $personalColor")
            _hairColorState.value = HairColorUiState.Loading

            when (val result = repository.getHairColorRecommendations(personalColor)) {
                is ApiResult.Success -> {
                    Log.d(TAG, "✅ 염색색 추천 성공: ${result.data.recommended.size}개")
                    _hairColorState.value = HairColorUiState.Success(
                        personalColor = result.data.personal_color,
                        recommended = result.data.recommended,
                        avoid = result.data.avoid
                    )
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "❌ 염색색 추천 실패: ${result.message}")
                    _hairColorState.value = HairColorUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * 염색색 추천 상태 초기화
     */
    fun resetHairColorState() {
        Log.d(TAG, "염색색 추천 상태 초기화: Idle로 변경")
        _hairColorState.value = HairColorUiState.Idle
    }

    // ========================================
    // ✅ v34: 염색색 합성 기능
    // ========================================

    /**
     * 선택한 염색색을 현재 사진에 합성
     *
     * @param colorName 염색색 이름 (예: 밀크브라운)
     * @param colorHex 염색색 HEX 코드 (예: #C4A484)
     */
    fun synthesizeHairColor(colorName: String, colorHex: String) {
        if (!canSynthesize()) {
            Log.e(TAG, "❌ 합성 불가: 일일 무료 횟수 소진")
            _hairColorSynthesisState.value = HairColorSynthesisUiState.Error("오늘의 무료 합성 횟수(3회)를 모두 사용했습니다. 내일 다시 이용해주세요!")
            return
        }

        val imageUri = currentImageUri
        if (imageUri == null) {
            Log.e(TAG, "❌ 염색색 합성 실패: 이미지 URI가 없습니다")
            _hairColorSynthesisState.value = HairColorSynthesisUiState.Error("이미지가 없습니다. 다시 분석을 시도해주세요.")
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "🎨 염색색 합성 시작: $colorName ($colorHex)")
            _hairColorSynthesisState.value = HairColorSynthesisUiState.Loading(colorName)

            when (val result = repository.synthesizeHairColor(imageUri, colorName, colorHex, deviceId)) {
                is ApiResult.Success -> {
                    Log.d(TAG, "✅ 염색색 합성 성공: $colorName")
                    consumeUsage()
                    _hairColorSynthesisState.value = HairColorSynthesisUiState.Success(
                        synthesizedImage = result.data,
                        colorName = colorName
                    )
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "❌ 염색색 합성 실패: ${result.message}")
                    _hairColorSynthesisState.value = HairColorSynthesisUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * 염색색 합성 상태 초기화
     */
    fun resetHairColorSynthesisState() {
        Log.d(TAG, "염색색 합성 상태 초기화: Idle로 변경")
        _hairColorSynthesisState.value = HairColorSynthesisUiState.Idle
    }
}