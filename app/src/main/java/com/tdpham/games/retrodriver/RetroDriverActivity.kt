package com.tdpham.games.retrodriver

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class RetroDriverActivity : BaseGameActivity() {
    override val gameKey = "retrodriver"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_retrodriver))
    override val gameInstructions get() = getString(R.string.game_retrodriver_instructions)

    override fun getLayoutId() = R.layout.activity_retro_driver
    override fun getGameViewId() = R.id.retrodriver_view
}
