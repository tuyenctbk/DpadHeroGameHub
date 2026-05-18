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

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        SoundManager.init(this)
        ConfigManager.init()
        AdManager.init(this)

        // Hide system UI with modern approach for API 30+, fallback for older versions
        hideSystemUI()

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2000)
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
