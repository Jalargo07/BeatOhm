package com.beatohm.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.inmobi.ads.InMobiBanner
import com.inmobi.ads.InMobiInterstitial
import com.inmobi.ads.listeners.BannerAdEventListener
import com.inmobi.ads.listeners.InterstitialAdEventListener
import com.inmobi.sdk.InMobiSdk
import com.inmobi.sdk.SdkInitializationListener
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object InMobiManager {
    private const val TAG = "InMobiManager"
    private const val ACCOUNT_ID = "169d63f637234c7a926270573a4b685e"
    private const val BANNER_PLACEMENT_ID = 10000783393L
    private const val INTERSTITIAL_PLACEMENT_ID = 10000222082L
    private const val REWARDED_PLACEMENT_ID = 10000783395L

    sealed class InitState {
        object Initializing : InitState()
        object Ready : InitState()
        object Failed : InitState()
    }

    enum class RewardedAdResult {
        REWARD_EARNED,
        NO_FILL,
        ERROR,
        NOT_INITIALIZED
    }

    private val _initState = MutableStateFlow<InitState>(InitState.Initializing)
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    var isInitialized = false
        private set

    fun initialize(context: Context) {
        if (isInitialized) return
        _initState.value = InitState.Initializing
        InMobiSdk.init(context, ACCOUNT_ID, null, object : SdkInitializationListener {
            override fun onInitializationComplete(error: Error?) {
                if (error != null) {
                    Log.e(TAG, "InMobi init failed: ${error.message}")
                    _initState.value = InitState.Failed
                } else {
                    Log.d(TAG, "InMobi init successful")
                    isInitialized = true
                    _initState.value = InitState.Ready
                }
            }
        })
    }

    // kept for future interstitial use - see A-021
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        if (!isInitialized) {
            Log.w(TAG, "showInterstitial: not initialized")
            onDismissed()
            return
        }

        val interstitial = InMobiInterstitial(activity, INTERSTITIAL_PLACEMENT_ID, object : InterstitialAdEventListener() {
            override fun onAdDisplayed(ad: InMobiInterstitial, info: com.inmobi.ads.AdMetaInfo) {
                Log.d(TAG, "Interstitial displayed")
            }

            override fun onAdDismissed(ad: InMobiInterstitial) {
                Log.d(TAG, "Interstitial dismissed")
                onDismissed()
            }

            override fun onAdFetchFailed(ad: InMobiInterstitial, status: com.inmobi.ads.InMobiAdRequestStatus) {
                Log.e(TAG, "Interstitial fetch failed: ${status.message}")
                onDismissed()
            }

            override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: com.inmobi.ads.AdMetaInfo) {
                Log.d(TAG, "Interstitial loaded, showing now")
                ad.show()
            }

            override fun onAdLoadFailed(ad: InMobiInterstitial, status: com.inmobi.ads.InMobiAdRequestStatus) {
                Log.e(TAG, "Interstitial load failed: ${status.message}")
                onDismissed()
            }

            override fun onUserLeftApplication(ad: InMobiInterstitial) {
                Log.d(TAG, "User left app from interstitial")
            }
        })

        interstitial.load()
        Log.d(TAG, "showInterstitial called")
    }

    /**
     * Shows a rewarded ad. Reward is ONLY granted via [onRewardEarned] when
     * [InterstitialAdEventListener.onRewardsUnlocked] fires. The reward callback
     * is idempotent (AtomicBoolean guard). No-fill, load error or dismiss without
     * reward all route to [onFailed] — the caller keeps the regen paused.
     */
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onFailed: (RewardedAdResult) -> Unit = {}
    ) {
        if (!isInitialized) {
            Log.w(TAG, "showRewardedAd: not initialized")
            onFailed(RewardedAdResult.NOT_INITIALIZED)
            return
        }

        val rewardDelivered = AtomicBoolean(false)

        val rewarded = InMobiInterstitial(activity, REWARDED_PLACEMENT_ID, object : InterstitialAdEventListener() {
            override fun onAdDisplayed(ad: InMobiInterstitial, info: com.inmobi.ads.AdMetaInfo) {
                Log.d(TAG, "Rewarded displayed")
            }

            override fun onAdDismissed(ad: InMobiInterstitial) {
                Log.d(TAG, "Rewarded dismissed")
                if (!rewardDelivered.get()) {
                    Log.w(TAG, "Rewarded dismissed without reward")
                }
            }

            override fun onAdFetchFailed(ad: InMobiInterstitial, status: com.inmobi.ads.InMobiAdRequestStatus) {
                Log.e(TAG, "Rewarded fetch failed: ${status.message}")
                onFailed(RewardedAdResult.NO_FILL)
            }

            override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: com.inmobi.ads.AdMetaInfo) {
                Log.d(TAG, "Rewarded loaded, showing now")
                ad.show()
            }

            override fun onAdLoadFailed(ad: InMobiInterstitial, status: com.inmobi.ads.InMobiAdRequestStatus) {
                Log.e(TAG, "Rewarded load failed: ${status.message}")
                onFailed(RewardedAdResult.ERROR)
            }

            override fun onRewardsUnlocked(ad: InMobiInterstitial, rewards: Map<Any, Any>) {
                if (rewardDelivered.compareAndSet(false, true)) {
                    Log.d(TAG, "Rewarded earned! Rewards: $rewards")
                    onRewardEarned()
                }
            }

            override fun onUserLeftApplication(ad: InMobiInterstitial) {
                Log.d(TAG, "User left app from rewarded")
            }
        })

        rewarded.load()
        Log.d(TAG, "showRewardedAd called")
    }

    fun createBanner(
        activity: Activity,
        onLoaded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ): InMobiBanner {
        val banner = InMobiBanner(activity, BANNER_PLACEMENT_ID)
        banner.setListener(object : BannerAdEventListener() {
            override fun onAdLoadSucceeded(ad: InMobiBanner, info: com.inmobi.ads.AdMetaInfo) {
                Log.d(TAG, "Banner loaded")
                onLoaded?.invoke()
            }

            override fun onAdLoadFailed(ad: InMobiBanner, status: com.inmobi.ads.InMobiAdRequestStatus) {
                Log.e(TAG, "Banner load failed: ${status.message}")
                onFailed?.invoke()
            }

            override fun onAdDisplayed(ad: InMobiBanner) {
                Log.d(TAG, "Banner displayed")
            }

            override fun onAdDismissed(ad: InMobiBanner) {
                Log.d(TAG, "Banner dismissed")
            }

            override fun onAdClicked(ad: InMobiBanner, rewardMap: Map<Any, Any>) {
                Log.d(TAG, "Banner clicked")
            }

            override fun onUserLeftApplication(ad: InMobiBanner) {
                Log.d(TAG, "User left app from banner")
            }
        })
        banner.setBannerSize(320, 50)
        banner.load()
        return banner
    }
}
