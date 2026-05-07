package com.tdpham.games.minesweeper

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MinesweeperActivity : BaseGameActivity() {
    override val gameKey = "minesweeper"
    override val gameTitle = "MINESWEEPER GUIDE"
    override val gameInstructions = "• Use D-PAD to move the cursor.\n• Press CENTER to reveal a cell.\n• Press MENU or 'S' to flag a mine.\n• Clear all safe cells to win.\n• First move is always safe!"

    override fun getLayoutId() = R.layout.activity_minesweeper
    override fun getGameViewId() = R.id.minesweeper_view
}
