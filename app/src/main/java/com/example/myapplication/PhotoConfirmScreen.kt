package com.example.myapplication

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.ui.theme.MyApplicationTheme

// 색상 상수
private object PhotoConfirmColors {
    val Background = Color.White
    val Primary = Color(0xFF5B4FFF)
    val TextPrimary = Color.Black
    val TextSecondary = Color(0xFF666666)
    val ImagePlaceholder = Color(0xFFF0F0F0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoConfirmScreen(
    imageUri: Uri?,
    isLoading: Boolean = false,
    selectedGender: String = "male", // "male" or "female"
    onGenderSelected: (String) -> Unit = {},
    onBackClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onAnalyzeClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            PhotoConfirmTopBar(onBackClick = onBackClick)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PhotoConfirmColors.Background)
                .padding(padding)
                .verticalScroll(rememberScrollState()) // ✅ 스크롤 추가
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 헤더 섹션
            HeaderSection()

            Spacer(modifier = Modifier.height(24.dp))

            // 이미지 프리뷰 (높이 제한 추가)
            ImagePreviewCard(imageUri = imageUri)

            Spacer(modifier = Modifier.height(24.dp))

            // 성별 선택 섹션 추가
            GenderSelectionSection(
                selectedGender = selectedGender,
                onGenderSelected = onGenderSelected
            )

            Spacer(modifier = Modifier.height(24.dp)) // weight 대신 고정 높이

            // 액션 버튼들
            ActionButtons(
                isLoading = isLoading,
                isImageValid = imageUri != null,
                onAnalyzeClick = onAnalyzeClick,
                onRetryClick = onRetryClick
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoConfirmTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.photo_confirm_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PhotoConfirmColors.Background,
            titleContentColor = PhotoConfirmColors.TextPrimary
        )
    )
}

@Composable
private fun HeaderSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.photo_confirm_header),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = PhotoConfirmColors.TextPrimary
        )

        Text(
            text = stringResource(R.string.photo_confirm_subheader),
            fontSize = 14.sp,
            color = PhotoConfirmColors.TextSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun GenderSelectionSection(
    selectedGender: String,
    onGenderSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "성별을 선택해주세요",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = PhotoConfirmColors.TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 남성 버튼
            GenderButton(
                text = "남성용",
                isSelected = selectedGender == "male",
                onClick = { onGenderSelected("male") },
                modifier = Modifier.weight(1f)
            )

            // 여성 버튼
            GenderButton(
                text = "여성용",
                isSelected = selectedGender == "female",
                onClick = { onGenderSelected("female") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GenderButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) PhotoConfirmColors.Primary else Color(0xFFF0F0F0),
            contentColor = if (isSelected) Color.White else PhotoConfirmColors.TextSecondary
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = if (isSelected) ButtonDefaults.buttonElevation(4.dp) else ButtonDefaults.buttonElevation(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ImagePreviewCard(imageUri: Uri?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp, max = 500.dp), // ✅ 최소/최대 높이 설정
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = PhotoConfirmColors.ImagePlaceholder
        )
    ) {
        when {
            imageUri != null -> {
                AsyncImage(
                    model = imageUri,
                    contentDescription = stringResource(R.string.photo_confirm_image_desc),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp, max = 500.dp), // ✅ 이미지 높이 제한
                    contentScale = ContentScale.Fit, // ✅ 이미지 비율 유지
                    alignment = Alignment.Center // ✅ 중앙 정렬
                )
            }
            else -> {
                ImagePlaceholder()
            }
        }
    }
}

@Composable
private fun ImagePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(PhotoConfirmColors.ImagePlaceholder),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.photo_confirm_image_error),
            color = Color.Gray
        )
    }
}

@Composable
private fun ActionButtons(
    isLoading: Boolean,
    isImageValid: Boolean,
    onAnalyzeClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnalyzeButton(
            isLoading = isLoading,
            enabled = isImageValid,
            onClick = onAnalyzeClick
        )

        RetryButton(onClick = onRetryClick)
    }
}

@Composable
private fun AnalyzeButton(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PhotoConfirmColors.Primary
        ),
        shape = RoundedCornerShape(16.dp),
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            LoadingContent()
        } else {
            Text(
                text = stringResource(R.string.photo_confirm_analyze_button),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color.White,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.photo_confirm_analyzing),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PhotoConfirmColors.Primary
        )
    ) {
        Text(
            text = stringResource(R.string.photo_confirm_retry_button),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoConfirmScreenPreview() {
    MyApplicationTheme {
        PhotoConfirmScreen(
            imageUri = null,
            isLoading = false
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoConfirmScreenLoadingPreview() {
    MyApplicationTheme {
        PhotoConfirmScreen(
            imageUri = null,
            isLoading = true
        )
    }
}