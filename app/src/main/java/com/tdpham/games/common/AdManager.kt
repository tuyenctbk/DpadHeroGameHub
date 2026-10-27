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
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.tdpham.games.BuildConfig
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import android.view.View
import com.tdpham.games.R
import com.google.android.gms.ads.RequestConfiguration
import java.util.concurrent.Executors

object AdManager {
    private const val TAG = "AdManager"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5190563950149825/9226641952"
    private const val NATIVE_AD_UNIT_ID = "ca-app-pub-5190563950149825/5584626448"
    private const val REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5190563950149825/6673801665"
    
    // Official Google AdMob Test Unit IDs for robust testing and debug fallbacks
    private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    
    private var isInitialized = false
    private var isInitializing = false
    
    // Single-Slot Interstitial Caching
    private var mInterstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    // Single-Slot Standard Rewarded & Rewarded Interstitial Caching
    private var mStandardRewardedAd: RewardedAd? = null
    private var mRewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoadingRewarded = false

    // Native Ad Single Caching
    private var currentNativeAd: NativeAd? = null
    private var prefetchedNativeAd: NativeAd? = null
    private var isNativeLoading = false

    // Ad freshness (Google expires cached full-screen ads after ~1 hour) & retry backoff
    private const val AD_EXPIRY_MS = 55 * 60 * 1000L
    private val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 45_000L, 120_000L)
    private var interstitialLoadedAt = 0L
    private var rewardedLoadedAt = 0L
    private var nativeLoadedAt = 0L
    private var interstitialRetries = 0
    private var rewardedRetries = 0
    private var nativeRetries = 0

    private fun retryDelay(attempt: Int): Long = RETRY_DELAYS_MS[attempt.coerceAtMost(RETRY_DELAYS_MS.size - 1)]
    private fun isFresh(loadedAt: Long) = System.currentTimeMillis() - loadedAt < AD_EXPIRY_MS

    private fun isInterstitialReady(): Boolean {
        if (mInterstitialAd != null && !isFresh(interstitialLoadedAt)) {
            Log.d(TAG, "Cached interstitial expired; discarding.")
            mInterstitialAd = null
        }
        return mInterstitialAd != null
    }

    fun isRewardedReady(): Boolean {
        if (!isFresh(rewardedLoadedAt)) {
            mStandardRewardedAd = null
            mRewardedInterstitialAd = null
        }
        return mStandardRewardedAd != null || mRewardedInterstitialAd != null
    }

    // Session tracking
    private var sessionStartTime: Long = 0L
    private var isSessionTracked = false
    
    // Frequency control
    private var lastAdShowTime: Long = 0L
    private var lastIdleAdDismissTime: Long = 0L
    private var adsShownInSession = 0
    private var idleAdsShownInSession = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onNativeAdLoadedListener: ((NativeAd) -> Unit)? = null
    private var onNativeAdFailedListener: (() -> Unit)? = null
    private var gameOversSinceAd = 0

    /**
     * Counts a game over across all games in this session (not per-activity) and returns true when an
     * interstitial is due: sooner for the first ad of the session, then at the regular frequency.
     */
    fun registerGameOver(): Boolean {
        gameOversSinceAd++
        val needed = if (adsShownInSession == 0) ConfigManager.getAdsFirstGameOverFrequency()
        else ConfigManager.getAdsGameOverFrequency()
        return gameOversSinceAd >= needed
    }

    fun resetGameOverCounter() { gameOversSinceAd = 0 }

    fun recordIdleAdDismissed() {
        lastIdleAdDismissTime = System.currentTimeMillis()
        Log.d(TAG, "Idle screensaver ad dismissed. 45s grace period active.")
    }

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

                // Register emulator and debug builds as test devices so Google fills test ad slots
                val testDeviceIds = mutableListOf(AdRequest.DEVICE_ID_EMULATOR)
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(testDeviceIds)
                        .build()
                )

                MobileAds.initialize(appContext) {
                    Log.d(TAG, "MobileAds initialized successfully.")
                    isInitialized = true
                    isInitializing = false
                    mainHandler.post {
                        // Preload interstitial now; keep the native ad warm shortly after so the
                        // idle screensaver never falls back to the house ad.
                        // Rewarded is preloaded by games via preloadRewarded() only when needed.
                        loadInterstitial(appContext)
                        mainHandler.postDelayed({ loadNativeAd(appContext) }, 3000L)
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
            if (!ConfigManager.isAdsEnabled() || isLoadingInterstitial || isInterstitialReady()) {
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
                            // No-fill backoff retry so an ad is ready by the next game over
                            val delay = retryDelay(interstitialRetries++)
                            mainHandler.postDelayed({ loadInterstitial(appContext) }, delay)
                        }

                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            Log.d(TAG, "Interstitial Ad loaded and cached.")
                            mInterstitialAd = interstitialAd
                            interstitialLoadedAt = System.currentTimeMillis()
                            interstitialRetries = 0
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

            // Check 2.1: Post-idle screensaver grace period (45s)
            val postIdleGraceMs = 45000L
            val timeSinceIdleAd = System.currentTimeMillis() - lastIdleAdDismissTime
            if (!force && lastIdleAdDismissTime > 0 && timeSinceIdleAd < postIdleGraceMs) {
                Log.d(TAG, "Ad skipped: Post-idle screensaver grace active (${timeSinceIdleAd / 1000}s ago)")
                onAdDismissed()
                return
            }

            // Check 3: Check eligibility
            if (!shouldShowAds(activity)) {
                onAdDismissed()
                return
            }

            // Check 4: Display ad if loaded and not expired
            val ad = if (isInterstitialReady()) mInterstitialAd else null
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
        val postIdleGraceMs = 45000L
        val timeSinceIdleAd = System.currentTimeMillis() - lastIdleAdDismissTime
        if (lastIdleAdDismissTime > 0 && timeSinceIdleAd < postIdleGraceMs) return false
        if (context != null && !shouldShowAds(context)) return false
        return isInterstitialReady()
    }

    /**
     * Preloads a Rewarded Interstitial ad using the real unit (test unit in debug builds only).
     * Call only when a feature will actually show it, to avoid wasted requests lowering match rate.
     */
    fun preloadRewarded(context: Context) = loadRewarded(context)

    fun loadRewarded(context: Context) {
        try {
            if (!ConfigManager.isAdsEnabled() || isLoadingRewarded || isRewardedReady()) {
                return
            }
            isLoadingRewarded = true

            val appContext = context.applicationContext
            mainHandler.post {
                val adRequest = AdRequest.Builder().build()
                val unitId = if (BuildConfig.DEBUG) TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID else REWARDED_INTERSTITIAL_AD_UNIT_ID
                RewardedInterstitialAd.load(appContext, unitId, adRequest, object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdFailedToLoad(e: LoadAdError) {
                        Log.d(TAG, "Rewarded Interstitial failed to load: ${e.message}")
                        mRewardedInterstitialAd = null
                        isLoadingRewarded = false
                        // Only retry a limited number of times; rewarded is optional
                        if (rewardedRetries < RETRY_DELAYS_MS.size) {
                            val delay = retryDelay(rewardedRetries++)
                            mainHandler.postDelayed({ loadRewarded(appContext) }, delay)
                        }
                    }
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        Log.d(TAG, "Rewarded Interstitial loaded and cached.")
                        mRewardedInterstitialAd = ad
                        rewardedLoadedAt = System.currentTimeMillis()
                        rewardedRetries = 0
                        isLoadingRewarded = false
                    }
                })
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load rewarded ad: ${e.message}", e)
            mStandardRewardedAd = null
            mRewardedInterstitialAd = null
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
            val standardAd = mStandardRewardedAd
            if (standardAd != null) {
                standardAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Standard Rewarded Ad dismissed.")
                        mStandardRewardedAd = null
                        lastAdShowTime = System.currentTimeMillis()
                        loadRewarded(activity.applicationContext)
                        onAdDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d(TAG, "Standard Rewarded Ad failed to show: ${adError.message}")
                        mStandardRewardedAd = null
                        loadRewarded(activity.applicationContext)
                        onAdDismissed()
                    }
                }
                standardAd.show(activity) { rewardItem ->
                    onUserEarnedReward(rewardItem)
                }
                return
            }

            val ad = mRewardedInterstitialAd
            if (ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Rewarded Interstitial Ad dismissed.")
                        mRewardedInterstitialAd = null
                        lastAdShowTime = System.currentTimeMillis()
                        loadRewarded(activity.applicationContext)
                        onAdDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d(TAG, "Rewarded Interstitial Ad failed to show: ${adError.message}")
                        mRewardedInterstitialAd = null
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

    /**
     * Loads Native Ad on demand when screensaver/idle is actually activated.
     */
    fun loadNativeAd(context: Context) {
        try {
            if (!ConfigManager.isAdsEnabled() || isNativeLoading) return
            if (prefetchedNativeAd != null && isFresh(nativeLoadedAt)) return
            if (isLoadingRewarded || isLoadingInterstitial) {
                Log.d(TAG, "Deferring native ad load: High-priority rewarded or interstitial ad is loading")
                mainHandler.postDelayed({ loadNativeAd(context.applicationContext) }, 3000L)
                return
            }
            prefetchedNativeAd?.destroy()
            prefetchedNativeAd = null
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
                        nativeLoadedAt = System.currentTimeMillis()
                        nativeRetries = 0
                        isNativeLoading = false
                        onNativeAdLoadedListener?.invoke(nativeAd)
                    }
                    .withNativeAdOptions(adOptions)
                    .withAdListener(object : com.google.android.gms.ads.AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d(TAG, "Native Ad failed to load: ${adError.message}")
                            isNativeLoading = false
                            onNativeAdFailedListener?.invoke()
                            val delay = retryDelay(nativeRetries++)
                            mainHandler.postDelayed({ loadNativeAd(appContext) }, delay)
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
        val nextAd = if (prefetchedNativeAd != null && isFresh(nativeLoadedAt)) prefetchedNativeAd else null
        if (nextAd == null) {
            // Stale or missing: drop it and warm a fresh one
            prefetchedNativeAd?.destroy()
            prefetchedNativeAd = null
            loadNativeAd(context)
            return null
        }
        currentNativeAd?.destroy()
        currentNativeAd = nextAd
        prefetchedNativeAd = null
        idleAdsShownInSession++
        // Immediately warm the next one for the next idle cycle
        mainHandler.postDelayed({ loadNativeAd(context.applicationContext) }, 5000L)
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
