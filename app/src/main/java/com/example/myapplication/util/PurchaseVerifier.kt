package com.example.myapplication.util

import com.example.myapplication.repository.AuthRepository
import com.example.myapplication.repository.PurchaseVerification
import java.security.MessageDigest

/**
 * Play 결제에 붙이는 계정 표시 (BillingFlowParams.setObfuscatedAccountId).
 * 서버 user_id를 그대로 넘기지 않고 해시만 넘긴다.
 */
internal fun purchaseAccountHash(userId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(userId.toByteArray())
        .joinToString("") { "%02x".format(it) }

/**
 * 구매를 서버 검증에 보내기 전에 거른다.
 *
 * - 결제에 계정 표시가 있는데 지금 사용자 것이 아니면 보내지 않는다. 한 기기에서 계정을 바꾼 뒤
 *   복구 경로가 앞 계정의 결제를 지금 계정으로 지급받는 것을 막는다. 결제한 계정으로 다시
 *   로그인하면 그때 보낸다. 계정 표시가 없는 결제(예전 버전)는 지금처럼 보낸다.
 * - 서버가 계정 불일치(403)로 거절한 토큰은 이번 세션에서 다시 보내지 않는다.
 */
internal class PurchaseVerifier(private val repository: AuthRepository) {

    /** 서버가 계정 불일치로 거절한 토큰 (프로세스가 살아 있는 동안만) */
    private val blockedTokens = mutableSetOf<String>()

    /**
     * @param purchaseAccountId 결제의 obfuscatedAccountId (없으면 null)
     * @param currentUserId 지금 로그인한 서버 user_id (모르면 null → 계정 표시가 있는 결제는 보내지 않는다)
     * @return 서버 검증 결과. 보내지 않고 건너뛰었으면 null
     */
    suspend fun verify(
        productId: String,
        purchaseToken: String,
        purchaseAccountId: String?,
        currentUserId: String?
    ): PurchaseVerification? {
        if (purchaseToken in blockedTokens) return null
        if (!belongsToCurrentUser(purchaseAccountId, currentUserId)) return null
        val verification = repository.verifyPurchase(productId, purchaseToken)
        if (verification is PurchaseVerification.AccountMismatch) blockedTokens += purchaseToken
        return verification
    }

    private fun belongsToCurrentUser(purchaseAccountId: String?, currentUserId: String?): Boolean =
        purchaseAccountId.isNullOrEmpty() ||
            (currentUserId != null && purchaseAccountId == purchaseAccountHash(currentUserId))
}
