package com.tdpham.games.simon

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SimonSaysActivity : BaseGameActivity() {
    override val gameKey = "simon_says"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_simon))
    override val gameInstructions get() = getString(R.string.game_simon_instructions)

    override fun getLayoutId() = R.layout.activity_simon_says
    override fun getGameViewId() = R.id.simon_view
}
