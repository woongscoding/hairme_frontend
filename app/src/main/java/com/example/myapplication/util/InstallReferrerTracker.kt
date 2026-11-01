package com.example.myapplication.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener

/**
 * Play Install Referrer를 설치당 한 번 읽어 Firebase에 남긴다.
 *
 * 공유 링크(ShareHelper.installUrl)에 실은 utm_source=result_share 가 여기로 돌아와서
 * "공유 1건당 설치"(목표 0.1 이상)를 잴 수 있다. 첫 실행에 실패하면 다음 실행에서 다시 시도한다.
 */
object InstallReferrerTracker {

    private const val TAG = "InstallReferrer"
    private const val PREFS_NAME = "hairme_install_referrer"
    private const val KEY_DONE = "referrer_logged"

    fun trackOnce(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return

        val client = InstallReferrerClient.newBuilder(appContext).build()
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    try {
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                val referrer = client.installReferrer.installReferrer.orEmpty()
                                // referrer는 "utm_source=...&utm_medium=..." 형태의 쿼리 문자열
                                val params = Uri.parse("https://x/?$referrer")
                                AnalyticsHelper.logInstallReferrer(
                                    source = params.getQueryParameter("utm_source") ?: "(none)",
                                    medium = params.getQueryParameter("utm_medium") ?: "(none)",
                                    campaign = params.getQueryParameter("utm_campaign") ?: "(none)"
                                )
                                prefs.edit().putBoolean(KEY_DONE, true).apply()
                            }
                            // Play 스토어가 없는 기기 등 - 다시 시도해도 결과가 같다
                            InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED ->
                                prefs.edit().putBoolean(KEY_DONE, true).apply()
                            else -> Log.w(TAG, "Install Referrer 응답 $responseCode - 다음 실행 때 재시도")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Install Referrer 읽기 실패: ${e.message}")
                    } finally {
                        client.endConnection()
                    }
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        } catch (e: Exception) {
            Log.w(TAG, "Install Referrer 연결 실패: ${e.message}")
        }
    }
}
