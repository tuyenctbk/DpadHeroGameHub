package com.tdpham.games.common

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase
import com.tdpham.games.R
import com.tdpham.games.hub.GuideManager

abstract class BaseGameActivity : AppCompatActivity() {
    
    protected abstract val gameKey: String
    protected abstract val gameTitle: String
    protected abstract val gameInstructions: String
    
    protected lateinit var gameView: GameView
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var btnHelp: View
    private var isGuideShowing = false
    private var hasStarted = false

    protected open fun shouldShowHelpButton(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutId())

        firebaseAnalytics = Firebase.analytics
        
        val view = findViewById<View>(getGameViewId())
        if (view is GameView) {
            gameView = view
            gameView.gameKey = gameKey
            gameView.onGameOver = { score ->
                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Param.LEVEL_NAME, gameKey)
                bundle.putInt(FirebaseAnalytics.Param.SCORE, score)
                firebaseAnalytics.logEvent("level_end", bundle)
            }
        } else {
            throw IllegalStateException("View must implement GameView interface")
        }

        btnHelp = findViewById(R.id.btn_show_guide)
        btnHelp.setOnClickListener { showGameGuide() }
        btnHelp.isFocusable = false
        btnHelp.isFocusableInTouchMode = false
        
        // Hide or show the help UI container based on game preference
        val helpContainer = (btnHelp.parent as? View)
        if (shouldShowHelpButton()) {
            helpContainer?.visibility = View.VISIBLE
        } else {
            helpContainer?.visibility = View.GONE
        }

        if (GuideManager.shouldShowGuide(this, gameKey)) {
            showGameGuide()
        } else {
            startGameWithAnalytics()
            (view as View).requestFocus()
        }

        saveLastPlayed()
    }

    private fun startGameWithAnalytics() {
        hasStarted = true
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.LEVEL_NAME, gameKey)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LEVEL_START, bundle)
        gameView.startGame()
    }

    private fun saveLastPlayed() {
        val prefs = getSharedPreferences("game_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("last_played", gameKey).apply()
    }

    abstract fun getLayoutId(): Int
    abstract fun getGameViewId(): Int

    protected fun showGameGuide() {
        isGuideShowing = true
        gameView.pause()
        val btnText = if (hasStarted) getString(R.string.resume) else getString(R.string.start_game)
        GuideManager.showGuide(
            this, gameKey, gameTitle, gameInstructions, btnText,
            onDismiss = {
                isGuideShowing = false
                if (!hasStarted) {
                    startGameWithAnalytics()
                } else {
                    AdManager.showInterstitial(this) {
                        gameView.resume()
                    }
                }
                (gameView as View).requestFocus()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (!isGuideShowing) {
            gameView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            AdManager.showInterstitial(this) {
                finish()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            gameView.toggleSound()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_H || keyCode == KeyEvent.KEYCODE_INFO) {
            showGameGuide()
            return true
        }
        return (gameView as View).onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return (gameView as View).onKeyUp(keyCode, event)
    }
}
