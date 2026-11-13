package com.example.myapplication.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierCard
import com.example.myapplication.ui.theme.AtelierDividerLine
import com.example.myapplication.ui.theme.AtelierPrimaryButton
import com.example.myapplication.ui.theme.AtelierTopBar
import com.example.myapplication.ui.theme.atelierLabel
import com.example.myapplication.ui.theme.atelierSerif

/**
 * 홈의 "분석 시작" 다음 화면: 헤어 분석 / 체형별 아이템 중 하나를 고른다.
 *
 * 사용자 대부분이 헤어 때문에 들어오므로 헤어를 크게 위에 두고,
 * 체형은 작게 아래에 둔다. 두 기능은 필요한 사진(얼굴 / 전신)이 달라
 * 사진 선택 전에 갈라야 한다.
 */
@Composable
fun AnalysisChoiceScreen(
    onBackClick: () -> Unit,
    onHairClick: () -> Unit,
    onBodyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Atelier.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AtelierTopBar(
            title = "분석 선택",
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
            Text(text = "무엇을 분석할까요?", style = atelierSerif(size = 26))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "사진 한 장으로 어울리는 스타일을 찾아드려요",
                fontSize = 14.sp,
                color = Atelier.TextSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            HairChoiceCard(onClick = onHairClick)

            Spacer(modifier = Modifier.height(14.dp))

            BodyChoiceCard(onClick = onBodyClick)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** 헤어 분석: 주 선택지 (크게, 검은 버튼) */
@Composable
private fun HairChoiceCard(onClick: () -> Unit) {
    AtelierCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HAIR",
                        style = atelierLabel(size = 11, color = Atelier.BrandVioletSoft, tracking = 0.16f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "헤어스타일 추천", style = atelierSerif(size = 22))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "얼굴형 · 퍼스널컬러 분석 + 가상 스타일링",
                        fontSize = 13.sp,
                        color = Atelier.TextSecondary
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
                text = "얼굴 사진 1장 · 약 5초",
                fontSize = 13.sp,
                color = Atelier.TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            AtelierPrimaryButton(
                text = "헤어 분석하기",
                onClick = onClick,
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

/** 체형별 아이템: 보조 선택지 (작게, 한 줄 카드) */
@Composable
private fun BodyChoiceCard(onClick: () -> Unit) {
    AtelierCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Checkroom,
                contentDescription = null,
                tint = Atelier.BrandViolet,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "체형별 아이템 추천",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Atelier.Ink
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ChoiceNewBadge()
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "전신 사진 1장 · 어울리는 핏 찾기 · 무료",
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
    }
}

/** 새 기능 표시 (홈에 있던 배지와 같은 모양) */
@Composable
private fun ChoiceNewBadge() {
    Text(
        text = "NEW",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Atelier.BrandViolet,
        modifier = Modifier
            .border(1.dp, Atelier.BrandVioletSoft, Atelier.PillShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun AnalysisChoiceScreenPreview() {
    AnalysisChoiceScreen(onBackClick = {}, onHairClick = {}, onBodyClick = {})
}
