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
    /** Static capability: true when this game supports rewarded revive (used to preload rewarded ads). */
    val supportsRevive: Boolean get() = canRevive
    /** Dynamic eligibility at game-over time: true when the current state can be revived (never true on win). */
    val canRevive: Boolean get() = false
    fun reviveGame(): Boolean = false
}
