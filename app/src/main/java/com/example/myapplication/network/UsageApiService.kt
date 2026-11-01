package com.example.myapplication.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface UsageApiService {

    @GET("/api/usage")
    suspend fun getUsage(
        @Query("device_id") deviceId: String
    ): Response<UsageResponse>
}

/**
 * 비로그인 무료 합성 한도.
 *
 * free_policy / experiment_arm / signup_bonus 는 무료 정책 실험(신규 기기 50:50)용 필드다.
 * 서버가 아직 내려주지 않으면 null이고, 앱은 기존 정책(매일 N회, 보상형=크레딧, 가입 보너스 5)으로 동작한다.
 */
data class UsageResponse(
    @SerializedName("daily_limit")
    val dailyLimit: Int,
    val used: Int,
    val remaining: Int,
    /** "daily"(매일 초기화) | "lifetime"(평생 한도) */
    @SerializedName("free_policy")
    val freePolicy: String? = null,
    /** "control" | "treatment" - 실험군이면 보상형 광고가 크레딧 대신 무료 기능을 연다 */
    @SerializedName("experiment_arm")
    val experimentArm: String? = null,
    /** 로그인 시 받는 보너스 횟수 (결제 화면·로그인 유도 문구용) */
    @SerializedName("signup_bonus")
    val signupBonus: Int? = null
)
