package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.network.BodyAnalysisResponse
import com.example.myapplication.network.BodyFit
import com.example.myapplication.network.COUPANG_DEFAULT_DISCLOSURE
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierCard
import com.example.myapplication.ui.theme.AtelierDividerLine
import com.example.myapplication.ui.theme.AtelierPrimaryButton
import com.example.myapplication.ui.theme.AtelierSegmentedControl
import com.example.myapplication.ui.theme.AtelierTopBar
import com.example.myapplication.ui.theme.AtelierUnderlineButton
import com.example.myapplication.ui.theme.atelierLabel
import com.example.myapplication.ui.theme.atelierSerif
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/*
 * 체형별 아이템 추천 화면 3개 (헤어 분석과 별도 기능)
 *
 * 1. BodyGuideScreen  - 촬영 가이드: 좋은 예 1 + 나쁜 예 4, 성별 선택
 * 2. BodyCameraScreen - 점선 실루엣 + 3초 타이머 카메라 (실시간 자세 체크 없음)
 * 3. BodyResultScreen - 체형, 어울리는 핏(쿠팡 검색 링크), 피할 핏
 */

private const val TAG = "BodyFashion"
private const val TIMER_SECONDS = 3

private val GoodColor = Color(0xFF2F7D4F)
private val BadColor = Color(0xFFC2410C)
private val FigureFill = Color(0xFFC9D3E4)
private val FigureStroke = Color(0xFF5B6474)
private val GuideLineColor = Color(0xFF8FB0FF)

// ========== 사람 그림 (가이드 목업과 같은 100x200 좌표) ==========

private const val BODY_PATH =
    "M40 36 C44 34 56 34 60 36 L68 40 L74 96 L68 97 L62 52 L61 104 L59 192 L52 192 L50 120 " +
        "L48 192 L41 192 L39 104 L38 52 L32 97 L26 96 L32 40 Z"
private const val SIDE_BODY_PATH = "M40 36 C44 34 56 34 60 36 L62 104 L59 192 L41 192 L39 104 Z"
private const val SIDE_ARM_PATH = "M54 40 L62 70"
private const val OVERSIZE_COAT_PATH = "M34 36 C44 32 56 32 66 36 L84 54 L86 132 L14 132 L16 54 Z"
private const val OVERSIZE_LEGS_PATH = "M44 140 L42 192 L49 192 L50 150 L51 192 L58 192 L56 140 Z"

private fun pathOf(data: String): Path = PathParser().parsePathString(data).toPath()

private enum class GuideFigure(
    val viewBox: FloatArray, // x, y, width, height
    val label: String
) {
    Good(floatArrayOf(0f, 0f, 100f, 200f), "좋은 예: 정면 전신"),
    FeetCut(floatArrayOf(0f, 0f, 100f, 140f), "나쁜 예: 발이 잘린 사진"),
    Side(floatArrayOf(0f, 0f, 100f, 200f), "나쁜 예: 옆으로 선 사진"),
    Oversize(floatArrayOf(0f, 0f, 100f, 200f), "나쁜 예: 오버핏 옷으로 체형이 가려진 사진"),
    TooClose(floatArrayOf(22f, 4f, 56f, 70f), "나쁜 예: 너무 가까워 상반신만 나온 사진")
}

/** viewBox 좌표계를 캔버스 가운데에 맞춰 그린다 */
private fun DrawScope.inViewBox(viewBox: FloatArray, block: DrawScope.() -> Unit) {
    val (vx, vy, vw, vh) = viewBox.toList()
    val scale = min(size.width / vw, size.height / vh)
    val dx = (size.width - vw * scale) / 2f
    val dy = (size.height - vh * scale) / 2f
    withTransform({
        translate(dx, dy)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-vx, -vy)
    }, block)
}

@Composable
private fun GuideIllustration(figure: GuideFigure, modifier: Modifier = Modifier) {
    val body = remember { pathOf(BODY_PATH) }
    val side = remember { pathOf(SIDE_BODY_PATH) }
    val sideArm = remember { pathOf(SIDE_ARM_PATH) }
    val coat = remember { pathOf(OVERSIZE_COAT_PATH) }
    val legs = remember { pathOf(OVERSIZE_LEGS_PATH) }
    Canvas(
        modifier = modifier
            .clipToBounds()
            .semantics { contentDescription = figure.label }
    ) {
        inViewBox(figure.viewBox) {
            val stroke = Stroke(width = 1.5f, join = StrokeJoin.Round)
            fun figurePath(path: Path) {
                drawPath(path, FigureFill)
                drawPath(path, FigureStroke, style = stroke)
            }
            drawCircle(FigureFill, radius = 11f, center = Offset(50f, 22f))
            drawCircle(FigureStroke, radius = 11f, center = Offset(50f, 22f), style = stroke)
            when (figure) {
                GuideFigure.Good, GuideFigure.FeetCut, GuideFigure.TooClose -> figurePath(body)
                GuideFigure.Side -> {
                    figurePath(side)
                    drawPath(sideArm, FigureStroke, style = stroke)
                }
                GuideFigure.Oversize -> {
                    figurePath(legs)
                    figurePath(coat)
                }
            }
        }
    }
}

// ========== 1. 촬영 가이드 ==========

@Composable
fun BodyGuideScreen(
    gender: String,
    onGenderChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Atelier.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AtelierTopBar(
            title = "체형별 아이템 추천",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationContentDescription = "뒤로 가기",
            onNavigationClick = onBackClick
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "이렇게 찍어주세요", style = atelierSerif(size = 24))
            Spacer(modifier = Modifier.height(16.dp))

            AtelierSegmentedControl(
                options = listOf("male" to "남성", "female" to "여성"),
                selectedValue = gender,
                onSelect = onGenderChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            GoodExampleCard()

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "이런 사진은 다시 찍어야 해요",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Atelier.Ink
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BadExampleCard(GuideFigure.FeetCut, "발이 잘렸어요", Modifier.weight(1f))
                BadExampleCard(GuideFigure.Side, "옆으로 섰어요", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BadExampleCard(GuideFigure.Oversize, "오버핏이라 체형이 가려져요", Modifier.weight(1f))
                BadExampleCard(GuideFigure.TooClose, "너무 가까워요", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))
            TipRow(
                icon = Icons.Filled.Timer,
                text = "폰을 허리 높이에 세워 두고 3초 타이머로 찍으면 편해요"
            )
            Spacer(modifier = Modifier.height(8.dp))
            TipRow(
                icon = Icons.Outlined.Info,
                text = "사진은 서비스 개선을 위해 1년간 보관한 뒤 자동 삭제해요"
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            AtelierPrimaryButton(
                text = "촬영하기",
                onClick = onTakePhotoClick,
                modifier = Modifier.fillMaxWidth()
            )
            AtelierUnderlineButton(
                text = "앨범에서 선택",
                onClick = onGalleryClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun GoodExampleCard() {
    AtelierCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(150.dp)
                    .background(Atelier.Background, Atelier.ThumbShape)
                    .padding(8.dp)
            ) {
                GuideIllustration(GuideFigure.Good, Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                ExampleBadge(good = true, text = "좋은 예")
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "정면 · 전신 · 붙는 옷",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Atelier.Ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "2~3m 거리에서 머리부터 발끝까지, 팔은 몸에서 살짝 떼고 찍어요.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Atelier.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun BadExampleCard(figure: GuideFigure, caption: String, modifier: Modifier = Modifier) {
    AtelierCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Atelier.Background, Atelier.ThumbShape)
                    .padding(6.dp)
            ) {
                GuideIllustration(figure, Modifier.fillMaxSize())
                ExampleBadge(
                    good = false,
                    text = null,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = caption,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Atelier.Ink,
                modifier = Modifier.height(34.dp)
            )
        }
    }
}

@Composable
private fun ExampleBadge(good: Boolean, text: String?, modifier: Modifier = Modifier) {
    val color = if (good) GoodColor else BadColor
    Row(
        modifier = modifier
            .background(color, Atelier.PillShape)
            .padding(horizontal = if (text == null) 3.dp else 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (good) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = if (text == null) "나쁜 예" else null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        text?.let {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun TipRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Atelier.BrandViolet,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 13.sp, color = Atelier.TextSecondary)
    }
}

/** 앨범에서 사진 한 장 고르기 (시스템 Photo Picker, 저장소 권한 불필요) */
@Composable
fun rememberBodyPhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

// ========== 2. 카메라 (점선 실루엣 + 3초 타이머) ==========

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                continuation.resume(future.get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

@Composable
fun BodyCameraScreen(
    onCloseClick: () -> Unit,
    onGuideClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onPhotoTaken: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 폰을 세워 두고 멀리서 찍으니 화면을 보면서 맞출 수 있게 전면 카메라가 기본
    var useFrontCamera by remember { mutableStateOf(true) }
    var timerOn by remember { mutableStateOf(true) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(hasPermission, useFrontCamera) {
        if (!hasPermission) return@LaunchedEffect
        try {
            val provider = context.cameraProvider()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = when {
                useFrontCamera && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 카메라 연결 실패: ${e.message}")
        }
    }
    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    fun capture() {
        isCapturing = true
        val file = File(context.cacheDir, "body_${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    onPhotoTaken(Uri.fromFile(file))
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    Log.e(TAG, "❌ 촬영 실패: ${exception.message}")
                    android.widget.Toast.makeText(
                        context,
                        "사진을 찍지 못했어요. 다시 시도해주세요",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    fun onShutter() {
        if (isCapturing) return
        // 카운트다운 중에 다시 누르면 취소
        if (countdown != null) {
            countdownJob?.cancel()
            countdown = null
            return
        }
        if (!timerOn) {
            capture()
            return
        }
        countdownJob = scope.launch {
            for (second in TIMER_SECONDS downTo 1) {
                countdown = second
                delay(1000)
            }
            countdown = null
            capture()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraRoundButton(Icons.Filled.Close, "닫기", onCloseClick)
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(Atelier.PillShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onGuideClick)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "가이드", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            CameraRoundButton(Icons.Filled.Cameraswitch, "카메라 전환") {
                if (countdown == null && !isCapturing) useFrontCamera = !useFrontCamera
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (hasPermission) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                SilhouetteOverlay(Modifier.fillMaxSize())
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "카메라 권한이 있어야 촬영할 수 있어요.\n앨범에서 전신 사진을 골라도 돼요.",
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    AtelierUnderlineButton(
                        text = "권한 다시 요청",
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
            }
            countdown?.let {
                Text(
                    text = it.toString(),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Text(
            text = if (countdown != null) "셔터를 다시 누르면 취소돼요" else "점선 안에 몸을 맞춰주세요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2F37))
                    .clickable(onClick = onGalleryClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = "앨범에서 선택",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (countdown != null) BadColor else Color.White)
                    .clickable(enabled = hasPermission && !isCapturing, onClick = ::onShutter)
                    .semantics { contentDescription = if (countdown != null) "촬영 취소" else "촬영" },
                contentAlignment = Alignment.Center
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = CameraBackground
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (timerOn) Color(0xFF2459D6) else Color.White.copy(alpha = 0.16f))
                    .clickable(enabled = countdown == null) { timerOn = !timerOn }
                    .semantics {
                        contentDescription = if (timerOn) "타이머 3초 켜짐" else "타이머 꺼짐"
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (timerOn) Icons.Filled.Timer else Icons.Filled.TimerOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (timerOn) "${TIMER_SECONDS}초" else "끔",
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private val CameraBackground = Color(0xFF0E1013)

@Composable
private fun CameraRoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** 몸을 맞출 점선 실루엣 + 머리/발 위치 가이드 선 */
@Composable
private fun SilhouetteOverlay(modifier: Modifier = Modifier) {
    val body = remember { pathOf(BODY_PATH) }
    Box(modifier = modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(0.5f)
                .semantics { contentDescription = "몸을 맞출 점선 실루엣 가이드" }
        ) {
            inViewBox(floatArrayOf(0f, 0f, 100f, 200f)) {
                val dashed = Stroke(
                    width = 0.9f,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f, 2f))
                )
                drawLine(GuideLineColor, Offset(4f, 6f), Offset(96f, 6f), strokeWidth = 0.6f)
                drawLine(GuideLineColor, Offset(4f, 196f), Offset(96f, 196f), strokeWidth = 0.6f)
                drawCircle(Color.White, radius = 11f, center = Offset(50f, 22f), style = dashed)
                drawPath(body, Color.White, style = dashed)
            }
        }
    }
}

// ========== 3. 결과 ==========

@Composable
fun BodyAnalyzingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Atelier.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Atelier.BrandViolet, strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "체형을 분석하고 있어요", style = atelierSerif(size = 18))
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "몇 초면 끝나요", fontSize = 13.sp, color = Atelier.TextTertiary)
    }
}

@Composable
fun BodyErrorScreen(
    message: String,
    isPhotoProblem: Boolean,
    onRetakeClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Atelier.Background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isPhotoProblem) "다시 찍어주세요" else "분석하지 못했어요",
            style = atelierSerif(size = 22)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = Atelier.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        AtelierPrimaryButton(text = "다시 찍기", onClick = onRetakeClick, modifier = Modifier.fillMaxWidth())
        AtelierUnderlineButton(text = "홈으로", onClick = onHomeClick)
    }
}

@Composable
fun BodyResultScreen(
    result: BodyAnalysisResponse,
    onItemClick: (BodyFit) -> Unit,
    onRetakeClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Atelier.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AtelierTopBar(
            title = "체형 분석 결과",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationContentDescription = "홈으로",
            onNavigationClick = onHomeClick
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "YOUR BODY FIT",
                style = atelierLabel(size = 11, color = Atelier.BrandVioletSoft, tracking = 0.16f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = result.bodyTypeLabel, style = atelierSerif(size = 28))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = result.description,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Atelier.TextSecondary
            )

            result.secondary.forEach { secondary ->
                Spacer(modifier = Modifier.height(12.dp))
                AtelierCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                    Column {
                        Text(
                            text = "+ ${secondary.label}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Atelier.BrandVioletDeep
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = secondary.description,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Atelier.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(text = "이렇게 입어보세요", style = atelierSerif(size = 18))
            Spacer(modifier = Modifier.height(10.dp))
            AtelierCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    result.recommended.forEachIndexed { index, fit ->
                        if (index > 0) AtelierDividerLine(modifier = Modifier.padding(horizontal = 16.dp))
                        RecommendedFitRow(fit = fit, onClick = { onItemClick(fit) })
                    }
                }
            }

            if (result.avoid.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(text = "피하면 좋아요", style = atelierSerif(size = 18))
                Spacer(modifier = Modifier.height(10.dp))
                AtelierCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        result.avoid.forEachIndexed { index, fit ->
                            if (index > 0) AtelierDividerLine(modifier = Modifier.padding(horizontal = 16.dp))
                            AvoidFitRow(fit)
                        }
                    }
                }
            }

            // 쿠팡 링크가 하나라도 보이면 대가성 문구는 항상 함께 보여야 한다
            if (result.recommended.any { it.productId != null }) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = result.disclosure?.takeIf { it.isNotBlank() } ?: COUPANG_DEFAULT_DISCLOSURE,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = Atelier.TextTertiary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            AtelierPrimaryButton(text = "다시 찍기", onClick = onRetakeClick, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RecommendedFitRow(fit: BodyFit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = fit.keyword, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Atelier.Ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = fit.reason, fontSize = 13.sp, lineHeight = 19.sp, color = Atelier.TextSecondary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (fit.productId != null) {
            Box(
                modifier = Modifier
                    .clip(Atelier.PillShape)
                    .border(1.dp, Atelier.Ink, Atelier.PillShape)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text = "쿠팡에서 보기", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Atelier.Ink)
            }
        } else {
            Text(text = "스타일링 팁", style = atelierLabel(size = 11, color = Atelier.TextTertiary, tracking = 0.04f))
        }
    }
}

@Composable
private fun AvoidFitRow(fit: BodyFit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = BadColor,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = fit.keyword, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Atelier.Ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = fit.reason, fontSize = 13.sp, lineHeight = 19.sp, color = Atelier.TextSecondary)
        }
    }
}
