package com.example.myapplication.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.AnalysisResult
import com.example.myapplication.data.auth.TokenManager
import com.example.myapplication.repository.ApiResult
import com.example.myapplication.repository.HairstyleRepository
import com.example.myapplication.repository.SynthesisResult
import com.example.myapplication.util.AnalyticsHelper
import com.example.myapplication.util.DeviceIdHelper
// import dagger.hilt.android.lifecycle.HiltViewModel  // Hilt 임시 비활성화
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    data class Success(
        val synthesizedImage: Bitmap,
        val hairstyleName: String,
        // 스타일 맞춤 제휴 제품 (최대 3개, 빈 배열이면 UI에서 섹션 숨김)
        val recommendedProducts: List<com.example.myapplication.network.RecommendedProduct> = emptyList(),
        // 대가성 문구 (서버 제공값 우선, null이면 UI에서 표준 문구 폴백)
        val disclosure: String? = null,
        // 무료 미리보기(512px + 워터마크) → "이 스타일 고화질로 보기" CTA
        val isPreview: Boolean = false
    ) : SynthesisUiState()
    /** 과금 한도(402/429)는 여기로 오지 않고 결제 화면([PaywallRequest])으로 간다 */
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
    data class Success(
        val synthesizedImage: Bitmap,
        val colorName: String,
        val colorHex: String,
        val isPreview: Boolean = false
    ) : HairColorSynthesisUiState()
    data class Error(val message: String) : HairColorSynthesisUiState()
}

/**
 * 비로그인 무료 합성 한도 + 무료 정책 실험 그룹.
 *
 * freePolicy/experimentArm/signupBonus는 서버(/api/usage)가 내려줄 때만 바뀌고,
 * 그 전에는 기존 정책(매일 3회, 보상형=크레딧, 가입 보너스 5회)으로 동작한다.
 */
data class SynthesisUsageState(
    val remaining: Int = 3,
    val dailyLimit: Int = 3,
    val isLoading: Boolean = false,
    val isApiError: Boolean = false,
    val freePolicy: String = FREE_POLICY_DAILY,
    val experimentArm: String = ARM_CONTROL,
    val signupBonus: Int = 5
) {
    /** 실험군: 보상형 광고가 크레딧 대신 원가 0원 기능(퍼스널컬러 상세·전체 랭킹)을 연다 */
    val isTreatment: Boolean get() = experimentArm == ARM_TREATMENT

    companion object {
        const val FREE_POLICY_DAILY = "daily"
        const val FREE_POLICY_LIFETIME = "lifetime"
        const val ARM_CONTROL = "control"
        const val ARM_TREATMENT = "treatment"
    }
}

/** 결제 화면 때문에 멈춘 합성. 결제·로그인 후 이어서 실행한다. */
sealed class PendingSynthesis {
    data class Style(val name: String) : PendingSynthesis()
    data class Color(val name: String, val hex: String) : PendingSynthesis()
}

/**
 * 결제 화면 요청.
 *
 * @param trigger "exhausted"(무료·크레딧 소진 후 합성 탭) | "hd_upgrade"(무료 결과의 "고화질로 보기")
 * @param limitType "credits"(회원 402) | "device"(비로그인 429) | "client_device"(서버 호출 전 차단) | "preview"
 * @param limitDetail 기기 한도 429의 limit_detail. "lifetime"(평생 무료 체험 소진)이면
 *                    다시 채워지지 않으므로 로그인 유도 문구로 띄운다
 */
data class PaywallRequest(
    val trigger: String,
    val limitType: String,
    val pending: PendingSynthesis?,
    val limitDetail: String? = null
) {
    val isLifetimeLimit: Boolean get() = limitDetail == SynthesisUsageState.FREE_POLICY_LIFETIME
}

/**
 * AnalysisViewModel
 * 
 * 임시로 Hilt 비활성화 - 빌드 문제 해결 후 재활성화 예정
 * AndroidViewModel을 사용하여 Application Context 접근
 */
// @HiltViewModel  // Hilt 임시 비활성화
// Hilt 비활성화로 인해 Repository를 직접 생성 (Application Context 전달).
// @JvmOverloads: 기본 ViewModel 팩토리가 찾는 (Application) 생성자를 유지하면서 테스트에서 Repository를 주입한다
class AnalysisViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: HairstyleRepository = HairstyleRepository(application)
) : AndroidViewModel(application) {

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

    // 결제 화면 (null이면 닫힘)
    private val _paywall = MutableStateFlow<PaywallRequest?>(null)
    val paywall: StateFlow<PaywallRequest?> = _paywall.asStateFlow()

    // 합성 응답의 회원 잔액 (AuthViewModel의 "크레딧 N개"를 갱신)
    private val _memberBalance = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val memberBalance: SharedFlow<Int> = _memberBalance.asSharedFlow()

    // 원가 0원 기능(퍼스널컬러 상세·전체 랭킹) 해제 여부 - 분석마다 새로 잠긴다
    private val _detailUnlocked = MutableStateFlow(false)
    val detailUnlocked: StateFlow<Boolean> = _detailUnlocked.asStateFlow()

    // 합성 성공 신호 ("다음 커트" 알림 예약용)
    private val _synthesisSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val synthesisSucceeded: SharedFlow<Unit> = _synthesisSucceeded.asSharedFlow()

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
        _detailUnlocked.value = false

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
                    val body = result.data
                    val previous = _usageState.value
                    _usageState.value = SynthesisUsageState(
                        remaining = body.remaining,
                        dailyLimit = body.dailyLimit,
                        isLoading = false,
                        isApiError = false,
                        freePolicy = body.freePolicy ?: SynthesisUsageState.FREE_POLICY_DAILY,
                        experimentArm = body.experimentArm ?: SynthesisUsageState.ARM_CONTROL,
                        signupBonus = body.signupBonus ?: previous.signupBonus
                    )
                    AnalyticsHelper.setExperimentArm(_usageState.value.experimentArm)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Usage 조회 실패: ${result.message}")
                    // 한도는 서버가 다시 판정하므로 막지 않는다. 실험 그룹은 마지막 값을 유지한다.
                    _usageState.value = _usageState.value.copy(
                        isLoading = false,
                        isApiError = true
                    )
                }
            }
        }
    }

    /**
     * 합성 응답의 quota 블록 반영.
     * 회원은 잔액을 AuthViewModel로 넘기고, 비회원은 남은 횟수를 갱신한다.
     * (예전에는 /api/usage/consume을 따로 불렀지만 서버에서 폐기돼 아무것도 차감하지 않는다)
     */
    private fun applyQuota(result: SynthesisResult) {
        val quota = result.quota ?: return // 캐시 적중 = 차감 없음
        when (quota.mode) {
            "credits" -> quota.balance?.let { _memberBalance.tryEmit(it) }
            "device" -> _usageState.value = _usageState.value.copy(
                remaining = quota.remaining ?: _usageState.value.remaining,
                dailyLimit = quota.dailyLimit ?: _usageState.value.dailyLimit,
                isApiError = false
            )
        }
    }

    /** 로그인 여부. 회원이면 과금 주체가 device 카운터가 아니라 크레딧이다. */
    private fun isLoggedIn(): Boolean = TokenManager.getAccessToken() != null

    /**
     * 서버가 과금 한도로 거절했으면 결제 화면을 띄우고 true.
     *
     * 429는 IP 한도·분당 제한도 같은 코드라서, 기기 한도(limit_type=device)일 때만 결제 화면이다.
     * 나머지 429는 "잠시 후 다시" 안내라 일반 에러로 보여준다.
     * 기기 한도는 limit_detail로 일일("daily")·평생("lifetime", 실험군 무료 체험)을 구분한다.
     */
    private fun openPaywallIfQuotaDenied(error: ApiResult.Error, pending: PendingSynthesis): Boolean {
        val limitType = when {
            error.code == HairstyleRepository.HTTP_INSUFFICIENT_CREDITS -> "credits"
            error.code == 429 && error.reason == "device" -> "device"
            else -> return false
        }
        val limitDetail = error.limitDetail.takeIf { limitType == "device" }
        if (limitType == "device") {
            val usage = _usageState.value
            _usageState.value = usage.copy(
                remaining = 0,
                isApiError = false,
                // /api/usage를 받기 전에 막힌 경우에도 화면 문구("무료 체험")가 맞게
                freePolicy = if (limitDetail == SynthesisUsageState.FREE_POLICY_LIFETIME) {
                    SynthesisUsageState.FREE_POLICY_LIFETIME
                } else {
                    usage.freePolicy
                }
            )
        }
        _paywall.value = PaywallRequest("exhausted", limitType, pending, limitDetail)
        return true
    }

    /** 결제 화면 닫기 */
    fun dismissPaywall() {
        _paywall.value = null
    }

    /**
     * 결제(또는 로그인 보너스)로 합성할 수 있게 됐을 때 멈췄던 합성을 이어서 실행한다.
     */
    fun resumePendingSynthesis() {
        val pending = _paywall.value?.pending
        _paywall.value = null
        when (pending) {
            is PendingSynthesis.Style -> synthesizeHairstyle(pending.name)
            is PendingSynthesis.Color -> synthesizeHairColor(pending.name, pending.hex)
            null -> Unit
        }
    }

    /**
     * 무료 미리보기 결과의 "이 스타일 고화질로 보기".
     *
     * 회원 합성은 1K 고화질이라, 크레딧이 남은 회원이면 같은 스타일을 바로 다시 합성한다.
     * 비회원이거나 크레딧이 없으면 결제 화면(비회원은 로그인 보너스 안내)을 띄운다.
     *
     * @param memberCredits 회원 잔액. 비로그인이면 null
     */
    fun requestHdUpgrade(pending: PendingSynthesis, memberCredits: Int?) {
        resetSynthesisState()
        resetHairColorSynthesisState()
        if (memberCredits != null && memberCredits > 0) {
            when (pending) {
                is PendingSynthesis.Style -> synthesizeHairstyle(pending.name)
                is PendingSynthesis.Color -> synthesizeHairColor(pending.name, pending.hex)
            }
        } else {
            _paywall.value = PaywallRequest("hd_upgrade", "preview", pending)
        }
    }

    /** 보상형 광고 시청 또는 유료 사용자 → 퍼스널컬러 상세·전체 랭킹 열기 */
    fun unlockDetail() {
        _detailUnlocked.value = true
    }

    /**
     * 클라이언트 측 사전 차단 여부.
     *
     * usageState는 /api/usage(device_id 기준)에서 온다. 이 엔드포인트는 로그인을 아예
     * 모르기 때문에, 게스트로 무료 3회를 쓴 뒤 로그인한 사용자는 크레딧이 남아 있어도
     * remaining=0이 되어 여기서 막혀버렸다. 서버 429 안내문("로그인하면 가입 보너스
     * 5회를 받고 바로 이어서 합성할 수 있어요")과 정면으로 모순된다.
     *
     * 회원은 서버가 크레딧으로 판정하게 넘기고(402면 광고 CTA를 띄운다), device 한도는
     * 비로그인에만 적용한다.
     */
    private fun canSynthesize(): Boolean {
        if (isLoggedIn()) return true
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
            Log.d(TAG, "합성 불가: 무료 횟수 소진 → 결제 화면")
            _paywall.value = PaywallRequest("exhausted", "client_device", PendingSynthesis.Style(hairstyleName))
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
                    AnalyticsHelper.logSynthesisResult("ai_hairstyle", hairstyleName, success = true)
                    applyQuota(result.data)
                    _synthesisState.value = SynthesisUiState.Success(
                        synthesizedImage = result.data.image,
                        hairstyleName = hairstyleName,
                        recommendedProducts = result.data.recommendedProducts,
                        disclosure = result.data.disclosure,
                        isPreview = result.data.isPreview
                    )
                    _synthesisSucceeded.tryEmit(Unit)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "❌ 헤어스타일 합성 실패: ${result.message} (code=${result.code})")
                    AnalyticsHelper.logSynthesisResult(
                        "ai_hairstyle", hairstyleName, success = false, code = result.code
                    )
                    _synthesisState.value =
                        if (openPaywallIfQuotaDenied(result, PendingSynthesis.Style(hairstyleName))) SynthesisUiState.Idle
                        else SynthesisUiState.Error(result.message)
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

    /**
     * 최근 분석 결과 스냅샷 (클릭 요청의 hair_profile용). 분석 전이면 null.
     */
    private fun currentHairProfile(): com.example.myapplication.network.HairProfile? {
        val success = _uiState.value as? AnalysisUiState.Success ?: return null
        return com.example.myapplication.network.HairProfile(
            faceShape = success.data.face_shape,
            personalColor = success.data.skin_tone,
            gender = currentGender
        )
    }

    /**
     * 제휴 제품 카드 탭 → 서버에서 제휴 링크 발급 후 외부 브라우저로 열기.
     *
     * 호출 측(로그인 상태 확인 완료)에서 실행하며, 링크 발급은 반드시 매번 이 API를 경유한다.
     * 실패(네트워크/404 등)는 합성 결과 화면을 깨뜨리지 않도록 onFailure로 조용히 처리.
     *
     * @param onLink 발급된 affiliate_url (메인 스레드에서 호출됨) → 외부 브라우저 오픈
     * @param onFailure 실패 시 (짧은 토스트 등)
     */
    fun openProductLink(
        product: com.example.myapplication.network.RecommendedProduct,
        style: String,
        source: String,
        onLink: (url: String) -> Unit,
        onFailure: () -> Unit
    ) {
        viewModelScope.launch {
            val request = com.example.myapplication.network.ProductClickRequest(
                productId = product.productId,
                style = style,
                source = source,
                hairProfile = currentHairProfile(),
                // 비로그인도 클릭 로그가 남아야 "헤어 프로필 x 제품 클릭" 데이터가
                // 끊기지 않는다. 로그인 상태면 서버가 토큰의 user_id를 우선한다.
                deviceId = deviceId
            )
            when (val result = repository.clickProduct(request)) {
                is ApiResult.Success -> {
                    val url = result.data.affiliateUrl
                    if (!url.isNullOrBlank()) {
                        onLink(url)
                    } else {
                        onFailure()
                    }
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "❌ 제휴 링크 발급 실패: ${result.message} (code=${result.code})")
                    onFailure()
                }
            }
        }
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
            Log.d(TAG, "염색 합성 불가: 무료 횟수 소진 → 결제 화면")
            _paywall.value = PaywallRequest("exhausted", "client_device", PendingSynthesis.Color(colorName, colorHex))
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
                    AnalyticsHelper.logSynthesisResult("hair_color", colorName, success = true)
                    applyQuota(result.data)
                    _hairColorSynthesisState.value = HairColorSynthesisUiState.Success(
                        synthesizedImage = result.data.image,
                        colorName = colorName,
                        colorHex = colorHex,
                        isPreview = result.data.isPreview
                    )
                    _synthesisSucceeded.tryEmit(Unit)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "❌ 염색색 합성 실패: ${result.message} (code=${result.code})")
                    AnalyticsHelper.logSynthesisResult(
                        "hair_color", colorName, success = false, code = result.code
                    )
                    _hairColorSynthesisState.value =
                        if (openPaywallIfQuotaDenied(result, PendingSynthesis.Color(colorName, colorHex))) HairColorSynthesisUiState.Idle
                        else HairColorSynthesisUiState.Error(result.message)
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