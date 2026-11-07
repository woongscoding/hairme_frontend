package com.example.myapplication.network

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 체형별 아이템 추천 (POST /api/fashion/body-analyze) 응답 모델
 *
 * 전신 사진 한 장으로 체형을 판정하고 어울리는 핏/피할 핏을 돌려준다.
 * 추천 항목의 productId 가 있으면 쿠팡 검색 링크가 있는 것이고, 링크는
 * /api/products/click 을 거쳐서만 받는다 (클릭 로그). 없으면 스타일링 팁이다.
 *
 * 구버전 서버 호환: 필드가 빠져도 크래시 없이 파싱되도록 기본값을 둔다.
 */

/** 추천/피할 핏 1개. imageUrl 은 키워드 대표 이미지(AI 생성 예시, 실제 상품 아님) */
@Keep
data class BodyFit(
    val keyword: String = "",
    val reason: String = "",
    @SerializedName("product_id")
    val productId: String? = null,
    @SerializedName("image_url")
    val imageUrl: String? = null
)

/** 보조 체형 표시 (예: 상체 긴 비율) */
@Keep
data class BodySecondary(
    val key: String = "",
    val label: String = "",
    val description: String = ""
)

@Keep
data class BodyAnalysisResponse(
    val success: Boolean = false,
    @SerializedName("body_type")
    val bodyType: String = "",
    @SerializedName("body_type_label")
    val bodyTypeLabel: String = "",
    val description: String = "",
    val secondary: List<BodySecondary> = emptyList(),
    val recommended: List<BodyFit> = emptyList(),
    val avoid: List<BodyFit> = emptyList(),
    val disclosure: String? = null,
    val gender: String = ""
)
