package com.tdpham.games.common

interface GameView {
    var gameKey: String
    fun startGame()
    fun pause()
    fun resume()
    fun resetGame()
    fun toggleSound(): Boolean
}
