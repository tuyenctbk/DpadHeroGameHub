package com.tdpham.games.slidepuzzle

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SlidePuzzleActivity : BaseGameActivity() {
    override val gameKey = "slide_puzzle"
    override val gameTitle = "SLIDE PUZZLE GUIDE"
    override val gameInstructions =
        "• Use DPAD to move cursor.\n" +
            "• Press CENTER to slide tile.\n" +
            "• Reorder pieces to form picture.\n" +
            "• Numbers show correct order."

    override fun getLayoutId() = R.layout.activity_slide_puzzle
    override fun getGameViewId() = R.id.slide_puzzle_view
}
