package com.tdpham.games.slidepuzzle

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SlidePuzzleActivity : BaseGameActivity() {
    override val gameKey = "slide_puzzle"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_slide_puzzle))
    override val gameInstructions get() = getString(R.string.game_slide_puzzle_instructions)

    override fun getLayoutId() = R.layout.activity_slide_puzzle
    override fun getGameViewId() = R.id.slide_puzzle_view
}
