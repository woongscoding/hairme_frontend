package com.example.myapplication.util

import com.example.myapplication.repository.AuthRepository
import com.example.myapplication.repository.PurchaseVerification
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * PurchaseVerifier 테스트: 서버에 보내기 전 계정 확인과, 계정 불일치 토큰의 세션 내 재전송 차단.
 */
class PurchaseVerifierTest {

    private lateinit var repository: AuthRepository
    private lateinit var verifier: PurchaseVerifier

    private val granted = PurchaseVerification.Granted(granted = 10, balance = 10)
    private val mismatch = PurchaseVerification.AccountMismatch("다른 계정에서 한 결제예요.")

    @Before
    fun setup() {
        repository = mockk()
        verifier = PurchaseVerifier(repository)
    }

    @Test
    fun `계정 해시는 서버 expected_obfuscated_account_id와 같은 값`() {
        // python: hashlib.sha256(user_id.encode("utf-8")).hexdigest()
        assertEquals(
            "fc95297aa4f56781f0decb7d4bf59b1447f09b3611039b80188b1c6beb03ee6a",
            purchaseAccountHash("user-a")
        )
        assertEquals(
            "0a0fcbeeb6bf76eece41dcfb2a09c4663f6acadfceb574a50246a63f945f1fd2",
            purchaseAccountHash("사용자-1")
        )
    }

    @Test
    fun `계정 표시가 지금 사용자와 다르면 서버에 보내지 않는다`() = runTest {
        val result = verifier.verify("credits_10", "tok-1", purchaseAccountHash("user-a"), currentUserId = "user-b")

        assertNull(result)
        coVerify(exactly = 0) { repository.verifyPurchase(any(), any()) }
    }

    @Test
    fun `지금 사용자를 모르면 계정 표시가 있는 결제는 보내지 않는다`() = runTest {
        val result = verifier.verify("credits_10", "tok-1", purchaseAccountHash("user-a"), currentUserId = null)

        assertNull(result)
        coVerify(exactly = 0) { repository.verifyPurchase(any(), any()) }
    }

    @Test
    fun `계정 표시가 지금 사용자와 같으면 보낸다`() = runTest {
        coEvery { repository.verifyPurchase("credits_10", "tok-1") } returns granted

        val result = verifier.verify("credits_10", "tok-1", purchaseAccountHash("user-a"), currentUserId = "user-a")

        assertEquals(granted, result)
    }

    @Test
    fun `계정 표시가 없는 결제는 지금처럼 보낸다`() = runTest {
        coEvery { repository.verifyPurchase("credits_10", "tok-1") } returns granted

        assertEquals(granted, verifier.verify("credits_10", "tok-1", null, currentUserId = "user-b"))
        assertEquals(granted, verifier.verify("credits_10", "tok-1", "", currentUserId = "user-b"))
    }

    @Test
    fun `서버가 계정 불일치로 거절한 토큰은 이번 세션에서 다시 보내지 않는다`() = runTest {
        coEvery { repository.verifyPurchase("credits_10", "tok-1") } returns mismatch
        coEvery { repository.verifyPurchase("credits_10", "tok-2") } returns granted

        assertIs<PurchaseVerification.AccountMismatch>(verifier.verify("credits_10", "tok-1", null, "user-a"))
        assertNull(verifier.verify("credits_10", "tok-1", null, "user-a"))
        coVerify(exactly = 1) { repository.verifyPurchase("credits_10", "tok-1") }

        // 다른 토큰은 영향받지 않는다
        assertEquals(granted, verifier.verify("credits_10", "tok-2", null, "user-a"))
    }
}
