package com.tdpham.games.starfighter

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class StarFighterActivity : BaseGameActivity() {
    override val gameKey = "star_fighter"
    override val gameTitle = "STAR FIGHTER GUIDE"
    override val gameInstructions =
        "• Move ship with D-PAD.\n" +
            "• Lasers fire automatically.\n" +
            "• Destroy enemies to score.\n" +
            "• Don't let enemies hit you!\n" +
            "• Press 'S' to toggle sound."

    override fun getLayoutId() = R.layout.activity_star_fighter
    override fun getGameViewId() = R.id.star_fighter_view
}
