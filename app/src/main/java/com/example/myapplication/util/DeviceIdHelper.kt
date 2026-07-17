package com.example.myapplication.util

import android.content.Context
import android.provider.Settings

object DeviceIdHelper {

    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
