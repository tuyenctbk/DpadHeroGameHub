package com.tdpham.games.syobon

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SyobonActivity : BaseGameActivity() {
    override val gameKey = "syobon_action"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_syobon))
    override val gameInstructions get() = getString(R.string.game_syobon_instructions)

    override fun getLayoutId() = R.layout.activity_syobon
    override fun getGameViewId() = R.id.syobon_view
}
