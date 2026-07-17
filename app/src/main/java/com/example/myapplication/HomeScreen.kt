package com.example.myapplication.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

// 브랜드 컬러
private val PrimaryPurple = Color(0xFF5B4FFF)
private val LightPurple = Color(0xFFE8E6FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartClick: () -> Unit,
    onFindSalonClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { /* 타이틀 없음 */ },
                actions = {
                    // 우측 상단 미용실 찾기 버튼
                    IconButton(onClick = onFindSalonClick) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "근처 미용실 찾기",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 로고 섹션
                LogoSection()

                Spacer(modifier = Modifier.height(48.dp))

                // 설명 섹션
                DescriptionSection()

                Spacer(modifier = Modifier.weight(1f))

                // 시작하기 버튼
                StartButton(onClick = onStartClick)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LogoSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 로고 아이콘 (실제 이미지가 없으므로 플레이스홀더)
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(24.dp),
            color = LightPurple
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✂️",
                    fontSize = 60.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 앱 이름
        Text(
            text = stringResource(R.string.home_app_title),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryPurple
        )
    }
}

@Composable
private fun DescriptionSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.home_main_description),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.home_sub_description),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StartButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryPurple,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = stringResource(R.string.home_start_button),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(
            onStartClick = {}
        )
    }
}