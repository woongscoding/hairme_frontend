package com.example.myapplication.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.myapplication.AnalysisResult
import com.example.myapplication.HairstyleRecommendation
import com.example.myapplication.network.HairstyleApiService
import com.example.myapplication.network.ProductClickRequest
import com.example.myapplication.network.ProductClickResponse
import com.example.myapplication.network.RecommendedProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
// import javax.inject.Inject  // Hilt 임시 비활성화

import com.example.myapplication.util.NetworkUtils
import com.example.myapplication.util.toNetworkError
import com.example.myapplication.util.toUserFriendlyMessage
import com.example.myapplication.data.local.AnalysisHistoryDao
import com.example.myapplication.data.local.AnalysisHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 헤어스타일 합성 결과 묶음: 합성 이미지 + 스타일 맞춤 제휴 제품(+대가성 문구)
 */
data class SynthesisResult(
    val image: Bitmap,
    val recommendedProducts: List<RecommendedProduct> = emptyList(),
    val disclosure: String? = null
)

/**
 * HairstyleRepository
 *
 * 임시로 Hilt 비활성화 - 빌드 문제 해결 후 재활성화 예정
 * Context를 생성자로 받도록 수정
 */
class HairstyleRepository(
    private val context: Context
) {

    // Hilt 비활성화로 인해 수동으로 의존성 생성
    // TODO: Hilt 재활성화 후 생성자 주입으로 변경

    // Hairstyle Lambda용 API (얼굴 분석, 헤어스타일 합성)
    private val apiService: HairstyleApiService by lazy {
        com.example.myapplication.network.RetrofitClient.hairstyleApiService
    }

    // Beauty Lambda용 API (염색색 추천/합성, 퍼스널컬러) - 2025-12-31 분리
    private val beautyApiService: com.example.myapplication.network.BeautyApiService by lazy {
        com.example.myapplication.network.RetrofitClient.beautyApiService
    }

    // Usage API (일일 무료 합성 횟수 관리)
    private val usageApiService: com.example.myapplication.network.UsageApiService by lazy {
        com.example.myapplication.network.RetrofitClient.usageApiService
    }

    // Products API (제휴 제품 추천/클릭) - 클릭은 JWT 필요
    private val productsApiService: com.example.myapplication.network.ProductsApiService by lazy {
        com.example.myapplication.network.RetrofitClient.productsApiService
    }

    private val networkUtils: NetworkUtils by lazy {
        NetworkUtils(context)
    }
    private val analysisHistoryDao: AnalysisHistoryDao? = null  // Room Database는 나중에 추가

    companion object {
        private const val TAG = "HairstyleRepository"
        private const val MAX_IMAGE_WIDTH = 1024
        private const val MAX_IMAGE_HEIGHT = 1024
        private const val JPEG_QUALITY = 85
    }

    /**
     * 이미지를 분석하여 헤어스타일 추천 결과를 반환
     * ✅ v26: 성별 파라미터 추가
     */
    suspend fun analyzeImage(imageUri: Uri, gender: String = "male"): ApiResult<AnalysisResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 이미지 분석 시작")
            Log.d(TAG, "   - URI: $imageUri")

            // 1. URI를 File로 변환 (EXIF 회전 + 리사이징)
            val imageFile = uriToFile(imageUri)
            if (imageFile == null) {
                Log.e(TAG, "❌ 이미지 변환 실패")
                return@withContext ApiResult.Error("이미지를 처리할 수 없습니다")
            }

            Log.d(TAG, "✅ 이미지 파일 생성 완료")
            Log.d(TAG, "   - 파일명: ${imageFile.name}")
            Log.d(TAG, "   - 크기: ${imageFile.length() / 1024}KB")
            Log.d(TAG, "   - 경로: ${imageFile.absolutePath}")
            Log.d(TAG, "   - 존재 여부: ${imageFile.exists()}")

            // 2. 파일 크기 검증 (너무 작거나 큰 파일 거부)
            val fileSizeKB = imageFile.length() / 1024
            if (fileSizeKB < 10) {
                Log.e(TAG, "❌ 파일이 너무 작습니다: ${fileSizeKB}KB")
                return@withContext ApiResult.Error("이미지 파일이 너무 작습니다")
            }
            if (fileSizeKB > 5120) { // 5MB
                Log.e(TAG, "❌ 파일이 너무 큽니다: ${fileSizeKB}KB")
                return@withContext ApiResult.Error("이미지 파일이 너무 큽니다 (최대 5MB)")
            }

            // 3. Multipart Body 생성 (이미지 파일 + 성별)
            val requestBody = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", imageFile.name, requestBody)

            // 성별 파라미터 추가
            val genderPart = MultipartBody.Part.createFormData("gender", gender)

            // 4. API 호출 (gender 파라미터 포함)
            Log.d(TAG, "🌐 API 호출 시작... (gender=$gender)")
            val response = apiService.analyzeHairstyle(filePart, genderPart)
            Log.d(TAG, "📡 서버 응답 수신 - HTTP ${response.code()}")

            // 4. 임시 파일 삭제
            imageFile.delete()

            // 5. 응답 처리
            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                Log.d(TAG, "📦 서버 응답 파싱 완료")
                Log.d(TAG, "   - success: ${apiResponse.success}")
                Log.d(TAG, "   - analysis_id: ${apiResponse.analysis_id}")
                Log.d(TAG, "   - error: ${apiResponse.error}")
                Log.d(TAG, "   - processing_time: ${apiResponse.processing_time}")
                Log.d(TAG, "   - model_used: ${apiResponse.model_used}")

                // ✅ success 필드 체크 추가
                if (!apiResponse.success) {
                    val errorMsg = apiResponse.error ?: "알 수 없는 오류가 발생했습니다"
                    Log.e(TAG, "❌ 서버 분석 실패")
                    Log.e(TAG, "   - 에러 메시지: $errorMsg")
                    return@withContext ApiResult.Error("서버 오류: $errorMsg")
                }

                Log.d(TAG, "✅ API 성공 - analysisId: ${apiResponse.analysis_id}")

                // API 응답에서 데이터가 없는 경우 처리
                if (apiResponse.data == null) {
                    Log.e(TAG, "❌ API 응답에 데이터가 없음")
                    return@withContext ApiResult.Error("서버 응답이 올바르지 않습니다")
                }

                // ✅ v26: 서버 응답 상세 로깅 (hairstyle_id 확인용)
                Log.d(TAG, "📊 서버 응답 상세 정보:")
                Log.d(TAG, "   - 얼굴형: ${apiResponse.data.analysis.face_shape}")
                Log.d(TAG, "   - 피부톤: ${apiResponse.data.analysis.personal_color}")
                Log.d(TAG, "   - 추천 개수: ${apiResponse.data.recommendations.size}")
                apiResponse.data.recommendations.forEachIndexed { index, rec ->
                    Log.d(TAG, "   [${index + 1}] ${rec.style_name}")
                    Log.d(TAG, "       - hairstyle_id: ${rec.hairstyle_id}")
                    Log.d(TAG, "       - score: ${rec.score}")
                    Log.d(TAG, "       - reason: ${rec.reason}")
                }

                // AnalysisData를 AnalysisResult로 변환
                val result = apiResponse.data.toAnalysisResult()

                // ✅ v26: 변환된 결과 로깅 (hairstyle_id가 제대로 전달되는지 확인)
                Log.d(TAG, "🔄 변환된 AnalysisResult:")
                result.recommended_styles.forEachIndexed { index, style ->
                    Log.d(TAG, "   [${index + 1}] ${style.name}")
                    Log.d(TAG, "       - id: ${style.id}")
                    Log.d(TAG, "       - score: ${style.score}")
                }

                // 6. 캐싱 (옵션)
                analysisHistoryDao?.let { dao ->
                    try {
                        val entity = AnalysisHistoryEntity.fromAnalysisResult(
                            analysisId = apiResponse.analysis_id,
                            result = result
                        )
                        dao.insertAnalysis(entity)
                        Log.d(TAG, "✅ 캐싱 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ 캐싱 실패 (무시): ${e.message}")
                    }
                }

                ApiResult.Success(result, apiResponse.analysis_id)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "알 수 없는 오류"
                Log.e(TAG, "❌ API 실패 (${response.code()}): $errorMsg")
                ApiResult.Error("서버 오류: $errorMsg", response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 예외 발생: ${e.message}", e)
            val networkError = e.toNetworkError()
            ApiResult.Error(networkError.toUserFriendlyMessage())
        }
    }

    /**
     * URI를 File로 변환하면서 EXIF 회전 처리 + 이미지 리사이징
     */
    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val rotation = getRotationFromExif(inputStream)
            inputStream.close()

            val newInputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(newInputStream)
            newInputStream.close()

            if (originalBitmap == null) return null

            val rotatedBitmap = if (rotation != 0) {
                rotateBitmap(originalBitmap, rotation)
            } else {
                originalBitmap
            }

            val resizedBitmap = resizeBitmap(rotatedBitmap)
            val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")

            FileOutputStream(tempFile).use { outputStream ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            }

            if (originalBitmap != rotatedBitmap && originalBitmap != resizedBitmap) {
                originalBitmap.recycle()
            }
            if (rotatedBitmap != resizedBitmap) {
                rotatedBitmap.recycle()
            }
            resizedBitmap.recycle()

            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getRotationFromExif(inputStream: InputStream): Int {
        return try {
            val exif = ExifInterface(inputStream)
            when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_WIDTH && height <= MAX_IMAGE_HEIGHT) {
            return bitmap
        }

        val scaleWidth = MAX_IMAGE_WIDTH.toFloat() / width
        val scaleHeight = MAX_IMAGE_HEIGHT.toFloat() / height
        val scale = minOf(scaleWidth, scaleHeight)

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ========================================
    // 캐싱 관련 메서드 - Hilt 비활성화로 인해 임시 구현
    // ========================================

    /**
     * 모든 분석 히스토리 조회 (Flow로 실시간 업데이트)
     * 임시로 빈 Flow 반환
     * ✅ v26: DynamoDB UUID 지원 (Int → String)
     */
    fun getAllAnalysisHistory(): Flow<List<Pair<String?, AnalysisResult>>> {
        return kotlinx.coroutines.flow.flowOf(emptyList())
    }

    /**
     * 가장 최근 분석 결과 조회
     * 임시로 null 반환
     * ✅ v26: DynamoDB UUID 지원 (Int → String)
     */
    suspend fun getLatestAnalysis(): Pair<String?, AnalysisResult>? {
        return null
    }

    /**
     * 특정 analysisId로 결과 조회
     * 임시로 null 반환
     */
    suspend fun getAnalysisByServerId(@Suppress("UNUSED_PARAMETER") analysisId: String): Pair<String?, AnalysisResult>? {
        return null
    }

    /**
     * 오래된 히스토리 삭제 (30일 이상)
     * 임시로 아무 동작 안함
     */
    suspend fun cleanOldHistory() {
        Log.d(TAG, "임시: cleanOldHistory 호출됨 (비활성화)")
    }

    /**
     * 모든 히스토리 삭제
     * 임시로 아무 동작 안함
     */
    suspend fun clearAllHistory() {
        Log.d(TAG, "임시: clearAllHistory 호출됨 (비활성화)")
    }

    /**
     * 히스토리 개수 조회
     * 임시로 0 반환
     */
    fun getHistoryCount(): Flow<Int> {
        return kotlinx.coroutines.flow.flowOf(0)
    }

    /**
     * ✅ v30: 헤어스타일 합성 API 호출
     *
     * 사용자 얼굴 사진에 선택한 헤어스타일을 적용한 이미지를 생성합니다.
     *
     * @param imageUri 사용자 얼굴 사진 URI
     * @param hairstyleName 적용할 헤어스타일 이름 (예: 투블럭컷)
     * @param gender 성별 (male/female)
     * @return 합성된 이미지 + 스타일 맞춤 제휴 제품(SynthesisResult) 또는 에러
     */
    suspend fun synthesizeHairstyle(
        imageUri: Uri,
        hairstyleName: String,
        gender: String = "male",
        deviceId: String
    ): ApiResult<SynthesisResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎨 헤어스타일 합성 시작")
            Log.d(TAG, "   - 스타일: $hairstyleName")
            Log.d(TAG, "   - 성별: $gender")

            // 1. URI를 File로 변환
            val imageFile = uriToFile(imageUri)
            if (imageFile == null) {
                Log.e(TAG, "❌ 이미지 변환 실패")
                return@withContext ApiResult.Error("이미지를 처리할 수 없습니다")
            }

            Log.d(TAG, "✅ 이미지 파일 생성 완료 (${imageFile.length() / 1024}KB)")

            // 2. Multipart Body 생성
            val requestBody = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", imageFile.name, requestBody)
            val hairstyleNamePart = MultipartBody.Part.createFormData("hairstyle_name", hairstyleName)
            val genderPart = MultipartBody.Part.createFormData("gender", gender)
            val deviceIdPart = MultipartBody.Part.createFormData("device_id", deviceId)

            // 3. API 호출
            Log.d(TAG, "🌐 합성 API 호출 시작...")
            val response = apiService.synthesizeHairstyle(filePart, hairstyleNamePart, genderPart, deviceIdPart)
            Log.d(TAG, "📡 서버 응답 수신 - HTTP ${response.code()}")

            // 4. 임시 파일 삭제
            imageFile.delete()

            // 5. 응답 처리
            if (response.isSuccessful && response.body() != null) {
                val synthesisResponse = response.body()!!

                if (!synthesisResponse.success || synthesisResponse.imageBase64 == null) {
                    val errorMsg = synthesisResponse.message ?: "합성에 실패했습니다"
                    Log.e(TAG, "❌ 합성 실패: $errorMsg")
                    return@withContext ApiResult.Error(errorMsg)
                }

                // Base64 디코딩하여 Bitmap으로 변환
                try {
                    // 🔍 디버깅: Base64 응답 확인
                    val base64Data = synthesisResponse.imageBase64
                    Log.d(TAG, "📦 Base64 길이: ${base64Data?.length ?: 0}")
                    Log.d(TAG, "📦 Base64 시작 50자: ${base64Data?.take(50)}")
                    Log.d(TAG, "📦 이미지 포맷: ${synthesisResponse.imageFormat}")

                    val imageBytes = android.util.Base64.decode(
                        synthesisResponse.imageBase64,
                        android.util.Base64.DEFAULT
                    )
                    Log.d(TAG, "📦 디코딩된 바이트 크기: ${imageBytes.size}")
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                    if (bitmap == null) {
                        Log.e(TAG, "❌ 이미지 디코딩 실패")
                        // 🔍 디버깅: 상세 정보를 에러 메시지에 포함
                        val debugInfo = "Base64길이:${base64Data?.length ?: 0}, 시작:${base64Data?.take(30)}, 바이트:${imageBytes.size}"
                        return@withContext ApiResult.Error("이미지 디코딩 실패\n[$debugInfo]")
                    }

                    // 제휴 제품 추천 (없거나 빈 배열이면 UI에서 섹션 숨김)
                    Log.d(TAG, "🛍️ 추천 제품 ${synthesisResponse.recommendedProducts.size}개 수신")
                    Log.d(TAG, "✅ 합성 완료 (${synthesisResponse.processingTime}초)")
                    ApiResult.Success(
                        SynthesisResult(
                            image = bitmap,
                            recommendedProducts = synthesisResponse.recommendedProducts,
                            disclosure = synthesisResponse.disclosure
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Base64 디코딩 실패: ${e.message}")
                    return@withContext ApiResult.Error("이미지 처리에 실패했습니다")
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "서버 오류"
                Log.e(TAG, "❌ API 실패 (${response.code()}): $errorMsg")
                ApiResult.Error("서버 오류: $errorMsg", response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 예외 발생: ${e.message}", e)
            val networkError = e.toNetworkError()
            ApiResult.Error(networkError.toUserFriendlyMessage())
        }
    }

    /**
     * 제휴 제품 클릭 → 제휴 링크 발급 (POST /api/products/click, JWT 필요)
     *
     * 클릭 로그는 서버가 기록하므로 반드시 매번 이 API를 경유해야 한다.
     * (제휴 링크 하드코딩/로컬 캐싱 금지)
     *
     * - 성공: affiliate_url 포함 응답
     * - 404: 존재하지 않는 제품 → code=404 에러로 반환 (UI에서 무시+토스트)
     */
    suspend fun clickProduct(request: ProductClickRequest): ApiResult<ProductClickResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🛍️ 제휴 링크 발급 요청: ${request.productId} (source=${request.source})")
                val response = productsApiService.clickProduct(request)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success && !body.affiliateUrl.isNullOrBlank()) {
                        Log.d(TAG, "✅ 제휴 링크 발급 성공")
                        ApiResult.Success(body)
                    } else {
                        Log.w(TAG, "❌ 제휴 링크 응답에 URL 없음")
                        ApiResult.Error("제품 링크를 불러오지 못했어요", response.code())
                    }
                } else {
                    Log.w(TAG, "❌ 제휴 링크 발급 실패 (${response.code()})")
                    ApiResult.Error("제품 링크를 불러오지 못했어요", response.code())
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 제휴 링크 발급 예외: ${e.message}")
                val networkError = e.toNetworkError()
                ApiResult.Error(networkError.toUserFriendlyMessage())
            }
        }

    /**
     * ✅ v33: 퍼스널컬러 기반 염색색 추천 조회
     *
     * @param personalColor 퍼스널컬러 (봄웜, 여름쿨, 가을웜, 겨울쿨)
     * @return 추천 염색색 목록
     */
    suspend fun getHairColorRecommendations(
        personalColor: String
    ): ApiResult<com.example.myapplication.network.HairColorRecommendationResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎨 염색색 추천 조회 시작: $personalColor (Beauty Lambda)")

            // Beauty Lambda 사용 (경량, 빠른 응답)
            val response = beautyApiService.getHairColorRecommendations(personalColor)
            Log.d(TAG, "📡 서버 응답 수신 - HTTP ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                if (result.success) {
                    Log.d(TAG, "✅ 염색색 추천 성공: ${result.recommended.size}개")
                    ApiResult.Success(result)
                } else {
                    Log.e(TAG, "❌ 염색색 추천 실패")
                    ApiResult.Error("염색색 추천을 가져올 수 없습니다")
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "서버 오류"
                Log.e(TAG, "❌ API 실패 (${response.code()}): $errorMsg")
                ApiResult.Error("서버 오류: $errorMsg", response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 예외 발생: ${e.message}", e)
            val networkError = e.toNetworkError()
            ApiResult.Error(networkError.toUserFriendlyMessage())
        }
    }

    /**
     * ✅ v34: 염색색 합성 API 호출
     *
     * 사용자 얼굴 사진에 선택한 염색색을 적용한 이미지를 생성합니다.
     *
     * @param imageUri 사용자 얼굴 사진 URI
     * @param colorName 염색색 이름 (예: 밀크브라운)
     * @param colorHex 염색색 HEX 코드 (예: #C4A484)
     * @return 합성된 이미지 Bitmap 또는 에러
     */
    suspend fun synthesizeHairColor(
        imageUri: Uri,
        colorName: String,
        colorHex: String,
        deviceId: String
    ): ApiResult<Bitmap> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎨 염색색 합성 시작")
            Log.d(TAG, "   - 색상: $colorName ($colorHex)")

            // 1. URI를 File로 변환
            val imageFile = uriToFile(imageUri)
            if (imageFile == null) {
                Log.e(TAG, "❌ 이미지 변환 실패")
                return@withContext ApiResult.Error("이미지를 처리할 수 없습니다")
            }

            Log.d(TAG, "✅ 이미지 파일 생성 완료 (${imageFile.length() / 1024}KB)")

            // 2. Multipart Body 생성
            val requestBody = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", imageFile.name, requestBody)
            val colorNamePart = MultipartBody.Part.createFormData("color_name", colorName)
            val colorHexPart = MultipartBody.Part.createFormData("color_hex", colorHex)
            val deviceIdPart = MultipartBody.Part.createFormData("device_id", deviceId)

            // 3. API 호출 (Beauty Lambda 사용 - 경량, 빠른 응답)
            Log.d(TAG, "🌐 염색색 합성 API 호출 시작... (Beauty Lambda)")
            val response = beautyApiService.synthesizeHairColor(filePart, colorNamePart, colorHexPart, deviceIdPart)
            Log.d(TAG, "📡 서버 응답 수신 - HTTP ${response.code()}")

            // 4. 임시 파일 삭제
            imageFile.delete()

            // 5. 응답 처리
            if (response.isSuccessful && response.body() != null) {
                val synthesisResponse = response.body()!!

                if (!synthesisResponse.success || synthesisResponse.image_base64 == null) {
                    val errorMsg = synthesisResponse.message ?: "염색색 합성에 실패했습니다"
                    Log.e(TAG, "❌ 합성 실패: $errorMsg")
                    return@withContext ApiResult.Error(errorMsg)
                }

                // Base64 디코딩하여 Bitmap으로 변환
                try {
                    val base64Data = synthesisResponse.image_base64
                    Log.d(TAG, "📦 Base64 길이: ${base64Data?.length ?: 0}")

                    val imageBytes = android.util.Base64.decode(
                        base64Data,
                        android.util.Base64.DEFAULT
                    )
                    Log.d(TAG, "📦 디코딩된 바이트 크기: ${imageBytes.size}")
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                    if (bitmap == null) {
                        Log.e(TAG, "❌ 이미지 디코딩 실패")
                        return@withContext ApiResult.Error("이미지 디코딩 실패")
                    }

                    Log.d(TAG, "✅ 염색색 합성 완료 (${synthesisResponse.processing_time}초)")
                    ApiResult.Success(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Base64 디코딩 실패: ${e.message}")
                    return@withContext ApiResult.Error("이미지 처리에 실패했습니다")
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "서버 오류"
                Log.e(TAG, "❌ API 실패 (${response.code()}): $errorMsg")
                ApiResult.Error("서버 오류: $errorMsg", response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 예외 발생: ${e.message}", e)
            val networkError = e.toNetworkError()
            ApiResult.Error(networkError.toUserFriendlyMessage())
        }
    }

    // ========================================
    // 일일 무료 합성 횟수 관리
    // ========================================

    suspend fun getUsage(deviceId: String): ApiResult<com.example.myapplication.network.UsageResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📊 Usage 조회: $deviceId")
            val response = usageApiService.getUsage(deviceId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "✅ Usage 조회 성공: remaining=${body.remaining}/${body.dailyLimit}")
                ApiResult.Success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "서버 오류"
                Log.e(TAG, "❌ Usage 조회 실패 (${response.code()}): $errorMsg")
                ApiResult.Error("Usage 조회 실패: $errorMsg", response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Usage 조회 예외: ${e.message}", e)
            val networkError = e.toNetworkError()
            ApiResult.Error(networkError.toUserFriendlyMessage())
        }
    }

    suspend fun consumeUsage(deviceId: String): ApiResult<com.example.myapplication.network.UsageResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📊 Usage 소비: $deviceId")
            val response = usageApiService.consumeUsage(deviceId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "✅ Usage 소비 성공: remaining=${body.remaining}/${body.dailyLimit}")
                ApiResult.Success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "서버 오류"
                Log.e(TAG, "❌ Usage 소비 실패 (${response.code()}): $errorMsg")
                ApiResult.Error("Usage 소비 실패: $errorMsg", response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Usage 소비 예외: ${e.message}", e)
            val networkError = e.toNetworkError()
            ApiResult.Error(networkError.toUserFriendlyMessage())
        }
    }
}