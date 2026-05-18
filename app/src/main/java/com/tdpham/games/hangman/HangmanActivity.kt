package com.tdpham.games.hangman

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class HangmanActivity : BaseGameActivity() {
    override val gameKey = "hangman"
    override val gameTitle = "HANGMAN"
    override val gameInstructions = "• Select letters to guess the word.\n• You have 6 attempts.\n• Press Center to Guess."

    override fun getLayoutId() = R.layout.activity_hangman
    override fun getGameViewId() = R.id.hangman_view
}
