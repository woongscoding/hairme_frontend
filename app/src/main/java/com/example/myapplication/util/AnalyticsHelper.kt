package com.example.myapplication.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Firebase Analytics 헬퍼
 *
 * 주요 추적 항목:
 * 1. 화면별 screen_view (퍼널 분석용)
 * 2. 주요 사용자 액션 이벤트
 */
object AnalyticsHelper {

    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    // ================================
    // 화면 조회 이벤트 (퍼널 분석 핵심)
    // ================================
    fun logScreenView(screenName: String) {
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        })
    }

    // ================================
    // 사용자 액션 이벤트
    // ================================

    /** 홈 화면에서 "시작하기" 클릭 */
    fun logStartAnalysis() {
        firebaseAnalytics?.logEvent("start_analysis", null)
    }

    /** 홈 화면에서 "미용실 찾기" 클릭 */
    fun logFindSalonFromHome() {
        firebaseAnalytics?.logEvent("find_salon_from_home", null)
    }

    /** 사진 선택: 카메라 */
    fun logSelectCamera() {
        firebaseAnalytics?.logEvent("select_camera", null)
    }

    /** 사진 선택: 갤러리 */
    fun logSelectGallery() {
        firebaseAnalytics?.logEvent("select_gallery", null)
    }

    /** 성별 선택 */
    fun logSelectGender(gender: String) {
        firebaseAnalytics?.logEvent("select_gender", Bundle().apply {
            putString("gender", gender)
        })
    }

    /** 분석 시작 버튼 클릭 */
    fun logAnalyzeClick(gender: String) {
        firebaseAnalytics?.logEvent("analyze_click", Bundle().apply {
            putString("gender", gender)
        })
    }

    /** 분석 완료 (성공) */
    fun logAnalysisComplete(faceShape: String, skinTone: String, elapsedMs: Long) {
        firebaseAnalytics?.logEvent("analysis_complete", Bundle().apply {
            putString("face_shape", faceShape)
            putString("skin_tone", skinTone)
            putLong("elapsed_ms", elapsedMs)
        })
    }

    /** 분석 실패 */
    fun logAnalysisError(errorMessage: String) {
        firebaseAnalytics?.logEvent("analysis_error", Bundle().apply {
            putString("error_message", errorMessage.take(100))
        })
    }

    /** 헤어스타일 네이버 검색 클릭 */
    fun logSearchHairstyle(styleName: String) {
        firebaseAnalytics?.logEvent("search_hairstyle", Bundle().apply {
            putString("style_name", styleName)
        })
    }

    /** 헤어스타일 합성 클릭 */
    fun logSynthesizeHairstyle(styleName: String) {
        firebaseAnalytics?.logEvent("synthesize_hairstyle", Bundle().apply {
            putString("style_name", styleName)
        })
    }

    /** 염색색 합성 클릭 */
    fun logSynthesizeHairColor(colorName: String) {
        firebaseAnalytics?.logEvent("synthesize_hair_color", Bundle().apply {
            putString("color_name", colorName)
        })
    }

    /** 피드백 (좋아요/싫어요) */
    fun logFeedback(styleName: String, feedback: String) {
        firebaseAnalytics?.logEvent("style_feedback", Bundle().apply {
            putString("style_name", styleName)
            putString("feedback", feedback)
        })
    }

    /** 미용실 찾기 클릭 (결과 화면에서) */
    fun logFindSalonFromResult() {
        firebaseAnalytics?.logEvent("find_salon_from_result", null)
    }

    /** 미용실 카드 클릭 (카카오맵 열기) */
    fun logSalonClick(salonName: String) {
        firebaseAnalytics?.logEvent("salon_click", Bundle().apply {
            putString("salon_name", salonName)
        })
    }

    // ================================
    // 심화 분석용 이벤트
    // ================================

    /** 사진 입력 (퍼널 분석: 카메라 vs 갤러리 통합 추적) */
    fun logPhotoInput(method: String) {
        firebaseAnalytics?.logEvent("photo_input", Bundle().apply {
            putString("method", method) // "camera" or "gallery"
        })
    }

    /** 얼굴 분석 완료/실패 (퍼널 분석: 성공률 추적) */
    fun logFaceAnalysisComplete(success: Boolean, faceShape: String? = null, skinTone: String? = null, elapsedMs: Long? = null) {
        firebaseAnalytics?.logEvent("face_analysis_complete", Bundle().apply {
            putString("success", success.toString())
            faceShape?.let { putString("face_shape", it) }
            skinTone?.let { putString("skin_tone", it) }
            elapsedMs?.let { putLong("elapsed_ms", it) }
        })
    }

    /** 섹션 조회 (섹션별 인기도 분석) */
    fun logSectionView(section: String) {
        firebaseAnalytics?.logEvent("section_view", Bundle().apply {
            putString("section", section) // "ai_hairstyle", "trend_hairstyle", "personal_color"
        })
    }

    /** 개별 아이템 클릭 (아이템별 클릭률/위치별 분석) */
    fun logItemClick(section: String, itemName: String, position: Int) {
        firebaseAnalytics?.logEvent("item_click", Bundle().apply {
            putString("section", section)
            putString("item_name", itemName)
            putLong("position", position.toLong())
        })
    }

    /** 결과 저장 (engagement 분석) */
    fun logResultSave(section: String, itemName: String) {
        firebaseAnalytics?.logEvent("result_save", Bundle().apply {
            putString("section", section) // "ai_hairstyle", "hair_color"
            putString("item_name", itemName)
        })
    }

    /** 결과 공유 (engagement 분석) */
    fun logResultShare(section: String, method: String) {
        firebaseAnalytics?.logEvent("result_share", Bundle().apply {
            putString("section", section)
            putString("method", method) // "system_share" 등
        })
    }
}
