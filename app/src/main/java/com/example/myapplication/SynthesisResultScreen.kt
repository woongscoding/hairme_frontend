package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.myapplication.util.AnalyticsHelper
import com.example.myapplication.viewmodel.SynthesisUiState
import java.io.OutputStream

/**
 * SynthesisResultScreen.kt - v30 (헤어스타일 합성 결과 화면)
 *
 * Gemini 2.5 Flash Image API로 합성된 헤어스타일 결과를 보여주는 다이얼로그
 */

private object SynthesisColors {
    val Background = Color.White
    val Primary = Color(0xFF5B4FFF)
    val TextPrimary = Color.Black
    val TextSecondary = Color(0xFF666666)
    val SaveButtonBg = Color(0xFF4CAF50)
    val ShareButtonBg = Color(0xFF2196F3)
}

/**
 * 합성 로딩 다이얼로그
 */
@Composable
fun SynthesisLoadingDialog(
    hairstyleName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* 로딩 중에는 닫기 불가 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SynthesisColors.Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = SynthesisColors.Primary,
                    strokeWidth = 6.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "'$hairstyleName' 스타일 적용 중...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SynthesisColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.synthesis_loading_desc),
                    fontSize = 14.sp,
                    color = SynthesisColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text("취소", color = SynthesisColors.TextSecondary)
                }
            }
        }
    }
}

/**
 * 합성 결과 다이얼로그
 */
@Composable
fun SynthesisResultDialog(
    synthesizedImage: Bitmap,
    hairstyleName: String,
    onDismiss: () -> Unit,
    onTryAnother: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SynthesisColors.Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.synthesis_result_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SynthesisColors.TextPrimary
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.synthesis_close),
                            tint = SynthesisColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 적용된 스타일 이름
                Text(
                    text = "'$hairstyleName' 스타일",
                    fontSize = 16.sp,
                    color = SynthesisColors.Primary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 합성 결과 이미지
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = synthesizedImage.asImageBitmap(),
                        contentDescription = "합성된 헤어스타일 이미지",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 버튼 영역
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 이미지 저장 버튼
                    Button(
                        onClick = {
                            AnalyticsHelper.logResultSave("ai_hairstyle", hairstyleName)
                            saveImageToGallery(context, synthesizedImage, hairstyleName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SynthesisColors.SaveButtonBg
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.synthesis_save),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 공유 버튼
                    OutlinedButton(
                        onClick = {
                            AnalyticsHelper.logResultShare("ai_hairstyle", "system_share")
                            shareImage(context, synthesizedImage)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.synthesis_share),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 다른 스타일 적용 버튼
                TextButton(
                    onClick = onTryAnother,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.synthesis_try_another),
                        color = SynthesisColors.Primary
                    )
                }
            }
        }
    }
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SynthesisColors.Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 48.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.synthesis_error),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SynthesisColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = errorMessage,
                    fontSize = 14.sp,
                    color = SynthesisColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("닫기")
                    }

                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SynthesisColors.Primary
                        )
                    ) {
                        Text("다시 시도")
                    }
                }
            }
        }
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
    Dialog(
        onDismissRequest = { /* 로딩 중에는 닫기 불가 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SynthesisColors.Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = SynthesisColors.Primary,
                    strokeWidth = 6.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "'$colorName' 색상 적용 중...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SynthesisColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "AI가 자연스럽게 염색 효과를 적용하고 있어요",
                    fontSize = 14.sp,
                    color = SynthesisColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text("취소", color = SynthesisColors.TextSecondary)
                }
            }
        }
    }
}

/**
 * 염색색 합성 결과 다이얼로그
 */
@Composable
fun ColorSynthesisResultDialog(
    synthesizedImage: Bitmap,
    colorName: String,
    onDismiss: () -> Unit,
    onTryAnother: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SynthesisColors.Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "염색 시뮬레이션 결과",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SynthesisColors.TextPrimary
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = SynthesisColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 적용된 컬러 이름
                Text(
                    text = "'$colorName' 컬러",
                    fontSize = 16.sp,
                    color = SynthesisColors.Primary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 합성 결과 이미지
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = synthesizedImage.asImageBitmap(),
                        contentDescription = "염색 적용 이미지",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 버튼 영역
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 이미지 저장 버튼
                    Button(
                        onClick = {
                            AnalyticsHelper.logResultSave("hair_color", colorName)
                            saveImageToGallery(context, synthesizedImage, colorName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SynthesisColors.SaveButtonBg
                        )
                    ) {
                        Text(
                            text = "저장하기",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 공유 버튼
                    OutlinedButton(
                        onClick = {
                            AnalyticsHelper.logResultShare("hair_color", "system_share")
                            shareImage(context, synthesizedImage)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "공유",
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 다른 색상 적용 버튼
                TextButton(
                    onClick = onTryAnother,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "다른 색상 적용해보기",
                        color = SynthesisColors.Primary
                    )
                }
            }
        }
    }
}
