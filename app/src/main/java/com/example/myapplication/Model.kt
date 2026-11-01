package com.example.myapplication

import androidx.annotation.Keep

/**
 * 분석 결과 데이터 클래스
 */
@Keep
data class AnalysisResult(
    val face_shape: String,
    val skin_tone: String,
    val recommended_styles: List<HairstyleRecommendation>,
    // 전체 스타일 랭킹 (스타일 이름, 점수 순). 서버 미지원이면 빈 목록
    val full_ranking: List<String> = emptyList()
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
    val imageSearchUrl: String? = null,  // ✅ v27: 네이버 검색 URL (더 이상 UI에서 사용 안 함)
    val source: String = "ml",  // ✅ v35: 추천 소스 ("ml" 또는 "trend")
    val imageUrl: String? = null,  // ✅ v36: AI 예시 이미지 절대 URL (매핑 없으면 null)
    val rank: Int? = null  // 서버 순위 (구버전 서버·히스토리 복원이면 null → 화면 순서로 대체)
)

/**
 * 추천 카드에서 합성으로 들어갔을 때의 추천 맥락.
 * 합성 요청(analysis_id/rec_rank/hairstyle_id)과 결과 저장·공유 신호에 실어 보낸다.
 * 추천 밖 진입(analysis_id 없음)이면 만들지 않는다.
 */
data class StyleRecContext(
    val analysisId: String,
    val recRank: Int?,
    val hairstyleId: Int?
)