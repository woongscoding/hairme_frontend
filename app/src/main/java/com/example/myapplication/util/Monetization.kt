package com.example.myapplication.util

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * 유료 사용자(한 번이라도 결제한 계정) 기억.
 *
 * 유료 사용자에게는 광고를 보여주지 않는다. 서버 판정(/api/credits)을 받기 전에도
 * 광고가 잠깐 보였다가 사라지지 않도록 마지막 판정을 계정별로 저장해 둔다.
 */
object PaidUserStore {

    private const val PREFS_NAME = "hairme_monetization"
    private const val KEY_PAID_USER_IDS = "paid_user_ids"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isPaid(userId: String): Boolean =
        prefs?.getStringSet(KEY_PAID_USER_IDS, emptySet())?.contains(userId) == true

    fun setPaid(userId: String, paid: Boolean) {
        val current = prefs?.getStringSet(KEY_PAID_USER_IDS, emptySet()).orEmpty()
        val updated = if (paid) current + userId else current - userId
        prefs?.edit()?.putStringSet(KEY_PAID_USER_IDS, updated)?.apply()
    }
}

/**
 * 앱 쪽 A/B 배정.
 *
 * 무료 정책 실험(비회원 한도·보상형 광고)은 서버가 배정하고 /api/usage의 experiment_arm으로 받는다.
 * 여기서는 서버를 거칠 필요 없는 결제 화면 표시 실험만 기기 해시로 나눈다 (같은 기기는 항상 같은 그룹).
 */
object Experiments {

    /** 30회 상품 노출 방식: 보조 노출 vs "추천" 배지 */
    const val CREDITS30_SECONDARY = "secondary"
    const val CREDITS30_RECOMMENDED = "recommended"

    fun credits30Variant(deviceId: String): String =
        if (bucket("credits30_badge", deviceId) == 0) CREDITS30_SECONDARY else CREDITS30_RECOMMENDED

    /** 실험 이름을 소금으로 섞어, 실험끼리 같은 기기 집합으로 나뉘지 않게 한다 */
    private fun bucket(experiment: String, deviceId: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest("$experiment:$deviceId".toByteArray())
        return digest[0].toInt() and 1
    }
}
