package com.example.myapplication.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.myapplication.AnalysisResult
import com.example.myapplication.network.AnalysisData
import com.example.myapplication.network.ApiAnalysis
import com.example.myapplication.network.ApiRecommendation
import com.example.myapplication.network.ApiResponse
import com.example.myapplication.network.HairstyleApiService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HairstyleRepository 유닛 테스트
 *
 * 테스트 항목:
 * 1. 이미지 분석 성공 시 ApiResult.Success 반환
 * 2. 이미지 분석 실패 시 ApiResult.Error 반환
 * 3. HTTP 에러 응답 시 적절한 에러 메시지 반환
 * 4. 네트워크 예외 발생 시 에러 처리
 * 5. analysisId가 올바르게 반환되는지 확인
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HairstyleRepositoryTest {

    private lateinit var repository: HairstyleRepository
    private lateinit var mockContext: Context
    private lateinit var mockApiService: HairstyleApiService
    private lateinit var mockUri: Uri
    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockApiService = mockk(relaxed = true)
        mockUri = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)

        // Context Mock 설정
        every { mockContext.contentResolver } returns mockContentResolver
        every { mockContext.cacheDir } returns File(System.getProperty("java.io.tmpdir"))

        // ContentResolver Mock 설정 (빈 InputStream 반환)
        val mockInputStream = ByteArrayInputStream(ByteArray(0))
        every { mockContentResolver.openInputStream(any()) } returns mockInputStream

        // Repository 생성
        repository = HairstyleRepository(mockContext)

        // ApiService를 Mock으로 교체 (리플렉션 사용)
        val apiServiceField = HairstyleRepository::class.java.getDeclaredField("apiService")
        apiServiceField.isAccessible = true
        apiServiceField.set(repository, mockApiService)

        // Bitmap 관련 Mock (Android Framework 의존성 제거)
        mockkStatic(Bitmap::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `이미지 분석 성공 시 ApiResult_Success를 반환해야 한다`() = runTest {
        // Given: 성공 응답 Mock
        val mockResponse = ApiResponse(
            success = true,
            analysis_id = 123,
            data = AnalysisData(
                analysis = ApiAnalysis(
                    face_shape = "계란형",
                    personal_color = "쿨톤"
                ),
                recommendations = listOf(
                    ApiRecommendation(
                        style_name = "레이어드컷",
                        reason = "얼굴형에 잘 어울림"
                    )
                )
            ),
            error = null
        )

        coEvery { mockApiService.analyzeHairstyle(any()) } returns Response.success(mockResponse)

        // Mock 파일 생성을 위한 설정
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.delete() } returns true
        mockkConstructor(File::class)
        every { anyConstructed<File>().delete() } returns true

        // Mock FileOutputStream
        val mockOutputStream = mockk<FileOutputStream>(relaxed = true)
        mockkConstructor(FileOutputStream::class)
        every { anyConstructed<FileOutputStream>().use<Unit>(any()) } just Runs

        // Mock Bitmap
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        mockkStatic("android.graphics.BitmapFactory")
        every { android.graphics.BitmapFactory.decodeStream(any()) } returns mockBitmap
        every { mockBitmap.width } returns 1000
        every { mockBitmap.height } returns 1000
        every { mockBitmap.compress(any(), any(), any()) } returns true
        every { mockBitmap.recycle() } just Runs

        // Mock ExifInterface
        mockkConstructor(androidx.exifinterface.media.ExifInterface::class)
        every {
            anyConstructed<androidx.exifinterface.media.ExifInterface>().getAttributeInt(any(), any())
        } returns androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL

        // When: analyzeImage 호출
        val result = repository.analyzeImage(mockUri)

        // Then: Success 결과 확인
        assertTrue(result is ApiResult.Success)
        assertEquals(123, (result as ApiResult.Success).analysisId)
        assertEquals("계란형", result.data.face_shape)
        assertEquals("쿨톤", result.data.skin_tone)
        assertEquals(1, result.data.recommended_styles.size)
        assertEquals("레이어드컷", result.data.recommended_styles[0].name)
    }

    @Test
    fun `이미지 분석 실패 시 ApiResult_Error를 반환해야 한다`() = runTest {
        // Given: 실패 응답 Mock
        val errorMessage = "분석에 실패했습니다"
        val mockResponse = ApiResponse(
            success = false,
            analysis_id = null,
            data = null,
            error = errorMessage
        )

        coEvery { mockApiService.analyzeHairstyle(any()) } returns Response.success(mockResponse)

        // Mock 설정 (이미지 처리용)
        setupImageMocks()

        // When: analyzeImage 호출
        val result = repository.analyzeImage(mockUri)

        // Then: Error 결과 확인
        assertTrue(result is ApiResult.Error)
        assertEquals(errorMessage, (result as ApiResult.Error).message)
    }

    @Test
    fun `HTTP 에러 응답 시 적절한 에러 메시지를 반환해야 한다`() = runTest {
        // Given: HTTP 500 에러 Mock
        val errorBody = "Internal Server Error".toResponseBody("text/plain".toMediaTypeOrNull())
        coEvery { mockApiService.analyzeHairstyle(any()) } returns Response.error(500, errorBody)

        setupImageMocks()

        // When: analyzeImage 호출
        val result = repository.analyzeImage(mockUri)

        // Then: Error 결과 확인
        assertTrue(result is ApiResult.Error)
        assertEquals(500, (result as ApiResult.Error).code)
        assertTrue(result.message.contains("서버 오류"))
    }

    @Test
    fun `네트워크 예외 발생 시 에러를 반환해야 한다`() = runTest {
        // Given: 네트워크 예외 Mock
        coEvery { mockApiService.analyzeHairstyle(any()) } throws Exception("Network timeout")

        setupImageMocks()

        // When: analyzeImage 호출
        val result = repository.analyzeImage(mockUri)

        // Then: Error 결과 확인
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.contains("네트워크 오류"))
    }

    @Test
    fun `이미지 파일을 읽을 수 없을 때 에러를 반환해야 한다`() = runTest {
        // Given: InputStream이 null인 경우
        every { mockContentResolver.openInputStream(any()) } returns null

        // When: analyzeImage 호출
        val result = repository.analyzeImage(mockUri)

        // Then: Error 결과 확인
        assertTrue(result is ApiResult.Error)
        assertEquals("이미지 파일을 읽을 수 없습니다", (result as ApiResult.Error).message)
    }

    @Test
    fun `여러 추천 스타일을 올바르게 파싱해야 한다`() = runTest {
        // Given: 3개의 추천 스타일이 포함된 응답
        val mockResponse = ApiResponse(
            success = true,
            analysis_id = 456,
            data = AnalysisData(
                analysis = ApiAnalysis("계란형", "쿨톤"),
                recommendations = listOf(
                    ApiRecommendation("레이어드컷", "1순위"),
                    ApiRecommendation("웨이브 펌", "2순위"),
                    ApiRecommendation("시스루뱅", "3순위")
                )
            ),
            error = null
        )

        coEvery { mockApiService.analyzeHairstyle(any()) } returns Response.success(mockResponse)
        setupImageMocks()

        // When: analyzeImage 호출
        val result = repository.analyzeImage(mockUri)

        // Then: 모든 스타일이 올바르게 파싱되어야 함
        assertTrue(result is ApiResult.Success)
        val styles = (result as ApiResult.Success).data.recommended_styles
        assertEquals(3, styles.size)

        // 스타일별 점수 확인 (하드코딩된 점수)
        assertEquals(0.95, styles[0].score)
        assertEquals(0.88, styles[1].score)
        assertEquals(0.82, styles[2].score)

        // 이름과 이유 확인
        assertEquals("레이어드컷", styles[0].name)
        assertEquals("1순위", styles[0].reason)
    }

    /**
     * 이미지 처리에 필요한 공통 Mock 설정
     */
    private fun setupImageMocks() {
        // Mock 파일
        mockkConstructor(File::class)
        every { anyConstructed<File>().delete() } returns true

        // Mock FileOutputStream
        mockkConstructor(FileOutputStream::class)
        every { anyConstructed<FileOutputStream>().use<Unit>(any()) } just Runs

        // Mock Bitmap
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        mockkStatic("android.graphics.BitmapFactory")
        every { android.graphics.BitmapFactory.decodeStream(any()) } returns mockBitmap
        every { mockBitmap.width } returns 1000
        every { mockBitmap.height } returns 1000
        every { mockBitmap.compress(any(), any(), any()) } returns true
        every { mockBitmap.recycle() } just Runs

        // Mock ExifInterface
        mockkConstructor(androidx.exifinterface.media.ExifInterface::class)
        every {
            anyConstructed<androidx.exifinterface.media.ExifInterface>().getAttributeInt(any(), any())
        } returns androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
    }
}
