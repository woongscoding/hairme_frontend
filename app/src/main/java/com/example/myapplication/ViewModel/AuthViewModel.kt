package com.example.myapplication.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.auth.TokenManager
import com.example.myapplication.network.AuthUser
import com.example.myapplication.repository.ApiResult
import com.example.myapplication.repository.AuthRepository
import com.example.myapplication.util.AnalyticsHelper
import com.example.myapplication.util.BillingManager
import com.example.myapplication.util.PaidUserStore
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 로그인 UI 상태
 */
sealed class AuthUiState {
    /** 앱 시작 직후 저장된 토큰으로 자동 로그인 검증 중 */
    object Initializing : AuthUiState()

    /** 비로그인 (기존 기능은 모두 사용 가능) */
    object LoggedOut : AuthUiState()

    /** 카카오 SDK 로그인 ~ 서버 JWT 교환 진행 중 */
    object LoggingIn : AuthUiState()

    /** 로그인 완료 */
    data class LoggedIn(val user: AuthUser) : AuthUiState()
}

/**
 * AuthViewModel
 *
 * 카카오 SDK 로그인 → 서버 JWT 교환 → 자동 로그인/로그아웃 상태 관리.
 * 기존 AnalysisViewModel과 동일하게 수동 DI (Hilt 재활성화 시 변경)
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository()

    companion object {
        private const val TAG = "AuthViewModel"

        // 광고 보상은 AdMob → 서버 SSV 콜백으로 비동기 지급되므로 바로 조회하면 아직 0일 수 있다
        private const val REWARD_POLL_ATTEMPTS = 5
        private const val REWARD_FIRST_DELAY_MS = 800L
        private const val REWARD_RETRY_DELAY_MS = 1500L
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initializing)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** 일회성 에러 메시지 (Toast 표시 후 consumeError() 호출) */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 신규 가입 환영 다이얼로그용 (null이 아니면 표시) */
    private val _welcomeUser = MutableStateFlow<AuthUser?>(null)
    val welcomeUser: StateFlow<AuthUser?> = _welcomeUser.asStateFlow()

    /** 광고 보상 결과 안내 (스낵바 표시 후 consumeRewardMessage() 호출) */
    private val _rewardMessage = MutableStateFlow<String?>(null)
    val rewardMessage: StateFlow<String?> = _rewardMessage.asStateFlow()

    /** 광고 시청 후 서버 지급을 기다리는 중 (버튼 중복 클릭 방지) */
    private val _isWaitingReward = MutableStateFlow(false)
    val isWaitingReward: StateFlow<Boolean> = _isWaitingReward.asStateFlow()

    /**
     * 한 번이라도 결제한 계정인지. 유료 사용자에게는 광고를 보여주지 않고
     * 첫 구매 상품(starter_5)도 내지 않는다.
     */
    private val _hasPurchased = MutableStateFlow(false)
    val hasPurchased: StateFlow<Boolean> = _hasPurchased.asStateFlow()

    /**
     * 서버가 알려준 첫 구매 상품 구매 가능 여부 (GET /api/credits/products의 available).
     * null(비회원·조회 전·실패)이면 결제 화면은 [hasPurchased]로 판단한다.
     */
    private val _starterAvailable = MutableStateFlow<Boolean?>(null)
    val starterAvailable: StateFlow<Boolean?> = _starterAvailable.asStateFlow()

    /** 결제 결과 안내 (Toast 표시 후 consumePurchaseMessage() 호출) */
    private val _purchaseMessage = MutableStateFlow<String?>(null)
    val purchaseMessage: StateFlow<String?> = _purchaseMessage.asStateFlow()

    /** 결제 지급 완료 신호 - 결제 화면을 닫고 막혔던 합성을 이어서 실행하는 데 쓴다 */
    private val _purchaseGranted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val purchaseGranted: SharedFlow<Unit> = _purchaseGranted.asSharedFlow()

    init {
        tryAutoLogin()

        viewModelScope.launch {
            BillingManager.events.collect { event -> onBillingEvent(event) }
        }

        // Authenticator에서 리프레시 실패로 토큰이 삭제되면 로그아웃 상태로 전환
        viewModelScope.launch {
            TokenManager.hasTokens.collect { hasTokens ->
                if (!hasTokens && _uiState.value is AuthUiState.LoggedIn) {
                    Log.w(TAG, "⚠️ 토큰 만료로 자동 로그아웃")
                    _uiState.value = AuthUiState.LoggedOut
                }
            }
        }
    }

    /**
     * 앱 시작 시 저장된 토큰으로 자동 로그인 (GET /api/auth/me로 검증)
     * 실패해도 비로그인 상태로 진입할 뿐 기능 사용에는 지장 없음
     */
    private fun tryAutoLogin() {
        if (!repository.hasStoredTokens()) {
            _uiState.value = AuthUiState.LoggedOut
            return
        }
        viewModelScope.launch {
            when (val result = repository.getMe()) {
                is ApiResult.Success -> {
                    Log.d(TAG, "✅ 자동 로그인 성공: ${result.data.nickname}")
                    onLoggedIn(result.data)
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "❌ 자동 로그인 실패 (${result.code}): ${result.message}")
                    // 401 = 리프레시까지 만료 → 토큰 폐기. 네트워크 오류면 토큰 유지 (다음 실행 때 재시도)
                    if (result.code == 401) {
                        repository.logout()
                    }
                    _uiState.value = AuthUiState.LoggedOut
                }
            }
        }
    }

    /**
     * 카카오 로그인 시작
     * 카카오톡 설치 시 카카오톡으로, 실패/미설치 시 카카오계정(웹)으로 폴백.
     * 사용자가 명시적으로 취소한 경우는 폴백하지 않고 조용히 복귀.
     *
     * @param context Activity Context (카카오톡 앱 전환에 필요)
     */
    fun loginWithKakao(context: Context) {
        if (_uiState.value is AuthUiState.LoggingIn) return
        _uiState.value = AuthUiState.LoggingIn

        val accountLoginCallback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            when {
                error is ClientError && error.reason == ClientErrorCause.Cancelled -> {
                    // 사용자 취소: 조용히 복귀
                    Log.d(TAG, "카카오계정 로그인 취소")
                    _uiState.value = AuthUiState.LoggedOut
                }
                error != null -> {
                    Log.e(TAG, "❌ 카카오계정 로그인 실패: ${error.message}")
                    _uiState.value = AuthUiState.LoggedOut
                    _errorMessage.value = "카카오 로그인에 실패했어요. 잠시 후 다시 시도해주세요"
                }
                token != null -> exchangeKakaoToken(token.accessToken)
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                when {
                    error is ClientError && error.reason == ClientErrorCause.Cancelled -> {
                        // 사용자가 명시적으로 취소 → 폴백하지 않음
                        Log.d(TAG, "카카오톡 로그인 취소")
                        _uiState.value = AuthUiState.LoggedOut
                    }
                    error != null -> {
                        // 카카오톡 로그인 실패 (미연결 계정 등) → 카카오계정 로그인 폴백
                        Log.w(TAG, "카카오톡 로그인 실패, 카카오계정으로 폴백: ${error.message}")
                        UserApiClient.instance.loginWithKakaoAccount(context, callback = accountLoginCallback)
                    }
                    token != null -> exchangeKakaoToken(token.accessToken)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(context, callback = accountLoginCallback)
        }
    }

    /**
     * 카카오 액세스 토큰 → 서버 JWT 교환
     */
    private fun exchangeKakaoToken(kakaoAccessToken: String) {
        viewModelScope.launch {
            when (val result = repository.loginWithKakaoToken(kakaoAccessToken)) {
                is ApiResult.Success -> {
                    AnalyticsHelper.logLogin(result.data.isNewUser)
                    onLoggedIn(result.data.user)
                    if (result.data.isNewUser) {
                        _welcomeUser.value = result.data.user
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = AuthUiState.LoggedOut
                    _errorMessage.value = result.message
                }
            }
        }
    }

    /**
     * 로그아웃: 카카오 SDK 세션 종료 + 로컬 JWT 삭제
     */
    fun logout() {
        UserApiClient.instance.logout { error ->
            // SDK 로그아웃 실패해도 로컬 토큰은 무조건 삭제 (SDK가 내부 토큰은 폐기함)
            if (error != null) {
                Log.w(TAG, "카카오 SDK 로그아웃 오류 (무시): ${error.message}")
            }
        }
        repository.logout()
        BillingManager.setCurrentUser(null)
        _uiState.value = AuthUiState.LoggedOut
        _hasPurchased.value = false
        _starterAvailable.value = null
    }

    /**
     * 로그인 직후 공통 처리: 유료 여부 복원 → 서버 재판정, 그리고 로그인 전에 끝난
     * (지급 대상을 몰라 보류된) 결제를 서버로 다시 보낸다.
     */
    private fun onLoggedIn(user: AuthUser) {
        _uiState.value = AuthUiState.LoggedIn(user)
        _hasPurchased.value = PaidUserStore.isPaid(user.userId)
        // 복구는 이 계정의 결제만 서버에 보낸다 (계정을 바꾼 기기에서 앞 계정 결제가 넘어오지 않게)
        BillingManager.setCurrentUser(user.userId)
        BillingManager.recoverPurchases()
        refreshCreditStatus()
        refreshStarterAvailability()
    }

    /** 첫 구매 상품 노출 여부를 서버 기준으로 다시 받는다 (결제 화면을 열 때·결제 결과 뒤) */
    fun refreshStarterAvailability() {
        if (_uiState.value !is AuthUiState.LoggedIn) return
        viewModelScope.launch {
            val available = repository.getStarterAvailable() ?: return@launch
            if (_uiState.value is AuthUiState.LoggedIn) _starterAvailable.value = available
        }
    }

    /** 잔액·유료 여부를 서버 기준으로 다시 맞춘다 */
    private fun refreshCreditStatus() {
        viewModelScope.launch {
            val result = repository.getCreditStatus()
            if (result !is ApiResult.Success) return@launch
            val state = _uiState.value as? AuthUiState.LoggedIn ?: return@launch
            _uiState.value = AuthUiState.LoggedIn(state.user.copy(credits = result.data.balance))
            _hasPurchased.value = result.data.hasPurchased
            PaidUserStore.setPaid(state.user.userId, result.data.hasPurchased)
        }
    }

    /**
     * 합성 응답의 quota.balance로 잔액을 바로 맞춘다 (별도 조회 없이 화면의 "N개" 갱신).
     */
    fun updateCredits(balance: Int) {
        val state = _uiState.value as? AuthUiState.LoggedIn ?: return
        if (state.user.credits != balance) {
            _uiState.value = AuthUiState.LoggedIn(state.user.copy(credits = balance))
        }
    }

    /**
     * 크레딧 상품 결제. 결과는 [BillingManager.events]로 돌아온다.
     */
    fun purchase(activity: Activity, productId: String, placement: String) {
        val user = (_uiState.value as? AuthUiState.LoggedIn)?.user
        if (user == null) {
            _purchaseMessage.value = "로그인 후 구매할 수 있어요"
            return
        }
        BillingManager.launch(activity, productId, user.userId, placement)
    }

    private fun onBillingEvent(event: BillingManager.BillingEvent) {
        when (event) {
            is BillingManager.BillingEvent.Granted -> {
                val state = _uiState.value as? AuthUiState.LoggedIn
                if (state != null) {
                    _hasPurchased.value = true
                    PaidUserStore.setPaid(state.user.userId, true)
                    if (event.balance != null) updateCredits(event.balance) else refreshCreditStatus()
                }
                if (event.granted > 0) {
                    _purchaseMessage.value = "크레딧 ${event.granted}개가 충전됐어요"
                }
                if (event.productId == BillingManager.SKU_STARTER_5) _starterAvailable.value = false
                _purchaseGranted.tryEmit(Unit)
            }
            BillingManager.BillingEvent.Pending ->
                _purchaseMessage.value = "결제가 대기 중이에요. 결제가 끝나면 크레딧이 자동으로 들어와요"
            BillingManager.BillingEvent.Cancelled -> Unit
            is BillingManager.BillingEvent.Failed -> {
                _purchaseMessage.value = event.message
                // 첫 구매 상품 재구매 거절(자동 환불)이면 결제 화면에서 그 상품을 내린다
                refreshStarterAvailability()
            }
        }
    }

    fun consumePurchaseMessage() {
        _purchaseMessage.value = null
    }

    /**
     * 보상형 광고 시청 완료 후 크레딧 반영 대기 + 갱신
     *
     * 크레딧을 지급하는 주체는 앱이 아니라 서버(AdMob SSV 콜백)다.
     * 시청 직후에는 아직 반영 전일 수 있어 잔액이 오를 때까지 몇 번 재조회한다.
     */
    fun refreshCreditsAfterReward() {
        val before = (_uiState.value as? AuthUiState.LoggedIn)?.user?.credits ?: return
        _isWaitingReward.value = true

        viewModelScope.launch {
            try {
                repeat(REWARD_POLL_ATTEMPTS) { attempt ->
                    delay(if (attempt == 0) REWARD_FIRST_DELAY_MS else REWARD_RETRY_DELAY_MS)

                    val result = repository.getCreditBalance()
                    if (result !is ApiResult.Success) return@repeat

                    val state = _uiState.value as? AuthUiState.LoggedIn ?: return@launch
                    _uiState.value = AuthUiState.LoggedIn(state.user.copy(credits = result.data))

                    if (result.data > before) {
                        Log.d(TAG, "✅ 광고 보상 반영: $before → ${result.data}")
                        _rewardMessage.value = "크레딧 1개가 지급됐어요!"
                        return@launch
                    }
                }
                // 일일 상한(서버 REWARD_AD_DAILY_LIMIT=3)에 걸렸거나 지급이 지연된 경우
                Log.w(TAG, "⚠️ 광고 보상이 아직 반영되지 않음")
                _rewardMessage.value =
                    "지급이 조금 지연되고 있어요. 잠시 후 확인해 주세요. (하루 3회까지 받을 수 있어요)"
            } finally {
                _isWaitingReward.value = false
            }
        }
    }

    /**
     * 내 정보 재조회 (크레딧 갱신 등)
     */
    fun refreshUser() {
        if (_uiState.value !is AuthUiState.LoggedIn) return
        viewModelScope.launch {
            val result = repository.getMe()
            if (result is ApiResult.Success) {
                _uiState.value = AuthUiState.LoggedIn(result.data)
            }
        }
    }

    fun consumeError() {
        _errorMessage.value = null
    }

    fun consumeWelcome() {
        _welcomeUser.value = null
    }

    fun consumeRewardMessage() {
        _rewardMessage.value = null
    }
}
