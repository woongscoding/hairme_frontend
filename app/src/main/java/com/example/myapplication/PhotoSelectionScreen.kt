package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierCard
import com.example.myapplication.ui.theme.AtelierTopBar
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.atelierSerif

@Composable
fun PhotoSelectionScreen(
    onBackClick: () -> Unit,
    onCameraClick: () -> Unit = {},
    onGalleryClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Atelier.Background)
            .statusBarsPadding()
    ) {
        AtelierTopBar(
            title = stringResource(R.string.photo_selection_title),
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationContentDescription = stringResource(R.string.back),
            onNavigationClick = onBackClick
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // 헤더
            Text(
                text = stringResource(R.string.photo_selection_header),
                style = atelierSerif(size = 26)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.photo_selection_subheader),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Atelier.TextSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 카메라 선택 카드
            SelectionCard(
                icon = Icons.Default.PhotoCamera,
                title = stringResource(R.string.photo_selection_camera_title),
                description = stringResource(R.string.photo_selection_camera_desc),
                onClick = onCameraClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 갤러리 선택 카드
            SelectionCard(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.photo_selection_gallery_title),
                description = stringResource(R.string.photo_selection_gallery_desc),
                onClick = onGalleryClick
            )
        }

        // 하단 고정 팁: 상단 헤어라인 보더 위 lightbulb + 안내문
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Atelier.Border)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Atelier.BrandVioletSoft,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.photo_selection_tip),
                    fontSize = 13.sp,
                    color = Atelier.TextSecondary
                )
            }
        }
    }
}

@Composable
fun SelectionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    AtelierCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 원형 아이콘 웰 52dp
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(1.dp, Atelier.Border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Atelier.BrandViolet,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = atelierSerif(size = 17)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Atelier.TextSecondary
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

@Preview(showBackground = true)
@Composable
fun PhotoSelectionScreenPreview() {
    MyApplicationTheme {
        PhotoSelectionScreen(
            onBackClick = {}
        )
    }
}
