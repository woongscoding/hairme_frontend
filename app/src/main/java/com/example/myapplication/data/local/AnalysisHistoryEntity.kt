package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.myapplication.AnalysisResult
import com.example.myapplication.HairstyleRecommendation

/**
 * 분석 히스토리를 저장하는 Room Entity
 *
 * @property id 자동 생성 Primary Key
 * @property analysisId 서버에서 받은 분석 ID
 * @property faceShape 얼굴형
 * @property skinTone 피부톤
 * @property recommendations 추천 헤어스타일 (JSON으로 저장)
 * @property timestamp 분석 시간
 */
@Entity(tableName = "analysis_history")
data class AnalysisHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val analysisId: String?,  // ✅ v26: DynamoDB UUID 지원 (Int → String)
    val faceShape: String,
    val skinTone: String,
    val recommendations: String, // JSON 문자열로 저장
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Entity를 AnalysisResult로 변환
     */
    fun toAnalysisResult(): AnalysisResult {
        // JSON 문자열을 List<HairstyleRecommendation>으로 파싱
        val recommendationsList = parseRecommendations(recommendations)
        return AnalysisResult(
            face_shape = faceShape,
            skin_tone = skinTone,
            recommended_styles = recommendationsList
        )
    }

    companion object {
        /**
         * AnalysisResult를 Entity로 변환
         */
        fun fromAnalysisResult(
            analysisId: String?,  // ✅ v26: DynamoDB UUID 지원
            result: AnalysisResult
        ): AnalysisHistoryEntity {
            val recommendationsJson = result.recommended_styles.joinToString("|||") { rec ->
                "${rec.name}::${rec.score ?: ""}::${rec.reason}::${rec.source}"
            }
            return AnalysisHistoryEntity(
                analysisId = analysisId,
                faceShape = result.face_shape,
                skinTone = result.skin_tone,
                recommendations = recommendationsJson
            )
        }

        /**
         * JSON 문자열을 List<HairstyleRecommendation>으로 파싱
         */
        private fun parseRecommendations(json: String): List<HairstyleRecommendation> {
            if (json.isBlank()) return emptyList()

            return json.split("|||").mapNotNull { item ->
                val parts = item.split("::")
                if (parts.size >= 3) {
                    HairstyleRecommendation(
                        name = parts[0],
                        score = parts[1].toDoubleOrNull(), // ✅ v35: nullable (빈 문자열→null)
                        reason = parts[2],
                        source = if (parts.size >= 4) parts[3] else "ml" // ✅ v35: 하위 호환
                    )
                } else null
            }
        }
    }
}
