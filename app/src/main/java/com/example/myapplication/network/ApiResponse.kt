package com.example.myapplication.network

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 백엔드 API 응답을 파싱하기 위한 데이터 클래스들
 * v20: analysis_id 추가 (피드백 기능)
 */

/**
 * API 전체 응답 구조
 *
 * 백엔드 응답 예시:
 * {
 *   "success": true,
 *   "analysis_id": 123,
 *   "data": { ... },
 *   "error": null,
 *   "processing_time": 10.94,
 *   "model_used": "gemini-flash-latest"
 * }
 */
@Keep
data class ApiResponse(
    val success: Boolean,
    val analysis_id: String? = null,  // ✅ v26: DynamoDB UUID 지원 (Int → String)
    val data: AnalysisData? = null,
    val error: String? = null,
    val processing_time: Double? = null,
    val model_used: String? = null
)

/**
 * 분석 데이터 구조
 *
 * 백엔드 data 필드:
 * {
 *   "analysis": { ... },
 *   "recommendations": [ ... ]
 * }
 */
@Keep
data class AnalysisData(
    val analysis: Analysis,
    val recommendations: List<Recommendation>
) {
    /**
     * 앱의 AnalysisResult로 변환
     */
    fun toAnalysisResult(): com.example.myapplication.AnalysisResult {
        return com.example.myapplication.AnalysisResult(
            face_shape = analysis.face_shape,
            skin_tone = analysis.personal_color,
            recommended_styles = recommendations.map { it.toHairstyleRecommendation() }
        )
    }
}

/**
 * 얼굴 분석 정보
 *
 * 백엔드 analysis 필드:
 * {
 *   "face_shape": "계란형",
 *   "personal_color": "쿨톤",
 *   "features": "이목구비가 뚜렷하고..."
 * }
 */
@Keep
data class Analysis(
    val face_shape: String,
    val personal_color: String,
    val features: String
)

/**
 * 헤어스타일 추천 정보
 *
 * 백엔드 recommendations 배열 요소:
 * {
 *   "hairstyle_id": 123,
 *   "style_name": "시스루뱅 단발펌",
 *   "reason": "계란형 얼굴을 가장 돋보이게 하며...",
 *   "score": 0.95,
 *   "image_search_url": "https://search.naver.com/..."
 * }
 */
@Keep
data class Recommendation(
    val hairstyle_id: Int? = null,  // ✅ v23: 백엔드 DB의 헤어스타일 ID (0-446)
    val style_name: String,
    val reason: String,
    val score: Double? = null,  // ✅ v23: ML 예측 점수
    val image_search_url: String? = null,  // ✅ v20: 네이버 이미지 검색 URL
    val source: String = "ml"  // ✅ v35: 추천 소스 ("ml" 또는 "trend")
) {
    /**
     * 앱의 HairstyleRecommendation으로 변환
     * v27: image_search_url 매핑 추가
     * v35: source 필드 매핑 추가
     */
    fun toHairstyleRecommendation(): com.example.myapplication.HairstyleRecommendation {
        return com.example.myapplication.HairstyleRecommendation(
            id = hairstyle_id,  // ✅ v23: ID 매핑
            name = style_name,
            score = score, // ✅ v35: nullable 그대로 전달 (트렌드는 null)
            reason = reason,
            imageSearchUrl = image_search_url,  // ✅ v27: 네이버 검색 URL (성별 접두사 포함)
            source = source  // ✅ v35: 추천 소스 전달
        )
    }
}

// ✅ v26: 피드백 API 요청 모델 (DynamoDB UUID 지원)
// 백엔드가 요구하는 필드: analysis_id, style_index, feedback, naver_clicked
@Keep
data class FeedbackRequest(
    val analysis_id: String,     // 분석 요청 ID - DynamoDB UUID (ApiResponse.analysis_id)
    val style_index: Int,        // 추천 헤어스타일 인덱스 (1, 2, 3) - 1-based index!
    val feedback: String,        // "like" 또는 "dislike"
    val naver_clicked: Boolean = false  // 네이버 검색 클릭 여부 (기본값: false)
)

// ✅ v26: 피드백 API 응답 모델 (DynamoDB UUID 지원)
@Keep
data class FeedbackResponse(
    val success: Boolean,
    val message: String,
    val analysis_id: String? = null,  // DynamoDB UUID
    val style_index: Int? = null
)

// ✅ v30: 헤어스타일 합성 API 응답 모델
@Keep
data class SynthesisResponse(
    val success: Boolean,
    @SerializedName("image_base64")
    val imageBase64: String? = null,  // Base64 인코딩된 합성 이미지
    @SerializedName("image_format")
    val imageFormat: String? = null,  // 이미지 포맷 (png, jpg 등)
    val message: String? = null,
    @SerializedName("processing_time")
    val processingTime: Double? = null
)