package com.example.myapplication

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.myapplication.network.FeedbackRequest
import com.example.myapplication.network.HairColorItem
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierCard
import com.example.myapplication.ui.theme.AtelierCircleIconButton
import com.example.myapplication.ui.theme.AtelierDividerLine
import com.example.myapplication.ui.theme.AtelierPrimaryButton
import com.example.myapplication.ui.theme.AtelierSecondaryButton
import com.example.myapplication.ui.theme.AtelierTopBar
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.atelierLabel
import com.example.myapplication.ui.theme.atelierSerif
import com.example.myapplication.util.AnalyticsHelper
import com.example.myapplication.viewmodel.HairColorUiState
import com.example.myapplication.viewmodel.SynthesisUsageState
import kotlinx.coroutines.launch

/**
 * ResultScreen.kt - Atelier 리디자인 (v20 피드백 / v30 합성 / v33 염색 추천 로직 유지)
 *
 * UI 레이어만 "Atelier — 에디토리얼 아이보리" 디자인으로 교체.
 * 콜백 시그니처·ViewModel 연결·피드백 API·애널리틱스 로직은 기존과 동일.
 */

private const val TAG = "ResultScreen"

@Composable
fun ResultScreen(
    faceShape: String,      // ✅ v22: 신버전 API용
    skinTone: String,       // ✅ v22: 신버전 API용
    recommendedStyles: List<HairstyleRecommendation>,
    analysisId: String? = null, // ✅ v26: DynamoDB UUID 지원 (Int → String)
    elapsedTimeMs: Long = 0, // ✅ 분석 소요 시간
    gender: String = "male", // ✅ v28: 성별 (헤어스타일 이름 앞에 표시)
    hairColorState: HairColorUiState = HairColorUiState.Idle, // ✅ v33: 염색색 추천 상태
    usageState: SynthesisUsageState = SynthesisUsageState(), // 일일 무료 합성 횟수 상태
    onBackClick: () -> Unit = {},
    onFindSalonClick: ((String) -> Unit)? = null, // ✅ v21: 미용실 찾기 콜백
    onSynthesizeClick: ((String) -> Unit)? = null, // ✅ v30: 헤어스타일 합성 콜백
    onSynthesizeHairColor: ((String, String) -> Unit)? = null // ✅ v34: 염색색 합성 콜백 (colorName, colorHex)
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = Atelier.Background,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                AtelierTopBar(
                    title = stringResource(R.string.result_title),
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    navigationContentDescription = stringResource(R.string.result_back_to_home),
                    onNavigationClick = onBackClick,
                    actions = {
                        if (elapsedTimeMs > 0) {
                            Text(
                                text = "${String.format("%.1f", elapsedTimeMs / 1000.0)}초 소요",
                                fontSize = 12.sp,
                                color = Atelier.TextTertiary
                            )
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 요약 카드: 얼굴형 / 퍼스널컬러 2분할
            AnalysisSummaryCard(faceShape = faceShape, skinTone = skinTone)

            Spacer(modifier = Modifier.height(28.dp))

            // 추천 헤어스타일 섹션
            RecommendationSection(
                styles = recommendedStyles,
                analysisId = analysisId,
                gender = gender,
                usageState = usageState,
                snackbarHostState = snackbarHostState,
                onSynthesizeClick = onSynthesizeClick
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ✅ v33: 염색색 추천 섹션
            HairColorRecommendationSection(
                hairColorState = hairColorState,
                usageState = usageState,
                snackbarHostState = snackbarHostState,
                onSynthesizeHairColor = onSynthesizeHairColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 하단 CTA: 미용실 찾기(보더 pill) + 홈으로(블랙 pill)
            onFindSalonClick?.let { callback ->
                AtelierSecondaryButton(
                    text = "이 스타일 잘하는 주변 미용실 찾기",
                    onClick = { callback("주변 미용실") },
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                    leadingIcon = Icons.Default.LocationOn
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            AtelierPrimaryButton(
                text = stringResource(R.string.result_home_button),
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AnalysisSummaryCard(faceShape: String, skinTone: String) {
    AtelierCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            SummaryCell(
                label = stringResource(R.string.result_face_shape_label),
                value = faceShape,
                modifier = Modifier.weight(1f)
            )
            // 세로 헤어라인 구분선
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Atelier.Divider)
            )
            SummaryCell(
                label = stringResource(R.string.result_skin_tone_label),
                value = skinTone,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Text(
            text = label,
            style = atelierLabel(size = 11)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = atelierSerif(size = 21, color = Atelier.BrandVioletDeep)
        )
    }
}

@Composable
private fun RecommendationSection(
    styles: List<HairstyleRecommendation>,
    analysisId: String? = null,
    gender: String = "male",
    usageState: SynthesisUsageState = SynthesisUsageState(),
    snackbarHostState: SnackbarHostState? = null,
    onSynthesizeClick: ((String) -> Unit)? = null
) {
    // ✅ v35: ML 추천과 트렌드 스타일 분리
    val mlStyles = styles.filter { it.source == "ml" }
    val trendStyles = styles.filter { it.source == "trending" }

    // ✅ v36: 전체화면 이미지 뷰어 대상 (imageUrl, 표시 이름)
    var viewerTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val onImageClick: (HairstyleRecommendation, String, Int) -> Unit = { style, displayName, rank ->
        // image_url이 null인 카드는 뷰어를 열지 않음
        style.imageUrl?.let { url ->
            val section = if (style.source == "trending") "trend_hairstyle" else "ai_hairstyle"
            AnalyticsHelper.logItemClick(section, displayName, rank)
            viewerTarget = url to displayName
        }
    }

    // ✅ 섹션 조회 이벤트 (AI 헤어스타일 섹션)
    LaunchedEffect(Unit) {
        AnalyticsHelper.logSectionView("ai_hairstyle")
    }

    viewerTarget?.let { (imageUrl, styleName) ->
        FullscreenImageViewer(
            imageUrl = imageUrl,
            styleName = styleName,
            onDismiss = { viewerTarget = null }
        )
    }

    Column {
        // 섹션 헤더: 세리프 제목 + 우측 "가상 체험 N회 남음"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.result_recommendations_title),
                style = atelierSerif(size = 17)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "가상 체험 ${usageState.remaining}회 남음",
                fontSize = 12.sp,
                color = Atelier.TextTertiary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        mlStyles.forEachIndexed { index, style ->
            val originalIndex = styles.indexOf(style)
            if (index == 0) {
                // 1위: 이미지 중심 히어로 카드
                HeroRecommendationCard(
                    rank = 1,
                    style = style,
                    analysisId = analysisId,
                    styleIndex = originalIndex,
                    gender = gender,
                    usageState = usageState,
                    snackbarHostState = snackbarHostState,
                    onSynthesizeClick = onSynthesizeClick,
                    onImageClick = onImageClick
                )
            } else {
                // 2위 이하: 컴팩트 행 카드
                CompactRecommendationCard(
                    rank = index + 1,
                    style = style,
                    analysisId = analysisId,
                    styleIndex = originalIndex,
                    gender = gender,
                    usageState = usageState,
                    snackbarHostState = snackbarHostState,
                    onSynthesizeClick = onSynthesizeClick,
                    onImageClick = onImageClick
                )
            }

            if (index < mlStyles.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ✅ v35: 트렌드 스타일 섹션
        if (trendStyles.isNotEmpty()) {
            // ✅ 섹션 조회 이벤트 (트렌드 헤어스타일 섹션)
            LaunchedEffect(Unit) {
                AnalyticsHelper.logSectionView("trend_hairstyle")
            }

            Spacer(modifier = Modifier.height(12.dp))

            trendStyles.forEachIndexed { index, style ->
                val originalIndex = styles.indexOf(style)
                CompactRecommendationCard(
                    rank = index + 1,
                    style = style,
                    analysisId = analysisId,
                    styleIndex = originalIndex,
                    gender = gender,
                    usageState = usageState,
                    snackbarHostState = snackbarHostState,
                    onSynthesizeClick = onSynthesizeClick,
                    onImageClick = onImageClick,
                    isTrend = true
                )

                if (index < trendStyles.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

/** 성별 접두사 포함 표시 이름 (✅ v28 로직 유지) */
private fun displayStyleName(style: HairstyleRecommendation, gender: String): String {
    val genderPrefix = when (gender) {
        "male" -> "남자"
        "female" -> "여자"
        else -> ""
    }
    return if (genderPrefix.isNotEmpty()) "$genderPrefix ${style.name}" else style.name
}

/** 이미지 없음·로드 실패 시 플레이스홀더 (✅ v36: 기존 디자인 유지) */
@Composable
private fun StyleImagePlaceholder(
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
    text: String? = null
) {
    Box(
        modifier = modifier.background(Atelier.Divider),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = Atelier.Chevron,
                modifier = Modifier.size(iconSize)
            )
            if (text != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = Atelier.TextTertiary
                )
            }
        }
    }
}

/**
 * ✅ v36: 스타일 예시 이미지 (Coil)
 * - 로딩 중: 프로그레스 / 실패·null: 플레이스홀더
 * - crossfade는 앱 전역 ImageLoader에서 활성화 (HairMeApplication)
 */
@Composable
private fun StyleExampleImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderIconSize: androidx.compose.ui.unit.Dp = 28.dp,
    placeholderText: String? = null
) {
    if (imageUrl == null) {
        StyleImagePlaceholder(
            modifier = modifier,
            iconSize = placeholderIconSize,
            text = placeholderText
        )
        return
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Atelier.Divider),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Atelier.BrandViolet,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        error = {
            StyleImagePlaceholder(
                modifier = Modifier.fillMaxSize(),
                iconSize = placeholderIconSize,
                text = placeholderText
            )
        }
    )
}

/**
 * ✅ v36: 전체화면 이미지 뷰어
 * - 핀치 줌 + 더블탭 줌 + 드래그 패닝
 * - 상단 닫기 버튼 / 뒤로가기(onDismissRequest)로 닫힘
 * - 하단에 스타일 이름 표시
 *
 * @param cacheKey presigned URL처럼 호출마다 주소가 바뀌는 이미지의 캐시 키
 *                 (MyResultsScreen에서 S3 key 전달). null이면 url 기본 키 사용.
 */
@Composable
fun FullscreenImageViewer(
    imageUrl: String,
    styleName: String,
    onDismiss: () -> Unit,
    cacheKey: String? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }

        // 확대 배율에 맞춰 패닝 범위를 화면 밖으로 못 나가게 제한
        fun clampOffset(target: Offset, targetScale: Float): Offset {
            val maxX = containerSize.width * (targetScale - 1f) / 2f
            val maxY = containerSize.height * (targetScale - 1f) / 2f
            return Offset(
                target.x.coerceIn(-maxX, maxX),
                target.y.coerceIn(-maxY, maxY)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.93f))
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        offset = clampOffset(offset + pan, newScale)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapPosition ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val targetScale = 2.5f
                                val center = Offset(
                                    containerSize.width / 2f,
                                    containerSize.height / 2f
                                )
                                scale = targetScale
                                offset = clampOffset((center - tapPosition) * targetScale, targetScale)
                            }
                        }
                    )
                }
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .crossfade(true)
                    .build(),
                contentDescription = styleName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            )

            // 상단 닫기 버튼
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = Color.White
                )
            }

            // 하단 스타일 이름
            Text(
                text = styleName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .background(Color.Black.copy(alpha = 0.4f), Atelier.PillShape)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
    }
}

/** 1위 카드: 상단 3:4 AI 예시 이미지 + 추천 이유 + 액션 행 (매칭 % 미표시) */
@Composable
private fun HeroRecommendationCard(
    rank: Int,
    style: HairstyleRecommendation,
    analysisId: String?,
    styleIndex: Int,
    gender: String,
    usageState: SynthesisUsageState,
    snackbarHostState: SnackbarHostState?,
    onSynthesizeClick: ((String) -> Unit)?,
    onImageClick: (HairstyleRecommendation, String, Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentFeedback by remember { mutableStateOf<String?>(null) }
    val displayName = displayStyleName(style, gender)

    AtelierCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // ✅ v36: AI 예시 이미지 (3:4) — 탭하면 전체화면 뷰어 (null이면 기존 190dp 플레이스홀더, 동작 없음)
            StyleExampleImage(
                imageUrl = style.imageUrl,
                contentDescription = displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (style.imageUrl != null) Modifier.aspectRatio(3f / 4f)
                        else Modifier.height(190.dp)
                    )
                    .clickable(enabled = style.imageUrl != null) {
                        onImageClick(style, displayName, rank)
                    },
                placeholderText = "예시 이미지 준비 중이에요"
            )

            Column(modifier = Modifier.padding(20.dp)) {
                // 제목 (매칭 % / 프로그레스 바는 표시하지 않음 — 점수는 순위 계산에만 사용)
                Text(
                    text = "$rank · $displayName",
                    style = atelierSerif(size = 19),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 추천 이유
                Text(
                    text = style.reason,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Atelier.TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 액션 행: 보더 pill "가상으로 체험하기" + 원형 좋아요/싫어요
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    onSynthesizeClick?.let { callback ->
                        SynthesizePillButton(
                            styleName = style.name,
                            usageState = usageState,
                            snackbarHostState = snackbarHostState,
                            onSynthesizeClick = callback,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FeedbackIconButtons(
                        currentFeedback = currentFeedback,
                        onFeedback = { feedback ->
                            currentFeedback = feedback
                            AnalyticsHelper.logFeedback(style.name, feedback)
                            coroutineScope.launch {
                                if (analysisId != null) {
                                    submitFeedback(
                                        context = context,
                                        analysisId = analysisId,
                                        styleIndex = styleIndex + 1, // ✅ 1-based index
                                        feedback = feedback
                                    )
                                } else {
                                    android.util.Log.e(TAG, "❌ analysis_id가 없어 피드백을 제출할 수 없습니다")
                                    Toast.makeText(context, "분석 ID가 없어 피드백을 저장할 수 없습니다", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 2위 이하 · 트렌드: 컴팩트 행 카드 */
@Composable
private fun CompactRecommendationCard(
    rank: Int,
    style: HairstyleRecommendation,
    analysisId: String?,
    styleIndex: Int,
    gender: String,
    usageState: SynthesisUsageState,
    snackbarHostState: SnackbarHostState?,
    onSynthesizeClick: ((String) -> Unit)?,
    onImageClick: (HairstyleRecommendation, String, Int) -> Unit = { _, _, _ -> },
    isTrend: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentFeedback by remember { mutableStateOf<String?>(null) }
    val displayName = displayStyleName(style, gender)

    AtelierCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = style.imageUrl != null) {
                        onImageClick(style, displayName, rank)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ✅ v36: 64dp AI 예시 썸네일 (null·실패 시 기존 플레이스홀더)
                StyleExampleImage(
                    imageUrl = style.imageUrl,
                    contentDescription = displayName,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(Atelier.ThumbShape),
                    placeholderIconSize = 20.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isTrend) displayName else "$rank · $displayName",
                            style = atelierSerif(size = 16)
                        )
                        if (isTrend) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TrendBadge()
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 매칭 %는 표시하지 않고 항상 추천 이유를 보여준다
                    Text(
                        text = style.reason,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Atelier.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (style.imageUrl != null) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Atelier.Chevron,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 가상 체험 + 피드백 (기존 기능 유지)
            if (onSynthesizeClick != null || analysisId != null) {
                Spacer(modifier = Modifier.height(12.dp))
                AtelierDividerLine()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    onSynthesizeClick?.let { callback ->
                        SynthesizePillButton(
                            styleName = style.name,
                            usageState = usageState,
                            snackbarHostState = snackbarHostState,
                            onSynthesizeClick = callback,
                            modifier = Modifier.weight(1f),
                            height = 40.dp
                        )
                    }

                    FeedbackIconButtons(
                        currentFeedback = currentFeedback,
                        size = 40.dp,
                        onFeedback = { feedback ->
                            currentFeedback = feedback
                            AnalyticsHelper.logFeedback(style.name, feedback)
                            coroutineScope.launch {
                                if (analysisId != null) {
                                    submitFeedback(
                                        context = context,
                                        analysisId = analysisId,
                                        styleIndex = styleIndex + 1, // ✅ 1-based index
                                        feedback = feedback
                                    )
                                } else {
                                    android.util.Log.e(TAG, "❌ analysis_id가 없어 피드백을 제출할 수 없습니다")
                                    Toast.makeText(context, "분석 ID가 없어 피드백을 저장할 수 없습니다", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendBadge() {
    Text(
        text = "TREND",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Atelier.TrendAccent,
        modifier = Modifier
            .border(1.dp, Atelier.TrendBorder, Atelier.PillShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** 보더 pill "가상으로 체험하기" — 소진 시 opacity 0.4 disabled + 스낵바 로직 유지 */
@Composable
private fun SynthesizePillButton(
    styleName: String,
    usageState: SynthesisUsageState,
    snackbarHostState: SnackbarHostState?,
    onSynthesizeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp
) {
    val coroutineScope = rememberCoroutineScope()
    val isExhausted = usageState.remaining <= 0 && !usageState.isApiError

    OutlinedButton(
        onClick = {
            if (isExhausted) {
                coroutineScope.launch {
                    snackbarHostState?.showSnackbar(
                        "오늘의 무료 합성 횟수(3회)를 모두 사용했습니다. 내일 다시 이용해주세요!"
                    )
                }
            } else {
                onSynthesizeClick(styleName)
            }
        },
        enabled = !isExhausted,
        modifier = modifier
            .height(height)
            .then(if (isExhausted) Modifier.alpha(0.4f) else Modifier),
        shape = Atelier.PillShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Atelier.Ink),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Atelier.Ink,
            disabledContentColor = Atelier.Ink
        )
    ) {
        Text(
            text = stringResource(R.string.synthesis_try_button),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 원형 좋아요/싫어요 버튼 — 선택 시 보더·아이콘 퍼플 (✅ 피드백 API 연동 유지) */
@Composable
private fun FeedbackIconButtons(
    currentFeedback: String?,
    onFeedback: (String) -> Unit,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AtelierCircleIconButton(
            icon = Icons.Default.ThumbUp,
            contentDescription = stringResource(R.string.result_feedback_like),
            selected = currentFeedback == "like",
            size = size,
            onClick = { onFeedback("like") }
        )
        AtelierCircleIconButton(
            icon = Icons.Default.ThumbDown,
            contentDescription = stringResource(R.string.result_feedback_dislike),
            selected = currentFeedback == "dislike",
            size = size,
            onClick = { onFeedback("dislike") }
        )
    }
}

// ✅ v26: 피드백 제출 함수 (DynamoDB UUID 지원)
// 백엔드가 요구하는 필드: analysis_id, style_index, feedback
private suspend fun submitFeedback(
    context: android.content.Context,
    analysisId: String,       // 분석 요청 ID - DynamoDB UUID
    styleIndex: Int,          // 헤어스타일 인덱스 (0, 1, 2)
    feedback: String          // "like" 또는 "dislike"
) {
    try {
        android.util.Log.d(TAG, "📤 피드백 제출 시작 (v25 - 백엔드 API 형식)")
        android.util.Log.d(TAG, "   - analysis_id: $analysisId")
        android.util.Log.d(TAG, "   - style_index: $styleIndex")
        android.util.Log.d(TAG, "   - feedback: $feedback")

        val response = RetrofitClient.hairstyleApiService.submitFeedback(
            FeedbackRequest(
                analysis_id = analysisId,
                style_index = styleIndex,
                feedback = feedback
            )
        )

        android.util.Log.d(TAG, "📡 서버 응답 수신 - HTTP ${response.code()}")

        if (response.isSuccessful && response.body()?.success == true) {
            android.util.Log.d(TAG, "✅ 피드백 제출 성공")
            val message = when (feedback) {
                "like" -> context.getString(R.string.result_feedback_like_saved)
                "dislike" -> "싫어요가 반영되었습니다"
                else -> "피드백이 저장되었습니다"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } else {
            val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
            android.util.Log.e(TAG, "❌ 서버 오류 (${response.code()}): $errorBody")
            android.util.Log.e(TAG, "   - Response body: ${response.body()}")
            Toast.makeText(
                context,
                "피드백 저장 실패 (코드: ${response.code()})",
                Toast.LENGTH_LONG
            ).show()
        }
    } catch (e: java.net.SocketTimeoutException) {
        // 타임아웃 오류
        android.util.Log.e(TAG, "❌ 피드백 제출 타임아웃: ${e.message}", e)
        Toast.makeText(
            context,
            "서버 응답 시간이 초과되었습니다. 다시 시도해주세요.",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: java.net.UnknownHostException) {
        // 네트워크 연결 오류
        android.util.Log.e(TAG, "❌ 네트워크 연결 실패: ${e.message}", e)
        Toast.makeText(
            context,
            "네트워크 연결을 확인해주세요.",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        // 기타 오류
        android.util.Log.e(TAG, "❌ 피드백 제출 실패: ${e.message}", e)
        android.util.Log.e(TAG, "   - Exception type: ${e.javaClass.simpleName}")
        Toast.makeText(
            context,
            "피드백 저장 실패: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}

// ✅ v33: 염색색 추천 섹션
@Composable
private fun HairColorRecommendationSection(
    hairColorState: HairColorUiState,
    usageState: SynthesisUsageState = SynthesisUsageState(),
    snackbarHostState: SnackbarHostState? = null,
    onSynthesizeHairColor: ((String, String) -> Unit)? = null
) {
    // ✅ 섹션 조회 이벤트 (퍼스널컬러 섹션)
    LaunchedEffect(hairColorState) {
        if (hairColorState is HairColorUiState.Success) {
            AnalyticsHelper.logSectionView("personal_color")
        }
    }

    if (hairColorState is HairColorUiState.Idle) return

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "퍼스널컬러 염색 추천",
                style = atelierSerif(size = 17)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (hairColorState is HairColorUiState.Success) {
                Text(
                    text = "${hairColorState.personalColor} 기준",
                    fontSize = 12.sp,
                    color = Atelier.TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (hairColorState) {
            is HairColorUiState.Loading -> {
                AtelierCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Atelier.BrandViolet,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            is HairColorUiState.Success -> {
                // 추천 컬러
                hairColorState.recommended.take(3).forEachIndexed { index, color ->
                    HairColorCard(
                        color = color,
                        rank = index + 1,
                        usageState = usageState,
                        snackbarHostState = snackbarHostState,
                        onSynthesizeClick = onSynthesizeHairColor
                    )
                    if (index < 2) Spacer(modifier = Modifier.height(10.dp))
                }

                // 피해야 할 컬러 안내
                if (hairColorState.avoid.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = Atelier.TrendAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "피해야 할 컬러: " +
                                hairColorState.avoid.take(2).joinToString(", ") { it.name },
                            fontSize = 13.sp,
                            color = Atelier.TextSecondary
                        )
                    }
                }
            }
            is HairColorUiState.Error -> {
                AtelierCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "염색 추천을 불러올 수 없습니다",
                        fontSize = 14.sp,
                        color = Atelier.TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            HairColorUiState.Idle -> Unit
        }
    }
}

// ✅ v33: 염색 컬러 카드 — 52dp 원형 스와치 + 보더 pill "체험" 버튼
@Composable
private fun HairColorCard(
    color: HairColorItem,
    rank: Int,
    usageState: SynthesisUsageState = SynthesisUsageState(),
    snackbarHostState: SnackbarHostState? = null,
    onSynthesizeClick: ((String, String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val isExhausted = usageState.remaining <= 0 && !usageState.isApiError

    AtelierCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 원형 스와치: 실제 hex + 흰 보더 3dp + 바깥 1dp 링
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(1.dp, Atelier.Border, CircleShape)
                    .padding(1.dp)
                    .background(Color.White, CircleShape)
                    .padding(3.dp)
                    .background(
                        try {
                            Color(android.graphics.Color.parseColor(color.hex))
                        } catch (e: Exception) {
                            Color.Gray
                        },
                        CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = color.name,
                    style = atelierSerif(size = 16)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (color.level.isNotEmpty())
                        "${color.description} · 밝기 ${color.level}"
                    else
                        color.description,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Atelier.TextSecondary
                )
            }

            // ✅ v34: 염색색 합성 버튼
            onSynthesizeClick?.let { callback ->
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = {
                        if (isExhausted) {
                            coroutineScope.launch {
                                snackbarHostState?.showSnackbar(
                                    "오늘의 무료 합성 횟수(3회)를 모두 사용했습니다. 내일 다시 이용해주세요!"
                                )
                            }
                        } else {
                            AnalyticsHelper.logItemClick("personal_color", color.name, rank)
                            callback(color.name, color.hex)
                        }
                    },
                    enabled = !isExhausted,
                    modifier = Modifier
                        .height(36.dp)
                        .then(if (isExhausted) Modifier.alpha(0.4f) else Modifier),
                    shape = Atelier.PillShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Atelier.Ink),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Atelier.Ink,
                        disabledContentColor = Atelier.Ink
                    )
                ) {
                    Text(text = "체험", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ResultScreenPreview() {
    MyApplicationTheme {
        ResultScreen(
            faceShape = "계란형",
            skinTone = "여름쿨",
            elapsedTimeMs = 4532,
            gender = "male",
            hairColorState = HairColorUiState.Success(
                personalColor = "여름쿨",
                recommended = listOf(
                    HairColorItem(
                        name = "애쉬브라운",
                        hex = "#8B7355",
                        level = "6-7",
                        description = "회색빛이 도는 부드러운 브라운"
                    ),
                    HairColorItem(
                        name = "로즈브라운",
                        hex = "#BC8F8F",
                        level = "7-8",
                        description = "장미빛이 도는 브라운으로 여성스럽고 우아한 느낌"
                    )
                ),
                avoid = emptyList()
            ),
            recommendedStyles = listOf(
                HairstyleRecommendation(
                    name = "투블럭컷",
                    score = 0.92,
                    reason = "얼굴형에 가장 잘 어울리는 스타일"
                ),
                HairstyleRecommendation(
                    name = "댄디컷",
                    score = 0.85,
                    reason = "쿨톤 피부에 잘 어울림"
                ),
                HairstyleRecommendation(
                    name = "리프컷",
                    score = null,
                    reason = "요즘 가장 인기 있는 남성 헤어스타일",
                    source = "trending"
                )
            )
        )
    }
}
