package com.tdpham.games.solitaire

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SolitaireActivity : BaseGameActivity() {
    override val gameKey = "solitaire"
    override val gameTitle = "SOLITAIRE GUIDE"
    override val gameInstructions = "• Use D-PAD to move the cursor.\n• Press CENTER to select/pick up cards, and CENTER to place them.\n• Build foundations by suit from Ace to King.\n• Build tableaus by alternating color and descending rank.\n• Press 'S' or MUTE to toggle sound."

    override fun getLayoutId() = R.layout.activity_solitaire
    override fun getGameViewId() = R.id.solitaire_game_view
}
