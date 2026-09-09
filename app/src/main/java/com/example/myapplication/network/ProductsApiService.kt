package com.example.myapplication.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 제휴 제품(쿠팡파트너스) API 인터페이스
 *
 * Hairstyle Lambda에 호스팅되며 JWT 인증 클라이언트(hairstyleRetrofit)로 생성한다.
 * - click: Authorization: Bearer 필수 (AuthInterceptor가 자동 첨부)
 * - recommendations: 인증 불필요 (토큰이 있어도 첨부되지만 무해)
 */
interface ProductsApiService {

    /**
     * 스타일 맞춤 추천 조회 (인증 불필요, 선택 사용)
     */
    @GET("/api/products/recommendations")
    suspend fun getRecommendations(
        @Query("style") style: String,
        @Query("gender") gender: String
    ): Response<ProductRecommendationsResponse>

    /**
     * 클릭 → 제휴 링크 발급 (핵심)
     *
     * 반드시 매번 이 엔드포인트를 경유해야 서버가 클릭 로그를 남긴다.
     * - 200: affiliate_url 발급
     * - 404: 존재하지 않는 제품 (구버전 캐시) → 무시하고 토스트
     */
    @POST("/api/products/click")
    suspend fun clickProduct(
        @Body request: ProductClickRequest
    ): Response<ProductClickResponse>
}
