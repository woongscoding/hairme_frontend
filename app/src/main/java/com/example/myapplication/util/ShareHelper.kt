package com.example.myapplication.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

/**
 * 합성 결과 공유.
 *
 * 공유되는 결과 이미지가 이 앱의 무료 광고판이다. 그래서
 * 1) 이미지 하단에 HAIRME 워드마크 + 설치 안내 띠를 붙이고
 * 2) 공유 텍스트에 Play 설치 링크를 넣는다. 링크의 referrer(utm_*)가 설치 후
 *    InstallReferrerTracker로 돌아와 "공유 1건당 설치"를 잴 수 있다.
 */
object ShareHelper {

    /**
     * 짧은 공유 링크 (App Links). 웹에서 hairme.app/s 리다이렉트(앱 있으면 앱, 없으면 Play)와
     * /.well-known/assetlinks.json 이 준비되면 "https://hairme.app/s" 로 채운다.
     * 비어 있으면 Play 링크를 직접 쓴다. AndroidManifest의 App Links 호스트와 같아야 한다.
     */
    private const val SHORT_LINK_BASE = ""

    private const val CAMPAIGN = "result_share_v1"

    // Atelier 팔레트와 같은 값 (Compose Color를 쓰지 않으려고 ARGB로 둔다)
    private const val FOOTER_BG = 0xFFFAF7F2.toInt()
    private const val FOOTER_LINE = 0xFFE2DCCF.toInt()
    private const val INK = 0xFF1C1826.toInt()
    private const val TEXT_TERTIARY = 0xFF6B665C.toInt()

    /**
     * 설치 링크. section은 utm_medium으로 들어가 어떤 결과(스타일/염색)의 공유인지 구분한다.
     */
    fun installUrl(context: Context, section: String): String {
        if (SHORT_LINK_BASE.isNotBlank()) {
            return "$SHORT_LINK_BASE?m=$section"
        }
        val referrer = "utm_source=result_share&utm_medium=$section&utm_campaign=$CAMPAIGN"
        return "https://play.google.com/store/apps/details?id=${context.packageName}" +
            "&referrer=" + URLEncoder.encode(referrer, "UTF-8")
    }

    /** 결과 이미지 아래에 브랜드 띠를 붙인 새 Bitmap (원본은 건드리지 않는다) */
    fun brandedBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val footerHeight = (width * 0.13f).toInt().coerceAtLeast(72)
        val result = Bitmap.createBitmap(width, source.height + footerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val top = source.height.toFloat()
        canvas.drawRect(0f, top, width.toFloat(), top + footerHeight, Paint().apply { color = FOOTER_BG })
        canvas.drawRect(0f, top, width.toFloat(), top + (width / 400f).coerceAtLeast(1f), Paint().apply { color = FOOTER_LINE })

        val padding = width * 0.05f
        val centerY = top + footerHeight / 2f

        val wordmark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = footerHeight * 0.34f
            letterSpacing = 0.2f
        }
        canvas.drawText("HAIRME", padding, centerY - (wordmark.descent() + wordmark.ascent()) / 2f, wordmark)

        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_TERTIARY
            textSize = footerHeight * 0.2f
            textAlign = Paint.Align.RIGHT
        }
        // 두 줄을 띠 가운데에 오도록 기준선을 위아래로 나눈다
        val t = caption.textSize
        canvas.drawText("AI 헤어스타일 미리 입어보기", width - padding, centerY - 0.15f * t, caption)
        canvas.drawText("Google Play에서 'HairMe' 검색", width - padding, centerY + 1.0f * t, caption)
        return result
    }

    /**
     * 공유 시트 열기.
     *
     * @param section "ai_hairstyle" | "hair_color" (utm_medium, result_share 이벤트와 같은 값)
     * @param itemName 스타일/염색 이름 (공유 문구용)
     */
    fun shareResult(context: Context, bitmap: Bitmap, section: String, itemName: String) {
        try {
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(dir, "HairMe_shared_${System.currentTimeMillis()}.png")
            val branded = brandedBitmap(bitmap)
            FileOutputStream(file).use { branded.compress(Bitmap.CompressFormat.PNG, 100, it) }
            branded.recycle()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val text = "HairMe에서 '$itemName' 미리 입어봤어요.\n" +
                "내 얼굴형에 어울리는 머리 찾기 → ${installUrl(context, section)}"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "헤어스타일 공유"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "공유하지 못했어요. 잠시 후 다시 시도해 주세요", Toast.LENGTH_SHORT).show()
        }
    }
}
