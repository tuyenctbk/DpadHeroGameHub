package com.tdpham.games.flappy

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class FlappyHeroActivity : BaseGameActivity() {
    override val gameKey = "flappy_hero"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_flappy))
    override val gameInstructions get() = getString(R.string.game_flappy_instructions)

    override fun getLayoutId() = R.layout.activity_flappy_hero
    override fun getGameViewId() = R.id.flappy_view
}
