package com.tdpham.games.sudoku

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SudokuActivity : BaseGameActivity() {
    override val gameKey = "sudoku"
    override val gameTitle = "SUDOKU GUIDE"
    override val gameInstructions =
        "• D-PAD: move cell. CENTER: cycle value 0 (clear) through 9 on empty cells.\n" +
            "• Given numbers (white) are locked.\n" +
            "• No repeats in any row, column, or 3×3 box; conflicts tint red.\n" +
            "• When the grid is complete and valid, CENTER loads the next puzzle."

    override fun getLayoutId() = R.layout.activity_sudoku
    override fun getGameViewId() = R.id.sudoku_view
}
