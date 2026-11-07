package com.example.myapplication.util

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.myapplication.network.BodyAnalysisResponse
import com.google.gson.Gson
import java.io.File

/** 마지막 체형 분석 결과 + 그때 올린 사진 (앱 내부 저장소 파일 경로, 없으면 null) */
data class SavedBodyResult(
    val result: BodyAnalysisResponse,
    val photoPath: String?
)

/**
 * 마지막 체형 분석 결과를 기기에 보관한다.
 *
 * 결과는 메모리(ViewModel)에만 있으면 쿠팡 앱에 다녀오는 사이 프로세스가 정리될 때
 * 사라진다. 그래서 결과 JSON 과 사진 사본을 앱 내부 저장소에 두고 다시 보여준다.
 */
interface BodyResultStore {
    fun load(): SavedBodyResult?

    /** 결과와 사진 사본을 저장하고, 저장된 사진 파일 경로를 돌려준다 (사진 복사 실패 시 null) */
    fun save(result: BodyAnalysisResponse, photo: Uri): String?
}

class PrefsBodyResultStore(private val app: Application) : BodyResultStore {

    private val prefs by lazy { app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val photoDir by lazy { File(app.filesDir, PHOTO_DIR) }
    private val gson = Gson()

    override fun load(): SavedBodyResult? = try {
        val json = prefs.getString(KEY_RESULT, null)
        val result = json?.let { gson.fromJson(it, BodyAnalysisResponse::class.java) }
        if (result == null || result.bodyType.isBlank()) {
            null
        } else {
            val photo = prefs.getString(KEY_PHOTO, null)
                ?.let { File(photoDir, it) }
                ?.takeIf { it.isFile }
            SavedBodyResult(result, photo?.absolutePath)
        }
    } catch (e: Exception) {
        Log.w(TAG, "저장된 체형 결과를 읽지 못함: ${e.message}")
        null
    }

    override fun save(result: BodyAnalysisResponse, photo: Uri): String? {
        // 사진이 바뀌어도 이미지 캐시가 예전 사진을 보여주지 않게 파일명을 매번 새로 만든다
        val saved = try {
            photoDir.mkdirs()
            val target = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
            val copied = app.contentResolver.openInputStream(photo)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied != null && target.length() > 0) target else null.also { target.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "체형 사진 사본 저장 실패: ${e.message}")
            null
        }

        prefs.edit()
            .putString(KEY_RESULT, gson.toJson(result))
            .putString(KEY_PHOTO, saved?.name)
            .apply()

        // 지난 사진 사본은 지운다 (최신 1장만 유지)
        photoDir.listFiles()?.filter { it != saved }?.forEach { it.delete() }
        return saved?.absolutePath
    }

    private companion object {
        const val TAG = "BodyResultStore"
        const val PREFS_NAME = "body_fashion"
        const val KEY_RESULT = "last_result"
        const val KEY_PHOTO = "last_photo"
        const val PHOTO_DIR = "body_fashion"
    }
}
