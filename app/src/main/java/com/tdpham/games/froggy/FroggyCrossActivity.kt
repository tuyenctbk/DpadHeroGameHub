package com.tdpham.games.froggy

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class FroggyCrossActivity : BaseGameActivity() {
    override val gameKey = "froggy_cross"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_froggy))
    override val gameInstructions get() = getString(R.string.game_froggy_instructions)

    override fun getLayoutId() = R.layout.activity_froggy_cross
    override fun getGameViewId() = R.id.froggy_view
}
