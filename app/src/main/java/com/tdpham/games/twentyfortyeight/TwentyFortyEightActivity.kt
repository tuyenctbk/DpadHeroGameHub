package com.tdpham.games.twentyfortyeight

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class TwentyFortyEightActivity : BaseGameActivity() {
    override val gameKey = "4096"
    override val gameTitle = "4096 GUIDE"
    override val gameInstructions = "• Use D-PAD to slide tiles.\n• Tiles with same numbers merge.\n• Reach the 4096 tile to win!\n• Game ends when the board is full.\n• Press CENTER to restart if stuck."

    override fun getLayoutId() = R.layout.activity_2048
    override fun getGameViewId() = R.id.twenty_forty_eight_view
}
