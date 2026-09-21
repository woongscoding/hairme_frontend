package com.example.myapplication.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * 크레딧 API (JWT 필요 - hairstyleRetrofit의 AuthInterceptor가 자동 첨부)
 *
 * 보상형 광고 시청 후 지급은 AdMob → 서버 SSV 콜백 경로로 이뤄지므로,
 * 앱은 지급을 요청하지 않고 잔액만 다시 조회한다.
 */
interface CreditsApiService {

    @GET("/api/credits")
    suspend fun getCredits(): Response<CreditsResponse>
}

data class CreditsResponse(
    val balance: Int = 0
)
