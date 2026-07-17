package com.example.myapplication

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.net.URLEncoder

/**
 * ResultScreen.kt - 네이버 이미지 검색 연동 버전
 *
 * 주요 개선사항:
 * 1. 색상을 ResultColors 객체로 중앙 관리
 * 2. 컴포저블을 작은 단위로 분리하여 재사용성 향상
 * 3. 반복되는 코드 제거 (AnalysisInfoItem)
 * 4. 가독성 향상을 위한 구조 개선
 * 5. ✅ 헤어스타일 이름 클릭 시 네이버 이미지 검색 연동
 */

// 색상 상수
private object ResultColors {
    val Background = Color.White
    val Primary = Color(0xFF5B4FFF)
    val SummaryCardBg = Color(0xFFF8F7FF)
    val RecommendationCardBg = Color(0xFFE8E3FF)
    val SearchIconBg = Color(0xFF03C75A) // 네이버 그린
    val TextPrimary = Color.Black
    val TextSecondary = Color(0xFF666666)
    val TextTertiary = Color(0xFF999999)
    val ProgressTrack = Color(0xFFD0C9FF)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    faceShape: String,
    skinTone: String,
    recommendedStyles: List<HairstyleRecommendation>,
    onBackClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            ResultTopBar(onBackClick = onBackClick)
        }
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
                skinTone = skinTone
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 추천 헤어스타일 섹션
            RecommendationSection(styles = recommendedStyles)

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
        title = { Text("분석 결과") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "홈으로"
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
    skinTone: String
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
            Text(
                text = "📊 분석 결과",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ResultColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalysisInfoItem(
                    label = "얼굴형",
                    value = faceShape
                )

                AnalysisInfoItem(
                    label = "피부톤",
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
private fun RecommendationSection(styles: List<HairstyleRecommendation>) {
    Column {
        Text(
            text = "✨ 추천 헤어스타일",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ResultColors.TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 안내 문구 추가
        Text(
            text = "💡 스타일 이름을 클릭하면 네이버에서 이미지를 볼 수 있어요",
            fontSize = 13.sp,
            color = ResultColors.TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        styles.forEachIndexed { index, style ->
            RecommendationCard(
                rank = index + 1,
                style = style
            )

            if (index < styles.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    rank: Int,
    style: HairstyleRecommendation
) {
    val context = LocalContext.current
    
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
                // 순위 배지
                RankBadge(rank = rank)

                Spacer(modifier = Modifier.width(16.dp))

                // 스타일 이름 (클릭 가능)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            // 네이버 이미지 검색 URL 생성
                            val encodedQuery = URLEncoder.encode(style.name, "UTF-8")
                            val naverImageUrl = "https://search.naver.com/search.naver?where=image&query=$encodedQuery"
                            
                            // 브라우저로 열기
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(naverImageUrl))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = style.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResultColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 검색 아이콘
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "네이버 이미지 검색",
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

            Spacer(modifier = Modifier.height(8.dp))

            // 매칭 점수
            MatchingScore(
                score = style.score,
                modifier = Modifier.padding(start = 56.dp)
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ResultColors.Primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$rank",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun MatchingScore(score: Double, modifier: Modifier = Modifier) {
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
            text = "${(score * 100).toInt()}% 매칭",
            fontSize = 12.sp,
            color = ResultColors.Primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
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
            text = "홈으로 돌아가기",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ResultScreenPreview() {
    MyApplicationTheme {
        ResultScreen(
            faceShape = "계란형",
            skinTone = "쿨톤",
            recommendedStyles = listOf(
                HairstyleRecommendation(
                    name = "중단발 레이어드컷",
                    score = 0.92,
                    reason = "얼굴형에 가장 잘 어울리는 스타일"
                ),
                HairstyleRecommendation(
                    name = "웨이브 펌",
                    score = 0.85,
                    reason = "쿨톤 피부에 잘 어울림"
                ),
                HairstyleRecommendation(
                    name = "시스루뱅",
                    score = 0.78,
                    reason = "이마가 넓은 계란형에 추천"
                )
            )
        )
    }
}
