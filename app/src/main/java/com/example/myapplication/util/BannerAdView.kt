package com.example.myapplication.util

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * 앵커형 적응형 배너.
 *
 * 왜 "앵커"인가: 결과 화면의 분석 결과는 이 앱의 핵심 가치다. 스크롤 콘텐츠 안에
 * 배너를 끼우면 추천 카드를 가리거나 밀어내서 AdMob의 "콘텐츠를 가리는 배치" 위반이
 * 되고 리텐션도 깨진다. 그래서 스크롤 영역 **밖** 하단에 고정하고, 배너 높이만큼
 * 콘텐츠 영역을 줄인다.
 *
 * 디버그 빌드는 Google 테스트 단위로 분기한다 (RewardedAdManager와 동일한 이유:
 * 개발 중 실광고 클릭은 무효 트래픽으로 계정 정지 사유).
 */
object BannerAdConfig {

    private const val TAG = "BannerAdView"

    /** Google이 공개한 적응형 배너 테스트 단위 */
    private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

    /**
     * 프로덕션 배너 단위 ID.
     *
     * 비어 있으면 릴리스 빌드에서 배너를 **렌더링하지 않는다** (가짜 ID로 요청해서
     * 무효 트래픽을 만들지 않기 위해). AdMob 콘솔에서 배너 단위를 만든 뒤 여기에 채운다.
     */
    private const val PROD_AD_UNIT_ID = ""

    /** 노출할 단위 ID. null이면 배너를 그리지 않는다. */
    val adUnitId: String?
        get() = if (BuildConfig.DEBUG) TEST_AD_UNIT_ID else PROD_AD_UNIT_ID.ifBlank { null }

    /** 프로덕션 단위가 설정됐는지 (로그/진단용) */
    val isConfigured: Boolean
        get() = adUnitId != null

    internal fun log(message: String) = Log.d(TAG, message)
}

/**
 * 화면 하단에 고정하는 적응형 배너.
 *
 * 단위 ID가 없으면(릴리스 빌드에 PROD_AD_UNIT_ID 미설정) 아무것도 그리지 않고 높이 0을
 * 차지한다. 즉 ID를 받기 전에도 안전하게 배포할 수 있다.
 *
 * @param placement Firebase 계측용 지점 이름 (예: "result_bottom")
 */
@Composable
fun AnchoredBannerAd(
    placement: String,
    modifier: Modifier = Modifier
) {
    val adUnitId = BannerAdConfig.adUnitId ?: return
    // Compose 프리뷰에서는 AdView를 만들 수 없다 (SDK 초기화 없음)
    if (LocalInspectionMode.current) return

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp

    // 적응형 배너 높이는 기기 폭에 따라 달라진다. 미리 알아내서 자리를 먼저 잡아야
    // 광고가 뒤늦게 로드될 때 콘텐츠가 밀리는 레이아웃 점프가 없다.
    val adSize = remember(widthDp) { anchoredAdSize(context, widthDp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(adSize.height.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AnalyticsHelper.logAdRequest(placement, "banner")
                AdView(ctx).apply {
                    setAdSize(adSize)
                    this.adUnitId = adUnitId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            BannerAdConfig.log("배너 로드 완료 ($placement)")
                            AnalyticsHelper.logAdShown(placement, "banner")
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            BannerAdConfig.log("배너 로드 실패 ($placement): ${error.code} ${error.message}")
                            AnalyticsHelper.logAdFailed(placement, "banner", "${error.code}")
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            },
            // 화면을 떠날 때 AdView를 정리하지 않으면 Activity가 누수된다
            onRelease = { it.destroy() }
        )
    }
}

private fun anchoredAdSize(context: Context, widthDp: Int): AdSize =
    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
