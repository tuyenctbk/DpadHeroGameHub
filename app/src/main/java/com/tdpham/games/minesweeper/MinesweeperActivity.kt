package com.tdpham.games.minesweeper

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MinesweeperActivity : BaseGameActivity() {
    override val gameKey = "minesweeper"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.minesweeper))
    override val gameInstructions get() = getString(R.string.minesweeper_instructions)

    override fun getLayoutId() = R.layout.activity_minesweeper
    override fun getGameViewId() = R.id.minesweeper_view
}
