package com.example.myapplication.repository

import android.util.Log
import com.example.myapplication.data.auth.TokenManager
import com.example.myapplication.network.AuthResponse
import com.example.myapplication.network.AuthUser
import com.example.myapplication.network.ConsentRequest
import com.example.myapplication.network.KakaoLoginRequest
import com.example.myapplication.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * AuthRepository
 *
 * 카카오 액세스 토큰 ↔ 서버 JWT 교환, 내 정보 조회, 로그아웃 처리.
 * 기존 HairstyleRepository와 동일하게 수동 DI (Hilt 재활성화 시 생성자 주입으로 변경)
 */
class AuthRepository {

    private val authApiService by lazy {
        RetrofitClient.authApiService
    }

    companion object {
        private const val TAG = "AuthRepository"
        private const val NETWORK_ERROR_MESSAGE = "네트워크 연결을 확인해주세요"
    }

    /**
     * 카카오 SDK 액세스 토큰을 서버로 보내 자체 JWT 발급 (가입 겸용)
     * 성공 시 토큰을 EncryptedSharedPreferences에 저장
     */
    suspend fun loginWithKakaoToken(kakaoAccessToken: String): ApiResult<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 카카오 토큰 → 서버 JWT 교환 시작")
            val response = authApiService.kakaoLogin(KakaoLoginRequest(kakaoAccessToken))
            val body = response.body()

            if (response.isSuccessful && body != null) {
                TokenManager.saveTokens(body.accessToken, body.refreshToken)
                Log.d(TAG, "✅ 로그인 성공 - nickname=${body.user.nickname}, isNewUser=${body.isNewUser}")
                ApiResult.Success(body)
            } else {
                Log.e(TAG, "❌ 로그인 실패 - HTTP ${response.code()}")
                ApiResult.Error(loginErrorMessage(response.code()), response.code())
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ 네트워크 오류: ${e.message}")
            ApiResult.Error(NETWORK_ERROR_MESSAGE)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 로그인 중 오류: ${e.message}")
            ApiResult.Error("로그인 처리 중 문제가 발생했어요. 잠시 후 다시 시도해주세요")
        }
    }

    /**
     * 저장된 JWT로 내 정보 조회 (자동 로그인 검증)
     * access 만료 시 Authenticator가 자동으로 리프레시 후 재시도한다
     */
    suspend fun getMe(): ApiResult<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val response = authApiService.getMe()
            val body = response.body()

            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.user)
            } else {
                Log.w(TAG, "❌ 내 정보 조회 실패 - HTTP ${response.code()}")
                ApiResult.Error("로그인이 만료되었어요. 다시 로그인해주세요", response.code())
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ 네트워크 오류: ${e.message}")
            ApiResult.Error(NETWORK_ERROR_MESSAGE)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 내 정보 조회 중 오류: ${e.message}")
            ApiResult.Error("사용자 정보를 불러오지 못했어요")
        }
    }

    /**
     * 학습 데이터 활용 동의 변경
     */
    suspend fun updateConsent(trainingConsent: Boolean): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = authApiService.updateConsent(ConsentRequest(trainingConsent))
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("동의 설정 변경에 실패했어요", response.code())
            }
        } catch (e: IOException) {
            ApiResult.Error(NETWORK_ERROR_MESSAGE)
        } catch (e: Exception) {
            ApiResult.Error("동의 설정 변경 중 문제가 발생했어요")
        }
    }

    /**
     * 로컬 JWT 삭제 (카카오 SDK 로그아웃은 ViewModel에서 별도 호출)
     */
    fun logout() {
        TokenManager.clearTokens()
        Log.d(TAG, "✅ 로컬 토큰 삭제 완료")
    }

    fun hasStoredTokens(): Boolean = TokenManager.getAccessToken() != null

    private fun loginErrorMessage(code: Int): String = when (code) {
        401 -> "카카오 인증이 만료되었어요. 다시 로그인해주세요"
        429 -> "요청이 너무 많아요. 잠시 후 다시 시도해주세요"
        503 -> "카카오 서비스가 일시적으로 불안정해요. 잠시 후 다시 시도해주세요"
        else -> "로그인에 실패했어요. 잠시 후 다시 시도해주세요 (오류 $code)"
    }
}
