package com.tdpham.games.sudoku

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SudokuActivity : BaseGameActivity() {
    override val gameKey = "sudoku"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_sudoku))
    override val gameInstructions get() = getString(R.string.game_sudoku_instructions)

    override fun getLayoutId() = R.layout.activity_sudoku
    override fun getGameViewId() = R.id.sudoku_view
}
