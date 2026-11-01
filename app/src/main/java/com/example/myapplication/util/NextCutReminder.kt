package com.example.myapplication.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import java.util.concurrent.TimeUnit

/**
 * "언제 미용실 가세요?" 응답 저장 (첫 분석 뒤 한 번만 묻는다).
 */
object SalonIntentStore {

    private const val PREFS_NAME = "hairme_salon_intent"
    private const val KEY_INTENT = "intent"
    private const val KEY_ANSWERED_AT = "answered_at"
    private const val KEY_LAST_SYNTHESIS_AT = "last_synthesis_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasAnswered(context: Context): Boolean = prefs(context).contains(KEY_INTENT)

    fun intent(context: Context): String? = prefs(context).getString(KEY_INTENT, null)

    fun answeredAt(context: Context): Long = prefs(context).getLong(KEY_ANSWERED_AT, 0L)

    fun save(context: Context, intent: String) {
        prefs(context).edit()
            .putString(KEY_INTENT, intent)
            .putLong(KEY_ANSWERED_AT, System.currentTimeMillis())
            .apply()
    }

    fun lastSynthesisAt(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNTHESIS_AT, 0L)

    fun markSynthesis(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_SYNTHESIS_AT, System.currentTimeMillis()).apply()
    }
}

/**
 * 재방문 리마인드 알림 예약.
 *
 * - 기본: 마지막 합성 5주 뒤 "다음 커트" (보통 커트 주기)
 * - "2주 안에" 간다고 답했으면: 응답 10일 뒤 "미용실 가기 전에" (방문 직전에 맞춘다)
 * 둘 중 아직 오지 않은 더 이른 시점 하나만 예약한다. 합성할 때마다 다시 계산해 덮어쓴다.
 */
object NextCutReminder {

    const val CHANNEL_ID = "next_cut"
    const val EXTRA_PUSH_TYPE = "push_type"
    const val TYPE_NEXT_CUT = "next_cut"
    const val TYPE_SALON_VISIT = "salon_visit"

    private const val WORK_NAME = "next_cut_reminder"
    private val NEXT_CUT_DELAY_MS = TimeUnit.DAYS.toMillis(35)
    private val SALON_VISIT_DELAY_MS = TimeUnit.DAYS.toMillis(10)

    fun reschedule(context: Context) {
        val now = System.currentTimeMillis()
        val candidates = buildList {
            SalonIntentStore.lastSynthesisAt(context).takeIf { it > 0 }?.let {
                add(it + NEXT_CUT_DELAY_MS to TYPE_NEXT_CUT)
            }
            if (SalonIntentStore.intent(context) == "within_2_weeks") {
                add(SalonIntentStore.answeredAt(context) + SALON_VISIT_DELAY_MS to TYPE_SALON_VISIT)
            }
        }
        val (fireAt, type) = candidates.filter { it.first > now }.minByOrNull { it.first } ?: return

        val request = OneTimeWorkRequestBuilder<NextCutReminderWorker>()
            .setInitialDelay(fireAt - now, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(EXTRA_PUSH_TYPE to type))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Android 13+는 POST_NOTIFICATIONS 권한이 있어야 알림이 뜬다 */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    internal fun show(context: Context, type: String) {
        if (needsPermission(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "다음 커트 알림", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val (title, body) = when (type) {
            TYPE_SALON_VISIT -> "곧 미용실 가시죠?" to "체험해 본 스타일을 다시 보고, 디자이너에게 바로 보여주세요."
            else -> "다음 커트할 때가 됐어요" to "이번엔 어떤 머리로 바꿀지 사진 한 장으로 미리 입어보세요."
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PUSH_TYPE, type)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, type.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hairme)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(WORK_NAME.hashCode(), notification)
            AnalyticsHelper.logPushSent(type)
        } catch (e: SecurityException) {
            // 예약 뒤 사용자가 권한을 거둔 경우
        }
    }
}

class NextCutReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        NextCutReminder.show(
            applicationContext,
            inputData.getString(NextCutReminder.EXTRA_PUSH_TYPE) ?: NextCutReminder.TYPE_NEXT_CUT
        )
        return Result.success()
    }
}
