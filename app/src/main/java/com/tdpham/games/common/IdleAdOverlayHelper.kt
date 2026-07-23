package com.tdpham.games.common

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.tdpham.games.R
import com.google.android.gms.ads.nativead.NativeAdView

class IdleAdOverlayHelper(private val activity: Activity) {

    private var rootContainer: FrameLayout? = null
    private var adOverlay: View? = null
    private var isAdShowing = false

    fun init() {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        rootContainer = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(rootContainer)
    }

    fun showState(state: IdleAdManager.IdleState, remainingToFull: Int) {
        if (!AdManager.canShowIdleAd(activity)) {
            hideAd()
            return
        }

        when (state) {
            IdleAdManager.IdleState.ACTIVE, IdleAdManager.IdleState.IDLE_YIELD -> hideAd()
            IdleAdManager.IdleState.IDLE_CORNER -> showCornerAd()
            IdleAdManager.IdleState.IDLE_PRE_FULL -> showWarning(remainingToFull)
            IdleAdManager.IdleState.IDLE_FULL, IdleAdManager.IdleState.IDLE_LOOP -> showFullScreenAd()
        }
    }

    private fun showCornerAd() {
        if (isAdShowing && adOverlay?.id == R.id.native_ad_view) return
        
        val nativeAd = AdManager.getNextNativeAd(activity) ?: return
        
        rootContainer?.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        adOverlay = inflater.inflate(R.layout.layout_native_ad_corner, rootContainer, false).apply {
            val params = layoutParams as FrameLayout.LayoutParams
            params.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            params.setMargins(0, 0, 32, 32)
            layoutParams = params
        }
        
        val adView = adOverlay as NativeAdView
        AdManager.populateNativeAdView(nativeAd, adView)
        rootContainer?.addView(adOverlay)
        isAdShowing = true
        
        // Soft slide-in animation
        adOverlay?.translationX = 400f
        adOverlay?.animate()?.translationX(0f)?.setDuration(500)?.start()
    }

    private fun showWarning(seconds: Int) {
        if (adOverlay?.id != R.id.screensaver_root) {
            rootContainer?.removeAllViews()
            val inflater = LayoutInflater.from(activity)
            adOverlay = inflater.inflate(R.layout.layout_native_ad_screensaver, rootContainer, false)
            rootContainer?.addView(adOverlay)
        }
        
        val warningContainer = adOverlay?.findViewById<View>(R.id.warning_container)
        val warningText = adOverlay?.findViewById<TextView>(R.id.warning_text)
        warningContainer?.visibility = View.VISIBLE
        warningText?.text = activity.getString(R.string.ad_warning_prefix, seconds)
        
        // Hide the ad parts during warning
        adOverlay?.findViewById<View>(R.id.native_ad_view)?.visibility = View.GONE
        isAdShowing = true
    }

    private fun showFullScreenAd() {
        val nativeAd = AdManager.getNextNativeAd(activity) ?: return
        
        rootContainer?.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        adOverlay = inflater.inflate(R.layout.layout_native_ad_screensaver, rootContainer, false)
        
        val adView = adOverlay?.findViewById<NativeAdView>(R.id.native_ad_view)
        if (adView != null) {
            AdManager.populateNativeAdView(nativeAd, adView)
            adView.visibility = View.VISIBLE
        }
        
        adOverlay?.findViewById<View>(R.id.warning_container)?.visibility = View.GONE
        rootContainer?.addView(adOverlay)
        isAdShowing = true
        
        // OLED Drift Animation
        startDriftAnimation()
    }

    private fun startDriftAnimation() {
        val driftView = adOverlay?.findViewById<View>(R.id.drift_container) ?: return
        driftView.animate().cancel()
        val anim = driftView.animate()
            .translationX(20f).translationY(15f)
            .setDuration(15000)
            .withEndAction {
                val nextDrift = adOverlay?.findViewById<View>(R.id.drift_container) ?: return@withEndAction
                nextDrift.animate()
                    .translationX(-20f).translationY(-15f)
                    .setDuration(15000)
                    .withEndAction { startDriftAnimation() }
                    .start()
            }
        anim.start()
    }

    private fun hideAd() {
        adOverlay?.findViewById<View>(R.id.drift_container)?.animate()?.cancel()
        if (!isAdShowing && rootContainer?.childCount == 0) return
        rootContainer?.removeAllViews()
        adOverlay = null
        isAdShowing = false
    }
}
