package com.tdpham.games.common

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAdOptions
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import android.view.View
import com.tdpham.games.R
import com.tdpham.games.BuildConfig
import java.util.concurrent.Executors

object AdManager {
    private const val TAG = "AdManager"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5190563950149825/9226641952"
    private const val NATIVE_AD_UNIT_ID = "ca-app-pub-5190563950149825/5584626448"
    
    private var isInitialized = false
    private var isInitializing = false
    private var mInterstitialAd: InterstitialAd? = null
    private var isLoading = false

    // Native Ad Double Buffering
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
                    Log.d(TAG, "AdMob is disabled by default.")
                    isInitializing = false
                    return@execute
                }

                MobileAds.initialize(appContext) {
                    Log.d(TAG, "MobileAds initialized successfully.")
                    isInitialized = true
                    isInitializing = false
                    mainHandler.post {
                        loadInterstitial(appContext)
                        loadNativeAd(appContext)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize MobileAds: ${e.message}", e)
                isInitializing = false
            }
        }
    }

    fun loadInterstitial(context: Context) {
        try {
            if (!ConfigManager.isAdsEnabled() || isLoading || (mInterstitialAd != null)) return
            isLoading = true

            val appContext = context.applicationContext
            mainHandler.post {
                val adRequest = AdRequest.Builder().build()
                InterstitialAd.load(appContext, INTERSTITIAL_AD_UNIT_ID, adRequest,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d(TAG, "Ad failed to load: ${adError.message}")
                            mInterstitialAd = null
                            isLoading = false
                        }

                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            Log.d(TAG, "Ad was loaded.")
                            mInterstitialAd = interstitialAd
                            isLoading = false
                        }
                    })
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load interstitial ad: ${e.message}", e)
            mInterstitialAd = null
            isLoading = false
        }
    }

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
        
        // Trigger prefetch for next cycle
        loadNativeAd(context)
        
        if (nextAd != null) {
            idleAdsShownInSession++
        }
        return nextAd
    }

    fun canShowIdleAd(context: Context): Boolean {
        if (!ConfigManager.isAdsEnabled()) return false
        
        // Check session cap for idle ads
        val maxIdleAds = ConfigManager.getAdsMaxPerSessionIdle()
        if (idleAdsShownInSession >= maxIdleAds) {
            Log.d(TAG, "Idle Ad skipped: Max ads per session ($maxIdleAds) reached")
            return false
        }

        return shouldShowAds(context)
    }

    fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        // Set the media view.
        adView.mediaView = adView.findViewById<MediaView>(R.id.ad_media)

        // Set other ad assets.
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)

        // The headline and mediaContent are guaranteed to be in every NativeAd.
        (adView.headlineView as TextView).text = nativeAd.headline
        val mediaContent = nativeAd.mediaContent
        if (mediaContent != null && (mediaContent.hasVideoContent() || mediaContent.aspectRatio > 0f)) {
            adView.mediaView?.setMediaContent(mediaContent)
            adView.mediaView?.visibility = View.VISIBLE
        } else {
            adView.mediaView?.visibility = View.GONE
        }

        // These assets aren't guaranteed to be in every NativeAd, so it's important to
        // check before assigning them.
        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView).text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button).text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad.
        adView.setNativeAd(nativeAd)
    }

    fun populateFallbackAdView(adView: NativeAdView) {
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)

        headlineView?.text = "D-Pad Game Hub [Test Ad]"
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

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit = {}) {
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

            // Check 2: Cooldown between ads
            val minInterval = ConfigManager.getAdsMinIntervalMs()
            val timeSinceLastAd = System.currentTimeMillis() - lastAdShowTime
            if (lastAdShowTime > 0 && timeSinceLastAd < minInterval) {
                Log.d(TAG, "Ad skipped: Cooldown active. Last ad ${timeSinceLastAd / 1000}s ago")
                onAdDismissed()
                return
            }

            // Check 3: Eligibility criteria
            if (!shouldShowAds(activity)) {
                Log.d(TAG, "Ad blocked: Eligibility criteria not met")
                onAdDismissed()
                return
            }

            // Check 4: Ad is ready
            val ad = mInterstitialAd
            if (ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Ad was dismissed.")
                        mInterstitialAd = null
                        adsShownInSession++
                        lastAdShowTime = System.currentTimeMillis()
                        loadInterstitial(activity.applicationContext) // Pre-load next
                        onAdDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d(TAG, "Ad failed to show: ${adError.message}")
                        mInterstitialAd = null
                        onAdDismissed()
                    }
                }
                ad.show(activity)
            } else {
                Log.d(TAG, "Ad not ready - pre-loading for next time")
                loadInterstitial(activity)
                onAdDismissed()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to show interstitial ad: ${e.message}", e)
            onAdDismissed()
        }
    }

    fun resetSessionAdCounter() {
        adsShownInSession = 0
        Log.d(TAG, "Session ad counter reset")
    }

    private fun shouldShowAds(context: Context): Boolean {
        val minDays = ConfigManager.getAdsMinDays()
        val minOpens = ConfigManager.getAdsMinOpens()
        val minSessionSecs = ConfigManager.getAdsMinSessionSeconds()
        
        val days = getDaysSinceInstall(context)
        val opens = getAppOpens(context)
        val sessionSecs = getSecondsInSession()
        
        val isInstallTimePassed = days >= minDays
        val isOpenCountPassed = opens >= minOpens
        val isSessionDelayPassed = sessionSecs >= minSessionSecs
        
        Log.d(TAG, "Eligibility check: Days=$days/$minDays, Opens=$opens/$minOpens, Session=$sessionSecs/$minSessionSecs")
        
        return isInstallTimePassed && isOpenCountPassed && isSessionDelayPassed
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
