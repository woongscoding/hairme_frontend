package com.example.myapplication.repository

import android.util.Log
import com.example.myapplication.data.auth.TokenManager
import com.example.myapplication.network.AuthResponse
import com.example.myapplication.network.AuthUser
import com.example.myapplication.network.ConsentRequest
import com.example.myapplication.network.CreditsApiService
import com.example.myapplication.network.KakaoLoginRequest
import com.example.myapplication.network.PurchaseRequest
import com.example.myapplication.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.myapplication.util.BillingManager
import com.example.myapplication.util.serverErrorField
import com.example.myapplication.util.serverErrorMessage
import java.io.IOException

/** 크레딧 잔액 + 유료 사용자 여부 */
data class CreditStatus(val balance: Int, val hasPurchased: Boolean)

/** 서버 구매 검증 결과 */
sealed class PurchaseVerification {
    /** 지급 완료 (409 중복 처리 포함 - balance는 null일 수 있다) → consume 해도 된다 */
    data class Granted(val granted: Int, val balance: Int?) : PurchaseVerification()
    /**
     * 지급 거절 → consume 하지 않는다.
     * 400(영수증 무효·취소·대기), 또는 첫 구매 상품 재구매(reason="one_per_account_used",
     * 서버가 승인하지 않아 Google이 자동 환불한다)
     */
    data class Rejected(val message: String, val reason: String? = null) : PurchaseVerification()
    /**
     * 결제한 계정과 로그인한 계정이 다름 (403 error="purchase_account_mismatch") → consume 하지 않는다.
     * 결제한 계정으로 로그인하면 그 계정에 지급된다. 이번 세션에서는 같은 토큰을 다시 보내지 않는다.
     */
    data class AccountMismatch(val message: String) : PurchaseVerification()
    /** 네트워크·서버 장애·로그아웃 → consume 하지 않고 다음 복구 때 다시 보낸다 */
    data class RetryLater(val message: String) : PurchaseVerification()
}

/**
 * AuthRepository
 *
 * 카카오 액세스 토큰 ↔ 서버 JWT 교환, 내 정보 조회, 로그아웃 처리.
 * 기존 HairstyleRepository와 동일하게 수동 DI (Hilt 재활성화 시 생성자 주입으로 변경)
 */
class AuthRepository(
    // 테스트에서 가짜 API를 넣는 자리 (기본값은 실제 Retrofit 클라이언트)
    creditsApiProvider: () -> CreditsApiService = { RetrofitClient.creditsApiService }
) {

    private val authApiService by lazy {
        RetrofitClient.authApiService
    }

    private val creditsApiService by lazy(creditsApiProvider)

    companion object {
        private const val TAG = "AuthRepository"
        private const val NETWORK_ERROR_MESSAGE = "네트워크 연결을 확인해주세요"
        const val ERROR_ONE_PER_ACCOUNT_USED = "one_per_account_used"
        private const val ONE_PER_ACCOUNT_REFUND_MESSAGE =
            "첫 구매 상품은 계정당 한 번만 살 수 있어요. 결제 금액은 자동 환불돼요."
        const val ERROR_PURCHASE_ACCOUNT_MISMATCH = "purchase_account_mismatch"
        private const val ACCOUNT_MISMATCH_MESSAGE =
            "다른 계정에서 한 결제예요. 결제한 계정으로 로그인하면 반영되고, 처리되지 않은 결제는 자동 환불돼요."
    }

    /**
     * 카카오 SDK 액세스 토큰을 서버로 보내 자체 JWT 발급 (가입 겸용)
     * 성공 시 토큰을 EncryptedSharedPreferences에 저장
     */
    suspend fun loginWithKakaoToken(kakaoAccessToken: String): ApiResult<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 카카오 토큰 → 서버 JWT 교환 시작")
            val response = authApiService.kakaoLogin(KakaoLoginRequest(kakaoAccessToken))
            val body = response.body()

            if (response.isSuccessful && body != null) {
                TokenManager.saveTokens(body.accessToken, body.refreshToken)
                Log.d(TAG, "✅ 로그인 성공 - nickname=${body.user.nickname}, isNewUser=${body.isNewUser}")
                ApiResult.Success(body)
            } else {
                Log.e(TAG, "❌ 로그인 실패 - HTTP ${response.code()}")
                ApiResult.Error(loginErrorMessage(response.code()), response.code())
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ 네트워크 오류: ${e.message}")
            ApiResult.Error(NETWORK_ERROR_MESSAGE)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 로그인 중 오류: ${e.message}")
            ApiResult.Error("로그인 처리 중 문제가 발생했어요. 잠시 후 다시 시도해주세요")
        }
    }

    /**
     * 저장된 JWT로 내 정보 조회 (자동 로그인 검증)
     * access 만료 시 Authenticator가 자동으로 리프레시 후 재시도한다
     */
    suspend fun getMe(): ApiResult<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val response = authApiService.getMe()
            val body = response.body()

            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.user)
            } else {
                Log.w(TAG, "❌ 내 정보 조회 실패 - HTTP ${response.code()}")
                ApiResult.Error("로그인이 만료되었어요. 다시 로그인해주세요", response.code())
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ 네트워크 오류: ${e.message}")
            ApiResult.Error(NETWORK_ERROR_MESSAGE)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 내 정보 조회 중 오류: ${e.message}")
            ApiResult.Error("사용자 정보를 불러오지 못했어요")
        }
    }

    /**
     * 크레딧 잔액만 조회 (보상형 광고 시청 후 갱신용)
     *
     * 내 정보 전체(getMe)보다 가볍고, 지급이 늦어 잔액이 아직 안 올랐을 때
     * 여러 번 재조회하기에 적합하다.
     */
    suspend fun getCreditBalance(): ApiResult<Int> = withContext(Dispatchers.IO) {
        try {
            val response = creditsApiService.getCredits()
            val body = response.body()

            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.balance)
            } else {
                Log.w(TAG, "❌ 크레딧 조회 실패 - HTTP ${response.code()}")
                ApiResult.Error("크레딧을 불러오지 못했어요", response.code())
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ 네트워크 오류: ${e.message}")
            ApiResult.Error(NETWORK_ERROR_MESSAGE)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 크레딧 조회 중 오류: ${e.message}")
            ApiResult.Error("크레딧을 불러오지 못했어요")
        }
    }

    /**
     * 잔액 + 유료 사용자 여부.
     *
     * 서버에 has_purchased 필드가 생기면 그것을, 없으면 최근 이력(20건)에 결제가 있는지로 판단한다.
     */
    suspend fun getCreditStatus(): ApiResult<CreditStatus> = withContext(Dispatchers.IO) {
        try {
            val response = creditsApiService.getCredits()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val purchasedInHistory = body.history.orEmpty().any { it.reason == "purchase" }
                ApiResult.Success(CreditStatus(body.balance, body.hasPurchased ?: purchasedInHistory))
            } else {
                ApiResult.Error("크레딧을 불러오지 못했어요", response.code())
            }
        } catch (e: IOException) {
            ApiResult.Error(NETWORK_ERROR_MESSAGE)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 크레딧 상태 조회 중 오류: ${e.message}")
            ApiResult.Error("크레딧을 불러오지 못했어요")
        }
    }

    /**
     * 첫 구매 상품(starter_5)을 이 계정이 아직 살 수 있는지 (GET /api/credits/products의 available).
     * 조회 실패·목록에 없음이면 null → 호출 측이 결제 이력으로 판단한다.
     */
    suspend fun getStarterAvailable(): Boolean? = withContext(Dispatchers.IO) {
        try {
            val response = creditsApiService.getProducts()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                body.products.firstOrNull { it.productId == BillingManager.SKU_STARTER_5 }?.available
            } else {
                Log.w(TAG, "❌ 상품 목록 조회 실패 - HTTP ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 상품 목록 조회 중 오류: ${e.message}")
            null
        }
    }

    /**
     * Play 구매 영수증을 서버에 보내 크레딧을 지급받는다.
     */
    suspend fun verifyPurchase(productId: String, purchaseToken: String): PurchaseVerification =
        withContext(Dispatchers.IO) {
            try {
                val response = creditsApiService.purchase(PurchaseRequest(productId, purchaseToken))
                val body = response.body()
                val errorBody = if (response.isSuccessful) null else response.errorBody()?.string()
                when {
                    response.isSuccessful && body != null ->
                        PurchaseVerification.Granted(body.granted, body.balance)
                    // 첫 구매 상품 재구매 거절. 서버는 지금 409로 보내므로 409보다 먼저 본다
                    // (409로 묶어 consume 하면 승인돼 자동 환불이 막힌다)
                    serverErrorField(errorBody, "error") == ERROR_ONE_PER_ACCOUNT_USED ->
                        PurchaseVerification.Rejected(
                            ONE_PER_ACCOUNT_REFUND_MESSAGE,
                            ERROR_ONE_PER_ACCOUNT_USED
                        )
                    // 결제한 계정 ≠ 로그인한 계정 (서버가 obfuscatedExternalAccountId로 판정)
                    response.code() == 403 &&
                        serverErrorField(errorBody, "error") == ERROR_PURCHASE_ACCOUNT_MISMATCH ->
                        PurchaseVerification.AccountMismatch(ACCOUNT_MISMATCH_MESSAGE)
                    // 이미 지급된 영수증 (앱이 consume 전에 죽었다가 복구하는 경우 등)
                    response.code() == 409 -> PurchaseVerification.Granted(0, null)
                    response.code() == 400 -> PurchaseVerification.Rejected(
                        serverErrorMessage(errorBody, 400)
                    )
                    else -> {
                        Log.w(TAG, "❌ 구매 검증 실패 - HTTP ${response.code()}")
                        PurchaseVerification.RetryLater(
                            "결제 확인이 지연되고 있어요. 앱을 다시 열면 자동으로 반영돼요."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 구매 검증 중 오류: ${e.message}")
                PurchaseVerification.RetryLater(
                    "네트워크 문제로 결제 확인이 지연되고 있어요. 앱을 다시 열면 자동으로 반영돼요."
                )
            }
        }

    /**
     * 학습 데이터 활용 동의 변경
     */
    suspend fun updateConsent(trainingConsent: Boolean): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = authApiService.updateConsent(ConsentRequest(trainingConsent))
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("동의 설정 변경에 실패했어요", response.code())
            }
        } catch (e: IOException) {
            ApiResult.Error(NETWORK_ERROR_MESSAGE)
        } catch (e: Exception) {
            ApiResult.Error("동의 설정 변경 중 문제가 발생했어요")
        }
    }

    /**
     * 로컬 JWT 삭제 (카카오 SDK 로그아웃은 ViewModel에서 별도 호출)
     */
    fun logout() {
        TokenManager.clearTokens()
        Log.d(TAG, "✅ 로컬 토큰 삭제 완료")
    }

    fun hasStoredTokens(): Boolean = TokenManager.getAccessToken() != null

    private fun loginErrorMessage(code: Int): String = when (code) {
        401 -> "카카오 인증이 만료되었어요. 다시 로그인해주세요"
        429 -> "요청이 너무 많아요. 잠시 후 다시 시도해주세요"
        503 -> "카카오 서비스가 일시적으로 불안정해요. 잠시 후 다시 시도해주세요"
        else -> "로그인에 실패했어요. 잠시 후 다시 시도해주세요 (오류 $code)"
    }
}
