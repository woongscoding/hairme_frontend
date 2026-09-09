package com.example.myapplication.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.auth.TokenManager
import com.example.myapplication.network.AuthUser
import com.example.myapplication.repository.ApiResult
import com.example.myapplication.repository.AuthRepository
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.flow.MutableStateFlow
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
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initializing)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** 일회성 에러 메시지 (Toast 표시 후 consumeError() 호출) */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 신규 가입 환영 다이얼로그용 (null이 아니면 표시) */
    private val _welcomeUser = MutableStateFlow<AuthUser?>(null)
    val welcomeUser: StateFlow<AuthUser?> = _welcomeUser.asStateFlow()

    init {
        tryAutoLogin()

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
                    _uiState.value = AuthUiState.LoggedIn(result.data)
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
                    _uiState.value = AuthUiState.LoggedIn(result.data.user)
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
        _uiState.value = AuthUiState.LoggedOut
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
}
