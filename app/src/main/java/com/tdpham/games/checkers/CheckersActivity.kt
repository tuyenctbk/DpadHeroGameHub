package com.tdpham.games.checkers

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class CheckersActivity : BaseGameActivity() {
    override val gameKey = "checkers"
    override val gameTitle = "CHECKERS GUIDE"
    override val gameInstructions =
        "• D-PAD: move cursor. CENTER: select square, then destination.\n" +
            "• Red (you) moves up; CPU moves down. Dark squares only.\n" +
            "• Captures are mandatory when possible; finish multi-jumps on the same piece (magenta outline).\n" +
            "• Reach the far row to crown a king (K); kings slide and capture on all diagonals.\n" +
            "• Clear all CPU pieces or block their moves to win."

    override fun getLayoutId() = R.layout.activity_checkers
    override fun getGameViewId() = R.id.checkers_view
}
