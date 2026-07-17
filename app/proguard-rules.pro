# ================================
# HairMe App ProGuard Rules
# ================================

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ================================
# ✅ 경고 무시 (빌드 로그 정리)
# ================================
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ================================
# Retrofit & OkHttp - 강화된 설정
# ================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# OkHttp 완전 보존
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okio.**

# Retrofit 완전 보존
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ✅ Retrofit 제네릭 타입 보존 (Response<T>)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ✅ Retrofit Converter (Gson)
-keep class retrofit2.converter.gson.** { *; }

# ================================
# Gson - 강화된 설정 (TypeToken 에러 방지)
# ================================
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# Keep API models - 모든 필드와 메서드 보존
-keep class com.example.myapplication.network.** { *; }
-keep class com.example.myapplication.Model { *; }
-keep class com.example.myapplication.data.** { *; }
-keep class com.example.myapplication.model.** { *; }

# Gson TypeAdapter
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ✅ 제네릭 타입 정보 보존 (ClassCastException 방지)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# ✅ Gson TypeToken 완전 보존 (ParameterizedType 에러 해결)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

# SerializedName 어노테이션 사용 필드 보존
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Enum 클래스 보존
-keepclassmembers enum * { *; }

# ✅ 제네릭 타입 파라미터 보존 (앱 패키지만)
-keepattributes Signature
-keepclassmembers class com.example.myapplication.** {
    <fields>;
}

# ================================
# Kotlinx Serialization
# ================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ================================
# Coroutines
# ================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ================================
# Jetpack Compose
# ================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ✅ Compose Runtime (rememberLauncherForActivityResult)
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** {
    *;
}

# ================================
# ViewModel
# ================================
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class androidx.lifecycle.** { *; }

# ================================
# Kotlin
# ================================
# Kotlin metadata 보호 (Reflection 및 타입 정보 유지)
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# Kotlin data class 보호 (copy, componentN 함수)
-keepclassmembers class * {
    *** copy(...);
    *** component1();
    *** component2();
    *** component3();
    *** component4();
    *** component5();
}

# ✅ Kotlin 리플렉션 (제네릭 타입 지원)
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ================================
# Application Classes
# ================================
-keep class com.example.myapplication.** { *; }

# ✅ BuildConfig 보호 (API 키 포함)
-keep class com.example.myapplication.BuildConfig { *; }
-keepclassmembers class com.example.myapplication.BuildConfig {
    public static <fields>;
}

# ✅ Sealed class 보호 (타입 캐스팅 오류 방지)
-keep class * extends com.example.myapplication.viewmodel.AnalysisUiState { *; }
-keepnames class com.example.myapplication.viewmodel.AnalysisUiState$* { *; }

# ✅ API 모델 클래스 강화 (Gson 파싱 + 변환 메서드 보호)
-keep class com.example.myapplication.network.ApiResponse { *; }
-keep class com.example.myapplication.network.AnalysisData { *; }
-keep class com.example.myapplication.network.Analysis { *; }
-keep class com.example.myapplication.network.Recommendation { *; }
-keep class com.example.myapplication.network.FeedbackRequest { *; }
-keep class com.example.myapplication.network.FeedbackResponse { *; }

# ✅ Data class 변환 메서드 보호 (toAnalysisResult, toHairstyleRecommendation)
-keepclassmembers class com.example.myapplication.network.** {
    public *** toAnalysisResult();
    public *** toHairstyleRecommendation();
}

# ✅ ViewModel 보호 강화
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
    public <fields>;
    public <methods>;
}

# ✅ Domain 모델 클래스 보호
-keep class com.example.myapplication.AnalysisResult { *; }
-keep class com.example.myapplication.HairstyleRecommendation { *; }

# ✅ Parcelable 보존 (데이터 전달)
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ================================
# Remove Logging (Production)
# ================================
# ✅ 임시로 비활성화 (디버깅 목적)
# -assumenosideeffects class android.util.Log {
#     public static int v(...);
#     public static int d(...);
#     public static int i(...);
#     public static int w(...);
# }

# ================================
# ✅ Java 리플렉션 API (ParameterizedType 보호)
# ================================
# 이것이 핵심! "java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType" 해결
-keep class java.lang.reflect.** { *; }
-keep interface java.lang.reflect.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod

# ✅ Type 계층 보호
-keep class java.lang.reflect.Type { *; }
-keep class java.lang.reflect.ParameterizedType { *; }
-keep class java.lang.reflect.GenericArrayType { *; }
-keep class java.lang.reflect.TypeVariable { *; }
-keep class java.lang.reflect.WildcardType { *; }

# ================================
# General Android
# ================================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ================================
# ✅ ActivityResultContracts (카메라/갤러리)
# ================================
-keep class androidx.activity.result.** { *; }
-keep class androidx.activity.result.contract.** { *; }
-keepclassmembers class androidx.activity.result.contract.ActivityResultContract {
    public <methods>;
}

# ✅ ActivityResultLauncher
-keep class androidx.activity.result.ActivityResultLauncher { *; }
-keep class androidx.activity.result.ActivityResultCallback { *; }

# ================================
# ✅ FileProvider (카메라 이미지 URI)
# ================================
-keep class androidx.core.content.FileProvider { *; }
-keepclassmembers class androidx.core.content.FileProvider {
    public <methods>;
}

# ================================
# ✅ AndroidViewModel (Application Context)
# ================================
-keep class androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}

# ================================
# ✅ ViewModelProvider.Factory
# ================================
-keep class androidx.lifecycle.ViewModelProvider$Factory { *; }
-keep class androidx.lifecycle.ViewModelProvider { *; }
-keepclassmembers class androidx.lifecycle.ViewModelProvider {
    public <methods>;
}

# ================================
# ✅ URI 및 Parcelable
# ================================
-keep class android.net.Uri { *; }
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ================================
# ✅ Context 관련 (ClassCastException 방지)
# ================================
-keep class android.content.Context { *; }
-keep class android.content.ContextWrapper { *; }
-keep class android.app.Application { *; }

# ================================
# ✅ Kakao Map SDK - 강화된 설정
# ================================
# 모든 Kakao Map 관련 클래스 완전 보존
-keep class com.kakao.vectormap.** { *; }
-keep interface com.kakao.vectormap.** { *; }
-keep enum com.kakao.vectormap.** { *; }
-keepclassmembers class com.kakao.vectormap.** { *; }
-dontwarn com.kakao.vectormap.**

# Kakao Map 콜백 및 리스너 강화
-keepclassmembers class * implements com.kakao.vectormap.** {
    public <methods>;
    public <fields>;
}

# Kakao Map 네이티브 라이브러리
-keep class com.kakao.maps.** { *; }
-keep interface com.kakao.maps.** { *; }
-keepclassmembers class com.kakao.maps.** { *; }
-dontwarn com.kakao.maps.**

# Kakao Map SDK 핵심 클래스들 명시적 보존
-keep class com.kakao.vectormap.KakaoMap { *; }
-keep class com.kakao.vectormap.MapView { *; }
-keep class com.kakao.vectormap.KakaoMapReadyCallback { *; }
-keep class com.kakao.vectormap.MapLifeCycleCallback { *; }
-keep class com.kakao.vectormap.LatLng { *; }
-keep class com.kakao.vectormap.camera.** { *; }
-keep class com.kakao.vectormap.label.** { *; }

# JNI 메서드 보호 (네이티브 라이브러리 통신)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Kakao Map 네이티브 브릿지
-keep class com.kakao.maps.open.** { *; }
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# ================================
# ✅ Google Play Services Location
# ================================
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.location.**

-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.common.**

# FusedLocationProviderClient
-keep class com.google.android.gms.location.FusedLocationProviderClient { *; }
-keep class com.google.android.gms.tasks.** { *; }

# ================================
# ✅ Room Database
# ================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** getInstance(***);
}

# Room TypeConverters
-keep class * {
    @androidx.room.TypeConverter <methods>;
}

-dontwarn androidx.room.**
-keep class androidx.room.** { *; }

# ================================
# ✅ WebView (SalonMapWebView)
# ================================
-keep class android.webkit.WebView { *; }
-keep class android.webkit.WebViewClient { *; }
-keep class android.webkit.WebChromeClient { *; }
-keep class android.webkit.JavascriptInterface { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# WebView JavaScript 브릿지
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ================================
# ✅ 네이티브 라이브러리 보호
# ================================
# .so 파일 보존 (Kakao Map 네이티브 라이브러리)
-keepclasseswithmembernames class * {
    native <methods>;
}

# System.loadLibrary 호출 보존
-keepclasseswithmembers class * {
    static <methods>;
}

# ================================
# ✅ 리소스 Shrinking 예외
# ================================
# Kakao Map 리소스 보존
-keep class **.R$* { *; }
-keepclassmembers class **.R$* {
    public static <fields>;
}

# AndroidManifest 메타데이터 보존
-keep class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*