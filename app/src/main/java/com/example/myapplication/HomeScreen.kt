package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.network.AuthUser
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierCard
import com.example.myapplication.ui.theme.AtelierDividerLine
import com.example.myapplication.ui.theme.AtelierPrimaryButton
import com.example.myapplication.ui.theme.AtelierSerif
import com.example.myapplication.ui.theme.atelierLabel
import com.example.myapplication.ui.theme.atelierSerif
import com.example.myapplication.viewmodel.AuthUiState

// 카카오 브랜드 가이드 색상
private val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabel = Color(0xFF191919)

/** 홈 "최근 분석" 행 표시용 UI 모델 (데이터 미연동 시 섹션 숨김) */
data class RecentAnalysisUi(
    val title: String,   // 예: "계란형 · 여름 쿨톤"
    val meta: String     // 예: "7월 12일 · 스타일 3건"
)

@Composable
fun HomeScreen(
    onStartClick: () -> Unit,
    onFindSalonClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    usageRemaining: Int? = null,
    recentAnalyses: List<RecentAnalysisUi> = emptyList(),
    onHistoryClick: (() -> Unit)? = null,
    authUiState: AuthUiState = AuthUiState.LoggedOut,
    onKakaoLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onMyResultsClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Atelier.Background)
            .statusBarsPadding()
    ) {
        HomeTopBar(
            onFindSalonClick = onFindSalonClick,
            onHistoryClick = onHistoryClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            HeroSection()

            Spacer(modifier = Modifier.height(28.dp))

            MainCtaCard(onStartClick = onStartClick)

            Spacer(modifier = Modifier.height(14.dp))

            QuickEntryGrid(
                usageRemaining = usageRemaining,
                onFindSalonClick = onFindSalonClick,
                onVirtualStylingClick = onStartClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            AccountSection(
                authUiState = authUiState,
                onKakaoLoginClick = onKakaoLoginClick,
                onLogoutClick = onLogoutClick,
                onMyResultsClick = onMyResultsClick
            )

            if (recentAnalyses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                RecentAnalysisSection(
                    items = recentAnalyses,
                    onHistoryClick = onHistoryClick
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HomeTopBar(
    onFindSalonClick: () -> Unit,
    onHistoryClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 워드마크
        Text(
            text = "HAIRME",
            fontFamily = AtelierSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            letterSpacing = 0.22.em,
            color = Atelier.Ink
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = onFindSalonClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = "근처 미용실 찾기",
                    tint = Atelier.Ink,
                    modifier = Modifier.size(22.dp)
                )
            }
            onHistoryClick?.let {
                IconButton(onClick = it, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "분석 히스토리",
                        tint = Atelier.Ink,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSection() {
    Column {
        Text(
            text = stringResource(R.string.home_main_description),
            style = atelierSerif(size = 30)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.home_sub_description),
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = Atelier.TextSecondary
        )
    }
}

@Composable
private fun MainCtaCard(onStartClick: () -> Unit) {
    AtelierCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI CONSULTATION",
                        style = atelierLabel(size = 11, color = Atelier.BrandVioletSoft, tracking = 0.16f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "얼굴형 분석",
                        style = atelierSerif(size = 22)
                    )
                }
                Icon(
                    imageVector = Icons.Default.FaceRetouchingNatural,
                    contentDescription = null,
                    tint = Atelier.BrandViolet,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            AtelierDividerLine()
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "약 5초 · 사진 한 장이면 충분해요",
                fontSize = 13.sp,
                color = Atelier.TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            AtelierPrimaryButton(
                text = stringResource(R.string.home_start_button),
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp,
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

/**
 * 계정 섹션
 * - 비로그인: 카카오 로그인 버튼 (로그인 없이도 기존 기능은 모두 사용 가능)
 * - 로그인: 닉네임 + 크레딧 + 로그아웃
 * - 자동 로그인 확인 중(Initializing): 깜빡임 방지를 위해 표시하지 않음
 */
@Composable
private fun AccountSection(
    authUiState: AuthUiState,
    onKakaoLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onMyResultsClick: () -> Unit
) {
    when (authUiState) {
        is AuthUiState.LoggedIn -> LoggedInCard(
            user = authUiState.user,
            onLogoutClick = onLogoutClick,
            onMyResultsClick = onMyResultsClick
        )
        AuthUiState.LoggedOut -> KakaoLoginButton(
            onClick = onKakaoLoginClick,
            isLoading = false
        )
        AuthUiState.LoggingIn -> KakaoLoginButton(
            onClick = onKakaoLoginClick,
            isLoading = true
        )
        AuthUiState.Initializing -> Unit
    }
}

@Composable
private fun LoggedInCard(
    user: AuthUser,
    onLogoutClick: () -> Unit,
    onMyResultsClick: () -> Unit
) {
    AtelierCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${user.nickname ?: "회원"}님",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Atelier.Ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "크레딧 ${user.credits}개",
                        fontSize = 12.sp,
                        color = Atelier.TextTertiary
                    )
                }
                Text(
                    text = "로그아웃",
                    fontSize = 12.sp,
                    color = Atelier.TextTertiary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onLogoutClick)
                )
            }

            AtelierDividerLine(modifier = Modifier.padding(horizontal = 18.dp))

            // 합성 결과 히스토리 진입 (크레딧 합성 결과는 서버에 자동 보관됨)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMyResultsClick)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = Atelier.BrandViolet,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "내가 만든 스타일",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Atelier.Ink,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Atelier.Chevron,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 카카오 브랜드 가이드 색상(#FEE500) 로그인 버튼 */
@Composable
private fun KakaoLoginButton(
    onClick: () -> Unit,
    isLoading: Boolean
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KakaoYellow,
            contentColor = KakaoLabel,
            disabledContainerColor = KakaoYellow.copy(alpha = 0.6f),
            disabledContentColor = KakaoLabel.copy(alpha = 0.6f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = KakaoLabel,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Filled.ChatBubble,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "카카오로 시작하기",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QuickEntryGrid(
    usageRemaining: Int?,
    onFindSalonClick: () -> Unit,
    onVirtualStylingClick: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        QuickEntryCard(
            icon = Icons.Outlined.LocationOn,
            title = "주변 미용실",
            description = "내 위치 기준 추천",
            onClick = onFindSalonClick,
            modifier = Modifier.weight(1f)
        )
        QuickEntryCard(
            icon = Icons.Default.AutoAwesome,
            title = "가상 스타일링",
            description = "오늘 무료 ${usageRemaining ?: 3}회 남음",
            onClick = onVirtualStylingClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickEntryCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AtelierCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Atelier.BrandViolet,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Atelier.Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = Atelier.TextTertiary
            )
        }
    }
}

@Composable
private fun RecentAnalysisSection(
    items: List<RecentAnalysisUi>,
    onHistoryClick: (() -> Unit)?
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "최근 분석",
                style = atelierSerif(size = 17)
            )
            Spacer(modifier = Modifier.weight(1f))
            onHistoryClick?.let {
                Text(
                    text = "전체 보기",
                    fontSize = 12.sp,
                    color = Atelier.TextTertiary,
                    modifier = Modifier.clickable(onClick = it)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        items.forEachIndexed { index, item ->
            if (index == 0) AtelierDividerLine()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 44dp 썸네일 자리 (사진 데이터 미연동 시 아이보리 박스)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Atelier.Divider, Atelier.ThumbShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Atelier.Ink
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.meta,
                        fontSize = 12.sp,
                        color = Atelier.TextTertiary
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Atelier.Chevron,
                    modifier = Modifier.size(20.dp)
                )
            }
            AtelierDividerLine()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        onStartClick = {},
        usageRemaining = 3,
        recentAnalyses = listOf(
            RecentAnalysisUi(title = "계란형 · 여름 쿨톤", meta = "7월 12일 · 스타일 3건")
        )
    )
}
