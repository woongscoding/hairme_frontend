package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.myapplication.network.MyResultItem
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierPrimaryButton
import com.example.myapplication.ui.theme.AtelierSecondaryButton
import com.example.myapplication.ui.theme.AtelierTopBar
import com.example.myapplication.ui.theme.atelierSerif
import com.example.myapplication.viewmodel.AuthUiState
import com.example.myapplication.viewmodel.MyResultsUiState

// 카카오 브랜드 가이드 색상 (HomeScreen과 동일)
private val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabel = Color(0xFF191919)

/**
 * "내가 만든 스타일" — 회원 합성 결과 히스토리 화면
 *
 * - 2열 사진 그리드, 최신순, continuation_token 무한 스크롤
 * - 로딩 / 빈 목록 / 에러(재시도) 상태 처리
 * - 비로그인(또는 401) 시 카카오 로그인 유도
 * - 항목 탭 → 기존 FullscreenImageViewer(ResultScreen.kt) 재사용
 *
 * ⚠️ presigned URL은 24시간 만료 — 이미지 캐시 키는 url이 아니라 응답의 key를 사용
 */
@Composable
fun MyResultsScreen(
    uiState: MyResultsUiState,
    authUiState: AuthUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onLoadMore: () -> Unit,
    onRequestLogin: () -> Unit,
    onGoSynthesizeClick: () -> Unit
) {
    // 전체화면 보기 대상 (null이면 닫힘)
    var selectedItem by remember { mutableStateOf<MyResultItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Atelier.Background)
            .statusBarsPadding()
    ) {
        AtelierTopBar(
            title = "내가 만든 스타일",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationContentDescription = "뒤로 가기",
            onNavigationClick = onBackClick
        )

        when (authUiState) {
            AuthUiState.Initializing -> LoadingState()
            AuthUiState.LoggedOut -> LoginPromptState(onRequestLogin = onRequestLogin, isLoggingIn = false)
            AuthUiState.LoggingIn -> LoginPromptState(onRequestLogin = onRequestLogin, isLoggingIn = true)
            is AuthUiState.LoggedIn -> when (uiState) {
                MyResultsUiState.Loading -> LoadingState()
                // 로그인 상태인데 서버가 401을 준 엣지 케이스 (토큰 무효화 등)
                MyResultsUiState.RequiresLogin -> LoginPromptState(onRequestLogin = onRequestLogin, isLoggingIn = false)
                MyResultsUiState.Empty -> EmptyState(onGoSynthesizeClick = onGoSynthesizeClick)
                is MyResultsUiState.Error -> ErrorState(message = uiState.message, onRetryClick = onRetryClick)
                is MyResultsUiState.Content -> ResultsGrid(
                    state = uiState,
                    onLoadMore = onLoadMore,
                    onItemClick = { selectedItem = it }
                )
            }
        }
    }

    selectedItem?.let { item ->
        FullscreenImageViewer(
            imageUrl = item.url,
            styleName = item.hairstyle ?: formatResultDate(item.createdAt),
            onDismiss = { selectedItem = null },
            cacheKey = item.key
        )
    }
}

// ========== 목록 ==========

@Composable
private fun ResultsGrid(
    state: MyResultsUiState.Content,
    onLoadMore: () -> Unit,
    onItemClick: (MyResultItem) -> Unit
) {
    val gridState = rememberLazyGridState()

    // 하단 6개 이내로 스크롤이 내려오면 다음 페이지 로드 (가드는 ViewModel이 담당)
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= gridState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore, state.items.size) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(state.items, key = { it.key }) { item ->
            ResultGridItem(item = item, onClick = { onItemClick(item) })
        }

        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Atelier.BrandViolet,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultGridItem(
    item: MyResultItem,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.url)
                // presigned URL은 호출마다 쿼리스트링이 달라짐 → S3 key로 캐시해야 히트됨
                .memoryCacheKey(item.key)
                .diskCacheKey(item.key)
                .crossfade(true)
                .build(),
            contentDescription = item.hairstyle ?: "합성 결과",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(Atelier.CardShape),
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
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Atelier.Divider),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = Atelier.Chevron,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        item.hairstyle?.let { name ->
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Atelier.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = formatResultDate(item.createdAt),
            fontSize = 11.sp,
            color = Atelier.TextTertiary
        )
    }
}

// ========== 로딩 / 빈 목록 / 에러 / 로그인 유도 ==========

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = Atelier.BrandViolet,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun EmptyState(onGoSynthesizeClick: () -> Unit) {
    CenteredMessage(
        icon = { StateIcon() },
        title = "아직 만든 스타일이 없어요",
        description = "얼굴형 분석 후 어울리는 스타일을\n내 사진에 합성해보세요"
    ) {
        AtelierPrimaryButton(
            text = "스타일 만들러 가기",
            onClick = onGoSynthesizeClick
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetryClick: () -> Unit) {
    CenteredMessage(
        icon = { StateIcon() },
        title = "결과를 불러오지 못했어요",
        description = message
    ) {
        AtelierSecondaryButton(
            text = "다시 시도",
            onClick = onRetryClick
        )
    }
}

@Composable
private fun LoginPromptState(onRequestLogin: () -> Unit, isLoggingIn: Boolean) {
    CenteredMessage(
        icon = { StateIcon() },
        title = "로그인이 필요해요",
        description = "로그인하고 크레딧으로 합성하면\n결과가 자동으로 보관돼요"
    ) {
        // 카카오 브랜드 가이드 색상 버튼 (HomeScreen과 동일 스타일)
        Button(
            onClick = onRequestLogin,
            enabled = !isLoggingIn,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KakaoYellow,
                contentColor = KakaoLabel,
                disabledContainerColor = KakaoYellow.copy(alpha = 0.6f),
                disabledContentColor = KakaoLabel.copy(alpha = 0.6f)
            )
        ) {
            if (isLoggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = KakaoLabel,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.ChatBubble,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "카카오로 시작하기",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StateIcon() {
    Icon(
        imageVector = Icons.Outlined.PhotoLibrary,
        contentDescription = null,
        tint = Atelier.Chevron,
        modifier = Modifier.size(40.dp)
    )
}

@Composable
private fun CenteredMessage(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    action: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = atelierSerif(size = 19)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Atelier.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        action()
        // 하단 앱바 높이만큼 시각적 중앙 보정
        Spacer(modifier = Modifier.height(56.dp))
    }
}

/**
 * "2026-08-11T12:34:56" → "2026.08.11"
 * 서버 포맷이 달라져도 크래시 없이 앞 10자만 사용
 */
private fun formatResultDate(createdAt: String?): String {
    return createdAt?.take(10)?.replace('-', '.') ?: ""
}
