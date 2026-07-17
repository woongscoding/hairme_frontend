package com.example.myapplication

import android.app.Application
import com.example.myapplication.util.AnalyticsHelper
import com.google.firebase.analytics.FirebaseAnalytics
import com.kakao.vectormap.KakaoMapSdk
// import dagger.hilt.android.HiltAndroidApp  // Hilt 임시 비활성화

/**
 * HairMe 앱의 Application 클래스
 *
 * 임시로 Hilt 비활성화 - 빌드 문제 해결 후 재활성화 예정
 */
// @HiltAndroidApp  // Hilt 임시 비활성화
class HairMeApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 카카오맵 SDK 초기화
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        // Firebase Analytics 초기화
        FirebaseAnalytics.getInstance(this)
        AnalyticsHelper.init(this)
    }
}