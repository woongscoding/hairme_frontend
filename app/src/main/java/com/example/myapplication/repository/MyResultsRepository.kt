package com.example.myapplication.repository

import android.util.Log
import com.example.myapplication.network.MyResultsResponse
import com.example.myapplication.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * MyResultsRepository
 *
 * 회원 합성 결과 히스토리 조회.
 * 기존 AuthRepository와 동일하게 수동 DI (Hilt 재활성화 시 생성자 주입으로 변경)
 */
class MyResultsRepository {

    private val myResultsApiService by lazy {
        RetrofitClient.myResultsApiService
    }

    companion object {
        private const val TAG = "MyResultsRepository"
        private const val NETWORK_ERROR_MESSAGE = "네트워크 연결을 확인해주세요"
        private const val FETCH_ERROR_MESSAGE = "결과를 불러오지 못했어요. 잠시 후 다시 시도해주세요"
    }

    /**
     * 내 합성 결과 목록 조회
     * access 만료 시 Authenticator가 자동으로 리프레시 후 재시도한다 (최종 실패 시 401)
     */
    suspend fun getMyResults(
        continuationToken: String? = null,
        limit: Int = 20
    ): ApiResult<MyResultsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = myResultsApiService.getMyResults(limit, continuationToken)
            val body = response.body()

            if (response.isSuccessful && body != null) {
                Log.d(TAG, "✅ 내 결과 ${body.results.size}건 조회 (nextToken=${body.nextToken != null})")
                ApiResult.Success(body)
            } else {
                Log.w(TAG, "❌ 내 결과 조회 실패 - HTTP ${response.code()}")
                val message = if (response.code() == 401) {
                    "로그인이 필요해요"
                } else {
                    FETCH_ERROR_MESSAGE
                }
                ApiResult.Error(message, response.code())
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ 네트워크 오류: ${e.message}")
            ApiResult.Error(NETWORK_ERROR_MESSAGE)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 내 결과 조회 중 오류: ${e.message}")
            ApiResult.Error(FETCH_ERROR_MESSAGE)
        }
    }
}
