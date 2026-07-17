package com.example.myapplication

import androidx.annotation.Keep

/**
 * 분석 결과 데이터 클래스
 */
@Keep
data class AnalysisResult(
    val face_shape: String,
    val skin_tone: String,
    val recommended_styles: List<HairstyleRecommendation>
)

/**
 * 헤어스타일 추천 정보
 * v23: hairstyle_id 추가 (MLOps 피드백용)
 * v27: image_search_url 추가 (성별 접두사 포함)
 * v35: source, score nullable 추가 (트렌드 스타일 지원)
 */
@Keep
data class HairstyleRecommendation(
    val id: Int? = null,    // ✅ v23: 백엔드 DB의 헤어스타일 ID (0-446)
    val name: String,
    val score: Double? = null,  // ✅ v35: nullable (트렌드 스타일은 score 없음)
    val reason: String,
    val imageSearchUrl: String? = null,  // ✅ v27: 네이버 검색 URL (성별 접두사 포함)
    val source: String = "ml"  // ✅ v35: 추천 소스 ("ml" 또는 "trend")
)