package com.tdpham.games.common

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tdpham.games.R

class PauseActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_TITLE = "extra_game_title"
        const val EXTRA_GAME_KEY = "extra_game_key"

        const val REQUEST_CODE_PAUSE = 9001

        const val RESULT_RESUME = Activity.RESULT_OK
        const val RESULT_OPTIONS = 101
        const val RESULT_RESTART = 102
        const val RESULT_EXIT = 103

        fun createIntent(activity: Activity, gameKey: String, gameTitle: String): Intent {
            return Intent(activity, PauseActivity::class.java).apply {
                putExtra(EXTRA_GAME_KEY, gameKey)
                putExtra(EXTRA_GAME_TITLE, gameTitle)
            }
        }
    }

    private lateinit var btnSound: Button
    private lateinit var btnMusic: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pause)

        val gameTitle = intent.getStringExtra(EXTRA_GAME_TITLE) ?: "ARCADE GAME"
        findViewById<TextView>(R.id.tv_pause_subtitle).text = gameTitle.uppercase()

        val btnResume = findViewById<Button>(R.id.btn_pause_resume)
        btnSound = findViewById(R.id.btn_pause_sound)
        btnMusic = findViewById(R.id.btn_pause_music)
        val btnOptions = findViewById<Button>(R.id.btn_pause_options)
        val btnRestart = findViewById<Button>(R.id.btn_pause_restart)
        val btnExit = findViewById<Button>(R.id.btn_pause_exit)

        updateSoundButtonText()
        updateMusicButtonText()

        val buttons = listOf(btnResume, btnSound, btnMusic, btnOptions, btnRestart, btnExit)
        buttons.forEach { button ->
            button.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this)
                    view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
        }

        btnResume.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            setResult(RESULT_RESUME)
            finish()
        }

        btnSound.setOnClickListener {
            SoundManager.toggleSound()
            HapticManager.vibrateClick(this)
            updateSoundButtonText()
        }

        btnMusic.setOnClickListener {
            SoundManager.toggleMusic()
            HapticManager.vibrateClick(this)
            updateMusicButtonText()
        }

        btnOptions.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            setResult(RESULT_OPTIONS)
            finish()
        }

        btnRestart.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            setResult(RESULT_RESTART)
            finish()
        }

        btnExit.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            setResult(RESULT_EXIT)
            finish()
        }

        btnResume.requestFocus()
    }

    private fun updateSoundButtonText() {
        val soundOn = SoundManager.isSoundEnabled()
        btnSound.text = if (soundOn) "${getString(R.string.sound_sfx_on)} 🔊" else "${getString(R.string.sound_sfx_off)} 🔇"
    }

    private fun updateMusicButtonText() {
        val musicOn = SoundManager.isMusicEnabled()
        btnMusic.text = if (musicOn) "${getString(R.string.music_on)} 🎵" else "${getString(R.string.music_off)} 🔇"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setResult(RESULT_RESUME)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
