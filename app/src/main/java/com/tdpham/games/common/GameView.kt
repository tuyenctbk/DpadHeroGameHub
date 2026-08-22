package com.tdpham.games.common

import android.content.Context

interface GameView {
    var gameKey: String
    var onGameOver: ((Int) -> Unit)?
    fun startGame()
    fun pause()
    fun resume()
    fun resetGame()
    fun toggleSound(): Boolean
    fun showOptionsDialog(context: Context, onComplete: () -> Unit): Boolean = false
}
