package com.tdpham.games.trivia

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class TriviaActivity : BaseGameActivity() {
    override val gameKey = "trivia"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_trivia))
    override val gameInstructions get() = getString(R.string.game_trivia_instructions)

    override fun getLayoutId() = R.layout.activity_trivia
    override fun getGameViewId() = R.id.trivia_view
}
