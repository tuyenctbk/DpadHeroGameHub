package com.tdpham.games.brickbreak

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class BrickBreakActivity : BaseGameActivity() {
    override val gameKey = "brick_break"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_brick_break))
    override val gameInstructions get() = getString(R.string.game_brick_break_instructions)

    override fun shouldShowHelpButton(): Boolean = true

    override fun getLayoutId() = R.layout.activity_brick_break
    override fun getGameViewId() = R.id.brick_break_view
}
