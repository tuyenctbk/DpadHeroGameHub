package com.tdpham.games.common

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.tdpham.games.R
import com.tdpham.games.hub.GuideManager

abstract class BaseGameActivity : AppCompatActivity() {
    
    protected abstract val gameKey: String
    protected abstract val gameTitle: String
    protected abstract val gameInstructions: String
    
    protected lateinit var gameView: GameView
    private lateinit var btnHelp: View
    private var isGuideShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutId())
        
        val view = findViewById<View>(getGameViewId())
        if (view is GameView) {
            gameView = view
            gameView.gameKey = gameKey
        } else {
            throw IllegalStateException("View must implement GameView interface")
        }

        btnHelp = findViewById(R.id.btn_show_guide)
        btnHelp.setOnClickListener { showGameGuide() }
        btnHelp.isFocusable = false
        btnHelp.isFocusableInTouchMode = false
        
        // Hide the help UI container to prevent any focus interference
        (btnHelp.parent as? View)?.visibility = View.GONE

        if (GuideManager.shouldShowGuide(this, gameKey)) {
            showGameGuide()
        } else {
            gameView.startGame()
            (view as View).requestFocus()
        }

        saveLastPlayed()
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
        GuideManager.showGuide(
            this, gameKey, gameTitle, gameInstructions,
            onDismiss = {
                isGuideShowing = false
                gameView.resume()
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
            finish()
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
