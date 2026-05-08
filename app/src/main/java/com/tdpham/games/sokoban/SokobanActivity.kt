package com.tdpham.games.sokoban

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SokobanActivity : BaseGameActivity() {
    override val gameKey = "sokoban"
    override val gameTitle = "SOKOBAN GUIDE"
    override val gameInstructions =
        "• Move with D-PAD.\n" +
            "• Push all boxes onto target tiles.\n" +
            "• You can only push one box at a time.\n" +
            "• Press CENTER to restart level."

    override fun getLayoutId() = R.layout.activity_sokoban
    override fun getGameViewId() = R.id.sokoban_view
}
