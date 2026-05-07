package com.tdpham.games.snake

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SnakeActivity : BaseGameActivity() {
    override val gameKey = "snake"
    override val gameTitle = "SNAKE GUIDE"
    override val gameInstructions = "• Use D-PAD to move the snake.\n• Eat red apples to grow and score.\n• Don't hit walls or yourself!\n• Press 'S' or MUTE to toggle sound.\n• Press CENTER to pause."

    override fun getLayoutId() = R.layout.activity_snake
    override fun getGameViewId() = R.id.snake_game_view
}
