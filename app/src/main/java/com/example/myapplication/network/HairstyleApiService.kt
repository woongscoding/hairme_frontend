package com.example.myapplication.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Hairstyle API 인터페이스
 *
 * 백엔드 FastAPI 서버와 통신하기 위한 Retrofit 인터페이스
 * v20: 피드백 API 추가
 * v30: 헤어스타일 합성 API 추가
 * v33: 퍼스널컬러 기반 염색색 추천 API 추가
 * v34: 염색색 합성 API 추가
 */
interface HairstyleApiService {

    /**
     * 얼굴 사진 분석 API 호출 (Hybrid: Gemini + ML)
     */
    @Multipart
    @POST("/api/v2/analyze-hybrid")
    suspend fun analyzeHairstyle(
        @Part file: MultipartBody.Part,
        @Part gender: MultipartBody.Part
    ): Response<ApiResponse>

    /**
     * 피드백 제출 API 호출
     */
    @POST("/api/feedback/submit")
    suspend fun submitFeedback(
        @Body request: FeedbackRequest
    ): Response<FeedbackResponse>

    /**
     * 헤어스타일 합성 API 호출
     */
    @Multipart
    @POST("/api/v2/synthesize")
    suspend fun synthesizeHairstyle(
        @Part file: MultipartBody.Part,
        @Part hairstyleName: MultipartBody.Part,
        @Part gender: MultipartBody.Part,
        @Part deviceId: MultipartBody.Part
    ): Response<SynthesisResponse>

    /**
     * v33: 퍼스널컬러 기반 염색색 추천 API
     */
    @GET("/api/hair-color/{personal_color}")
    suspend fun getHairColorRecommendations(
        @Path("personal_color") personalColor: String
    ): Response<HairColorRecommendationResponse>

    /**
     * v34: 염색색 합성 API 호출
     */
    @Multipart
    @POST("/api/hair-color/synthesize")
    suspend fun synthesizeHairColor(
        @Part file: MultipartBody.Part,
        @Part colorName: MultipartBody.Part,
        @Part colorHex: MultipartBody.Part,
        @Part deviceId: MultipartBody.Part
    ): Response<HairColorSynthesisResponse>
}

/**
 * v33: 염색색 추천 응답
 */
data class HairColorRecommendationResponse(
    val success: Boolean,
    val personal_color: String,
    val recommended: List<HairColorItem> = emptyList(),
    val avoid: List<AvoidColorItem> = emptyList()
)

/**
 * 추천 염색색 아이템
 */
data class HairColorItem(
    val name: String,
    val hex: String,
    val level: String,
    val description: String = "",
    val suitable_for: List<String> = emptyList()
)

/**
 * 피해야 할 염색색
 */
data class AvoidColorItem(
    val name: String,
    val reason: String
)

/**
 * v34: 염색색 합성 응답
 */
data class HairColorSynthesisResponse(
    val success: Boolean,
    val image_base64: String? = null,
    val image_format: String? = null,
    val message: String? = null,
    val color_name: String? = null,
    val color_hex: String? = null,
    val processing_time: Double? = null
)
