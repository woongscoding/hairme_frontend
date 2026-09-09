package com.example.myapplication.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 회원 합성 결과 히스토리 API (Hairstyle Lambda 호스팅)
 *
 * JWT 인증 클라이언트(hairstyleRetrofit)로 생성해야 한다.
 * Authorization: Bearer 필수 — AuthInterceptor가 자동 첨부, 비로그인이면 401.
 */
interface MyResultsApiService {

    /**
     * 내 합성 결과 목록 조회 (최신순, 페이지네이션)
     *
     * @param limit 페이지 크기 (1~100, 기본 20)
     * @param continuationToken 이전 응답의 next_token (첫 페이지는 null → 파라미터 생략)
     */
    @GET("/api/me/results")
    suspend fun getMyResults(
        @Query("limit") limit: Int = 20,
        @Query("continuation_token") continuationToken: String? = null
    ): Response<MyResultsResponse>
}

data class MyResultsResponse(
    val results: List<MyResultItem> = emptyList(),
    /** null이면 마지막 페이지 */
    @SerializedName("next_token")
    val nextToken: String? = null
)

data class MyResultItem(
    /** 항목 고유 ID — 페이지네이션 dedup과 이미지 캐시 키로 사용 */
    val key: String,
    /**
     * presigned URL — 24시간 만료 임시 링크.
     * ⚠️ DB/프리퍼런스에 저장해 재사용 금지. 화면 진입 시마다 API로 재조회할 것.
     * 같은 사진도 호출마다 쿼리스트링이 달라지므로 이미지 캐시 키는 [key]를 쓸 것.
     */
    val url: String,
    /** 합성했던 스타일명 (없을 수 있음) */
    val hairstyle: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null
)
