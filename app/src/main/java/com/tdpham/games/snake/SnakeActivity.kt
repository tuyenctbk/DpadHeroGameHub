package com.tdpham.games.snake

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SnakeActivity : BaseGameActivity() {
    override val gameKey = "snake"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.snake))
    override val gameInstructions get() = getString(R.string.snake_instructions)

    override fun getLayoutId() = R.layout.activity_snake
    override fun getGameViewId() = R.id.snake_game_view
}
