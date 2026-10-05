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

    /**
     * 제휴 제품 카드 클릭 (전환 분석). 실제 링크 발급/오픈 여부와 무관하게 탭 시점 기록.
     *
     * logged_in: 클릭이 로그인 필수였던 동안 비로그인(합성 성공의 84%)은 카드를 눌러도
     * 링크를 못 받았다. 개방 후 어느 세그먼트에서 클릭이 늘었는지 보려면 이 값이 필요하다.
     */
    fun logProductClick(productId: String, styleName: String, isLoggedIn: Boolean) {
        firebaseAnalytics?.logEvent("product_click", Bundle().apply {
            putString("product_id", productId)
            putString("style_name", styleName)
            putString("logged_in", isLoggedIn.toString())
        })
    }

    // ================================
    // 광고 계측 (AdMob)
    //
    // 배너/전면은 서버를 거치지 않아 CloudWatch(scripts/admob_watch.py)에 전혀 안 잡힌다.
    // AdMob↔Firebase 링크가 자동으로 넣어주는 ad_impression에만 의존하지 않도록
    // 요청/노출/실패를 placement 단위로 직접 찍는다.
    //
    // placement: "result_bottom"(결과 화면 앵커 배너), "paywall_402"(크레딧 소진 다이얼로그),
    //            "home_account"(홈 계정 카드)
    // format:    "banner" | "rewarded"
    // ================================

    /** 광고 요청 시작 */
    fun logAdRequest(placement: String, format: String) {
        firebaseAnalytics?.logEvent("ad_request", Bundle().apply {
            putString("placement", placement)
            putString("ad_format", format)
        })
    }

    /** 광고가 실제로 화면에 떴다 (배너: onAdLoaded, 보상형: 시청 시작) */
    fun logAdShown(placement: String, format: String) {
        firebaseAnalytics?.logEvent("ad_shown", Bundle().apply {
            putString("placement", placement)
            putString("ad_format", format)
        })
    }

    /** 광고 로드/표시 실패. reason은 AdMob 에러 코드 문자열 */
    fun logAdFailed(placement: String, format: String, reason: String) {
        firebaseAnalytics?.logEvent("ad_failed", Bundle().apply {
            putString("placement", placement)
            putString("ad_format", format)
            putString("reason", reason.take(100))
        })
    }

    /** 보상형 시청 완주 (크레딧 지급은 서버 SSV가 하므로 "지급됨"이 아니다) */
    fun logAdRewarded(placement: String) {
        firebaseAnalytics?.logEvent("ad_rewarded", Bundle().apply {
            putString("placement", placement)
        })
    }

    // ================================
    // 퍼널 구멍 메우기
    // ================================

    /**
     * 합성이 과금 한도에 막힌 순간 (광고가 떠야 할 지점).
     *
     * 서버 402/429가 Firebase에 전혀 안 남아 있어서 "광고를 보여줄 기회가 월 몇 번인지"를
     * 앱 지표만으로는 셀 수 없었다.
     *
     * @param limitType "credits"(회원 402) | "device"(비로그인 429) | "client_device"(서버 호출 전 클라이언트 차단)
     */
    fun logPaywallView(limitType: String, isLoggedIn: Boolean) {
        firebaseAnalytics?.logEvent("paywall_view", Bundle().apply {
            putString("limit_type", limitType)
            putString("logged_in", isLoggedIn.toString())
        })
    }

    /**
     * 합성 종료 (성공/실패 모두).
     *
     * 기존에는 synthesize_hairstyle(클릭)만 있어서 성공률을 서버 로그로만 볼 수 있었다.
     *
     * @param section "ai_hairstyle" | "hair_color"
     */
    fun logSynthesisResult(section: String, itemName: String, success: Boolean, code: Int? = null) {
        firebaseAnalytics?.logEvent("synthesis_result", Bundle().apply {
            putString("section", section)
            putString("item_name", itemName)
            putString("success", success.toString())
            code?.let { putLong("status_code", it.toLong()) }
        })
    }
}
