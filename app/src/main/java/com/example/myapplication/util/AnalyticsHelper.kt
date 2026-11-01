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
    // placement: "paywall_402"(결제 화면의 광고 옵션), "home_account"(홈 계정 카드),
    //            "result_unlock"(결과 화면 무료 기능 해제 - 실험군)
    // format:    "rewarded"
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
     * 결제 화면 노출. 구매율(paywall_view → purchase_complete)의 분모라서 화면이 뜰 때 한 번만 찍는다.
     *
     * @param trigger "exhausted"(무료·크레딧 소진 후 합성 탭) | "hd_upgrade"(무료 결과의 "고화질로 보기")
     * @param limitType "credits"(회원 402) | "device"(비로그인 429) | "client_device"(서버 호출 전 차단) | "preview"
     * @param credits30Variant 30회 상품 노출 A/B ("secondary" | "recommended")
     * @param experimentArm 무료 정책 실험 그룹 ("control" | "treatment")
     */
    fun logPaywallView(
        trigger: String,
        limitType: String,
        isLoggedIn: Boolean,
        credits30Variant: String,
        experimentArm: String
    ) {
        firebaseAnalytics?.logEvent("paywall_view", Bundle().apply {
            putString("trigger", trigger)
            putString("limit_type", limitType)
            putString("logged_in", isLoggedIn.toString())
            putString("credits30_variant", credits30Variant)
            putString("experiment_arm", experimentArm)
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

    // ================================
    // 결제 (Play Billing)
    // ================================

    /** 결제창을 연 순간 */
    fun logPurchaseStart(sku: String, placement: String) {
        firebaseAnalytics?.logEvent("purchase_start", Bundle().apply {
            putString("sku", sku)
            putString("placement", placement)
        })
    }

    /**
     * 서버 검증까지 끝난 구매. 구매자 수·객단가 판정의 기준 이벤트.
     * value/currency를 함께 넣어 Firebase 매출 집계에도 잡히게 한다.
     */
    fun logPurchaseComplete(sku: String, credits: Int, value: Double, currency: String, placement: String) {
        firebaseAnalytics?.logEvent("purchase_complete", Bundle().apply {
            putString("sku", sku)
            putLong("credits", credits.toLong())
            putDouble(FirebaseAnalytics.Param.VALUE, value)
            putString(FirebaseAnalytics.Param.CURRENCY, currency)
            putString("placement", placement)
        })
        firebaseAnalytics?.setUserProperty("paid_user", "true")
    }

    /** 결제 실패/취소. reason: "user_canceled" | "billing_<code>" | "server_rejected" | "server_retry" ... */
    fun logPurchaseFailed(sku: String, reason: String) {
        firebaseAnalytics?.logEvent("purchase_failed", Bundle().apply {
            putString("sku", sku)
            putString("reason", reason.take(100))
        })
    }

    /** 카카오 로그인 완료 (합성 후 가입 전환 = synthesis_result → login) */
    fun logLogin(isNewUser: Boolean) {
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, "kakao")
            putString("new_user", isNewUser.toString())
        })
    }

    /** 무료 결과 위 "이 스타일 고화질로 보기" 탭 */
    fun logHdUpgradeClick(section: String, isLoggedIn: Boolean) {
        firebaseAnalytics?.logEvent("hd_upgrade_click", Bundle().apply {
            putString("section", section)
            putString("logged_in", isLoggedIn.toString())
        })
    }

    /** 무료 정책 실험 그룹을 사용자 속성으로 (모든 이벤트를 그룹별로 나눠 보기 위함) */
    fun setExperimentArm(arm: String) {
        firebaseAnalytics?.setUserProperty("free_policy_arm", arm)
    }

    /**
     * 원가 0원 기능 해제 (퍼스널컬러 상세 · 전체 스타일 랭킹).
     * @param method "rewarded"(광고 시청) | "paid"(유료 사용자 자동 해제)
     */
    fun logFeatureUnlock(method: String) {
        firebaseAnalytics?.logEvent("feature_unlock", Bundle().apply {
            putString("method", method)
        })
    }

    // ================================
    // 성장 (공유 · 재방문)
    // ================================

    /** 첫 분석 뒤 "언제 미용실 가세요?" 응답 */
    fun logSalonIntentDeclared(intent: String) {
        firebaseAnalytics?.logEvent("salon_intent_declared", Bundle().apply {
            putString("intent", intent)
        })
        firebaseAnalytics?.setUserProperty("salon_intent", intent)
    }

    /**
     * Play Install Referrer (설치 1회만). 공유 링크에 붙인 utm 값이 그대로 들어온다.
     * 공유 1건당 설치 = result_share 대비 utm_source=result_share 설치 수.
     */
    fun logInstallReferrer(source: String, medium: String, campaign: String) {
        firebaseAnalytics?.logEvent("install_referrer", Bundle().apply {
            putString("utm_source", source.take(100))
            putString("utm_medium", medium.take(100))
            putString("utm_campaign", campaign.take(100))
        })
        firebaseAnalytics?.setUserProperty("install_source", source.take(36))
    }

    /** 공유 링크(App Links)로 앱이 열림 */
    fun logAppLinkOpen(path: String) {
        firebaseAnalytics?.logEvent("app_link_open", Bundle().apply {
            putString("path", path.take(100))
        })
    }

    /** 리마인드 알림 표시 / 탭. type: "next_cut" | "salon_visit" */
    fun logPushSent(type: String) {
        firebaseAnalytics?.logEvent("push_sent", Bundle().apply { putString("type", type) })
    }

    fun logPushOpen(type: String) {
        firebaseAnalytics?.logEvent("push_open", Bundle().apply { putString("type", type) })
    }
}
