package com.example.myapplication.viewmodel

import android.app.Application
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.myapplication.AnalysisResult
import com.example.myapplication.HairstyleRecommendation
import com.example.myapplication.repository.ApiResult
import com.example.myapplication.repository.HairstyleRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AnalysisViewModel 유닛 테스트
 *
 * 테스트 항목:
 * 1. 초기 상태가 Idle인지 확인
 * 2. analyzeImage 호출 시 Loading 상태로 변경되는지 확인
 * 3. 분석 성공 시 Success 상태로 변경되는지 확인
 * 4. 분석 실패 시 Error 상태로 변경되는지 확인
 * 5. resetState 호출 시 Idle 상태로 변경되는지 확인
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: AnalysisViewModel
    private lateinit var mockApplication: Application
    private lateinit var mockRepository: HairstyleRepository
    private lateinit var mockUri: Uri

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock 객체 생성
        mockApplication = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockUri = mockk(relaxed = true)

        // ViewModel 생성 시 Repository를 주입하기 위해 mockkConstructor 사용
        mockkConstructor(HairstyleRepository::class)
        every { anyConstructed<HairstyleRepository>().analyzeImage(any()) } returns mockk()

        viewModel = AnalysisViewModel(mockApplication)

        // Repository 필드를 mock으로 교체 (리플렉션 사용)
        val repositoryField = AnalysisViewModel::class.java.getDeclaredField("repository")
        repositoryField.isAccessible = true
        repositoryField.set(viewModel, mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `초기 상태는 Idle이어야 한다`() = runTest {
        // When: ViewModel이 생성됨
        // Then: 초기 상태는 Idle
        viewModel.uiState.test {
            assertEquals(AnalysisUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `analyzeImage 호출 시 Loading 상태를 거쳐 Success 상태로 변경되어야 한다`() = runTest {
        // Given: API 성공 응답 Mock
        val expectedResult = AnalysisResult(
            face_shape = "계란형",
            skin_tone = "쿨톤",
            recommended_styles = listOf(
                HairstyleRecommendation("레이어드컷", 0.95, "얼굴형에 잘 어울림")
            )
        )
        val expectedAnalysisId = 123

        coEvery { mockRepository.analyzeImage(mockUri) } returns ApiResult.Success(
            data = expectedResult,
            analysisId = expectedAnalysisId
        )

        // When: analyzeImage 호출
        viewModel.uiState.test {
            assertEquals(AnalysisUiState.Idle, awaitItem()) // 초기 상태

            viewModel.analyzeImage(mockUri)
            testScheduler.advanceUntilIdle() // 모든 코루틴 작업 완료 대기

            assertEquals(AnalysisUiState.Loading, awaitItem()) // Loading 상태

            val successState = awaitItem() as AnalysisUiState.Success
            assertEquals(expectedAnalysisId, successState.analysisId)
            assertEquals(expectedResult, successState.data)
        }

        // Then: Repository가 호출되었는지 확인
        coVerify(exactly = 1) { mockRepository.analyzeImage(mockUri) }
    }

    @Test
    fun `analyzeImage 호출 시 API 실패하면 Error 상태로 변경되어야 한다`() = runTest {
        // Given: API 실패 응답 Mock
        val errorMessage = "네트워크 오류"
        coEvery { mockRepository.analyzeImage(mockUri) } returns ApiResult.Error(errorMessage)

        // When: analyzeImage 호출
        viewModel.uiState.test {
            assertEquals(AnalysisUiState.Idle, awaitItem()) // 초기 상태

            viewModel.analyzeImage(mockUri)
            testScheduler.advanceUntilIdle()

            assertEquals(AnalysisUiState.Loading, awaitItem()) // Loading 상태

            val errorState = awaitItem() as AnalysisUiState.Error
            assertEquals(errorMessage, errorState.message)
        }

        // Then: Repository가 호출되었는지 확인
        coVerify(exactly = 1) { mockRepository.analyzeImage(mockUri) }
    }

    @Test
    fun `analyzeImage 호출 시 HTTP 에러 코드가 포함된 Error 상태로 변경되어야 한다`() = runTest {
        // Given: HTTP 에러 응답 Mock
        val errorMessage = "서버 오류: Internal Server Error"
        val errorCode = 500
        coEvery { mockRepository.analyzeImage(mockUri) } returns ApiResult.Error(
            message = errorMessage,
            code = errorCode
        )

        // When: analyzeImage 호출
        viewModel.uiState.test {
            assertEquals(AnalysisUiState.Idle, awaitItem())

            viewModel.analyzeImage(mockUri)
            testScheduler.advanceUntilIdle()

            assertEquals(AnalysisUiState.Loading, awaitItem())

            val errorState = awaitItem() as AnalysisUiState.Error
            assertEquals(errorMessage, errorState.message)
        }

        coVerify(exactly = 1) { mockRepository.analyzeImage(mockUri) }
    }

    @Test
    fun `resetState 호출 시 Idle 상태로 초기화되어야 한다`() = runTest {
        // Given: Success 상태로 설정
        val successResult = AnalysisResult(
            face_shape = "계란형",
            skin_tone = "쿨톤",
            recommended_styles = emptyList()
        )
        coEvery { mockRepository.analyzeImage(mockUri) } returns ApiResult.Success(
            data = successResult,
            analysisId = 1
        )

        viewModel.analyzeImage(mockUri)
        testScheduler.advanceUntilIdle()

        // When: resetState 호출
        viewModel.uiState.test {
            // Success 상태 확인
            assertTrue(awaitItem() is AnalysisUiState.Success)

            viewModel.resetState()

            // Then: Idle 상태로 변경
            assertEquals(AnalysisUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `여러 번 analyzeImage 호출 시 마지막 호출의 결과만 반영되어야 한다`() = runTest {
        // Given: 서로 다른 결과 Mock
        val result1 = AnalysisResult("계란형", "쿨톤", emptyList())
        val result2 = AnalysisResult("둥근형", "웜톤", emptyList())

        coEvery { mockRepository.analyzeImage(mockUri) } returnsMany listOf(
            ApiResult.Success(result1, 1),
            ApiResult.Success(result2, 2)
        )

        // When: 연속으로 두 번 호출
        viewModel.analyzeImage(mockUri)
        testScheduler.advanceUntilIdle()

        viewModel.analyzeImage(mockUri)
        testScheduler.advanceUntilIdle()

        // Then: 마지막 결과가 반영됨
        viewModel.uiState.test {
            val state = awaitItem() as AnalysisUiState.Success
            assertEquals(2, state.analysisId)
            assertEquals(result2, state.data)
        }
    }
}
