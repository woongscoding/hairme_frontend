package com.example.myapplication.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

/**
 * Atelier 디자인 시스템 - 에디토리얼 아이보리
 *
 * 따뜻한 종이색 배경, 세리프 헤드라인, 얇은 헤어라인 보더, 블랙 pill CTA.
 * 퍼플(#5B4FFF)은 배경이 아니라 작은 포인트로만 사용한다.
 */

// ========== Colors ==========
object Atelier {
    val Background = Color(0xFFFAF7F2)      // 모든 화면 배경 (따뜻한 아이보리)
    val Surface = Color(0xFFFFFFFF)         // 카드 배경
    val Border = Color(0xFFE2DCCF)          // 카드 보더, 구분선 (1dp)
    val Divider = Color(0xFFEEE9DE)         // 카드 내부 구분선
    val Ink = Color(0xFF1C1826)             // 기본 텍스트, 블랙 CTA 배경
    val TextSecondary = Color(0xFF6B665C)   // 본문 보조 텍스트
    val TextTertiary = Color(0xFF8A8578)    // 라벨, 메타 텍스트
    val Chevron = Color(0xFFC9C2B2)         // chevron 등 비활성 아이콘
    val BrandViolet = Color(0xFF5B4FFF)     // 포인트 (아이콘, 매칭 %, 프로그레스)
    val BrandVioletDeep = Color(0xFF4438D6) // 결과 값 텍스트
    val BrandVioletSoft = Color(0xFF9B94FF) // 오버라인 라벨, 보조 포인트
    val TrendAccent = Color(0xFFC2410C)     // TREND 배지 텍스트
    val TrendBorder = Color(0xFFE8C9B4)     // TREND 배지 보더

    val CardShape = RoundedCornerShape(4.dp)
    val ThumbShape = RoundedCornerShape(2.dp)
    val PillShape = RoundedCornerShape(999.dp)
}

// ========== Typography ==========
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// 디스플레이/제목용 세리프 (다운로더블 폰트, 실패 시 시스템 serif 폴백)
val AtelierSerif = FontFamily(
    Font(GoogleFont("Noto Serif KR"), googleFontProvider, weight = FontWeight.SemiBold),
    Font(GoogleFont("Noto Serif KR"), googleFontProvider, weight = FontWeight.Normal)
)

/** 세리프 제목 스타일 헬퍼 */
fun atelierSerif(size: Int, color: Color = Atelier.Ink, lineHeight: Float = 1.35f) = TextStyle(
    fontFamily = AtelierSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    color = color
)

/** 오버라인/메타 라벨: 11-12sp, letter-spacing 0.14em, 대문자 텍스트와 함께 사용 */
fun atelierLabel(size: Int = 11, color: Color = Atelier.TextTertiary, tracking: Float = 0.14f) = TextStyle(
    fontSize = size.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = tracking.em,
    color = color
)

// ========== Shared components ==========

/** Primary CTA: 블랙 pill, 흰 텍스트 */
@Composable
fun AtelierPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = Atelier.PillShape,
        elevation = ButtonDefaults.buttonElevation(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Atelier.Ink,
            contentColor = Color.White,
            disabledContainerColor = Atelier.Ink.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        leadingContent?.let {
            it()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        trailingContent?.let {
            Spacer(modifier = Modifier.width(8.dp))
            it()
        }
    }
}

/** Secondary: 투명 배경 + Ink 보더 pill */
@Composable
fun AtelierSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    borderColor: Color = Atelier.Ink,
    contentColor: Color = Atelier.Ink,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = Atelier.BrandViolet
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = Atelier.PillShape,
        border = BorderStroke(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        )
    ) {
        leadingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = if (enabled) leadingIconTint else leadingIconTint.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Tertiary: 언더라인 텍스트 버튼 */
@Composable
fun AtelierUnderlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = Atelier.TextTertiary,
            textDecoration = TextDecoration.Underline
        )
    }
}

/** 아이콘 버튼(좋아요/싫어요 등): 원형, 헤어라인 보더 */
@Composable
fun AtelierCircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 44.dp
) {
    val tint = if (selected) Atelier.BrandViolet else Atelier.TextTertiary
    val border = if (selected) Atelier.BrandViolet else Atelier.Border
    Box(
        modifier = modifier
            .size(size)
            .border(1.dp, border, CircleShape)
            .background(Atelier.Surface, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 흰 카드: radius 4dp, 헤어라인 보더, 그림자 없음 */
@Composable
fun AtelierCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(Atelier.Surface, Atelier.CardShape)
            .border(1.dp, Atelier.Border, Atelier.CardShape)
            .padding(contentPadding)
    ) {
        content()
    }
}

/** 카드 내부 헤어라인 구분선 */
@Composable
fun AtelierDividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Atelier.Divider)
    )
}

/** 앱바: 좌측 내비 아이콘 + 세리프 17sp 타이틀 + 우측 액션 */
@Composable
fun AtelierTopBar(
    title: String,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: () -> Unit = {},
    actions: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Atelier.Background)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (navigationIcon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNavigationClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = navigationContentDescription,
                    tint = Atelier.Ink,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = atelierSerif(size = 17),
            modifier = Modifier.weight(1f)
        )
        actions?.invoke()
        Spacer(modifier = Modifier.width(4.dp))
    }
}

/** 세그먼트 컨트롤 (pill 컨테이너 + 블랙 pill 선택) */
@Composable
fun AtelierSegmentedControl(
    options: List<Pair<String, String>>, // value to label
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Atelier.Surface, Atelier.PillShape)
            .border(1.dp, Atelier.Border, Atelier.PillShape)
            .padding(4.dp)
    ) {
        options.forEach { (value, label) ->
            val selected = value == selectedValue
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        if (selected) Atelier.Ink else Color.Transparent,
                        Atelier.PillShape
                    )
                    .clip(Atelier.PillShape)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else Atelier.TextTertiary
                )
            }
        }
    }
}
