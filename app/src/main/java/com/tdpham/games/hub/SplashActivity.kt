package com.tdpham.games.hub

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.tdpham.games.R
import com.tdpham.games.common.AdManager
import com.tdpham.games.common.ConfigManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.profile.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false

    private val startMainRunnable = Runnable {
        navigateToMain()
    }

    private fun navigateToMain() {
        if (hasNavigated || isFinishing || isDestroyed) return
        hasNavigated = true
        handler.removeCallbacks(startMainRunnable)

        ProfileManager.ensureDefaultProfileExists(this)
        val lastPlayerId = ProfileManager.getLastPlayerId(this)
        val profiles = ProfileManager.getProfiles(this)

        val intent = if (lastPlayerId != null && profiles.any { it.id == lastPlayerId }) {
            ProfileManager.setActiveProfileId(this, lastPlayerId)
            Intent(this, MainActivity::class.java).apply {
                putExtra("AUTO_LOGGED_IN", true)
            }
        } else {
            val activeId = ProfileManager.getActiveProfileId(this) ?: profiles.first().id
            ProfileManager.setActiveProfileId(this, activeId)
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch(Dispatchers.IO) {
            SoundManager.init(this@SplashActivity)
            withContext(Dispatchers.Main) {
                if (!hasNavigated && !isFinishing && !isDestroyed) {
                    SoundManager.playRetroSplashChime()
                }
            }

            launch { ConfigManager.init() }
            launch {
                delay(150)
                AdManager.init(this@SplashActivity)
            }
        }

        val splashDurationMs = 1400L
        val canvasView = findViewById<RetroSplashCanvasView>(R.id.retro_splash_canvas)
        canvasView?.onSplashFinished = {
            navigateToMain()
        }
        canvasView?.startAnimation(splashDurationMs)

        findViewById<View>(android.R.id.content)?.setOnClickListener {
            navigateToMain()
        }

        hideSystemUI()

        // Fallback safety timer slightly after animation duration
        handler.postDelayed(startMainRunnable, splashDurationMs + 150L)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return super.onKeyDown(keyCode, event)
        }
        navigateToMain()
        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            navigateToMain()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(startMainRunnable)
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }
}
