package com.example.myapplication.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import com.example.myapplication.network.BodyAnalysisResponse
import com.example.myapplication.network.BodyFit
import com.example.myapplication.network.ProductClickRequest
import com.example.myapplication.network.ProductClickResponse
import com.example.myapplication.repository.ApiResult
import com.example.myapplication.repository.HairstyleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BodyFashionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: HairstyleRepository
    private lateinit var viewModel: BodyFashionViewModel
    private val uri: Uri = mockk(relaxed = true)

    private val result = BodyAnalysisResponse(
        success = true,
        bodyType = "broad_shoulder",
        bodyTypeLabel = "어깨 넓은형",
        gender = "female",
        recommended = listOf(
            BodyFit("브이넥 블라우스", "시선을 세로로 모아줘요", "fashion-f-01"),
            BodyFit("상의 넣어 입기", "허리선이 살아나요", null)
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        repository = mockk()
        viewModel = BodyFashionViewModel(mockk(relaxed = true), repository) { "device-1" }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `분석 성공이면 결과 상태가 되고 선택한 성별로 요청한다`() = runTest(testDispatcher) {
        coEvery { repository.analyzeBody(uri, "female", "device-1") } returns ApiResult.Success(result)

        viewModel.setGender("female")
        viewModel.analyze(uri)
        assertEquals(BodyFashionUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        val state = viewModel.uiState.value as BodyFashionUiState.Success
        assertEquals("어깨 넓은형", state.result.bodyTypeLabel)
    }

    @Test
    fun `분석 실패면 서버 안내 문장을 그대로 보여준다`() = runTest(testDispatcher) {
        coEvery { repository.analyzeBody(uri, "male", "device-1") } returns
            ApiResult.Error("머리부터 발끝까지 나오게 찍어주세요", 400)

        viewModel.analyze(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.value as BodyFashionUiState.Error
        assertEquals("머리부터 발끝까지 나오게 찍어주세요", state.message)
        assertTrue("400은 사진 문제라 다시 찍기 안내", state.isPhotoProblem)
    }

    @Test
    fun `성별은 male 아니면 female 만 받는다`() {
        viewModel.setGender("unknown")
        assertEquals("male", viewModel.gender.value)
        viewModel.setGender("female")
        assertEquals("female", viewModel.gender.value)
    }

    @Test
    fun `reset 하면 진행 중인 분석 결과가 반영되지 않는다`() = runTest(testDispatcher) {
        coEvery { repository.analyzeBody(any(), any(), any()) } returns ApiResult.Success(result)

        viewModel.analyze(uri)
        viewModel.reset()
        advanceUntilIdle()

        assertEquals(BodyFashionUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `아이템 클릭은 체형 정보와 함께 링크를 발급받는다`() = runTest(testDispatcher) {
        coEvery { repository.analyzeBody(any(), any(), any()) } returns ApiResult.Success(result)
        val request = slot<ProductClickRequest>()
        coEvery { repository.clickProduct(capture(request)) } returns ApiResult.Success(
            ProductClickResponse(success = true, affiliateUrl = "https://link.coupang.com/a/x")
        )
        viewModel.analyze(uri)
        advanceUntilIdle()

        var opened: String? = null
        viewModel.openItemLink(result.recommended[0], onLink = { opened = it }, onFailure = {})
        advanceUntilIdle()

        assertEquals("https://link.coupang.com/a/x", opened)
        assertEquals("fashion-f-01", request.captured.productId)
        assertEquals("body_analysis", request.captured.source)
        assertNull(request.captured.style)
        assertEquals("broad_shoulder", request.captured.hairProfile?.bodyType)
        assertEquals("female", request.captured.hairProfile?.gender)
        assertEquals("device-1", request.captured.deviceId)
    }

    @Test
    fun `링크가 없는 스타일링 팁은 서버를 부르지 않는다`() = runTest(testDispatcher) {
        viewModel.openItemLink(result.recommended[1], onLink = {}, onFailure = {})
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.clickProduct(any()) }
    }

    @Test
    fun `링크 발급 실패면 onFailure`() = runTest(testDispatcher) {
        coEvery { repository.clickProduct(any()) } returns ApiResult.Error("실패", 404)

        var failed = false
        viewModel.openItemLink(result.recommended[0], onLink = {}, onFailure = { failed = true })
        advanceUntilIdle()

        assertTrue(failed)
    }
}
