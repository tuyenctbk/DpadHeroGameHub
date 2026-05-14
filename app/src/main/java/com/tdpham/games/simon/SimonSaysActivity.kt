package com.tdpham.games.simon

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SimonSaysActivity : BaseGameActivity() {
    override val gameKey = "simon_says"
    override val gameTitle = "SIMON SAYS GUIDE"
    override val gameInstructions =
        "• Watch the sequence of colors.\n" +
            "• Repeat it using the D-PAD:\n" +
            "  - UP: Yellow\n" +
            "  - RIGHT: Green\n" +
            "  - DOWN: Red\n" +
            "  - LEFT: Blue\n" +
            "• Sequence gets longer each round!"

    override fun getLayoutId() = R.layout.activity_simon_says
    override fun getGameViewId() = R.id.simon_view
}
