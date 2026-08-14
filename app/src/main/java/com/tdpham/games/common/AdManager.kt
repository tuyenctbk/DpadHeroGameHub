package com.tdpham.games.common

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import android.view.View
import com.tdpham.games.R
import java.util.concurrent.Executors

object AdManager {
    private const val TAG = "AdManager"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5190563950149825/9226641952"
    private const val NATIVE_AD_UNIT_ID = "ca-app-pub-5190563950149825/5584626448"
    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-5190563950149825/9226641952"
    
    private var isInitialized = false
    private var isInitializing = false
    
    // Single-Slot Interstitial Caching
    private var mInterstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    // Single-Slot Rewarded Caching
    private var mRewardedAd: RewardedAd? = null
    private var isLoadingRewarded = false

    // Native Ad Single Caching
    private var currentNativeAd: NativeAd? = null
    private var prefetchedNativeAd: NativeAd? = null
    private var isNativeLoading = false

    // Session tracking
    private var sessionStartTime: Long = 0L
    private var isSessionTracked = false
    
    // Frequency control
    private var lastAdShowTime: Long = 0L
    private var adsShownInSession = 0
    private var idleAdsShownInSession = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onNativeAdLoadedListener: ((NativeAd) -> Unit)? = null
    private var onNativeAdFailedListener: (() -> Unit)? = null

    fun setOnNativeAdLoadedListener(listener: ((NativeAd) -> Unit)?) {
        onNativeAdLoadedListener = listener
    }

    fun setOnNativeAdFailedListener(listener: (() -> Unit)?) {
        onNativeAdFailedListener = listener
    }

    fun init(context: Context) {
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
            adsShownInSession = 0
            idleAdsShownInSession = 0
        }
        if (!isSessionTracked) {
            isSessionTracked = true
            incrementAppOpens(context)
        }

        if (isInitialized || isInitializing) return
        isInitializing = true

        val appContext = context.applicationContext
        Executors.newSingleThreadExecutor().execute {
            try {
                if (!ConfigManager.isAdsEnabled()) {
                    Log.d(TAG, "AdMob is disabled by configuration.")
                    isInitializing = false
                    return@execute
                }

                MobileAds.initialize(appContext) {
                    Log.d(TAG, "MobileAds initialized successfully.")
                    isInitialized = true
                    isInitializing = false
                    mainHandler.post {
                        // Preload 1 Interstitial ad & 1 Rewarded ad for immediate match readiness
                        loadInterstitial(appContext)
                        loadRewarded(appContext)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize MobileAds: ${e.message}", e)
                isInitializing = false
            }
        }
    }

    /**
     * Preloads a single Interstitial Ad.
     * Guaranteed NO duplicate or redundant network requests if an ad is already loaded or loading.
     */
    fun loadInterstitial(context: Context) {
        try {
            if (!ConfigManager.isAdsEnabled() || isLoadingInterstitial || (mInterstitialAd != null)) {
                return
            }
            isLoadingInterstitial = true

            val appContext = context.applicationContext
            mainHandler.post {
                val adRequest = AdRequest.Builder().build()
                InterstitialAd.load(appContext, INTERSTITIAL_AD_UNIT_ID, adRequest,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d(TAG, "Interstitial Ad failed to load: ${adError.message}")
                            mInterstitialAd = null
                            isLoadingInterstitial = false
                        }

                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            Log.d(TAG, "Interstitial Ad loaded and cached.")
                            mInterstitialAd = interstitialAd
                            isLoadingInterstitial = false
                        }
                    })
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load interstitial ad: ${e.message}", e)
            mInterstitialAd = null
            isLoadingInterstitial = false
        }
    }

    /**
     * Displays the cached Interstitial ad if ready and cooldown passed.
     */
    fun showInterstitial(activity: Activity, force: Boolean = false, onAdDismissed: () -> Unit = {}) {
        try {
            if (!ConfigManager.isAdsEnabled()) {
                onAdDismissed()
                return
            }

            // Check 1: Session ad cap
            val maxAds = ConfigManager.getAdsMaxPerSession()
            if (adsShownInSession >= maxAds) {
                Log.d(TAG, "Ad skipped: Max ads per session ($maxAds) reached")
                onAdDismissed()
                return
            }

            // Check 2: Cooldown between ads (e.g. 45s)
            val minInterval = ConfigManager.getAdsMinIntervalMs()
            val timeSinceLastAd = System.currentTimeMillis() - lastAdShowTime
            if (!force && lastAdShowTime > 0 && timeSinceLastAd < minInterval) {
                Log.d(TAG, "Ad skipped: Cooldown active. Last ad ${timeSinceLastAd / 1000}s ago (min: ${minInterval / 1000}s)")
                onAdDismissed()
                return
            }

            // Check 3: Check eligibility
            if (!shouldShowAds(activity)) {
                onAdDismissed()
                return
            }

            // Check 4: Display ad if loaded
            val ad = mInterstitialAd
            if (ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial Ad was dismissed.")
                        mInterstitialAd = null
                        adsShownInSession++
                        lastAdShowTime = System.currentTimeMillis()
                        // Preload the next single slot ad
                        loadInterstitial(activity.applicationContext)
                        onAdDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d(TAG, "Interstitial Ad failed to show: ${adError.message}")
                        mInterstitialAd = null
                        loadInterstitial(activity.applicationContext)
                        onAdDismissed()
                    }
                }
                ad.show(activity)
            } else {
                Log.d(TAG, "Interstitial Ad not ready yet - requesting 1 for next time")
                loadInterstitial(activity.applicationContext)
                onAdDismissed()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in showInterstitial: ${e.message}", e)
            onAdDismissed()
        }
    }

    /**
     * Checks if an interstitial ad is eligible and ready to be shown.
     */
    fun canShowInterstitial(context: Context? = null): Boolean {
        if (!ConfigManager.isAdsEnabled()) return false
        if (adsShownInSession >= ConfigManager.getAdsMaxPerSession()) return false
        val timeSinceLastAd = System.currentTimeMillis() - lastAdShowTime
        if (lastAdShowTime > 0 && timeSinceLastAd < ConfigManager.getAdsMinIntervalMs()) return false
        if (context != null && !shouldShowAds(context)) return false
        return mInterstitialAd != null
    }

    /**
     * Preloads a single Rewarded Ad.
     */
    fun loadRewarded(context: Context) {
        try {
            if (!ConfigManager.isAdsEnabled() || isLoadingRewarded || (mRewardedAd != null)) {
                return
            }
            isLoadingRewarded = true

            val appContext = context.applicationContext
            mainHandler.post {
                val adRequest = AdRequest.Builder().build()
                RewardedAd.load(appContext, REWARDED_AD_UNIT_ID, adRequest,
                    object : RewardedAdLoadCallback() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d(TAG, "Rewarded Ad failed to load: ${adError.message}")
                            mRewardedAd = null
                            isLoadingRewarded = false
                        }

                        override fun onAdLoaded(rewardedAd: RewardedAd) {
                            Log.d(TAG, "Rewarded Ad loaded and cached.")
                            mRewardedAd = rewardedAd
                            isLoadingRewarded = false
                        }
                    })
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load rewarded ad: ${e.message}", e)
            mRewardedAd = null
            isLoadingRewarded = false
        }
    }

    /**
     * Shows a Rewarded Ad for high eCPM incentives (Revives, Continues, Bonus Points).
     */
    fun showRewarded(
        activity: Activity,
        onUserEarnedReward: (RewardItem) -> Unit,
        onAdDismissed: () -> Unit = {}
    ) {
        try {
            val ad = mRewardedAd
            if (ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Rewarded Ad dismissed.")
                        mRewardedAd = null
                        lastAdShowTime = System.currentTimeMillis()
                        loadRewarded(activity.applicationContext)
                        onAdDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d(TAG, "Rewarded Ad failed to show: ${adError.message}")
                        mRewardedAd = null
                        loadRewarded(activity.applicationContext)
                        onAdDismissed()
                    }
                }
                ad.show(activity) { rewardItem ->
                    onUserEarnedReward(rewardItem)
                }
            } else {
                Log.d(TAG, "Rewarded Ad not ready.")
                loadRewarded(activity.applicationContext)
                onAdDismissed()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in showRewarded: ${e.message}", e)
            onAdDismissed()
        }
    }
    fun showRewardedOrInterstitial(
        activity: Activity,
        onRewardGranted: () -> Unit,
        onAdClosed: () -> Unit = {}
    ) {
        try {
            if (!ConfigManager.isAdsEnabled()) {
                onRewardGranted()
                onAdClosed()
                return
            }

            // 1. Try Rewarded Ad first ($15-$35 eCPM)
            val rewardedAd = mRewardedAd
            if (rewardedAd != null) {
                var rewardEarned = false
                rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        mRewardedAd = null
                        lastAdShowTime = System.currentTimeMillis()
                        loadRewarded(activity.applicationContext)
                        if (rewardEarned) {
                            onRewardGranted()
                        }
                        onAdClosed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        mRewardedAd = null
                        loadRewarded(activity.applicationContext)
                        onRewardGranted()
                        onAdClosed()
                    }
                }
                rewardedAd.show(activity) {
                    rewardEarned = true
                }
                return
            }

            // 2. Fallback to Interstitial Ad ($3-$8 eCPM)
            val interstitialAd = mInterstitialAd
            if (interstitialAd != null) {
                interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        mInterstitialAd = null
                        lastAdShowTime = System.currentTimeMillis()
                        loadInterstitial(activity.applicationContext)
                        onRewardGranted()
                        onAdClosed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        mInterstitialAd = null
                        loadInterstitial(activity.applicationContext)
                        onRewardGranted()
                        onAdClosed()
                    }
                }
                interstitialAd.show(activity)
                return
            }

            // 3. If neither ad is currently cached, grant reward directly for smooth UX
            loadInterstitial(activity.applicationContext)
            loadRewarded(activity.applicationContext)
            onRewardGranted()
            onAdClosed()
        } catch (e: Throwable) {
            Log.e(TAG, "Exception in showRewardedOrInterstitial: ${e.message}", e)
            onRewardGranted()
            onAdClosed()
        }
    }

    /**
     * Loads Native Ad on demand when screensaver/idle is actually activated.
     */
    fun loadNativeAd(context: Context) {
        try {
            if (!ConfigManager.isAdsEnabled() || isNativeLoading || (prefetchedNativeAd != null)) return
            isNativeLoading = true

            val appContext = context.applicationContext
            mainHandler.post {
                val videoOptions = VideoOptions.Builder()
                    .setStartMuted(true)
                    .build()

                val adOptions = NativeAdOptions.Builder()
                    .setVideoOptions(videoOptions)
                    .build()

                val adLoader = AdLoader.Builder(appContext, NATIVE_AD_UNIT_ID)
                    .forNativeAd { nativeAd ->
                        Log.d(TAG, "Native Ad loaded.")
                        prefetchedNativeAd = nativeAd
                        isNativeLoading = false
                        onNativeAdLoadedListener?.invoke(nativeAd)
                    }
                    .withNativeAdOptions(adOptions)
                    .withAdListener(object : com.google.android.gms.ads.AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d(TAG, "Native Ad failed to load: ${adError.message}")
                            isNativeLoading = false
                            onNativeAdFailedListener?.invoke()
                        }
                    })
                    .build()
                adLoader.loadAd(AdRequest.Builder().build())
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load native ad: ${e.message}", e)
            isNativeLoading = false
            onNativeAdFailedListener?.invoke()
        }
    }

    fun getNextNativeAd(context: Context): NativeAd? {
        val nextAd = prefetchedNativeAd
        currentNativeAd?.destroy()
        currentNativeAd = nextAd
        prefetchedNativeAd = null
        
        if (nextAd != null) {
            idleAdsShownInSession++
        }
        return nextAd
    }

    fun canShowIdleAd(context: Context): Boolean {
        if (!ConfigManager.isAdsEnabled()) return false
        val maxIdleAds = ConfigManager.getAdsMaxPerSessionIdle()
        if (idleAdsShownInSession >= maxIdleAds) {
            return false
        }
        return shouldShowAds(context)
    }

    fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.mediaView = adView.findViewById<MediaView>(R.id.ad_media)
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)

        (adView.headlineView as? TextView)?.text = nativeAd.headline
        val mediaContent = nativeAd.mediaContent
        if (mediaContent != null && (mediaContent.hasVideoContent() || mediaContent.aspectRatio > 0f)) {
            adView.mediaView?.setMediaContent(mediaContent)
            adView.mediaView?.visibility = View.VISIBLE
        } else {
            adView.mediaView?.visibility = View.GONE
        }

        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as? TextView)?.text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as? Button)?.text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        adView.setNativeAd(nativeAd)
    }

    fun populateFallbackAdView(adView: NativeAdView) {
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)

        headlineView?.text = "D-Pad Arcade Hub"
        bodyView?.apply {
            text = "Enjoy endless classic arcade games with TV D-Pad controls!"
            visibility = View.VISIBLE
        }
        ctaView?.apply {
            text = "Play Now"
            visibility = View.VISIBLE
        }
        iconView?.apply {
            setImageResource(R.mipmap.ic_launcher)
            visibility = View.VISIBLE
        }
    }

    fun resetSessionAdCounter() {
        adsShownInSession = 0
        idleAdsShownInSession = 0
    }

    private fun shouldShowAds(context: Context): Boolean {
        if (!ConfigManager.isAdsEnabled()) return false
        val minDays = ConfigManager.getAdsMinDays()
        val minOpens = ConfigManager.getAdsMinOpens()
        val minSessionSecs = ConfigManager.getAdsMinSessionSeconds()
        
        val days = getDaysSinceInstall(context)
        val opens = getAppOpens(context)
        val sessionSecs = getSecondsInSession()
        
        return (days >= minDays) && (opens >= minOpens) && (sessionSecs >= minSessionSecs)
    }

    private fun getDaysSinceInstall(context: Context): Int {
        return try {
            val installTime = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            val diffMs = System.currentTimeMillis() - installTime
            (diffMs / (1000 * 60 * 60 * 24)).toInt()
        } catch (_: Exception) {
            0
        }
    }

    private fun getAppOpens(context: Context): Int {
        val prefs = context.getSharedPreferences("ads_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("app_opens", 0)
    }

    private fun incrementAppOpens(context: Context) {
        val prefs = context.getSharedPreferences("ads_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("app_opens", 0)
        prefs.edit { putInt("app_opens", current + 1) }
    }

    private fun getSecondsInSession(): Int {
        if (sessionStartTime == 0L) return 0
        val diffMs = System.currentTimeMillis() - sessionStartTime
        return (diffMs / 1000).toInt()
    }
}
