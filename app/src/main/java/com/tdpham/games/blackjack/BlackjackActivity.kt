package com.tdpham.games.blackjack

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class BlackjackActivity : BaseGameActivity() {
    override val gameKey = "blackjack"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_blackjack))
    override val gameInstructions get() = getString(R.string.game_blackjack_instructions)

    override fun getLayoutId() = R.layout.activity_blackjack
    override fun getGameViewId() = R.id.blackjack_view
}
