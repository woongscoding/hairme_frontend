import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    // id("com.google.dagger.hilt.android") version "2.48" // Hilt 임시 비활성화
    id("kotlin-kapt") // Room Database 컴파일을 위해 활성화
}

// ================================
// Keystore 설정 로드
// ================================
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// ================================
// local.properties에서 Kakao API 키 로드
// ================================
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hairme.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 88
        versionName = "5.12.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ================================
        // Kakao REST API 키를 BuildConfig에 등록
        // ================================
        val kakaoApiKey = localProperties.getProperty("kakao.rest.api.key") ?: ""
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoApiKey\"")

        // ================================
        // Kakao 네이티브 앱 키를 BuildConfig에 등록
        // ================================
        val kakaoNativeAppKey = localProperties.getProperty("kakao.native.app.key") ?: ""
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey

        // ================================
        // AdMob 앱 ID (비밀값 아님 - APK에 그대로 담기는 공개 식별자)
        // 광고 단위 ID는 RewardedAdManager 참고 (디버그 빌드는 테스트 광고 사용)
        // ================================
        manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-1902190021810378~6750985727"
    }

    // ================================
    // 앱 서명 설정 (Release 빌드용)
    // ================================
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")  // ✅ 서명 적용
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true  // BuildConfig 사용 가능하도록 추가
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // ================================
    // Lint 설정 (빌드 오류 방지)
    // ================================
    lint {
        checkReleaseBuilds = false  // Release 빌드 시 Lint 검사 비활성화
        abortOnError = false
    }

    // ================================
    // 16KB 페이지 크기 지원 설정
    // ================================
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // ================================
    // Android Core & Lifecycle
    // ================================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ================================
    // Jetpack Compose
    // ================================
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3") // collectAsStateWithLifecycle
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    // ================================
    // Navigation
    // ================================
    implementation("androidx.navigation:navigation-compose:2.8.5")  // 최신 버전으로 업데이트

    // ================================
    // Image Loading (Coil)
    // ================================
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ================================
    // Networking (Retrofit + OkHttp)
    // ================================
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")  // 최신 버전
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")  // 최신 버전

    // ================================
    // JSON Parsing
    // ================================
    implementation("com.google.code.gson:gson:2.10.1")

    // ================================
    // Coroutines
    // ================================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")  // 최신 버전
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // ================================
    // Kakao Map SDK
    // ================================
    implementation("com.kakao.maps.open:android:2.13.0")

    // ================================
    // Kakao Login SDK (v2-user) - 카카오 로그인
    // ================================
    implementation("com.kakao.sdk:v2-user:2.20.6")

    // ================================
    // EncryptedSharedPreferences (서버 JWT 암호화 저장)
    // ================================
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ================================
    // Google Play Services (GPS 위치)
    // ================================
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // ================================
    // AdMob (보상형 광고 - 크레딧 충전)
    // ================================
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // ================================
    // Dependency Injection (Hilt) - 임시 비활성화
    // ================================
    // implementation("com.google.dagger:hilt-android:2.48")
    // kapt("com.google.dagger:hilt-android-compiler:2.48")
    // implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // ================================
    // Room Database
    // ================================
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ================================
    // Firebase Analytics
    // ================================
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)


    // ================================
    // Testing
    // ================================
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:1.0.0") // StateFlow 테스팅

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // ================================
    // Debug
    // ================================
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}