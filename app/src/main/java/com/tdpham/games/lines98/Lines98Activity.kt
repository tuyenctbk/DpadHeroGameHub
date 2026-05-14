package com.tdpham.games.lines98

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class Lines98Activity : BaseGameActivity() {
    override val gameKey = "lines98"
    override val gameTitle = "LINES 98 GUIDE"
    override val gameInstructions = "• Use D-PAD to move the cursor.\n• Press CENTER to select a ball, then press CENTER on an empty cell to move it.\n• Align 5 or more balls of the same color (horizontal, vertical, or diagonal) to score and clear them.\n• Each move that doesn't clear lines adds 3 new balls.\n• Game ends when the board is full.\n• Press 'S' or MUTE to toggle sound."

    override fun getLayoutId() = R.layout.activity_lines98
    override fun getGameViewId() = R.id.lines98_game_view
}
