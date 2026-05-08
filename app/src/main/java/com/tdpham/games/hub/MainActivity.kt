package com.tdpham.games.hub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.tdpham.games.R
import com.tdpham.games.brickbreak.BrickBreakActivity
import com.tdpham.games.checkers.CheckersActivity
import com.tdpham.games.snake.SnakeActivity
import com.tdpham.games.minesweeper.MinesweeperActivity
import com.tdpham.games.sokoban.SokobanActivity
import com.tdpham.games.sudoku.SudokuActivity
import com.tdpham.games.twentyfortyeight.TwentyFortyEightActivity
import com.tdpham.games.trex.TRexActivity
import com.tdpham.games.tictactoe.TicTacToeActivity
import com.tdpham.games.common.SoundManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ImageButton>(R.id.btn_settings).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }

        val btnSnake = findViewById<Button>(R.id.btn_snake)
        setupGameButton(btnSnake) {
            startActivity(Intent(this, SnakeActivity::class.java))
        }

        val btnMinesweeper = findViewById<Button>(R.id.btn_minesweeper)
        setupGameButton(btnMinesweeper) {
            startActivity(Intent(this, MinesweeperActivity::class.java))
        }

        val btn4096 = findViewById<Button>(R.id.btn_4096)
        setupGameButton(btn4096) {
            startActivity(Intent(this, TwentyFortyEightActivity::class.java))
        }

        val btnTRex = findViewById<Button>(R.id.btn_trex)
        setupGameButton(btnTRex) {
            startActivity(Intent(this, TRexActivity::class.java))
        }

        val btnBrickBreak = findViewById<Button>(R.id.btn_brick_break)
        setupGameButton(btnBrickBreak) {
            startActivity(Intent(this, BrickBreakActivity::class.java))
        }

        val btnTicTacToe = findViewById<Button>(R.id.btn_tictactoe)
        setupGameButton(btnTicTacToe) {
            startActivity(Intent(this, TicTacToeActivity::class.java))
        }

        val btnCheckers = findViewById<Button>(R.id.btn_checkers)
        setupGameButton(btnCheckers) {
            startActivity(Intent(this, CheckersActivity::class.java))
        }

        val btnSokoban = findViewById<Button>(R.id.btn_sokoban)
        setupGameButton(btnSokoban) {
            startActivity(Intent(this, SokobanActivity::class.java))
        }

        val btnSudoku = findViewById<Button>(R.id.btn_sudoku)
        setupGameButton(btnSudoku) {
            startActivity(Intent(this, SudokuActivity::class.java))
        }

        focusLastPlayed()
    }

    private fun focusLastPlayed() {
        val prefs = getSharedPreferences("game_settings", Context.MODE_PRIVATE)
        val lastPlayed = prefs.getString("last_played", "snake")
        
        when (lastPlayed) {
            "snake" -> findViewById<Button>(R.id.btn_snake).requestFocus()
            "minesweeper" -> findViewById<Button>(R.id.btn_minesweeper).requestFocus()
            "4096" -> findViewById<Button>(R.id.btn_4096).requestFocus()
            "trex" -> findViewById<Button>(R.id.btn_trex).requestFocus()
            "brick_break" -> findViewById<Button>(R.id.btn_brick_break).requestFocus()
            "tic_tac_toe" -> findViewById<Button>(R.id.btn_tictactoe).requestFocus()
            "checkers" -> findViewById<Button>(R.id.btn_checkers).requestFocus()
            "sokoban" -> findViewById<Button>(R.id.btn_sokoban).requestFocus()
            "sudoku" -> findViewById<Button>(R.id.btn_sudoku).requestFocus()
            else -> findViewById<Button>(R.id.btn_snake).requestFocus()
        }
    }

    private fun setupGameButton(button: Button, action: () -> Unit) {
        button.setOnClickListener { action() }
        
        button.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Brighter (handled by selector) + Scale Up
                view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start()
                view.elevation = 20f
            } else {
                // Back to normal
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                view.elevation = 8f
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
