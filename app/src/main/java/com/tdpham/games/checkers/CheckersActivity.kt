package com.tdpham.games.checkers

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class CheckersActivity : BaseGameActivity() {
    override val gameKey = "checkers"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_checkers))
    override val gameInstructions get() = getString(R.string.game_checkers_instructions)

    override fun getLayoutId() = R.layout.activity_checkers
    override fun getGameViewId() = R.id.checkers_view
}
