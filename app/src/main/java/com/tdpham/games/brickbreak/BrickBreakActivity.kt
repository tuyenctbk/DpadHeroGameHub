package com.tdpham.games.brickbreak

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class BrickBreakActivity : BaseGameActivity() {
    override val gameKey = "brick_break"
    override val gameTitle = "BRICK BREAK GUIDE"
    override val gameInstructions =
        "• Move paddle with LEFT/RIGHT.\n" +
            "• Press CENTER to launch ball or pause.\n" +
            "• Break all bricks to clear the stage.\n" +
            "• You have 3 lives. Don't let the ball fall.\n" +
            "• Press 'S' or MUTE to toggle sound."

    override fun shouldShowHelpButton(): Boolean = true

    override fun getLayoutId() = R.layout.activity_brick_break
    override fun getGameViewId() = R.id.brick_break_view
}
