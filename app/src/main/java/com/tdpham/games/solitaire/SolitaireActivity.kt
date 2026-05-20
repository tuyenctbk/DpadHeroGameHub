package com.tdpham.games.solitaire

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SolitaireActivity : BaseGameActivity() {
    override val gameKey = "solitaire"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_solitaire))
    override val gameInstructions get() = getString(R.string.game_solitaire_instructions)

    override fun getLayoutId() = R.layout.activity_solitaire
    override fun getGameViewId() = R.id.solitaire_game_view
}
