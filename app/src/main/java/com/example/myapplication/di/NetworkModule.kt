package com.example.myapplication.di

import com.example.myapplication.BuildConfig
import com.example.myapplication.network.HairstyleApiService
// import dagger.Module  // Hilt 임시 비활성화
// import dagger.Provides  // Hilt 임시 비활성화
// import dagger.hilt.InstallIn  // Hilt 임시 비활성화
// import dagger.hilt.components.SingletonComponent  // Hilt 임시 비활성화
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
// import javax.inject.Singleton  // Hilt 임시 비활성화

/**
 * Hilt Network Module
 *
 * 네트워크 관련 의존성을 제공하는 모듈
 * - OkHttpClient
 * - Retrofit
 * - HairstyleApiService
 * 
 * 임시로 Hilt 비활성화 - 빌드 문제 해결 후 재활성화 예정
 */
// @Module  // Hilt 임시 비활성화
// @InstallIn(SingletonComponent::class)  // Hilt 임시 비활성화
object NetworkModule {

    // AWS Lambda 프로덕션 URL (2025-12-23 새 배포)
    private const val BASE_URL = "https://io2busbd3jci3oc536inimul5u0wrylc.lambda-url.ap-northeast-2.on.aws/"
    // 로컬 개발 URL: "http://192.168.0.3:8000/"

    /**
     * HTTP 로깅 인터셉터 제공
     */
    // @Provides  // Hilt 임시 비활성화
    // @Singleton  // Hilt 임시 비활성화
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    /**
     * OkHttpClient 제공
     */
    // @Provides  // Hilt 임시 비활성화
    // @Singleton  // Hilt 임시 비활성화
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor = provideLoggingInterceptor()  // 임시로 직접 호출
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)  // 30초 → 10초로 단축
            .readTimeout(30, TimeUnit.SECONDS)      // 60초 → 30초로 단축
            .writeTimeout(15, TimeUnit.SECONDS)     // 30초 → 15초로 단축
            .build()
    }

    /**
     * Retrofit 인스턴스 제공
     */
    // @Provides  // Hilt 임시 비활성화
    // @Singleton  // Hilt 임시 비활성화
    fun provideRetrofit(
        okHttpClient: OkHttpClient = provideOkHttpClient()  // 임시로 직접 호출
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * HairstyleApiService 제공
     */
    // @Provides  // Hilt 임시 비활성화
    // @Singleton  // Hilt 임시 비활성화
    fun provideHairstyleApiService(
        retrofit: Retrofit = provideRetrofit()  // 임시로 직접 호출
    ): HairstyleApiService {
        return retrofit.create(HairstyleApiService::class.java)
    }
}