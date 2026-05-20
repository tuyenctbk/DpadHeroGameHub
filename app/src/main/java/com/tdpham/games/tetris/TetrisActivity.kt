package com.tdpham.games.tetris

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class TetrisActivity : BaseGameActivity() {
    override val gameKey = "tetris"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_tetris))
    override val gameInstructions get() = getString(R.string.game_tetris_instructions)

    override fun getLayoutId() = R.layout.activity_tetris
    override fun getGameViewId() = R.id.tetris_view
}
