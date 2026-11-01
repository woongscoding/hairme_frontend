package com.example.myapplication.repository

import com.example.myapplication.network.CreditsApiService
import com.example.myapplication.network.PurchaseRequest
import com.example.myapplication.network.PurchaseResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AuthRepository.verifyPurchase 분기 테스트.
 *
 * BillingManager는 Granted일 때만 consume 한다. consume은 승인을 겸하므로,
 * 서버가 일부러 승인하지 않은 첫 구매 상품 재구매(one_per_account_used)를 Granted로 잘못 보면
 * Google 자동 환불이 막힌다 - 409라도 error 코드가 있으면 Rejected여야 한다.
 */
class AuthRepositoryVerifyPurchaseTest {

    private lateinit var api: CreditsApiService
    private lateinit var repository: AuthRepository

    private val request = PurchaseRequest("starter_5", "token-1")

    @Before
    fun setup() {
        api = mockk()
        repository = AuthRepository { api }
    }

    private fun errorResponse(code: Int, json: String): Response<PurchaseResponse> =
        Response.error(code, json.toResponseBody("application/json".toMediaTypeOrNull()))

    private suspend fun verify() = repository.verifyPurchase(request.productId, request.purchaseToken)

    @Test
    fun `200이면 지급 수량과 잔액으로 Granted`() = runTest {
        coEvery { api.purchase(request) } returns Response.success(
            PurchaseResponse(success = true, productId = "starter_5", granted = 5, balance = 8, orderId = "GPA.1")
        )

        val result = assertIs<PurchaseVerification.Granted>(verify())

        assertEquals(5, result.granted)
        assertEquals(8, result.balance)
    }

    @Test
    fun `error 코드 없는 409는 이미 지급된 구매라 consume 대상인 Granted`() = runTest {
        coEvery { api.purchase(request) } returns errorResponse(409, """{"detail":"이미 처리된 구매입니다."}""")

        val result = assertIs<PurchaseVerification.Granted>(verify())

        assertEquals(0, result.granted)
        assertNull(result.balance)
    }

    @Test
    fun `409 one_per_account_used는 consume 하지 않는 Rejected + 자동 환불 안내`() = runTest {
        coEvery { api.purchase(request) } returns errorResponse(
            409,
            """{"error":"one_per_account_used","message":"첫 구매 상품은 계정당 한 번만 구매할 수 있습니다.","product_id":"starter_5"}"""
        )

        val result = assertIs<PurchaseVerification.Rejected>(verify())

        assertEquals(AuthRepository.ERROR_ONE_PER_ACCOUNT_USED, result.reason)
        assertTrue(result.message.contains("결제 금액은 자동 환불돼요"))
    }

    @Test
    fun `422 one_per_account_used도 Rejected + 자동 환불 안내`() = runTest {
        coEvery { api.purchase(request) } returns errorResponse(
            422,
            """{"error":"one_per_account_used","message":"첫 구매 상품은 계정당 한 번만 구매할 수 있습니다."}"""
        )

        val result = assertIs<PurchaseVerification.Rejected>(verify())

        assertEquals(AuthRepository.ERROR_ONE_PER_ACCOUNT_USED, result.reason)
        assertTrue(result.message.contains("결제 금액은 자동 환불돼요"))
    }

    @Test
    fun `403 purchase_account_mismatch는 consume 하지 않는 AccountMismatch`() = runTest {
        coEvery { api.purchase(request) } returns errorResponse(
            403,
            """{"error":"purchase_account_mismatch","message":"다른 계정으로 결제된 구매입니다."}"""
        )

        val result = assertIs<PurchaseVerification.AccountMismatch>(verify())

        assertTrue(result.message.contains("결제한 계정으로 로그인"))
    }

    @Test
    fun `error 코드 없는 403은 계정 불일치로 보지 않고 RetryLater`() = runTest {
        coEvery { api.purchase(request) } returns errorResponse(403, """{"detail":"Not authenticated"}""")

        assertIs<PurchaseVerification.RetryLater>(verify())
    }

    @Test
    fun `400은 서버의 한국어 사유로 Rejected`() = runTest {
        coEvery { api.purchase(request) } returns errorResponse(400, """{"detail":"구매 영수증 검증에 실패했습니다."}""")

        val result = assertIs<PurchaseVerification.Rejected>(verify())

        assertEquals("구매 영수증 검증에 실패했습니다.", result.message)
        assertNull(result.reason)
    }

    @Test
    fun `네트워크 오류는 consume 하지 않고 RetryLater`() = runTest {
        coEvery { api.purchase(request) } throws IOException("timeout")

        assertIs<PurchaseVerification.RetryLater>(verify())
    }

    @Test
    fun `503 같은 서버 장애도 RetryLater`() = runTest {
        coEvery { api.purchase(request) } returns errorResponse(503, """{"detail":"결제 검증 서비스를 일시적으로 사용할 수 없습니다."}""")

        assertIs<PurchaseVerification.RetryLater>(verify())
    }
}
