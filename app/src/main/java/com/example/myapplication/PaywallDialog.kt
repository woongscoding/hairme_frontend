package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierPrimaryButton
import com.example.myapplication.ui.theme.AtelierSecondaryButton
import com.example.myapplication.ui.theme.AtelierUnderlineButton
import com.example.myapplication.ui.theme.atelierLabel
import com.example.myapplication.ui.theme.atelierSerif
import com.example.myapplication.util.BillingManager
import com.example.myapplication.util.BillingManager.CreditProduct
import com.example.myapplication.util.Experiments

/**
 * 결제 화면 (크레딧 3종).
 *
 * 뜨는 때는 두 가지뿐이다 (그 밖의 강제 팝업 없음):
 * - 무료 합성(비회원) 또는 크레딧(회원)을 다 쓰고 다음 합성을 눌렀을 때 (trigger="exhausted")
 * - 무료 결과(512px·워터마크) 위 "이 스타일 고화질로 보기"를 눌렀을 때 (trigger="hd_upgrade")
 *
 * 비회원에게는 로그인 보너스를 먼저 권한다 (가입 전환이 실험의 핵심 지표). 상품을 눌러도
 * 로그인부터 진행하고, 로그인 뒤 같은 상품 결제를 이어서 연다(호출 측 처리).
 *
 * @param products Play에서 불러온 상품. 비어 있으면(심사 전·Play 미설치) 구매 버튼을 끈다
 * @param showStarter 첫 구매 상품 노출 여부 (서버 products의 available, 없으면 결제 이력이 없는 계정·비회원)
 * @param isLifetimeFreePolicy 평생 무료 체험 소진(429 limit_detail="lifetime" 또는 /api/usage free_policy)
 * @param credits30Variant 30회 상품 노출 A/B ([Experiments])
 * @param showRewardedCredit 대조군 회원에게만 "광고 보고 1회 받기" (실험군·유료 사용자는 없음)
 */
@Composable
fun PaywallDialog(
    trigger: String,
    isLoggedIn: Boolean,
    isLifetimeFreePolicy: Boolean,
    signupBonus: Int,
    products: Map<String, CreditProduct>,
    showStarter: Boolean,
    credits30Variant: String,
    isPurchasing: Boolean,
    showRewardedCredit: Boolean,
    isWaitingReward: Boolean,
    onPurchase: (productId: String) -> Unit,
    onLogin: () -> Unit,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit
) {
    val visibleProducts = remember(products, showStarter, credits30Variant) {
        buildList {
            if (showStarter) products[BillingManager.SKU_STARTER_5]?.let { add(it) }
            products[BillingManager.SKU_CREDITS_10]?.let { add(it) }
            products[BillingManager.SKU_CREDITS_30]?.let { add(it) }
        }
    }
    // 기본 선택은 항상 10회 (A/B는 30회의 강조 방식만 바꾼다)
    var selectedId by remember(visibleProducts) {
        mutableStateOf(
            visibleProducts.firstOrNull { it.productId == BillingManager.SKU_CREDITS_10 }?.productId
                ?: visibleProducts.firstOrNull()?.productId
        )
    }
    val selected = visibleProducts.firstOrNull { it.productId == selectedId }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Atelier.Background, Atelier.CardShape)
                .border(1.dp, Atelier.Border, Atelier.CardShape)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = if (trigger == "hd_upgrade") "HD QUALITY" else "HAIRME CREDITS",
                style = atelierLabel(size = 11, color = Atelier.BrandVioletSoft)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = paywallTitle(trigger, isLoggedIn, isLifetimeFreePolicy),
                style = atelierSerif(size = 21)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when {
                    isLoggedIn ->
                        "크레딧 1개로 고화질(1K) 합성 1회. 얼굴 분석·스타일 추천·미용실 찾기는 계속 무료예요."
                    // 평생 무료 체험은 다시 채워지지 않는다 → 다음 행동은 로그인뿐임을 분명히
                    isLifetimeFreePolicy ->
                        "무료 체험은 한 번뿐이에요. 카카오로 로그인하면 합성 ${signupBonus}회를 더 받고 " +
                            "지금 고른 스타일을 바로 이어서 볼 수 있어요."
                    else ->
                        "카카오로 로그인하면 합성 ${signupBonus}회를 더 받고, 회원 합성은 워터마크 없는 고화질로 저장돼요."
                },
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Atelier.TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (!isLoggedIn) {
                AtelierPrimaryButton(
                    text = "로그인하고 ${signupBonus}회 더 받기",
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "또는 크레딧 구매 (로그인 후 결제)",
                    style = atelierLabel(size = 11)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (visibleProducts.isEmpty()) {
                Text(
                    text = "결제 정보를 불러오는 중이에요. 잠시 후 다시 시도해 주세요.",
                    fontSize = 13.sp,
                    color = Atelier.TextTertiary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visibleProducts.forEach { product ->
                        ProductOptionRow(
                            product = product,
                            selected = product.productId == selectedId,
                            badge = productBadge(product.productId, credits30Variant),
                            emphasized = product.productId != BillingManager.SKU_CREDITS_30 ||
                                credits30Variant == Experiments.CREDITS30_RECOMMENDED,
                            onClick = {
                                selectedId = product.productId
                                // 비회원은 고르는 순간 로그인 → 로그인 뒤 이 상품 결제를 이어서 연다
                                if (!isLoggedIn) onPurchase(product.productId)
                            }
                        )
                    }
                }
            }

            if (isLoggedIn) {
                Spacer(modifier = Modifier.height(20.dp))
                AtelierPrimaryButton(
                    text = when {
                        isPurchasing -> "결제 진행 중..."
                        selected != null -> "${selected.formattedPrice} 결제하기"
                        else -> "결제하기"
                    },
                    onClick = { selected?.let { onPurchase(it.productId) } },
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                    enabled = selected != null && !isPurchasing,
                    trailingContent = if (isPurchasing) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                    } else null
                )

                // 보상형은 사용자가 스스로 고르는 옵션으로만 둔다 (자동 재생은 AdMob 정책 위반)
                if (showRewardedCredit) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AtelierSecondaryButton(
                        text = if (isWaitingReward) "크레딧 지급 확인 중..." else "광고 보고 1회 받기",
                        onClick = onWatchAd,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWaitingReward
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            AtelierUnderlineButton(
                text = "닫기",
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

private fun paywallTitle(trigger: String, isLoggedIn: Boolean, isLifetime: Boolean): String = when {
    trigger == "hd_upgrade" -> "이 스타일,\n고화질로 보기"
    isLoggedIn -> "크레딧을 모두 사용했어요"
    isLifetime -> "무료 체험을 모두 사용했어요"
    else -> "오늘의 무료 체험을 모두 사용했어요"
}

private fun productBadge(productId: String, credits30Variant: String): String? = when (productId) {
    BillingManager.SKU_STARTER_5 -> "첫 구매"
    BillingManager.SKU_CREDITS_30 ->
        if (credits30Variant == Experiments.CREDITS30_RECOMMENDED) "추천" else null
    else -> null
}

/**
 * 상품 한 줄: 라디오 + "N회" + 회당 가격 + 배지 + 가격.
 * emphasized=false(30회 보조 노출 그룹)면 글자를 한 단계 낮춰 보조 옵션처럼 보이게 한다.
 */
@Composable
private fun ProductOptionRow(
    product: CreditProduct,
    selected: Boolean,
    badge: String?,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Atelier.BrandViolet else Atelier.Border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Atelier.CardShape)
            .background(Atelier.Surface, Atelier.CardShape)
            .border(if (selected) 2.dp else 1.dp, borderColor, Atelier.CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 라디오 표시
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(1.5.dp, borderColor, CircleShape)
                .padding(4.dp)
                .background(if (selected) Atelier.BrandViolet else Color.Transparent, CircleShape)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "합성 ${product.credits}회",
                    fontSize = if (emphasized) 15.sp else 14.sp,
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (emphasized) Atelier.Ink else Atelier.TextSecondary
                )
                badge?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Atelier.BrandViolet,
                        modifier = Modifier
                            .border(1.dp, Atelier.BrandVioletSoft, Atelier.PillShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            perUsePrice(product)?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = it, fontSize = 12.sp, color = Atelier.TextTertiary)
            }
        }
        Text(
            text = product.formattedPrice,
            fontSize = if (emphasized) 15.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) Atelier.Ink else Atelier.TextSecondary,
            textAlign = TextAlign.End
        )
    }
}

/** 원화일 때만 "회당 N원" (다른 통화는 반올림 표기가 애매해 생략) */
private fun perUsePrice(product: CreditProduct): String? {
    if (product.currencyCode != "KRW" || product.credits <= 0) return null
    val perUse = product.priceMicros / 1_000_000.0 / product.credits
    return "회당 " + String.format(java.util.Locale.KOREA, "%,d", Math.round(perUse)) + "원"
}
