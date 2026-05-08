package com.tdpham.games.sudoku

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SudokuActivity : BaseGameActivity() {
    override val gameKey = "sudoku"
    override val gameTitle = "SUDOKU GUIDE"
    override val gameInstructions =
        "• Move cursor with D-PAD.\n" +
            "• Press CENTER to cycle number 1-9.\n" +
            "• Fill all empty cells correctly.\n" +
            "• Press CENTER after finish to restart."

    override fun getLayoutId() = R.layout.activity_sudoku
    override fun getGameViewId() = R.id.sudoku_view
}
