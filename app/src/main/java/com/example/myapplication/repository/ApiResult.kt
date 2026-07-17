package com.example.myapplication.repository

/**
 * API 호출 결과를 나타내는 Sealed Class
 *
 * Sealed Class를 사용하는 이유:
 * 1. 타입 안전성: Success와 Error 두 가지 상태만 존재
 * 2. when 표현식에서 else 불필요 (모든 케이스 처리 강제)
 * 3. 명확한 에러 핸들링
 */
sealed class ApiResult<out T> {
    /**
     * API 호출 성공
     * @param data 성공 시 반환되는 데이터
     * @param analysisId 분석 ID (피드백 제출용)
     */
    data class Success<T>(
        val data: T,
        val analysisId: String? = null  // ✅ v26: DynamoDB UUID 지원 (Int → String)
    ) : ApiResult<T>()

    /**
     * API 호출 실패
     * @param message 에러 메시지
     * @param code 에러 코드 (선택사항)
     */
    data class Error(
        val message: String,
        val code: Int? = null
    ) : ApiResult<Nothing>()
}

/**
 * 확장 함수: ApiResult의 데이터를 안전하게 가져오기
 */
fun <T> ApiResult<T>.getDataOrNull(): T? {
    return when (this) {
        is ApiResult.Success -> data
        is ApiResult.Error -> null
    }
}

/**
 * 확장 함수: 성공 여부 확인
 */
fun <T> ApiResult<T>.isSuccess(): Boolean {
    return this is ApiResult.Success
}

/**
 * 확장 함수: 실패 여부 확인
 */
fun <T> ApiResult<T>.isError(): Boolean {
    return this is ApiResult.Error
}