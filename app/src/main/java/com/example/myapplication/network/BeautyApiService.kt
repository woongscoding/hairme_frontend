package com.example.myapplication.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Beauty API 인터페이스
 *
 * Beauty Lambda (경량, PyTorch 없음)와 통신
 * 2025-12-31: Lambda 분리로 인해 신규 생성
 *
 * 엔드포인트:
 * - 염색색 추천/합성
 * - 퍼스널컬러 분석
 * - 종합 뷰티 분석
 */
interface BeautyApiService {

    /**
     * 퍼스널컬러 기반 염색색 추천 API
     */
    @GET("/api/hair-color/{personal_color}")
    suspend fun getHairColorRecommendations(
        @Path("personal_color") personalColor: String
    ): Response<HairColorRecommendationResponse>

    /**
     * 염색색 합성 API 호출
     */
    @Multipart
    @POST("/api/hair-color/synthesize")
    suspend fun synthesizeHairColor(
        @Part file: MultipartBody.Part,
        @Part colorName: MultipartBody.Part,
        @Part colorHex: MultipartBody.Part,
        @Part deviceId: MultipartBody.Part
    ): Response<HairColorSynthesisResponse>

    /**
     * 퍼스널컬러 타입 목록 조회
     */
    @GET("/api/personal-color/types")
    suspend fun getPersonalColorTypes(): Response<PersonalColorTypesResponse>
}

/**
 * 퍼스널컬러 타입 목록 응답
 */
data class PersonalColorTypesResponse(
    val success: Boolean,
    val types: List<PersonalColorType> = emptyList()
)

/**
 * 퍼스널컬러 타입 정보
 */
data class PersonalColorType(
    val type: String,
    val korean_name: String,
    val english_name: String,
    val season: String,
    val tone: String,
    val description: String
)
