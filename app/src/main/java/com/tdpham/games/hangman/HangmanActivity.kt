package com.tdpham.games.hangman

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class HangmanActivity : BaseGameActivity() {
    override val gameKey = "hangman"
    override val gameTitle get() = getString(R.string.game_hangman)
    override val gameInstructions get() = getString(R.string.game_hangman_instructions)

    override fun getLayoutId() = R.layout.activity_hangman
    override fun getGameViewId() = R.id.hangman_view
}
