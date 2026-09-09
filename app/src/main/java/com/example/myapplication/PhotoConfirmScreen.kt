package com.example.myapplication

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierCard
import com.example.myapplication.ui.theme.AtelierPrimaryButton
import com.example.myapplication.ui.theme.AtelierSegmentedControl
import com.example.myapplication.ui.theme.AtelierTopBar
import com.example.myapplication.ui.theme.AtelierUnderlineButton
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.atelierSerif

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Atelier.Background)
            .statusBarsPadding()
    ) {
        AtelierTopBar(
            title = stringResource(R.string.photo_confirm_title),
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationContentDescription = stringResource(R.string.back),
            onNavigationClick = onBackClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 이미지 프리뷰 (기존 min/max 높이 로직 유지)
            ImagePreviewCard(imageUri = imageUri)

            Spacer(modifier = Modifier.height(24.dp))

            // 성별 선택 → 세그먼트 컨트롤
            GenderSelectionSection(
                selectedGender = selectedGender,
                onGenderSelected = onGenderSelected
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 액션 버튼들
            ActionButtons(
                isLoading = isLoading,
                isImageValid = imageUri != null,
                onAnalyzeClick = onAnalyzeClick,
                onRetryClick = onRetryClick
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
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
            text = "어떤 스타일을 추천할까요?",
            style = atelierSerif(size = 16),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        AtelierSegmentedControl(
            options = listOf(
                "male" to "남성 스타일",
                "female" to "여성 스타일"
            ),
            selectedValue = selectedGender,
            onSelect = onGenderSelected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ImagePreviewCard(imageUri: Uri?) {
    AtelierCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp, max = 500.dp)
    ) {
        when {
            imageUri != null -> {
                AsyncImage(
                    model = imageUri,
                    contentDescription = stringResource(R.string.photo_confirm_image_desc),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp, max = 500.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
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
            .background(Atelier.Divider),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.photo_confirm_image_error),
            fontSize = 14.sp,
            color = Atelier.TextTertiary,
            textAlign = TextAlign.Center
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnalyzeButton(
            isLoading = isLoading,
            enabled = isImageValid,
            onClick = onAnalyzeClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        AtelierUnderlineButton(
            text = stringResource(R.string.photo_confirm_retry_button),
            onClick = onRetryClick
        )
    }
}

@Composable
private fun AnalyzeButton(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (isLoading) {
        // 로딩 중: pill 내부 CircularProgressIndicator (기존 로직 유지)
        AtelierPrimaryButton(
            text = stringResource(R.string.photo_confirm_analyzing),
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            height = 56.dp,
            leadingContent = {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        )
    } else {
        AtelierPrimaryButton(
            text = stringResource(R.string.photo_confirm_analyze_button),
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            height = 56.dp,
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Atelier.BrandVioletSoft,
                    modifier = Modifier.size(18.dp)
                )
            }
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
