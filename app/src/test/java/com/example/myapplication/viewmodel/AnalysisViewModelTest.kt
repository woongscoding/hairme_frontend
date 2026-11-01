package com.example.myapplication.viewmodel

import android.app.Application
import android.net.Uri
import com.example.myapplication.AnalysisResult
import com.example.myapplication.HairstyleRecommendation
import com.example.myapplication.repository.ApiResult
import com.example.myapplication.repository.HairstyleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * AnalysisViewModel 분석 상태 전이 유닛 테스트
 *
 * Idle → Loading → Success/Error, resetState, 연속 호출 시 마지막 결과 반영을 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: AnalysisViewModel
    private lateinit var mockRepository: HairstyleRepository
    private lateinit var mockUri: Uri

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockUri = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        // 생성 시 init에서 부르는 것들 (히스토리 개수, 무료 횟수 조회)
        every { mockRepository.getHistoryCount() } returns flowOf(0)
        coEvery { mockRepository.getUsage(any()) } returns ApiResult.Error("오프라인")

        viewModel = AnalysisViewModel(mockk<Application>(relaxed = true), mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun result(faceShape: String) = AnalysisResult(
        face_shape = faceShape,
        skin_tone = "쿨톤",
        recommended_styles = listOf(
            HairstyleRecommendation(name = "레이어드컷", score = 0.95, reason = "얼굴형에 잘 어울림")
        )
    )

    @Test
    fun `초기 상태는 Idle이다`() {
        assertEquals(AnalysisUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `분석 중에는 Loading, 성공하면 Success가 된다`() = runTest(testDispatcher) {
        val expected = result("계란형")
        coEvery { mockRepository.analyzeImage(mockUri, any()) } coAnswers {
            delay(1_000)
            ApiResult.Success(expected, "id-1")
        }

        viewModel.analyzeImage(mockUri)
        testScheduler.runCurrent()
        assertIs<AnalysisUiState.Loading>(viewModel.uiState.value)

        testScheduler.advanceUntilIdle()
        val state = assertIs<AnalysisUiState.Success>(viewModel.uiState.value)
        assertEquals("id-1", state.analysisId)
        assertEquals(expected, state.data)
        coVerify(exactly = 1) { mockRepository.analyzeImage(mockUri, "male") }
    }

    @Test
    fun `분석이 실패하면 저장소의 에러 문구로 Error가 된다`() = runTest(testDispatcher) {
        coEvery { mockRepository.analyzeImage(mockUri, any()) } returns
            ApiResult.Error("서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요", 500)

        viewModel.analyzeImage(mockUri)
        testScheduler.advanceUntilIdle()

        val state = assertIs<AnalysisUiState.Error>(viewModel.uiState.value)
        assertEquals("서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요", state.message)
    }

    @Test
    fun `성별을 저장소에 그대로 넘긴다`() = runTest(testDispatcher) {
        coEvery { mockRepository.analyzeImage(mockUri, any()) } returns ApiResult.Success(result("계란형"), "id-1")

        viewModel.analyzeImage(mockUri, gender = "female")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.analyzeImage(mockUri, "female") }
    }

    @Test
    fun `resetState를 부르면 Idle로 돌아간다`() = runTest(testDispatcher) {
        coEvery { mockRepository.analyzeImage(mockUri, any()) } returns ApiResult.Success(result("계란형"), "id-1")
        viewModel.analyzeImage(mockUri)
        testScheduler.advanceUntilIdle()
        assertIs<AnalysisUiState.Success>(viewModel.uiState.value)

        viewModel.resetState()

        assertEquals(AnalysisUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `연속으로 분석하면 마지막 결과가 남는다`() = runTest(testDispatcher) {
        val first = result("계란형")
        val second = result("둥근형")
        coEvery { mockRepository.analyzeImage(mockUri, any()) } returnsMany listOf(
            ApiResult.Success(first, "id-1"),
            ApiResult.Success(second, "id-2")
        )

        viewModel.analyzeImage(mockUri)
        testScheduler.advanceUntilIdle()
        viewModel.analyzeImage(mockUri)
        testScheduler.advanceUntilIdle()

        val state = assertIs<AnalysisUiState.Success>(viewModel.uiState.value)
        assertEquals("id-2", state.analysisId)
        assertEquals(second, state.data)
    }
}
