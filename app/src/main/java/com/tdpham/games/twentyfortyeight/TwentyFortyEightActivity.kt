package com.tdpham.games.twentyfortyeight

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class TwentyFortyEightActivity : BaseGameActivity() {
    override val gameKey = "4096"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_4096))
    override val gameInstructions get() = getString(R.string.game_4096_instructions)

    override fun getLayoutId() = R.layout.activity_2048
    override fun getGameViewId() = R.id.twenty_forty_eight_view
}
