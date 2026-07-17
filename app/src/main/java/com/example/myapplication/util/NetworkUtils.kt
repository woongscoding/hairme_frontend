package com.example.myapplication.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
// import dagger.hilt.android.qualifiers.ApplicationContext  // Hilt 임시 비활성화
import kotlinx.coroutines.delay
// import javax.inject.Inject  // Hilt 임시 비활성화
// import javax.inject.Singleton  // Hilt 임시 비활성화

/**
 * 네트워크 유틸리티 클래스
 *
 * - 네트워크 연결 상태 확인
 * - 재시도 로직 (Exponential Backoff)
 *
 * 임시로 Hilt 비활성화 - 빌드 문제 해결 후 재활성화 예정
 */
// @Singleton  // Hilt 임시 비활성화
class NetworkUtils(
    private val context: Context? = null
) /* @Inject constructor(
    @ApplicationContext private val context: Context
) */ {

    /**
     * 네트워크 연결 상태 확인
     *
     * @return true면 인터넷 연결됨, false면 오프라인
     */
    fun isNetworkAvailable(): Boolean {
        // context가 null인 경우 기본값 반환
        if (context == null) return false
        
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 재시도 로직 with Exponential Backoff
     *
     * @param maxRetries 최대 재시도 횟수 (기본 3회)
     * @param initialDelayMs 첫 재시도 대기 시간 (기본 1초)
     * @param maxDelayMs 최대 대기 시간 (기본 10초)
     * @param factor 지수 증가 배수 (기본 2.0)
     * @param block 실행할 suspend 함수
     * @return block의 실행 결과
     * @throws Exception 모든 재시도 실패 시 마지막 예외를 throw
     */
    suspend fun <T> retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e

                // 마지막 시도면 예외를 throw
                if (attempt == maxRetries - 1) {
                    throw e
                }

                // Exponential Backoff 대기
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)

                android.util.Log.d(
                    "NetworkUtils",
                    "재시도 ${attempt + 1}/$maxRetries - 대기 시간: ${currentDelay}ms"
                )
            }
        }

        // 이론적으로는 도달하지 않지만, 안전을 위해 예외를 throw
        throw lastException ?: Exception("알 수 없는 오류")
    }
}

/**
 * 네트워크 에러 타입 분류
 */
sealed class NetworkError {
    object NoInternet : NetworkError()
    object Timeout : NetworkError()
    data class HttpError(val code: Int, val message: String) : NetworkError()
    data class Unknown(val message: String) : NetworkError()
}

/**
 * Exception을 NetworkError로 변환
 */
fun Exception.toNetworkError(): NetworkError {
    return when {
        this is java.net.UnknownHostException -> NetworkError.NoInternet
        this is java.net.ConnectException -> NetworkError.NoInternet
        this is java.net.SocketTimeoutException -> NetworkError.Timeout
        this.message?.contains("HTTP") == true -> {
            // HTTP 에러 코드 파싱 시도
            val code = message?.filter { it.isDigit() }?.take(3)?.toIntOrNull() ?: 0
            NetworkError.HttpError(code, message ?: "HTTP 오류")
        }
        else -> NetworkError.Unknown(message ?: "알 수 없는 오류")
    }
}

/**
 * NetworkError를 사용자 친화적인 메시지로 변환
 */
fun NetworkError.toUserFriendlyMessage(): String {
    return when (this) {
        is NetworkError.NoInternet -> "인터넷 연결을 확인해주세요"
        is NetworkError.Timeout -> "서버 응답 시간이 초과되었습니다. 다시 시도해주세요"
        is NetworkError.HttpError -> when (code) {
            400 -> "잘못된 요청입니다"
            401, 403 -> "권한이 없습니다"
            404 -> "서버를 찾을 수 없습니다"
            500, 502, 503 -> "서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요"
            else -> "서버 오류가 발생했습니다 (코드: $code)"
        }
        is NetworkError.Unknown -> "오류가 발생했습니다: $message"
    }
}