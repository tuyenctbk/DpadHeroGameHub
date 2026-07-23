package com.tdpham.games.frenzy

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class FrenzyActivity : BaseGameActivity() {
    override val gameKey = "frenzy"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_frenzy))
    override val gameInstructions get() = getString(R.string.game_frenzy_instructions)

    override fun getLayoutId() = R.layout.activity_frenzy
    override fun getGameViewId() = R.id.frenzy_view
}
