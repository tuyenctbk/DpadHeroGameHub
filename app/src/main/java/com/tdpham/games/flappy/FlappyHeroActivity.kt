package com.tdpham.games.flappy

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class FlappyHeroActivity : BaseGameActivity() {
    override val gameKey = "flappy_hero"
    override val gameTitle = "FLAPPY HERO GUIDE"
    override val gameInstructions =
        "• Press CENTER to flap your wings.\n" +
            "• Fly through the gaps in the pipes.\n" +
            "• Don't touch the pipes or the ground!\n" +
            "• Score 1 point for every pipe cleared.\n" +
            "• Test your reflexes!"

    override fun getLayoutId() = R.layout.activity_flappy_hero
    override fun getGameViewId() = R.id.flappy_view
}
