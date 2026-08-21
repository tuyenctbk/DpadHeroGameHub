package com.tdpham.games.connectfour

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class ConnectFourActivity : BaseGameActivity() {
    override val gameKey = "connect_four"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_connect_four))
    override val gameInstructions get() = getString(R.string.game_connect_four_instructions)

    override fun getLayoutId() = R.layout.activity_connect_four
    override fun getGameViewId() = R.id.connect_four_view
}
