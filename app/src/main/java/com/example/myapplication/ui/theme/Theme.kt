package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Atelier — 에디토리얼 아이보리 테마.
 * 다크 모드/다이나믹 컬러 없이 단일 라이트 스킴을 사용한다 (디자인 확정값).
 */
private val AtelierColorScheme = lightColorScheme(
    primary = Atelier.Ink,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = Atelier.BrandViolet,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = Atelier.BrandVioletSoft,
    background = Atelier.Background,
    onBackground = Atelier.Ink,
    surface = Atelier.Surface,
    onSurface = Atelier.Ink,
    surfaceVariant = Atelier.Divider,
    onSurfaceVariant = Atelier.TextSecondary,
    outline = Atelier.Border,
    outlineVariant = Atelier.Divider
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 시그니처 호환용 — Atelier 디자인은 단일 라이트 스킴만 사용
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AtelierColorScheme,
        typography = Typography,
        content = content
    )
}
