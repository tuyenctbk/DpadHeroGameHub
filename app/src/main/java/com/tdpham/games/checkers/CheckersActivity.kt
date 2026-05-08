package com.tdpham.games.checkers

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class CheckersActivity : BaseGameActivity() {
    override val gameKey = "checkers"
    override val gameTitle = "CHECKERS GUIDE"
    override val gameInstructions =
        "• Move cursor with D-PAD.\n" +
            "• Press CENTER to select and move a piece.\n" +
            "• Red moves up, CPU moves down.\n" +
            "• Capture all CPU pieces to win."

    override fun getLayoutId() = R.layout.activity_checkers
    override fun getGameViewId() = R.id.checkers_view
}
