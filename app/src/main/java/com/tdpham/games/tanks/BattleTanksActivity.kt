package com.tdpham.games.tanks

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class BattleTanksActivity : BaseGameActivity() {
    override val gameKey = "battle_tanks"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_tanks))
    override val gameInstructions get() = getString(R.string.game_tanks_instructions)

    override fun getLayoutId() = R.layout.activity_battle_tanks
    override fun getGameViewId() = R.id.tanks_view
}
