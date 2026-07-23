package com.tdpham.games.fruit

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class FruitActivity : BaseGameActivity() {
    override val gameKey = "fruit"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_fruit))
    override val gameInstructions get() = getString(R.string.game_fruit_instructions)

    override fun getLayoutId() = R.layout.activity_fruit
    override fun getGameViewId() = R.id.fruit_view
}
