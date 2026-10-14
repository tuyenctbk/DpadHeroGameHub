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
import com.tdpham.games.common.profile.ProfileManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var isSkipped = false

    private val startMainRunnable = Runnable {
        if (isFinishing || isDestroyed) return@Runnable
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
        finish()
    }

    private fun skipSplash() {
        if (!isSkipped) {
            isSkipped = true
            handler.removeCallbacks(startMainRunnable)
            startMainRunnable.run()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize audio and play custom 1-second retro chime sequence
        lifecycleScope.launch(Dispatchers.IO) {
            SoundManager.init(this@SplashActivity)
            withContext(Dispatchers.Main) {
                SoundManager.playRetroSplashChime()
            }
            
            // Asynchronous background pre-warming
            launch { ConfigManager.init() }
            launch { 
                delay(150)
                AdManager.init(this@SplashActivity) 
            }
        }

        // Configure 1-second Canvas animation
        val canvasView = findViewById<RetroSplashCanvasView>(R.id.retro_splash_canvas)
        canvasView?.onSplashFinished = {
            skipSplash()
        }
        canvasView?.startAnimation(1000L)

        // Set skippable click listener on root view
        findViewById<android.view.View>(android.R.id.content)?.setOnClickListener {
            skipSplash()
        }

        hideSystemUI()

        // 1-second fallback navigation timer
        handler.postDelayed(startMainRunnable, 1000L)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        skipSplash()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        skipSplash()
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
            window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }
}
