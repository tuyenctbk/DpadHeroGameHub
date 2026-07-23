package com.tdpham.games.monkey

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MonkeyActivity : BaseGameActivity() {
    override val gameKey = "monkey"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_monkey))
    override val gameInstructions get() = getString(R.string.game_monkey_instructions)

    override fun getLayoutId() = R.layout.activity_monkey
    override fun getGameViewId() = R.id.monkey_view
}
