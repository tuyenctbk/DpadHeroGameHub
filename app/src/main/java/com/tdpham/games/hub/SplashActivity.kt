package com.tdpham.games.hub

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tdpham.games.R
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.ConfigManager
import com.tdpham.games.common.AdManager


import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val startMainRunnable = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    private var pulseRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Run heavy initializations in background
        lifecycleScope.launch(Dispatchers.IO) {
            // Warm up SharedPreferences and SoundManager immediately
            SoundManager.init(this@SplashActivity)
            
            // Parallel init for Firebase and Ads to save time
            launch { ConfigManager.init() }
            launch { 
                delay(200) // Minimal gap for Binder stabilization
                AdManager.init(this@SplashActivity) 
            }
        }

        // Animation for splash content with staggered entry
        val logo = findViewById<android.view.View>(R.id.splash_logo)
        val title = findViewById<android.view.View>(R.id.splash_title)
        val loader = findViewById<android.view.View>(R.id.splash_loader)
        val hint = findViewById<android.view.View>(R.id.splash_hint)

        // Initial state
        listOf(logo, title, loader, hint).forEach {
            it.alpha = 0f
            it.translationY = 20f
        }

        // Staggered Entrance
        logo.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(100).start()
        title.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(250).start()
        loader.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(400).start()
        hint.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(550).start()

        // Subtle continuous pulse for logo
        val runnable = object : Runnable {
            override fun run() {
                logo.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(1000)
                    .withEndAction {
                        logo.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(1000)
                            .withEndAction(this)
                            .start()
                    }
                    .start()
            }
        }
        pulseRunnable = runnable
        logo.postDelayed(runnable, 600)

        // Hide system UI with modern approach for API 30+, fallback for older versions
        hideSystemUI()

        // Increased slightly to 1500ms to let the staggered animation breathe
        handler.postDelayed(startMainRunnable, 1800)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(startMainRunnable)
        pulseRunnable?.let {
            findViewById<android.view.View>(R.id.splash_logo)?.removeCallbacks(it)
        }
        findViewById<android.view.View>(R.id.splash_logo)?.animate()?.cancel()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use modern WindowInsets API for Android 11+ (API 30+)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.navigationBars())
            // Keep immersive by not showing transient bars - they'd interrupt gameplay
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // Fallback for Android 10 and below using deprecated but functional API
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }
}
