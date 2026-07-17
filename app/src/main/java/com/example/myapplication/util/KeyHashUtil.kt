package com.example.myapplication.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

/**
 * 카카오 API 키 해시 확인 유틸리티
 *
 * 사용법: KeyHashUtil.getKeyHash(context)를 호출하면 Logcat에 키 해시가 출력됩니다.
 */
object KeyHashUtil {

    /**
     * 앱의 키 해시를 가져와서 Logcat에 출력
     *
     * @param context Application Context
     * @return 키 해시 문자열 (null이면 오류 발생)
     */
    fun getKeyHash(context: Context): String? {
        try {
            val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9 (API 28) 이상
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                // Android 8.1 (API 27) 이하
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            // 서명 정보 가져오기
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9 이상: signingInfo 사용
                packageInfo.signingInfo?.signingCertificateHistory
                    ?: packageInfo.signingInfo?.apkContentsSigners
            } else {
                // Android 8.1 이하: signatures 사용
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            // 키 해시 생성
            signatures?.forEach { signature ->
                val md = MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val keyHash = Base64.encodeToString(md.digest(), Base64.NO_WRAP)

                Log.d("KeyHash", "========================================")
                Log.d("KeyHash", "패키지명: ${context.packageName}")
                Log.d("KeyHash", "키 해시: $keyHash")
                Log.d("KeyHash", "========================================")

                return keyHash
            }

            Log.w("KeyHash", "서명 정보를 찾을 수 없습니다")
        } catch (e: Exception) {
            Log.e("KeyHash", "키 해시 가져오기 실패", e)
        }
        return null
    }
}
