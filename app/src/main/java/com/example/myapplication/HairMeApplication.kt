package com.example.myapplication

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.example.myapplication.data.auth.TokenManager
import com.example.myapplication.util.AnalyticsHelper
import com.google.firebase.analytics.FirebaseAnalytics
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk
// import dagger.hilt.android.HiltAndroidApp  // Hilt 임시 비활성화

/**
 * HairMe 앱의 Application 클래스
 *
 * 임시로 Hilt 비활성화 - 빌드 문제 해결 후 재활성화 예정
 */
// @HiltAndroidApp  // Hilt 임시 비활성화
class HairMeApplication : Application(), ImageLoaderFactory {

    // 스타일 예시 이미지용 Coil 설정 — 서버가 Cache-Control 1년(immutable)을 주므로
    // 디스크 캐시에 올라간 이미지는 재방문 시 네트워크 요청 없이 로드됨
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024) // 128MB
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // 서버 JWT 토큰 저장소 초기화 (RetrofitClient 사용 전에 필수)
        TokenManager.init(this)

        // 카카오 로그인 SDK 초기화
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        // 카카오맵 SDK 초기화
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        // Firebase Analytics 초기화
        FirebaseAnalytics.getInstance(this)
        AnalyticsHelper.init(this)
    }
}