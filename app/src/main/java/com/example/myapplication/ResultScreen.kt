package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.network.FeedbackRequest
import com.example.myapplication.network.HairColorItem
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.util.AnalyticsHelper
import com.example.myapplication.viewmodel.HairColorUiState
import com.example.myapplication.viewmodel.SynthesisUsageState
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * ResultScreen.kt - v20 (피드백 기능 추가)
 *
 * 주요 개선사항:
 * 1. 색상을 ResultColors 객체로 중앙 관리
 * 2. 컴포저블을 작은 단위로 분리하여 재사용성 향상
 * 3. 반복되는 코드 제거 (AnalysisInfoItem)
 * 4. 가독성 향상을 위한 구조 개선
 * 5. ✅ 헤어스타일 이름 클릭 시 네이버 이미지 검색 연동
 * 6. ✅ v20: 좋아요/싫어요 버튼 추가
 * 7. ✅ v20: 피드백 API 연동
 */

// ✅ TAG 상수 추가
private const val TAG = "ResultScreen"

// 색상 상수
private object ResultColors {
    val Background = Color.White
    val Primary = Color(0xFF5B4FFF)
    val SummaryCardBg = Color(0xFFF8F7FF)
    val RecommendationCardBg = Color(0xFFE8E3FF)
    val SearchIconBg = Color(0xFF03C75A) // 네이버 그린
    val LikeColor = Color(0xFF4CAF50) // 좋아요 색상
    val DislikeColor = Color(0xFFF44336) // 싫어요 색상
    val TextPrimary = Color.Black
    val TextSecondary = Color(0xFF666666)
    val TextTertiary = Color(0xFF999999)
    val ProgressTrack = Color(0xFFD0C9FF)
}

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            ResultTopBar(onBackClick = onBackClick)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ResultColors.Background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // 분석 결과 요약
            AnalysisSummaryCard(
                faceShape = faceShape,
                skinTone = skinTone,
                elapsedTimeMs = elapsedTimeMs
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 일일 무료 합성 횟수 배너
            SynthesisUsageBanner(usageState = usageState)

            Spacer(modifier = Modifier.height(16.dp))

            // 추천 헤어스타일 섹션
            RecommendationSection(
                styles = recommendedStyles,
                analysisId = analysisId,
                gender = gender, // ✅ v28: 성별 전달
                usageState = usageState,
                snackbarHostState = snackbarHostState,
                onFindSalonClick = onFindSalonClick,
                onSynthesizeClick = onSynthesizeClick // ✅ v30: 합성 콜백 전달
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ✅ v33: 염색색 추천 섹션
            HairColorRecommendationSection(
                hairColorState = hairColorState,
                usageState = usageState,
                snackbarHostState = snackbarHostState,
                onSynthesizeHairColor = onSynthesizeHairColor // ✅ v34: 염색색 합성 콜백 전달
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 홈으로 버튼
            BackToHomeButton(onClick = onBackClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.result_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.result_back_to_home)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ResultColors.Background,
            titleContentColor = ResultColors.TextPrimary
        )
    )
}

@Composable
private fun AnalysisSummaryCard(
    faceShape: String,
    skinTone: String,
    elapsedTimeMs: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = ResultColors.SummaryCardBg
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.result_analysis_summary),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResultColors.TextPrimary
                )

                // ⏱️ 분석 소요 시간 표시
                if (elapsedTimeMs > 0) {
                    Text(
                        text = "⏱️ ${String.format("%.1f", elapsedTimeMs / 1000.0)}초",
                        fontSize = 12.sp,
                        color = ResultColors.TextSecondary,
                        modifier = Modifier
                            .background(
                                color = ResultColors.Primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalysisInfoItem(
                    label = stringResource(R.string.result_face_shape_label),
                    value = faceShape
                )

                AnalysisInfoItem(
                    label = stringResource(R.string.result_skin_tone_label),
                    value = skinTone
                )
            }
        }
    }
}

@Composable
private fun AnalysisInfoItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            color = ResultColors.TextTertiary
        )
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ResultColors.Primary
        )
    }
}

@Composable
private fun RecommendationSection(
    styles: List<HairstyleRecommendation>,
    analysisId: String? = null, // ✅ v26: DynamoDB UUID 지원 (Int → String)
    gender: String = "male", // ✅ v28: 성별 (헤어스타일 이름에 표시)
    usageState: SynthesisUsageState = SynthesisUsageState(),
    snackbarHostState: SnackbarHostState? = null,
    onFindSalonClick: ((String) -> Unit)? = null, // ✅ v21: 미용실 찾기 콜백
    onSynthesizeClick: ((String) -> Unit)? = null // ✅ v30: 헤어스타일 합성 콜백
) {
    // ✅ v35: ML 추천과 트렌드 스타일 분리
    val mlStyles = styles.filter { it.source == "ml" }
    val trendStyles = styles.filter { it.source == "trending" }

    // ✅ 섹션 조회 이벤트 (AI 헤어스타일 섹션)
    LaunchedEffect(Unit) {
        AnalyticsHelper.logSectionView("ai_hairstyle")
    }

    Column {
        Text(
            text = stringResource(R.string.result_recommendations_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ResultColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 안내 문구 수정
        Text(
            text = stringResource(R.string.result_recommendations_tip),
            fontSize = 13.sp,
            color = ResultColors.TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ML 추천 카드 (기존과 동일)
        mlStyles.forEachIndexed { index, style ->
            val originalIndex = styles.indexOf(style)
            RecommendationCard(
                rank = index + 1,
                style = style,
                analysisId = analysisId,
                styleIndex = originalIndex,
                gender = gender,
                usageState = usageState,
                snackbarHostState = snackbarHostState,
                onSynthesizeClick = onSynthesizeClick
            )

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

            Spacer(modifier = Modifier.height(24.dp))

            // 트렌드 구분 헤더
            Text(
                text = "\uD83D\uDD25 트렌드 스타일",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ResultColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "요즘 인기 있는 스타일을 추천해드려요",
                fontSize = 13.sp,
                color = ResultColors.TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            trendStyles.forEachIndexed { index, style ->
                val originalIndex = styles.indexOf(style)
                RecommendationCard(
                    rank = index + 1,
                    style = style,
                    analysisId = analysisId,
                    styleIndex = originalIndex,
                    gender = gender,
                    usageState = usageState,
                    snackbarHostState = snackbarHostState,
                    onSynthesizeClick = onSynthesizeClick
                )

                if (index < trendStyles.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // ✅ 통합 미용실 찾기 버튼 (하단에 하나만)
        onFindSalonClick?.let { callback ->
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { callback("주변 미용실") }, // "미용실"로만 검색
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFEE500) // 카카오 옐로우
                )
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "주변 미용실 바로가기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    rank: Int,
    style: HairstyleRecommendation,
    analysisId: String? = null, // ✅ v26: DynamoDB UUID 지원 (Int → String)
    styleIndex: Int,       // ✅ v25: 헤어스타일 인덱스 (0, 1, 2)
    gender: String = "male", // ✅ v28: 성별 (헤어스타일 이름에 표시)
    usageState: SynthesisUsageState = SynthesisUsageState(),
    snackbarHostState: SnackbarHostState? = null,
    onSynthesizeClick: ((String) -> Unit)? = null // ✅ v30: 헤어스타일 합성 콜백
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ✅ v20: 피드백 상태 관리
    var currentFeedback by remember { mutableStateOf<String?>(null) }

    // ✅ v28: 성별 접두사 생성 (남자/여자)
    val genderPrefix = when (gender) {
        "male" -> "남자"
        "female" -> "여자"
        else -> "" // neutral이거나 미지정 시 접두사 없음
    }
    // 성별 + 스타일 이름 조합 (예: "남자 투블럭컷")
    val displayStyleName = if (genderPrefix.isNotEmpty()) {
        "$genderPrefix ${style.name}"
    } else {
        style.name
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = ResultColors.RecommendationCardBg
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 순위 배지 - ✅ v35: 트렌드 스타일은 🔥 표시
                RankBadge(rank = rank, isTrend = style.source == "trending")

                Spacer(modifier = Modifier.width(16.dp))

                // 스타일 이름 (클릭 가능) - ✅ v28: 성별 접두사 포함
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = ResultColors.Primary),
                            onClick = {
                                // ✅ v27: API가 제공한 URL 사용 (성별 접두사 포함)
                                val naverImageUrl = style.imageSearchUrl ?: run {
                                    // Fallback: API URL이 없으면 직접 생성 (성별 포함)
                                    val encodedQuery = URLEncoder.encode("$displayStyleName 헤어스타일", "UTF-8")
                                    "https://search.naver.com/search.naver?where=image&query=$encodedQuery"
                                }

                                // 브라우저로 열기
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(naverImageUrl))
                                context.startActivity(intent)

                                // ✅ v28: 네이버 클릭 로그 (성별 포함)
                                AnalyticsHelper.logSearchHairstyle(displayStyleName)
                                // ✅ 아이템 클릭 이벤트 (섹션별 분석용)
                                val section = if (style.source == "trending") "trend_hairstyle" else "ai_hairstyle"
                                AnalyticsHelper.logItemClick(section, displayStyleName, rank)
                                android.util.Log.d(TAG, "🔍 네이버 검색 클릭: $displayStyleName (URL: $naverImageUrl)")
                            }
                        )
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayStyleName, // ✅ v28: 성별 접두사 포함된 이름 표시
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResultColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    // 검색 아이콘
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.result_search_desc),
                        tint = ResultColors.SearchIconBg,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 추천 이유
            Text(
                text = style.reason,
                fontSize = 14.sp,
                color = ResultColors.TextSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(start = 56.dp) // 순위 배지 너비만큼 들여쓰기
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ v20: 좋아요/싫어요 버튼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 좋아요 버튼
                FeedbackButton(
                    icon = Icons.Default.ThumbUp,
                    text = stringResource(R.string.result_feedback_like),
                    isSelected = currentFeedback == "like",
                    selectedColor = ResultColors.LikeColor,
                    onClick = {
                        currentFeedback = "like"
                        AnalyticsHelper.logFeedback(style.name, "like")
                        coroutineScope.launch {
                            // ✅ v25: 백엔드 API 형식에 맞춰 피드백 제출
                            if (analysisId != null) {
                                submitFeedback(
                                    context = context,
                                    analysisId = analysisId,
                                    styleIndex = styleIndex + 1,  // ✅ 1-based index로 변환 (0→1, 1→2, 2→3)
                                    feedback = "like"
                                )
                            } else {
                                android.util.Log.e(TAG, "❌ analysis_id가 없어 피드백을 제출할 수 없습니다")
                                Toast.makeText(context, "분석 ID가 없어 피드백을 저장할 수 없습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                // 싫어요 버튼
                FeedbackButton(
                    icon = Icons.Default.ThumbDown,
                    text = stringResource(R.string.result_feedback_dislike),
                    isSelected = currentFeedback == "dislike",
                    selectedColor = ResultColors.DislikeColor,
                    onClick = {
                        currentFeedback = "dislike"
                        AnalyticsHelper.logFeedback(style.name, "dislike")
                        coroutineScope.launch {
                            // ✅ v25: 백엔드 API 형식에 맞춰 피드백 제출
                            if (analysisId != null) {
                                submitFeedback(
                                    context = context,
                                    analysisId = analysisId,
                                    styleIndex = styleIndex + 1,  // ✅ 1-based index로 변환 (0→1, 1→2, 2→3)
                                    feedback = "dislike"
                                )
                            } else {
                                android.util.Log.e(TAG, "❌ analysis_id가 없어 피드백을 제출할 수 없습니다")
                                Toast.makeText(context, "분석 ID가 없어 피드백을 저장할 수 없습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 매칭 점수 - ✅ v35: score가 null이면 숨김 (트렌드 스타일)
            if (style.score != null) {
                MatchingScore(
                    score = style.score,
                    modifier = Modifier.padding(start = 56.dp)
                )
            }

            // ✅ v30: 헤어스타일 합성 버튼
            onSynthesizeClick?.let { callback ->
                Spacer(modifier = Modifier.height(12.dp))

                val isExhausted = usageState.remaining <= 0 && !usageState.isApiError
                val badgeColor = when {
                    usageState.remaining >= 2 -> ResultColors.TextTertiary
                    usageState.remaining == 1 -> Color(0xFFFFA726)
                    else -> Color(0xFFE53935)
                }

                Column(modifier = Modifier.padding(start = 56.dp)) {
                    Text(
                        text = "${usageState.remaining}/${usageState.dailyLimit}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = badgeColor,
                        modifier = Modifier.align(Alignment.End)
                    )
                    Button(
                        onClick = {
                            if (isExhausted) {
                                coroutineScope.launch {
                                    snackbarHostState?.showSnackbar(
                                        "오늘의 무료 합성 횟수(3회)를 모두 사용했습니다. 내일 다시 이용해주세요!"
                                    )
                                }
                            } else {
                                callback(style.name)
                            }
                        },
                        enabled = !isExhausted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ResultColors.Primary,
                            disabledContainerColor = ResultColors.Primary.copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.synthesis_try_button),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ✅ v21: 미용실 찾기 버튼 - 각 카드에서 제거 (하단 통합 버튼으로 이동)
        }
    }
}

// ✅ v20: 피드백 버튼 컴포넌트
@Composable
private fun FeedbackButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) selectedColor.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (isSelected) selectedColor else ResultColors.TextSecondary
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) selectedColor else ResultColors.TextTertiary.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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

@Composable
private fun RankBadge(rank: Int, isTrend: Boolean = false) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isTrend) Color(0xFFFF6B35) else ResultColors.Primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isTrend) "\uD83D\uDD25" else "$rank",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun MatchingScore(score: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { score.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ResultColors.Primary,
            trackColor = ResultColors.ProgressTrack
        )

        Text(
            text = context.getString(R.string.result_matching_percent, (score * 100).toInt()),
            fontSize = 12.sp,
            color = ResultColors.Primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// 일일 무료 합성 횟수 배너
@Composable
private fun SynthesisUsageBanner(usageState: SynthesisUsageState) {
    val remaining = usageState.remaining
    val limit = usageState.dailyLimit

    val (bgColor, textColor, icon) = when {
        usageState.isApiError -> Triple(
            Color(0xFFF5F5F5),
            ResultColors.TextSecondary,
            "~"
        )
        remaining == 0 -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFE53935),
            "!"
        )
        remaining == 1 -> Triple(
            Color(0xFFFFF3E0),
            Color(0xFFFFA726),
            ""
        )
        else -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF43A047),
            ""
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (remaining == 0 && !usageState.isApiError)
                "오늘의 무료 합성 횟수를 모두 사용했어요"
            else
                "오늘 남은 무료 합성",
            fontSize = 13.sp,
            color = textColor,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = if (usageState.isApiError) "$icon $remaining/$limit" else "$remaining/$limit",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// ✅ v21: 미용실 찾기 버튼 컴포넌트
@Composable
private fun FindSalonButton(
    @Suppress("UNUSED_PARAMETER") styleName: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFEE500), // 카카오 옐로우
            contentColor = Color.Black
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "주변 미용실 찾기",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BackToHomeButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ResultColors.Primary
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = stringResource(R.string.result_home_button),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ✅ v33: 염색색 추천 섹션
@Composable
private fun HairColorRecommendationSection(
    hairColorState: HairColorUiState,
    usageState: SynthesisUsageState = SynthesisUsageState(),
    snackbarHostState: SnackbarHostState? = null,
    onSynthesizeHairColor: ((String, String) -> Unit)? = null // ✅ v34: 염색색 합성 콜백
) {
    Column {
        Text(
            text = "✨ 퍼스널컬러 염색 추천",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ResultColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ✅ 섹션 조회 이벤트 (퍼스널컬러 섹션)
        LaunchedEffect(hairColorState) {
            if (hairColorState is HairColorUiState.Success) {
                AnalyticsHelper.logSectionView("personal_color")
            }
        }

        when (hairColorState) {
            is HairColorUiState.Loading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ResultColors.SummaryCardBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = ResultColors.Primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            is HairColorUiState.Success -> {
                Text(
                    text = "${hairColorState.personalColor}에 어울리는 염색 컬러",
                    fontSize = 14.sp,
                    color = ResultColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 추천 컬러
                hairColorState.recommended.take(3).forEachIndexed { index, color ->
                    HairColorCard(
                        color = color,
                        rank = index + 1,
                        usageState = usageState,
                        snackbarHostState = snackbarHostState,
                        onSynthesizeClick = onSynthesizeHairColor // ✅ v34: 염색색 합성 콜백 전달
                    )
                    if (index < 2) Spacer(modifier = Modifier.height(8.dp))
                }

                // 피해야 할 컬러 안내
                if (hairColorState.avoid.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "⚠️ 피해야 할 컬러",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ResultColors.DislikeColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = hairColorState.avoid.take(2).joinToString(", ") { it.name },
                        fontSize = 13.sp,
                        color = ResultColors.TextSecondary
                    )
                }
            }
            is HairColorUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Text(
                        text = "염색 추천을 불러올 수 없습니다",
                        fontSize = 14.sp,
                        color = ResultColors.TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            HairColorUiState.Idle -> {
                // 아무것도 표시하지 않음
            }
        }
    }
}

// ✅ v33: 염색 컬러 카드
@Composable
private fun HairColorCard(
    color: HairColorItem,
    rank: Int,
    usageState: SynthesisUsageState = SynthesisUsageState(),
    snackbarHostState: SnackbarHostState? = null,
    onSynthesizeClick: ((String, String) -> Unit)? = null // ✅ v34: 염색색 합성 콜백
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ResultColors.SummaryCardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 컬러 미리보기
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(color.hex))
                            } catch (e: Exception) {
                                Color.Gray
                            }
                        )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$rank",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .background(
                                    ResultColors.Primary,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = color.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ResultColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = color.description,
                        fontSize = 13.sp,
                        color = ResultColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                    if (color.level.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "밝기: ${color.level}",
                            fontSize = 11.sp,
                            color = ResultColors.TextTertiary
                        )
                    }
                }
            }

            // ✅ v34: 염색색 합성 버튼
            onSynthesizeClick?.let { callback ->
                Spacer(modifier = Modifier.height(12.dp))

                val isExhausted = usageState.remaining <= 0 && !usageState.isApiError
                val badgeColor = when {
                    usageState.remaining >= 2 -> ResultColors.TextTertiary
                    usageState.remaining == 1 -> Color(0xFFFFA726)
                    else -> Color(0xFFE53935)
                }
                val coroutineScope = rememberCoroutineScope()

                Column {
                    Text(
                        text = "${usageState.remaining}/${usageState.dailyLimit}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = badgeColor,
                        modifier = Modifier.align(Alignment.End)
                    )
                    Button(
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
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ResultColors.Primary.copy(alpha = 0.9f),
                            disabledContainerColor = ResultColors.Primary.copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = "이 색상 적용해보기",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
