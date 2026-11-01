package com.example.myapplication.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 크레딧 API (JWT 필요 - hairstyleRetrofit의 AuthInterceptor가 자동 첨부)
 *
 * 보상형 광고 시청 후 지급은 AdMob → 서버 SSV 콜백 경로로 이뤄지므로,
 * 앱은 지급을 요청하지 않고 잔액만 다시 조회한다.
 */
interface CreditsApiService {

    @GET("/api/credits")
    suspend fun getCredits(): Response<CreditsResponse>

    /**
     * 결제 화면용 상품 목록. 가격은 없다 (Play ProductDetails의 현지화 가격을 쓴다).
     * 첫 구매 상품(starter_5)을 이미 쓴 계정은 available=false.
     */
    @GET("/api/credits/products")
    suspend fun getProducts(): Response<CreditProductsResponse>

    /**
     * Play 인앱 결제 검증 + 크레딧 지급.
     *
     * 서버는 영수증을 확인(acknowledge)만 하고 소비(consume)는 하지 않는다.
     * 같은 상품을 다시 사려면 앱이 200/409를 받은 뒤 직접 consume 해야 한다.
     * - 400: 알 수 없는 상품 / 영수증 검증 실패 (취소·대기 포함)
     * - 409: 이미 처리된 구매 (지급 완료 상태 → consume만 하면 된다)
     * - error="one_per_account_used" (409 또는 422): 첫 구매 상품 재구매. 서버가 승인하지 않아
     *   Google이 자동 환불하므로 consume 하면 안 된다 (consume은 승인을 겸해 환불이 막힌다)
     * - 403 error="purchase_account_mismatch": 결제한 계정(obfuscatedExternalAccountId) ≠ 로그인 계정
     *   → consume 하지 않고, 이번 세션에서는 그 토큰을 다시 보내지 않는다
     * - 503: Play API 장애 → 다음 실행 때 복구 재시도
     */
    @POST("/api/credits/purchase")
    suspend fun purchase(
        @Body request: PurchaseRequest
    ): Response<PurchaseResponse>
}

data class CreditProductsResponse(
    val products: List<ServerCreditProduct> = emptyList()
)

data class ServerCreditProduct(
    @SerializedName("product_id")
    val productId: String = "",
    val credits: Int = 0,
    @SerializedName("one_per_account")
    val onePerAccount: Boolean = false,
    val available: Boolean = true
)

data class CreditsResponse(
    val balance: Int = 0,
    // 최근 20건. 결제 이력(reason="purchase") 유무로 유료 사용자를 판별한다.
    val history: List<CreditHistoryEntry>? = null,
    // 서버가 유료 사용자 여부를 직접 내려주면 history보다 우선 (20건 밖의 구매도 반영)
    @SerializedName("has_purchased")
    val hasPurchased: Boolean? = null
)

data class CreditHistoryEntry(
    val amount: Int = 0,
    val reason: String? = null,
    @SerializedName("balance_after")
    val balanceAfter: Int? = null,
    @SerializedName("created_at")
    val createdAt: String? = null
)

data class PurchaseRequest(
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("purchase_token")
    val purchaseToken: String
)

data class PurchaseResponse(
    val success: Boolean = false,
    @SerializedName("product_id")
    val productId: String? = null,
    val granted: Int = 0,
    val balance: Int = 0,
    @SerializedName("order_id")
    val orderId: String? = null
)
