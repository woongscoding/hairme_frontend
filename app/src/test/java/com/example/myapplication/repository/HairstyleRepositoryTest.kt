package com.example.myapplication.repository

import android.content.Context
import android.net.Uri
import com.example.myapplication.network.Analysis
import com.example.myapplication.network.AnalysisData
import com.example.myapplication.network.ApiResponse
import com.example.myapplication.network.HairstyleApiService
import com.example.myapplication.network.Recommendation
import com.example.myapplication.network.StyleActionRequest
import com.example.myapplication.network.SynthesisResponse
import com.example.myapplication.StyleRecContext
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okio.Buffer
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * HairstyleRepository.analyzeImage 유닛 테스트
 *
 * 이미지 전처리(uriToFile: EXIF 회전·리사이즈)는 Android 프레임워크 의존이라 가짜 파일로 대체하고,
 * 응답 처리(성공 변환·서버 실패·HTTP 에러·네트워크 예외)를 확인한다.
 */
class HairstyleRepositoryTest {

    private lateinit var repository: HairstyleRepository
    private lateinit var mockApiService: HairstyleApiService
    private lateinit var mockUri: Uri
    private lateinit var uploadFile: File

    @Before
    fun setup() {
        mockApiService = mockk()
        mockUri = mockk(relaxed = true)

        // 크기 검증(10KB 이상)을 통과하는 업로드용 임시 파일
        uploadFile = File.createTempFile("upload_", ".jpg").apply { writeBytes(ByteArray(20 * 1024)) }

        val context = mockk<Context>(relaxed = true)
        repository = spyk(HairstyleRepository(context) { mockApiService }, recordPrivateCalls = true)
        every { repository["uriToFile"](mockUri) } returns uploadFile
    }

    @After
    fun tearDown() {
        uploadFile.delete()
        unmockkAll()
    }

    private fun analysisResponse(vararg recommendations: Recommendation) = ApiResponse(
        success = true,
        analysis_id = "a1b2-c3d4",
        data = AnalysisData(
            analysis = Analysis(face_shape = "계란형", personal_color = "쿨톤", features = "이목구비가 뚜렷함"),
            recommendations = recommendations.toList()
        )
    )

    @Test
    fun `분석 성공 시 결과와 analysisId를 돌려준다`() = runTest {
        coEvery { mockApiService.analyzeHairstyle(any(), any()) } returns Response.success(
            analysisResponse(Recommendation(hairstyle_id = 12, style_name = "레이어드컷", reason = "얼굴형에 잘 어울림", score = 0.91))
        )

        val result = repository.analyzeImage(mockUri)

        assertIs<ApiResult.Success<*>>(result)
        assertEquals("a1b2-c3d4", result.analysisId)
        val data = (result as ApiResult.Success).data
        assertEquals("계란형", data.face_shape)
        assertEquals("쿨톤", data.skin_tone)
        assertEquals(listOf("레이어드컷"), data.recommended_styles.map { it.name })
    }

    @Test
    fun `여러 추천 스타일을 서버 순서와 점수대로 변환한다`() = runTest {
        coEvery { mockApiService.analyzeHairstyle(any(), any()) } returns Response.success(
            analysisResponse(
                Recommendation(style_name = "레이어드컷", reason = "1순위", score = 0.95),
                Recommendation(style_name = "웨이브 펌", reason = "2순위", score = 0.88),
                Recommendation(style_name = "시스루뱅", reason = "3순위", score = 0.82)
            )
        )

        val styles = (repository.analyzeImage(mockUri) as ApiResult.Success).data.recommended_styles

        assertEquals(listOf("레이어드컷", "웨이브 펌", "시스루뱅"), styles.map { it.name })
        assertEquals(listOf(0.95, 0.88, 0.82), styles.map { it.score })
        assertEquals("1순위", styles[0].reason)
    }

    @Test
    fun `서버가 success=false를 주면 한국어 에러 문구를 그대로 보여준다`() = runTest {
        coEvery { mockApiService.analyzeHairstyle(any(), any()) } returns Response.success(
            ApiResponse(success = false, error = "얼굴을 찾지 못했습니다. 정면 사진으로 다시 시도해주세요")
        )

        val result = repository.analyzeImage(mockUri)

        assertIs<ApiResult.Error>(result)
        assertEquals("얼굴을 찾지 못했습니다. 정면 사진으로 다시 시도해주세요", result.message)
    }

    @Test
    fun `HTTP 500은 본문 원문 대신 상태 코드별 안내를 보여준다`() = runTest {
        coEvery { mockApiService.analyzeHairstyle(any(), any()) } returns
            Response.error(500, "Internal Server Error".toResponseBody("text/plain".toMediaTypeOrNull()))

        val result = repository.analyzeImage(mockUri)

        assertIs<ApiResult.Error>(result)
        assertEquals(500, result.code)
        assertEquals("서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요", result.message)
    }

    @Test
    fun `네트워크 연결이 없으면 인터넷 연결 안내를 보여준다`() = runTest {
        coEvery { mockApiService.analyzeHairstyle(any(), any()) } throws UnknownHostException("no host")

        val result = repository.analyzeImage(mockUri)

        assertIs<ApiResult.Error>(result)
        assertEquals("인터넷 연결을 확인해주세요", result.message)
    }

    @Test
    fun `이미지를 읽을 수 없으면 서버를 부르지 않고 에러를 돌려준다`() = runTest {
        every { repository["uriToFile"](mockUri) } returns null

        val result = repository.analyzeImage(mockUri)

        assertIs<ApiResult.Error>(result)
        assertEquals("이미지를 처리할 수 없습니다", result.message)
    }

    // ===== 추천 스타일 행동 신호 =====

    private fun MultipartBody.Part?.value(): String? =
        this?.let { Buffer().also { buf -> it.body.writeTo(buf) }.readUtf8() }

    private fun MultipartBody.Part?.fieldName(): String? =
        this?.headers?.get("Content-Disposition")?.substringAfter("name=\"")?.substringBefore("\"")

    private fun stubSynthesize(captured: MutableList<MultipartBody.Part?>) {
        coEvery {
            mockApiService.synthesizeHairstyle(
                any(), any(), any(), any(),
                captureNullable(captured), captureNullable(captured), captureNullable(captured)
            )
        } returns Response.error<SynthesisResponse>(500, "err".toResponseBody("text/plain".toMediaTypeOrNull()))
    }

    @Test
    fun `추천 카드에서 들어온 합성은 analysis_id·rec_rank·hairstyle_id를 함께 보낸다`() = runTest {
        val captured = mutableListOf<MultipartBody.Part?>()
        stubSynthesize(captured)

        repository.synthesizeHairstyle(
            mockUri, "레이어드컷", "female", "dev-1",
            StyleRecContext(analysisId = "a1b2", recRank = 2, hairstyleId = 57)
        )

        assertEquals(listOf("analysis_id", "rec_rank", "hairstyle_id"), captured.map { it.fieldName() })
        assertEquals(listOf("a1b2", "2", "57"), captured.map { it.value() })
    }

    @Test
    fun `추천 밖 진입 합성은 추천 맥락 파트를 생략한다`() = runTest {
        val captured = mutableListOf<MultipartBody.Part?>()
        stubSynthesize(captured)

        repository.synthesizeHairstyle(mockUri, "레이어드컷", "female", "dev-1")

        assertEquals(listOf<MultipartBody.Part?>(null, null, null), captured)
    }

    @Test
    fun `hairstyle_id가 없으면 그 파트만 생략한다`() = runTest {
        val captured = mutableListOf<MultipartBody.Part?>()
        stubSynthesize(captured)

        repository.synthesizeHairstyle(
            mockUri, "레이어드컷", "female", "dev-1",
            StyleRecContext(analysisId = "a1b2", recRank = 4, hairstyleId = null)
        )

        assertEquals(listOf("a1b2", "4", null), captured.map { it.value() })
    }

    private val styleAction = StyleActionRequest(
        analysisId = "a1b2", styleName = "레이어드컷", hairstyleId = 57,
        action = "save", resultId = "abcd1234abcd1234", deviceId = "dev-1"
    )

    @Test
    fun `구버전 서버 404여도 스타일 행동 신호는 조용히 끝난다`() = runTest {
        coEvery { mockApiService.sendStyleAction(any()) } returns
            Response.error(404, "Not Found".toResponseBody("text/plain".toMediaTypeOrNull()))

        repository.sendStyleAction(styleAction)

        coVerify(exactly = 1) { mockApiService.sendStyleAction(styleAction) }
    }

    @Test
    fun `네트워크 예외도 스타일 행동 신호에서 삼킨다`() = runTest {
        coEvery { mockApiService.sendStyleAction(any()) } throws UnknownHostException("no host")

        repository.sendStyleAction(styleAction)
    }
}
