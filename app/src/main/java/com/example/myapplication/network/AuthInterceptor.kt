package com.example.myapplication.network

import android.util.Log
import com.example.myapplication.data.auth.TokenManager
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Hairstyle Lambda용 인증 인터셉터
 *
 * 서버 JWT가 저장되어 있으면 Authorization: Bearer 헤더를 자동 첨부한다.
 * ⚠️ 카카오 로컬 API용 클라이언트(KakaoAK 헤더)에는 절대 붙이지 말 것.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val accessToken = TokenManager.getAccessToken()
        return if (accessToken != null && request.header("Authorization") == null) {
            chain.proceed(
                request.newBuilder()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
            )
        } else {
            chain.proceed(request)
        }
    }
}

/**
 * 401 응답 시 /api/auth/refresh로 access 토큰을 자동 갱신하는 Authenticator
 *
 * - 갱신 성공: 새 토큰으로 원래 요청 1회 재시도
 * - 갱신 실패(리프레시 토큰 만료): 토큰 삭제 → 로그아웃 처리 (TokenManager.hasTokens로 전파)
 * - 네트워크 오류: 토큰 유지 (다음 요청에서 재시도)
 */
class TokenAuthenticator : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath

        // 로그인/리프레시 자체의 401은 갱신 대상이 아님 (카카오 토큰 무효/리프레시 만료)
        if (path == "/api/auth/kakao" || path == "/api/auth/refresh") return null

        // 재시도는 1회만 (무한 루프 방지)
        if (responseCount(response) >= 2) return null

        val refreshToken = TokenManager.getRefreshToken() ?: return null

        synchronized(this) {
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val currentToken = TokenManager.getAccessToken()

            // 다른 스레드가 이미 갱신을 끝낸 경우: 새 토큰으로 바로 재시도
            if (currentToken != null && currentToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            return try {
                Log.d(TAG, "🔄 access 토큰 만료 → 리프레시 시도")
                val refreshResponse = RetrofitClient.tokenRefreshApiService
                    .refreshTokenSync(RefreshRequest(refreshToken))
                    .execute()
                val newAccessToken = refreshResponse.body()?.accessToken

                if (refreshResponse.isSuccessful && newAccessToken != null) {
                    Log.d(TAG, "✅ 토큰 갱신 성공")
                    TokenManager.updateAccessToken(newAccessToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                } else {
                    Log.w(TAG, "❌ 토큰 갱신 실패 (HTTP ${refreshResponse.code()}) → 로그아웃 처리")
                    TokenManager.clearTokens()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 토큰 갱신 중 네트워크 오류: ${e.message}")
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
