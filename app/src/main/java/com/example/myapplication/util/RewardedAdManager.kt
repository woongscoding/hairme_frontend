package com.example.myapplication.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.myapplication.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlin.concurrent.thread

/**
 * AdMob 보상형 광고 매니저
 *
 * 크레딧 지급은 앱이 아니라 서버가 한다. 앱은 광고를 보여주면서 SSV 옵션에 user_id만
 * 실어 보내고, AdMob 서버가 우리 서버의 SSV 콜백(/api/credits/reward-callback)을 호출해
 * 크레딧 1개를 지급한다. 따라서 [show]의 onRewarded 는 "지급됐다"가 아니라
 * "시청이 끝났으니 곧 지급될 것"이라는 신호이고, 잔액은 서버에서 다시 조회해야 한다.
 *
 * Hilt가 비활성화된 프로젝트라 다른 헬퍼들과 같은 object 싱글톤 방식을 따른다.
 */
object RewardedAdManager {

    private const val TAG = "RewardedAdManager"

    // 디버그 빌드에서 실제 광고를 띄우면 무효 트래픽으로 계정이 정지될 수 있어
    // Google이 제공하는 테스트 광고 단위를 쓴다.
    private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val PROD_AD_UNIT_ID = "ca-app-pub-1902190021810378/4849788332"

    private val adUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_AD_UNIT_ID else PROD_AD_UNIT_ID

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var initialized = false

    @Volatile
    private var rewardedAd: RewardedAd? = null

    @Volatile
    private var isLoading = false

    /** 로드된(또는 로드 중인) 광고가 있는지 - UI에서 버튼 노출 판단용 */
    val isAdAvailable: Boolean
        get() = rewardedAd != null

    /**
     * SDK 초기화. Application.onCreate에서 1회 호출.
     *
     * MobileAds.initialize는 내부적으로 디스크/네트워크를 건드려 메인 스레드를
     * 수백 ms 블로킹하므로 백그라운드 스레드에서 호출한다.
     */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext
        thread(name = "admob-init") {
            MobileAds.initialize(appContext) {
                Log.d(TAG, "AdMob SDK 초기화 완료")
                // 초기화 직후 미리 한 장 받아둬야 사용자가 버튼을 눌렀을 때 바로 뜬다
                mainHandler.post { load(appContext) }
            }
        }
    }

    /**
     * 광고 미리 로드. 이미 로드돼 있거나 로드 중이면 아무것도 하지 않는다.
     */
    fun load(context: Context) {
        if (rewardedAd != null || isLoading) return
        if (!initialized) {
            initialize(context)
            return
        }

        isLoading = true
        val appContext = context.applicationContext

        RewardedAd.load(
            appContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad
                    Log.d(TAG, "보상형 광고 로드 완료")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                    Log.w(TAG, "보상형 광고 로드 실패: ${error.code} ${error.message}")
                }
            }
        )
    }

    /**
     * 광고 표시.
     *
     * @param userId 서버가 크레딧을 지급할 대상. 이 값이 없으면 서버는 AdMob의 검증 핑과
     *               구분하지 못해 지급 없이 200을 돌려주므로, 비로그인 상태에서는 호출하지 않는다.
     * @param onRewarded 시청 완료. 지급은 서버가 비동기로 하므로 잔액은 따로 조회해야 한다.
     * @param onDismissed 광고 화면이 닫힘 (보상 여부와 무관하게 항상 호출)
     * @param onFailed 보여줄 광고가 없거나 표시에 실패
     */
    fun show(
        activity: Activity,
        userId: String,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit = {},
        onFailed: (String) -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad == null) {
            load(activity)
            onFailed("광고를 불러오는 중이에요. 잠시 후 다시 시도해 주세요.")
            return
        }

        // 서버 SSV 콜백에 user_id를 실어 보낸다 (지급 대상 식별)
        ad.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder()
                .setUserId(userId)
                .build()
        )

        var rewarded = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                load(activity) // 다음 시청을 위해 미리 받아둔다
                if (rewarded) onRewarded()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                load(activity)
                Log.w(TAG, "보상형 광고 표시 실패: ${error.code} ${error.message}")
                onFailed("광고를 재생하지 못했어요. 잠시 후 다시 시도해 주세요.")
            }
        }

        ad.show(activity) {
            // 보상 콜백은 광고가 닫히기 전에 온다. 실제 알림은 닫힌 뒤에 한 번만 하려고
            // 여기서는 플래그만 세운다.
            rewarded = true
            Log.d(TAG, "보상형 광고 시청 완료 - 서버 지급 대기")
        }
    }
}
