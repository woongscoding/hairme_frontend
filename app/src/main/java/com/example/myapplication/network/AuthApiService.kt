package com.example.myapplication.network

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

/**
 * 인증 API 인터페이스 (Hairstyle Lambda 호스팅)
 *
 * 서버 흐름: 앱이 카카오 SDK로 로그인해서 받은 액세스 토큰을 서버로 보내면,
 * 서버가 검증 후 자체 JWT(access + refresh)를 발급한다.
 */
interface AuthApiService {

    /**
     * 카카오 로그인 (가입 겸용, rate limit 10/min)
     * - 401: 카카오 토큰 무효 → 재로그인 유도
     * - 503: 카카오 API 장애 → 잠시 후 재시도 안내
     */
    @POST("/api/auth/kakao")
    suspend fun kakaoLogin(
        @Body request: KakaoLoginRequest
    ): Response<AuthResponse>

    /**
     * 내 정보 조회 (Authorization: Bearer 필요 - 인터셉터가 자동 첨부)
     */
    @GET("/api/auth/me")
    suspend fun getMe(): Response<MeResponse>

    /**
     * 학습 데이터 활용 동의 변경 (Bearer 필요)
     */
    @PATCH("/api/auth/me/consent")
    suspend fun updateConsent(
        @Body request: ConsentRequest
    ): Response<Unit>
}

/**
 * 토큰 갱신 전용 인터페이스
 *
 * OkHttp Authenticator에서 동기(Call.execute()) 호출해야 하므로 별도 분리.
 * 인증 인터셉터가 없는 기본 OkHttp 클라이언트로 생성해야 함 (무한 재귀 방지).
 */
interface TokenRefreshApiService {

    @POST("/api/auth/refresh")
    fun refreshTokenSync(
        @Body request: RefreshRequest
    ): Call<RefreshResponse>
}

// ================================
// 요청 DTO
// ================================

data class KakaoLoginRequest(
    @SerializedName("kakao_access_token")
    val kakaoAccessToken: String
)

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class ConsentRequest(
    @SerializedName("training_consent")
    val trainingConsent: Boolean
)

// ================================
// 응답 DTO
// ================================

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("token_type")
    val tokenType: String = "bearer",
    @SerializedName("is_new_user")
    val isNewUser: Boolean = false,
    val user: AuthUser
)

data class RefreshResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String = "bearer"
)

data class MeResponse(
    val user: AuthUser
)

data class AuthUser(
    @SerializedName("user_id")
    val userId: String,
    val nickname: String? = null,
    val email: String? = null,
    val credits: Int = 0,
    @SerializedName("training_consent")
    val trainingConsent: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String? = null
)
