package com.tdpham.games.lines98

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class Lines98Activity : BaseGameActivity() {
    override val gameKey = "lines98"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_lines98))
    override val gameInstructions get() = getString(R.string.game_lines98_instructions)

    override fun getLayoutId() = R.layout.activity_lines98
    override fun getGameViewId() = R.id.lines98_game_view
}
