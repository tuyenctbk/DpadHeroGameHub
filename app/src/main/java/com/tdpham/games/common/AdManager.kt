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

object AdManager {
    private const val TAG = "AdManager"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5190563950149825/9226641952"
    
    private var isInitialized = false
    private var isInitializing = false
    private var mInterstitialAd: InterstitialAd? = null
    private var isLoading = false

    // Session tracking
    private var sessionStartTime: Long = 0L
    private var isSessionTracked = false
    
    // Frequency control
    private var lastAdShowTime: Long = 0L
    private var adsShownInSession = 0
    private val mainHandler = Handler(Looper.getMainLooper())

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

        try {
            if (!ConfigManager.isAdsEnabled()) {
                Log.d(TAG, "AdMob is disabled by default.")
                isInitializing = false
                return
            }
            
            val appContext = context.applicationContext
            // Initialize MobileAds on a background thread to prevent blocking the UI thread
            Thread {
                try {
                    MobileAds.initialize(appContext) {
                        isInitialized = true
                        isInitializing = false
                        loadInterstitial(appContext)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to initialize MobileAds: ${e.message}", e)
                    isInitializing = false
                }
            }.start()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize MobileAds: ${e.message}", e)
            isInitializing = false
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
