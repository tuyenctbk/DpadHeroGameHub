package com.tdpham.games.sokoban

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SokobanActivity : BaseGameActivity() {
    override val gameKey = "sokoban"
    override val gameTitle = "SOKOBAN GUIDE"
    override val gameInstructions =
        "• D-PAD: move. Push boxes ($) onto targets (cyan rings); on-target shows as *.\n" +
            "• Only one box at a time; you cannot pull.\n" +
            "• CENTER: restart current level if stuck.\n" +
            "• When a level is solved, CENTER advances. Clear all levels for a high score."

    override fun getLayoutId() = R.layout.activity_sokoban
    override fun getGameViewId() = R.id.sokoban_view
}
