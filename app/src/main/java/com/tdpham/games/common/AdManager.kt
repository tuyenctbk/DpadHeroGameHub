package com.tdpham.games.common

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5190563950149825/9206126543"
    
    private var isInitialized = false
    private var mInterstitialAd: InterstitialAd? = null

    fun init(context: Context) {
        if (!ConfigManager.isAdsEnabled()) {
            Log.d(TAG, "AdMob is disabled by default.")
            return
        }
        if (isInitialized) return
        
        MobileAds.initialize(context) { status ->
            isInitialized = true
            loadInterstitial(context)
        }
    }

    fun loadInterstitial(context: Context) {
        if (!ConfigManager.isAdsEnabled()) return

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, adError.toString())
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Ad was loaded.")
                    mInterstitialAd = interstitialAd
                }
            })
    }

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit = {}) {
        if (!ConfigManager.isAdsEnabled() || mInterstitialAd == null) {
            Log.d(TAG, "AdMob is disabled or ad not ready.")
            onAdDismissed()
            return
        }

        mInterstitialAd?.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad was dismissed.")
                mInterstitialAd = null
                loadInterstitial(activity) // Load the next one
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.d(TAG, "Ad failed to show.")
                mInterstitialAd = null
                onAdDismissed()
            }
        }
        mInterstitialAd?.show(activity)
    }
}
