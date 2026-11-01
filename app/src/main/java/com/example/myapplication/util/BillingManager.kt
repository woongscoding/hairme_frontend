package com.example.myapplication.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.myapplication.data.auth.TokenManager
import com.example.myapplication.repository.AuthRepository
import com.example.myapplication.repository.PurchaseVerification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Google Play 인앱 결제 (크레딧 소모성 상품)
 *
 * 지급 주체는 서버다. 흐름:
 *   Play 결제 완료 → 서버 POST /api/credits/purchase (검증·acknowledge·지급)
 *   → 200/409면 앱이 consume → 같은 상품을 다시 살 수 있게 된다.
 *
 * 서버 검증 전에 앱이 죽거나 네트워크가 끊기면 구매는 consume 되지 않은 채로 남는다.
 * 연결 직후·로그인 직후·홈 복귀 때 [recoverPurchases]가 이런 구매를 다시 서버로 보낸다.
 * (Play는 3일 안에 acknowledge 되지 않은 구매를 자동 환불하므로 복구가 필수)
 *
 * RewardedAdManager와 같은 object 싱글톤 방식 (Hilt 비활성화 프로젝트).
 */
object BillingManager {

    private const val TAG = "BillingManager"

    const val SKU_STARTER_5 = "starter_5"
    const val SKU_CREDITS_10 = "credits_10"
    const val SKU_CREDITS_30 = "credits_30"

    /** 상품 → 지급 크레딧 수 (표시용. 실제 지급량은 서버 CREDIT_PRODUCTS가 정한다) */
    val CREDITS_BY_SKU = linkedMapOf(
        SKU_STARTER_5 to 5,
        SKU_CREDITS_10 to 10,
        SKU_CREDITS_30 to 30
    )

    /** Play Console에서 받아온 상품 정보 */
    data class CreditProduct(
        val productId: String,
        val credits: Int,
        val formattedPrice: String,
        val priceMicros: Long,
        val currencyCode: String,
        internal val details: ProductDetails
    )

    sealed class BillingEvent {
        /** 서버 지급 완료. balance가 null이면(중복 처리) 잔액을 다시 조회해야 한다 */
        data class Granted(val productId: String, val granted: Int, val balance: Int?) : BillingEvent()
        /** 결제 대기 (편의점 결제 등) - 완료되면 복구 경로로 지급된다 */
        object Pending : BillingEvent()
        object Cancelled : BillingEvent()
        data class Failed(val message: String) : BillingEvent()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val verifier = PurchaseVerifier(AuthRepository())

    /** 지금 로그인한 서버 user_id. 복구 때 다른 계정의 결제를 거르는 데 쓴다 ([setCurrentUser]) */
    @Volatile
    private var currentUserId: String? = null

    private var client: BillingClient? = null

    private val _products = MutableStateFlow<Map<String, CreditProduct>>(emptyMap())
    val products: StateFlow<Map<String, CreditProduct>> = _products.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    /** 결제창을 띄운 뒤 결과가 올 때까지 (중복 탭 방지) */
    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    /** 서버 검증 중인 토큰 (복구와 결제 콜백이 같은 구매를 두 번 보내지 않게) */
    private val tokensInFlight = mutableSetOf<String>()

    /** 마지막 결제 시작 지점 (purchase_complete 계측용) */
    private var lastPlacement: String = "unknown"

    /**
     * Application.onCreate에서 1회 호출. 연결되면 상품 조회 + 미처리 구매 복구.
     */
    fun init(context: Context) {
        if (client != null) return
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            // 서비스 연결이 끊긴 뒤 다음 호출 때 라이브러리가 알아서 다시 붙는다
            .enableAutoServiceReconnection()
            .build()
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Play 결제 연결 완료")
                    queryProducts()
                    recoverPurchases()
                } else {
                    Log.w(TAG, "Play 결제 연결 실패: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Play 결제 연결 끊김 (다음 호출 때 자동 재연결)")
            }
        })
    }

    /** 상품 정보 조회. Play Console에 아직 없는 상품(예: 등록 전 starter_5)은 목록에서 빠진다. */
    fun queryProducts() {
        val billing = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(CREDITS_BY_SKU.keys.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()
        billing.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "상품 조회 실패: ${result.responseCode} ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val loaded = queryResult.productDetailsList.mapNotNull { details ->
                val offer = details.oneTimePurchaseOfferDetails ?: return@mapNotNull null
                CreditProduct(
                    productId = details.productId,
                    credits = CREDITS_BY_SKU[details.productId] ?: 0,
                    formattedPrice = offer.formattedPrice,
                    priceMicros = offer.priceAmountMicros,
                    currencyCode = offer.priceCurrencyCode,
                    details = details
                )
            }.associateBy { it.productId }
            Log.d(TAG, "상품 ${loaded.keys} 로드 (미등록: ${queryResult.unfetchedProductList.map { it.productId }})")
            _products.value = loaded
        }
    }

    /** 로그인·로그아웃 때 호출. 복구는 이 사용자의 결제(또는 계정 표시가 없는 결제)만 서버에 보낸다 */
    fun setCurrentUser(userId: String?) {
        currentUserId = userId
    }

    /**
     * 결제창 열기. 로그인 상태에서만 호출한다 (서버 지급 대상이 계정이므로).
     *
     * @param userId 서버 user_id. Play에는 해시만 넘겨 부정 결제 탐지에 쓰게 한다.
     * @param placement 계측용 진입 지점 ("paywall_exhausted" | "paywall_hd_upgrade" ...)
     */
    fun launch(activity: Activity, productId: String, userId: String, placement: String) {
        val billing = client
        val product = _products.value[productId]
        if (billing == null || product == null) {
            queryProducts()
            _events.tryEmit(BillingEvent.Failed("결제 정보를 불러오는 중이에요. 잠시 후 다시 시도해 주세요."))
            return
        }
        if (_isPurchasing.value) return

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product.details)
            .apply {
                product.details.oneTimePurchaseOfferDetails?.offerToken?.let { setOfferToken(it) }
            }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setObfuscatedAccountId(purchaseAccountHash(userId))
            .build()

        currentUserId = userId
        lastPlacement = placement
        AnalyticsHelper.logPurchaseStart(productId, placement)
        val result = billing.launchBillingFlow(activity, flowParams)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            _isPurchasing.value = true
        } else {
            Log.w(TAG, "결제창 열기 실패: ${result.responseCode} ${result.debugMessage}")
            AnalyticsHelper.logPurchaseFailed(productId, "launch_${result.responseCode}")
            _events.tryEmit(BillingEvent.Failed("결제를 시작하지 못했어요. 잠시 후 다시 시도해 주세요."))
        }
    }

    /**
     * consume 안 된 구매를 서버로 다시 보낸다. 로그인 전이면 아무것도 하지 않는다
     * (지급 대상 계정을 알 수 없음 → 로그인 직후 다시 호출된다).
     */
    fun recoverPurchases() {
        val billing = client ?: return
        if (TokenManager.getAccessToken() == null) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billing.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            scope.launch {
                purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { handlePurchased(it, fromRecovery = true) }
            }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        scope.launch {
            _isPurchasing.value = false
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> handlePurchased(purchase, fromRecovery = false)
                        Purchase.PurchaseState.PENDING -> _events.emit(BillingEvent.Pending)
                        else -> Unit
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    AnalyticsHelper.logPurchaseFailed(lastSku(purchases), "user_canceled")
                    _events.emit(BillingEvent.Cancelled)
                }
                // 이전 구매가 consume 안 된 채 남아 있으면 같은 상품을 다시 못 산다 → 복구부터
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    recoverPurchases()
                    _events.emit(BillingEvent.Failed("이전 결제를 반영하고 있어요. 잠시 후 다시 확인해 주세요."))
                }
                else -> {
                    Log.w(TAG, "결제 실패: ${result.responseCode} ${result.debugMessage}")
                    AnalyticsHelper.logPurchaseFailed(lastSku(purchases), "billing_${result.responseCode}")
                    _events.emit(BillingEvent.Failed("결제를 완료하지 못했어요. 잠시 후 다시 시도해 주세요."))
                }
            }
        }
    }

    private suspend fun handlePurchased(purchase: Purchase, fromRecovery: Boolean) {
        val token = purchase.purchaseToken
        val productId = purchase.products.firstOrNull() ?: return
        if (!tokensInFlight.add(token)) return
        try {
            val verification = verifier.verify(
                productId = productId,
                purchaseToken = token,
                purchaseAccountId = purchase.accountIdentifiers?.obfuscatedAccountId,
                currentUserId = currentUserId
            )
            if (verification == null) {
                // 다른 계정의 결제, 또는 이번 세션에서 서버가 계정 불일치로 거절한 토큰 → 보내지 않는다
                Log.w(TAG, "다른 계정의 결제라 서버 검증을 건너뜀: $productId")
                return
            }
            when (verification) {
                is PurchaseVerification.Granted -> {
                    consume(token)
                    if (verification.granted > 0) {
                        val product = _products.value[productId]
                        AnalyticsHelper.logPurchaseComplete(
                            sku = productId,
                            credits = verification.granted,
                            value = (product?.priceMicros ?: 0L) / 1_000_000.0,
                            currency = product?.currencyCode ?: "KRW",
                            placement = if (fromRecovery) "recovery" else lastPlacement
                        )
                    }
                    _events.emit(BillingEvent.Granted(productId, verification.granted, verification.balance))
                }
                // consume 하지 않는다: 첫 구매 상품 재구매는 미승인으로 남아 Google이 자동 환불한다
                is PurchaseVerification.Rejected -> {
                    AnalyticsHelper.logPurchaseFailed(productId, verification.reason ?: "server_rejected")
                    if (!fromRecovery) _events.emit(BillingEvent.Failed(verification.message))
                }
                // consume 하지 않는다: 결제한 계정으로 로그인하면 그 계정에서 지급된다
                is PurchaseVerification.AccountMismatch -> {
                    AnalyticsHelper.logPurchaseFailed(productId, AuthRepository.ERROR_PURCHASE_ACCOUNT_MISMATCH)
                    if (!fromRecovery) _events.emit(BillingEvent.Failed(verification.message))
                }
                is PurchaseVerification.RetryLater -> {
                    AnalyticsHelper.logPurchaseFailed(productId, "server_retry")
                    if (!fromRecovery) _events.emit(BillingEvent.Failed(verification.message))
                }
            }
        } finally {
            tokensInFlight.remove(token)
        }
    }

    private fun consume(token: String) {
        val params = ConsumeParams.newBuilder().setPurchaseToken(token).build()
        client?.consumeAsync(params) { result, _ ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // 실패해도 지급은 끝났다. 다음 복구 때 409(중복)로 돌아와 다시 consume 된다.
                Log.w(TAG, "consume 실패: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun lastSku(purchases: List<Purchase>?): String =
        purchases?.firstOrNull()?.products?.firstOrNull() ?: "unknown"
}
