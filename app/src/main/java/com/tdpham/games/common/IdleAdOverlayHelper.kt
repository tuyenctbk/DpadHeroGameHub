package com.tdpham.games.common

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.tdpham.games.R
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

class IdleAdOverlayHelper(private val activity: Activity) {

    private var rootContainer: FrameLayout? = null
    private var adOverlay: View? = null
    private var fullScreenAdDialog: Dialog? = null
    private var cornerAdDialog: Dialog? = null
    private var isAdShowing = false
    private var currentAdState: IdleAdManager.IdleState = IdleAdManager.IdleState.ACTIVE
    private val mainHandler = Handler(Looper.getMainLooper())
    private var adLoadTimeoutRunnable: Runnable? = null

    fun init() {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        rootContainer = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            elevation = 9999f
        }
        root.addView(rootContainer)
    }

    fun showState(state: IdleAdManager.IdleState, remainingToFull: Int) {
        currentAdState = state
        if (!AdManager.canShowIdleAd(activity)) {
            hideAd()
            return
        }

        rootContainer?.bringToFront()

        when (state) {
            IdleAdManager.IdleState.ACTIVE, IdleAdManager.IdleState.IDLE_YIELD -> hideAd()
            IdleAdManager.IdleState.IDLE_CORNER -> showCornerAd()
            IdleAdManager.IdleState.IDLE_PRE_FULL -> showWarning(remainingToFull)
            IdleAdManager.IdleState.IDLE_FULL, IdleAdManager.IdleState.IDLE_LOOP -> showFullScreenAd()
        }
    }

    private fun showCornerAd() {
        if (fullScreenAdDialog?.isShowing == true) {
            fullScreenAdDialog?.dismiss()
            fullScreenAdDialog = null
        }
        if (isAdShowing && cornerAdDialog?.isShowing == true) return
        
        val inflater = LayoutInflater.from(activity)
        adOverlay = inflater.inflate(R.layout.layout_native_ad_corner, null, false)
        
        val adView = adOverlay as NativeAdView
        val nativeAd = AdManager.getNextNativeAd(activity)
        if (nativeAd != null) {
            AdManager.populateNativeAdView(nativeAd, adView)
        } else {
            AdManager.populateFallbackAdView(adView)
        }
        isAdShowing = true
        
        ensureCornerDialog(adOverlay!!)
        
        // Soft slide-in animation
        adOverlay?.translationX = 400f
        adOverlay?.animate()?.translationX(0f)?.setDuration(500)?.start()
    }

    private fun ensureCornerDialog(view: View) {
        if (activity.isFinishing || activity.isDestroyed) return

        if (cornerAdDialog == null) {
            cornerAdDialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(false)
                window?.setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                )
                window?.setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                window?.setGravity(Gravity.BOTTOM or Gravity.END)
            }
        }
        cornerAdDialog?.setContentView(view)
        if (cornerAdDialog?.isShowing != true) {
            try {
                cornerAdDialog?.show()
            } catch (e: Throwable) {
                Log.e("IdleAdOverlayHelper", "Failed to show cornerAdDialog: ${e.message}", e)
            }
        }
    }

    private fun showWarning(seconds: Int) {
        if (cornerAdDialog?.isShowing == true) {
            cornerAdDialog?.dismiss()
            cornerAdDialog = null
        }

        val inflater = LayoutInflater.from(activity)
        adOverlay = inflater.inflate(R.layout.layout_native_ad_screensaver, null, false)
        
        val warningContainer = adOverlay?.findViewById<View>(R.id.warning_container)
        val warningText = adOverlay?.findViewById<TextView>(R.id.warning_text)
        warningContainer?.visibility = View.VISIBLE
        warningText?.text = activity.getString(R.string.ad_warning_prefix, seconds)
        
        // Hide the ad parts during warning
        adOverlay?.findViewById<View>(R.id.native_ad_view)?.visibility = View.GONE
        isAdShowing = true

        ensureFullScreenDialog(adOverlay!!)

        // Proactively request native ad if not pre-fetched yet
        if (AdManager.getNextNativeAd(activity) == null) {
            AdManager.loadNativeAd(activity)
        }
    }

    private fun showFullScreenAd() {
        if (cornerAdDialog?.isShowing == true) {
            cornerAdDialog?.dismiss()
            cornerAdDialog = null
        }

        val nativeAd = AdManager.getNextNativeAd(activity)
        if (nativeAd == null) {
            cancelAdTimeout()
            val runnable = Runnable {
                Log.w("IdleAdOverlayHelper", "Ad load timed out at countdown end - displaying fallback test ad")
                displayFullScreenAdView(null)
            }
            adLoadTimeoutRunnable = runnable
            mainHandler.postDelayed(runnable, 1500)

            AdManager.loadNativeAd(activity)
            AdManager.setOnNativeAdLoadedListener { loadedAd ->
                cancelAdTimeout()
                if (currentAdState == IdleAdManager.IdleState.IDLE_FULL || currentAdState == IdleAdManager.IdleState.IDLE_LOOP) {
                    displayFullScreenAdView(loadedAd)
                }
            }
            AdManager.setOnNativeAdFailedListener {
                cancelAdTimeout()
                displayFullScreenAdView(null)
            }
            return
        }
        
        displayFullScreenAdView(nativeAd)
    }

    private fun cancelAdTimeout() {
        adLoadTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        adLoadTimeoutRunnable = null
    }

    private fun displayFullScreenAdView(nativeAd: NativeAd?) {
        cancelAdTimeout()
        val inflater = LayoutInflater.from(activity)
        adOverlay = inflater.inflate(R.layout.layout_native_ad_screensaver, null, false)
        
        val adView = adOverlay?.findViewById<NativeAdView>(R.id.native_ad_view)
        if (adView != null) {
            if (nativeAd != null) {
                AdManager.populateNativeAdView(nativeAd, adView)
            } else {
                AdManager.populateFallbackAdView(adView)
            }
            adView.visibility = View.VISIBLE
        }
        
        adOverlay?.findViewById<View>(R.id.warning_container)?.visibility = View.GONE
        isAdShowing = true
        
        ensureFullScreenDialog(adOverlay!!)

        // OLED Drift Animation
        startDriftAnimation()
    }

    private fun ensureFullScreenDialog(view: View) {
        if (activity.isFinishing || activity.isDestroyed) return

        view.setOnClickListener {
            IdleAdManager.notifyInteraction()
            hideAd()
        }

        if (fullScreenAdDialog == null) {
            fullScreenAdDialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(true)
                setCanceledOnTouchOutside(true)
                setOnKeyListener { _, _, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        IdleAdManager.notifyInteraction()
                        hideAd()
                    }
                    true
                }
            }
        }
        fullScreenAdDialog?.setContentView(view)
        if (fullScreenAdDialog?.isShowing != true) {
            try {
                fullScreenAdDialog?.show()
            } catch (e: Throwable) {
                Log.e("IdleAdOverlayHelper", "Failed to show fullScreenAdDialog: ${e.message}", e)
            }
        }
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
        cancelAdTimeout()
        AdManager.setOnNativeAdLoadedListener(null)
        AdManager.setOnNativeAdFailedListener(null)
        adOverlay?.findViewById<View>(R.id.drift_container)?.animate()?.cancel()
        
        if (fullScreenAdDialog?.isShowing == true) {
            try {
                fullScreenAdDialog?.dismiss()
            } catch (e: Throwable) {
                Log.e("IdleAdOverlayHelper", "Failed to dismiss fullScreenAdDialog: ${e.message}", e)
            }
        }
        fullScreenAdDialog = null

        if (cornerAdDialog?.isShowing == true) {
            try {
                cornerAdDialog?.dismiss()
            } catch (e: Throwable) {
                Log.e("IdleAdOverlayHelper", "Failed to dismiss cornerAdDialog: ${e.message}", e)
            }
        }
        cornerAdDialog = null

        if (!isAdShowing && rootContainer?.childCount == 0) return
        rootContainer?.removeAllViews()
        adOverlay = null
        isAdShowing = false
    }
}
