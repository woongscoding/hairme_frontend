package com.example.myapplication.network

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * 제휴 제품(쿠팡파트너스) 추천 관련 데이터 모델
 *
 * 서버(프로덕션)가 합성 응답에 스타일 맞춤 제휴 제품을 내려주고,
 * 클릭 시 제휴 링크를 발급한다. 클릭 로그는 서버가 기록하므로
 * 앱은 반드시 /api/products/click을 매번 경유해야 한다. (링크 하드코딩/캐싱 금지)
 */

/**
 * 대가성 문구 기본값 (법적 의무).
 *
 * 서버가 disclosure를 내려주면 그 값을 "그대로" 우선 사용하고,
 * 구버전 서버 호환 등으로 값이 없을 때만 이 표준 문구로 폴백한다.
 * (섹션에 제품이 노출되는 한 대가성 문구는 항상 보여야 하므로 폴백을 둔다.)
 */
const val COUPANG_DEFAULT_DISCLOSURE =
    "이 게시물은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다."

/**
 * 추천 제휴 제품 1건
 *
 * 합성 응답의 recommended_products 배열 요소 및
 * GET /api/products/recommendations의 products 배열 요소.
 *
 * 구버전 서버 호환: 필수 필드가 없어도 크래시 없이 파싱되도록 기본값을 둔다.
 */
@Keep
data class RecommendedProduct(
    @SerializedName("product_id")
    val productId: String = "",
    val name: String = "",
    val brand: String = "",
    val category: String = "",
    @SerializedName("price_krw")
    val priceKrw: Int = 0,
    @SerializedName("image_url")
    val imageUrl: String = ""
)

/**
 * 클릭 요청에 함께 보내는 최근 분석 스냅샷 (없으면 생략 가능)
 */
@Keep
data class HairProfile(
    @SerializedName("face_shape")
    val faceShape: String? = null,
    @SerializedName("personal_color")
    val personalColor: String? = null,
    val gender: String? = null
)

/**
 * POST /api/products/click 요청 바디
 *
 * @param source 호출 화면: "synthesis_result" | "analysis_result" | "browse"
 */
@Keep
data class ProductClickRequest(
    @SerializedName("product_id")
    val productId: String,
    val style: String,
    val source: String,
    @SerializedName("hair_profile")
    val hairProfile: HairProfile? = null
)

/**
 * POST /api/products/click 응답
 *
 * 404(존재하지 않는 제품)는 body 없이 올 수 있으므로 모든 필드 nullable.
 */
@Keep
data class ProductClickResponse(
    val success: Boolean = false,
    @SerializedName("product_id")
    val productId: String? = null,
    @SerializedName("affiliate_url")
    val affiliateUrl: String? = null,
    val disclosure: String? = null
)

/**
 * GET /api/products/recommendations 응답 (인증 불필요, 선택 사용)
 */
@Keep
data class ProductRecommendationsResponse(
    val style: String? = null,
    val products: List<RecommendedProduct> = emptyList(),
    val disclosure: String? = null
)
