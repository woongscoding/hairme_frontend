package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.myapplication.network.COUPANG_DEFAULT_DISCLOSURE
import com.example.myapplication.network.RecommendedProduct
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierPrimaryButton
import com.example.myapplication.ui.theme.AtelierSecondaryButton
import com.example.myapplication.ui.theme.AtelierUnderlineButton
import com.example.myapplication.ui.theme.atelierLabel
import com.example.myapplication.ui.theme.atelierSerif
import com.example.myapplication.util.AnalyticsHelper
import java.io.OutputStream

/**
 * SynthesisResultScreen.kt - Atelier 리디자인 (v30 합성 / v34 염색 로직 유지)
 *
 * Gemini 2.5 Flash Image API로 합성된 결과를 보여주는 다이얼로그.
 * 저장/공유/애널리틱스 로직은 기존과 동일, UI 레이어만 교체.
 */

/**
 * 합성 로딩 다이얼로그
 */
@Composable
fun SynthesisLoadingDialog(
    hairstyleName: String,
    onDismiss: () -> Unit
) {
    AtelierLoadingDialog(
        title = "'$hairstyleName' 스타일 적용 중",
        description = stringResource(R.string.synthesis_loading_desc),
        onDismiss = onDismiss
    )
}

/**
 * 합성 결과 다이얼로그
 *
 * @param beforeImageUri 원본 사진 (전달 시 Before/After 2열 그리드로 표시)
 * @param usageRemaining 남은 무료 합성 횟수 (헤더 우측 표시용)
 */
@Composable
fun SynthesisResultDialog(
    synthesizedImage: Bitmap,
    hairstyleName: String,
    onDismiss: () -> Unit,
    onTryAnother: () -> Unit,
    beforeImageUri: Uri? = null,
    usageRemaining: Int? = null,
    // 제휴 제품 추천 (빈 배열이면 섹션 미노출)
    recommendedProducts: List<RecommendedProduct> = emptyList(),
    disclosure: String? = null,
    isLoggedIn: Boolean = false,
    onProductClick: (RecommendedProduct) -> Unit = {},
    onRequestLogin: () -> Unit = {}
) {
    val context = LocalContext.current

    AtelierSynthesisDialog(
        headerTitle = stringResource(R.string.synthesis_result_title),
        heroTitle = "$hairstyleName,\n이렇게 어울려요",
        synthesizedImage = synthesizedImage,
        beforeImageUri = beforeImageUri,
        usageRemaining = usageRemaining,
        tryAnotherText = stringResource(R.string.synthesis_try_another),
        onDismiss = onDismiss,
        onTryAnother = onTryAnother,
        onSave = {
            AnalyticsHelper.logResultSave("ai_hairstyle", hairstyleName)
            saveImageToGallery(context, synthesizedImage, hairstyleName)
        },
        onShare = {
            AnalyticsHelper.logResultShare("ai_hairstyle", "system_share")
            shareImage(context, synthesizedImage)
        },
        extraContent = if (recommendedProducts.isNotEmpty()) {
            {
                RecommendedProductsSection(
                    products = recommendedProducts,
                    disclosure = disclosure?.takeIf { it.isNotBlank() } ?: COUPANG_DEFAULT_DISCLOSURE,
                    isLoggedIn = isLoggedIn,
                    onProductClick = onProductClick,
                    onRequestLogin = onRequestLogin
                )
            }
        } else null
    )
}

/**
 * 합성 에러 다이얼로그
 */
@Composable
fun SynthesisErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Atelier.Background, Atelier.CardShape)
                .border(1.dp, Atelier.Border, Atelier.CardShape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = Atelier.TrendAccent,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.synthesis_error),
                style = atelierSerif(size = 17),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = errorMessage,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Atelier.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AtelierSecondaryButton(
                    text = "닫기",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                AtelierPrimaryButton(
                    text = "다시 시도",
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    height = 48.dp
                )
            }
        }
    }
}

// ========================================
// ✅ v34: 염색색 합성 다이얼로그
// ========================================

/**
 * 염색색 합성 로딩 다이얼로그
 */
@Composable
fun ColorSynthesisLoadingDialog(
    colorName: String,
    onDismiss: () -> Unit
) {
    AtelierLoadingDialog(
        title = "'$colorName' 색상 적용 중",
        description = "AI가 자연스럽게 염색 효과를 적용하고 있어요",
        onDismiss = onDismiss
    )
}

/**
 * 염색색 합성 결과 다이얼로그
 */
@Composable
fun ColorSynthesisResultDialog(
    synthesizedImage: Bitmap,
    colorName: String,
    onDismiss: () -> Unit,
    onTryAnother: () -> Unit,
    beforeImageUri: Uri? = null,
    usageRemaining: Int? = null
) {
    val context = LocalContext.current

    AtelierSynthesisDialog(
        headerTitle = "염색 시뮬레이션 결과",
        heroTitle = "$colorName,\n이렇게 어울려요",
        synthesizedImage = synthesizedImage,
        beforeImageUri = beforeImageUri,
        usageRemaining = usageRemaining,
        tryAnotherText = "다른 색상 적용해보기",
        onDismiss = onDismiss,
        onTryAnother = onTryAnother,
        onSave = {
            AnalyticsHelper.logResultSave("hair_color", colorName)
            saveImageToGallery(context, synthesizedImage, colorName)
        },
        onShare = {
            AnalyticsHelper.logResultShare("hair_color", "system_share")
            shareImage(context, synthesizedImage)
        }
    )
}

// ========================================
// Atelier 공통 다이얼로그 레이아웃
// ========================================

@Composable
private fun AtelierLoadingDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* 로딩 중에는 닫기 불가 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Atelier.Background, Atelier.CardShape)
                .border(1.dp, Atelier.Border, Atelier.CardShape)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Atelier.BrandViolet,
                strokeWidth = 3.dp,
                trackColor = Atelier.Divider
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                style = atelierSerif(size = 17),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                color = Atelier.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            AtelierUnderlineButton(text = "취소", onClick = onDismiss)
        }
    }
}

/**
 * 스타일/염색 합성 결과 공통 레이아웃:
 * close 헤더 + Serif 24sp 제목 + Before/After 그리드 + 저장/공유 pill + 블랙 pill + 언더라인
 */
@Composable
private fun AtelierSynthesisDialog(
    headerTitle: String,
    heroTitle: String,
    synthesizedImage: Bitmap,
    beforeImageUri: Uri?,
    usageRemaining: Int?,
    tryAnotherText: String,
    onDismiss: () -> Unit,
    onTryAnother: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    // 결과 하단 추가 섹션(제휴 제품 추천 등). 스크롤 영역에 표시되며 없으면 미노출.
    extraContent: (@Composable () -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .background(Atelier.Background, Atelier.CardShape)
                .border(1.dp, Atelier.Border, Atelier.CardShape)
        ) {
            // 헤더: close + Serif 타이틀 + "N회 남음"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.synthesis_close),
                        tint = Atelier.Ink,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = headerTitle,
                    style = atelierSerif(size = 17),
                    modifier = Modifier.weight(1f)
                )
                usageRemaining?.let {
                    Text(
                        text = "${it}회 남음",
                        fontSize = 12.sp,
                        color = Atelier.TextTertiary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = heroTitle,
                    style = atelierSerif(size = 24)
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (beforeImageUri != null) {
                    // Before/After 2열 그리드
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = beforeImageUri,
                                contentDescription = stringResource(R.string.synthesis_before),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .clip(Atelier.CardShape)
                                    .border(1.dp, Atelier.Border, Atelier.CardShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "BEFORE",
                                style = atelierLabel(size = 12),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Image(
                                bitmap = synthesizedImage.asImageBitmap(),
                                contentDescription = stringResource(R.string.synthesis_after),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .clip(Atelier.CardShape)
                                    .border(2.dp, Atelier.BrandViolet, Atelier.CardShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "AFTER",
                                style = atelierLabel(size = 12, color = Atelier.BrandViolet),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                } else {
                    // 원본 사진이 없으면 After 단독 표시
                    Column {
                        Image(
                            bitmap = synthesizedImage.asImageBitmap(),
                            contentDescription = stringResource(R.string.synthesis_after),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                                .clip(Atelier.CardShape)
                                .border(2.dp, Atelier.BrandViolet, Atelier.CardShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AFTER",
                            style = atelierLabel(size = 12, color = Atelier.BrandViolet),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 저장/공유: 보더 pill 2열
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AtelierSecondaryButton(
                        text = stringResource(R.string.synthesis_save),
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Default.Download,
                        leadingIconTint = Atelier.Ink
                    )
                    AtelierSecondaryButton(
                        text = stringResource(R.string.synthesis_share),
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Default.Share,
                        leadingIconTint = Atelier.Ink
                    )
                }

                // 추가 섹션 (제휴 제품 추천 등)
                extraContent?.let {
                    Spacer(modifier = Modifier.height(28.dp))
                    it()
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 하단 고정: 블랙 pill + 언더라인
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AtelierPrimaryButton(
                    text = tryAnotherText,
                    onClick = onTryAnother,
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp
                )
                Spacer(modifier = Modifier.height(4.dp))
                AtelierUnderlineButton(
                    text = "결과로 돌아가기",
                    onClick = onDismiss
                )
            }
        }
    }
}

// ========================================
// 제휴 제품 추천 섹션
// ========================================

/**
 * "이 스타일 유지 아이템" 섹션.
 *
 * - 가로 스크롤 카드 최대 3개 (제품명/브랜드/가격)
 * - 대가성 문구는 하단에 항상, 잘리지 않게 표시 (법적 의무)
 * - 카드 탭: 로그인 상태면 onProductClick, 비로그인이면 로그인 유도 다이얼로그
 */
@Composable
private fun RecommendedProductsSection(
    products: List<RecommendedProduct>,
    disclosure: String,
    isLoggedIn: Boolean,
    onProductClick: (RecommendedProduct) -> Unit,
    onRequestLogin: () -> Unit
) {
    var showLoginPrompt by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "STYLE KIT",
            style = atelierLabel(size = 11, color = Atelier.BrandVioletSoft)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "이 스타일 유지 아이템",
            style = atelierSerif(size = 18)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            products.take(3).forEach { product ->
                ProductCard(
                    product = product,
                    onClick = {
                        if (isLoggedIn) onProductClick(product) else showLoginPrompt = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 대가성 문구 (필수): 접거나 자르지 않고 항상 전체 노출
        Text(
            text = disclosure,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = Atelier.TextTertiary
        )
    }

    if (showLoginPrompt) {
        ProductLoginPromptDialog(
            onConfirm = {
                showLoginPrompt = false
                onRequestLogin()
            },
            onDismiss = { showLoginPrompt = false }
        )
    }
}

/**
 * 제휴 제품 카드 (가로 스크롤 요소)
 */
@Composable
private fun ProductCard(
    product: RecommendedProduct,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(Atelier.CardShape)
            .background(Atelier.Surface, Atelier.CardShape)
            .border(1.dp, Atelier.Border, Atelier.CardShape)
            .clickable(onClick = onClick)
            .padding(bottom = 12.dp)
    ) {
        // 이미지 / 플레이스홀더 (정사각형)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Atelier.Background),
            contentAlignment = Alignment.Center
        ) {
            if (product.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = categoryPlaceholderEmoji(product.category),
                    fontSize = 36.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(modifier = Modifier.padding(horizontal = 10.dp)) {
            if (product.brand.isNotBlank()) {
                Text(
                    text = product.brand,
                    style = atelierLabel(size = 10),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
            Text(
                text = product.name,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Atelier.Ink,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatKrw(product.priceKrw),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Atelier.Ink
            )
        }
    }
}

/**
 * 비로그인 상태에서 카드 탭 시 노출되는 로그인 유도 다이얼로그
 */
@Composable
private fun ProductLoginPromptDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Atelier.Background, Atelier.CardShape)
                .border(1.dp, Atelier.Border, Atelier.CardShape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "제품을 보려면 로그인이 필요해요",
                style = atelierSerif(size = 17),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "로그인하면 스타일에 맞는 제품 정보를 확인할 수 있어요",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Atelier.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AtelierSecondaryButton(
                    text = "닫기",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                AtelierPrimaryButton(
                    text = "로그인하고 제품 보기",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    height = 48.dp
                )
            }
        }
    }
}

/** 가격 포맷: 12000 → "₩12,000" */
private fun formatKrw(priceKrw: Int): String =
    "₩" + String.format(java.util.Locale.KOREA, "%,d", priceKrw)

/**
 * image_url이 비었을 때 카테고리별 기본 플레이스홀더 이모지.
 *
 * 서버 카탈로그 카테고리(wax, pomade, hair_spray, hair_oil, curl_cream,
 * essence, treatment, color_shampoo, down_perm_kit 등)를 contains 매칭으로 커버.
 */
private fun categoryPlaceholderEmoji(category: String): String {
    val c = category.lowercase(java.util.Locale.ROOT)
    return when {
        c.contains("wax") || c.contains("pomade") -> "💈"
        c.contains("spray") -> "💨"
        c.contains("oil") -> "💧"
        c.contains("shampoo") || c.contains("essence") ||
            c.contains("treatment") || c.contains("cream") || c.contains("perm") -> "🧴"
        c.contains("comb") || c.contains("brush") || c.contains("tool") -> "🪮"
        else -> "🛍️"
    }
}

/**
 * 이미지를 갤러리에 저장
 */
private fun saveImageToGallery(context: Context, bitmap: Bitmap, styleName: String) {
    try {
        val filename = "HairMe_${styleName}_${System.currentTimeMillis()}.png"

        val outputStream: OutputStream?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (Scoped Storage)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HairMe")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            outputStream = uri?.let { context.contentResolver.openOutputStream(it) }
        } else {
            // Android 9 이하
            @Suppress("DEPRECATION")
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val hairMeDir = java.io.File(imagesDir, "HairMe")
            if (!hairMeDir.exists()) hairMeDir.mkdirs()

            val imageFile = java.io.File(hairMeDir, filename)
            outputStream = java.io.FileOutputStream(imageFile)
        }

        outputStream?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        Toast.makeText(context, context.getString(R.string.synthesis_saved), Toast.LENGTH_SHORT).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 이미지 공유
 */
private fun shareImage(context: Context, bitmap: Bitmap) {
    try {
        // 임시 파일로 저장
        val cachePath = java.io.File(context.cacheDir, "shared_images")
        cachePath.mkdirs()

        val file = java.io.File(cachePath, "HairMe_shared_${System.currentTimeMillis()}.png")
        val outputStream = java.io.FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.close()

        // FileProvider URI 생성
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        // 공유 인텐트
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            type = "image/png"
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            android.content.Intent.createChooser(shareIntent, "헤어스타일 공유")
        )

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "공유 실패: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
