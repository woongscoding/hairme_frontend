package com.example.myapplication.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 서버 JWT(access + refresh) 저장소
 *
 * EncryptedSharedPreferences로 암호화 저장.
 * Application.onCreate()에서 init() 호출 필수 (RetrofitClient보다 먼저).
 */
object TokenManager {

    private const val TAG = "TokenManager"
    private const val PREFS_NAME = "hairme_auth_tokens"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private lateinit var prefs: SharedPreferences

    /**
     * 토큰 보유 여부 (Authenticator에서 리프레시 실패로 토큰이 삭제되면
     * ViewModel이 이 Flow를 통해 로그아웃 상태로 전환)
     */
    private val _hasTokens = MutableStateFlow(false)
    val hasTokens: StateFlow<Boolean> = _hasTokens.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore 손상 등으로 암호화 저장소 생성 실패 시 앱이 죽지 않도록 일반 저장소로 폴백
            Log.e(TAG, "EncryptedSharedPreferences 생성 실패, 일반 SharedPreferences로 폴백: ${e.message}")
            context.applicationContext.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
        _hasTokens.value = getAccessToken() != null
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
        _hasTokens.value = true
    }

    /** 리프레시 성공 시 access 토큰만 교체 */
    fun updateAccessToken(accessToken: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
        _hasTokens.value = false
    }
}
