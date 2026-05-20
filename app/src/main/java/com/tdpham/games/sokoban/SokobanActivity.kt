package com.tdpham.games.sokoban

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SokobanActivity : BaseGameActivity() {
    override val gameKey = "sokoban"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_sokoban))
    override val gameInstructions get() = getString(R.string.game_sokoban_instructions)

    override fun getLayoutId() = R.layout.activity_sokoban
    override fun getGameViewId() = R.id.sokoban_view
}
