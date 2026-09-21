package com.example.myapplication.network

import com.example.myapplication.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 클라이언트 싱글톤 객체
 *
 * HTTPS 도메인과 통신하기 위한 설정
 * 2025-11-02: HTTP에서 HTTPS로 마이그레이션
 * 2025-11-10: 카카오 로컬 API 추가 (미용실 검색)
 * 2025-12-31: Lambda 분리 - Hairstyle/Beauty 분기
 */
object RetrofitClient {

    /**
     * ✅ Hairstyle Lambda (PyTorch ML 포함)
     * - /api/v2/analyze-hybrid (얼굴 분석)
     * - /api/feedback/submit (피드백)
     * - /api/v2/synthesize (헤어스타일 합성)
     */
    private const val HAIRSTYLE_BASE_URL = "https://2twrxtwvsxhtxgriycbhq7ncii0kcath.lambda-url.ap-northeast-2.on.aws/"

    /**
     * ✅ Beauty Lambda (경량, PyTorch 없음) - 2025-12-31 신규
     * - /api/hair-color/{type} (염색색 추천)
     * - /api/hair-color/synthesize (염색색 합성)
     * - /api/personal-color (퍼스널컬러 분석)
     * - /api/beauty/analyze (종합 뷰티 분석)
     */
    private const val BEAUTY_BASE_URL = "https://x3okizuqfolijwxvn5wkxh2e7u0hzlgl.lambda-url.ap-northeast-2.on.aws/"

    /**
     * 카카오 로컬 API Base URL
     */
    private const val KAKAO_BASE_URL = "https://dapi.kakao.com/"

    /**
     * HTTP 로깅 인터셉터
     * 디버그 빌드: BODY 레벨 (요청/응답 전체 로깅)
     * 릴리즈 빌드: NONE (로깅 비활성화)
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /**
     * 카카오 API 인증 인터셉터
     * 모든 요청에 Authorization 헤더 자동 추가
     */
    private val kakaoAuthInterceptor = okhttp3.Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")
            .build()
        chain.proceed(newRequest)
    }

    /**
     * OkHttp 클라이언트
     * 타임아웃 설정 및 로깅 인터셉터 추가
     */
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)  // 연결 타임아웃: 30초
        .readTimeout(60, TimeUnit.SECONDS)     // 읽기 타임아웃: 60초 (Gemini API 처리 시간 고려)
        .writeTimeout(30, TimeUnit.SECONDS)    // 쓰기 타임아웃: 30초
        .build()

    /**
     * Hairstyle Lambda용 OkHttp 클라이언트 (서버 JWT 인증)
     * - AuthInterceptor: 토큰이 있으면 Authorization: Bearer 자동 첨부
     * - TokenAuthenticator: 401 시 /api/auth/refresh로 자동 갱신 → 실패하면 토큰 삭제(로그아웃)
     */
    private val hairstyleOkHttpClient = okHttpClient.newBuilder()
        .addInterceptor(AuthInterceptor())
        .authenticator(TokenAuthenticator())
        .build()

    /**
     * Hairstyle Lambda용 Retrofit 인스턴스
     */
    private val hairstyleRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(HAIRSTYLE_BASE_URL)
        .client(hairstyleOkHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * 토큰 리프레시 전용 Retrofit 인스턴스
     * ⚠️ AuthInterceptor/Authenticator가 없는 기본 클라이언트 사용 (무한 재귀 방지)
     */
    private val tokenRefreshRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(HAIRSTYLE_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * Beauty Lambda용 Retrofit 인스턴스 (경량 Lambda)
     */
    private val beautyRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BEAUTY_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * HairstyleApiService 인스턴스
     * 얼굴 분석, 피드백, 헤어스타일 합성에 사용
     */
    val hairstyleApiService: HairstyleApiService = hairstyleRetrofit.create(HairstyleApiService::class.java)

    /**
     * BeautyApiService 인스턴스
     * 염색색 추천/합성, 퍼스널컬러에 사용
     */
    val beautyApiService: BeautyApiService = beautyRetrofit.create(BeautyApiService::class.java)

    /**
     * UsageApiService 인스턴스
     * 일일 무료 합성 횟수 관리에 사용 (Hairstyle Lambda 호스팅)
     */
    val usageApiService: UsageApiService = hairstyleRetrofit.create(UsageApiService::class.java)

    /**
     * AuthApiService 인스턴스
     * 카카오 로그인, 내 정보 조회, 동의 변경에 사용 (Hairstyle Lambda 호스팅)
     */
    val authApiService: AuthApiService = hairstyleRetrofit.create(AuthApiService::class.java)

    /**
     * CreditsApiService 인스턴스
     * 크레딧 잔액 조회에 사용 (Hairstyle Lambda 호스팅, JWT 필요)
     */
    val creditsApiService: CreditsApiService = hairstyleRetrofit.create(CreditsApiService::class.java)

    /**
     * TokenRefreshApiService 인스턴스
     * TokenAuthenticator가 동기 호출로 사용 (인증 인터셉터 없는 클라이언트)
     */
    val tokenRefreshApiService: TokenRefreshApiService = tokenRefreshRetrofit.create(TokenRefreshApiService::class.java)

    /**
     * ProductsApiService 인스턴스
     * 제휴 제품 추천/클릭에 사용 (Hairstyle Lambda 호스팅, 클릭은 JWT 필요)
     */
    val productsApiService: ProductsApiService = hairstyleRetrofit.create(ProductsApiService::class.java)

    /**
     * MyResultsApiService 인스턴스
     * 회원 합성 결과 히스토리 조회에 사용 (Hairstyle Lambda 호스팅, JWT 필수)
     */
    val myResultsApiService: MyResultsApiService = hairstyleRetrofit.create(MyResultsApiService::class.java)

    /**
     * 카카오 API용 OkHttp 클라이언트
     * 인증 인터셉터 추가
     */
    private val kakaoOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(kakaoAuthInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 카카오 로컬 API용 Retrofit 인스턴스
     */
    private val kakaoRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(KAKAO_BASE_URL)
        .client(kakaoOkHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * KakaoLocalApiService 인스턴스
     * 미용실 검색에 사용
     *
     * ⚠️ 사용 전 카카오 REST API 키 필요
     * https://developers.kakao.com/console/app 에서 발급
     */
    val kakaoLocalApiService: KakaoLocalApiService = kakaoRetrofit.create(KakaoLocalApiService::class.java)
}