package com.tdpham.games.tanks

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class BattleTanksActivity : BaseGameActivity() {
    override val gameKey = "battle_tanks"
    override val gameTitle = "BATTLE TANKS GUIDE"
    override val gameInstructions =
        "• Control your tank with D-PAD.\n" +
            "• Tank fires automatically.\n" +
            "• Destroy enemy tanks.\n" +
            "• Protect your BASE (the star).\n" +
            "• Bricks can be destroyed."

    override fun getLayoutId() = R.layout.activity_battle_tanks
    override fun getGameViewId() = R.id.tanks_view
}
