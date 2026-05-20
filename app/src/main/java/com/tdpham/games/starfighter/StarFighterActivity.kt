package com.tdpham.games.starfighter

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class StarFighterActivity : BaseGameActivity() {
    override val gameKey = "star_fighter"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_starfighter))
    override val gameInstructions get() = getString(R.string.game_starfighter_instructions)

    override fun getLayoutId() = R.layout.activity_star_fighter
    override fun getGameViewId() = R.id.star_fighter_view
}
