package com.tdpham.games.mentalmath

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MentalMathActivity : BaseGameActivity() {
    override val gameKey = "mental_math"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_mental_math))
    override val gameInstructions get() = getString(R.string.game_mental_math_instructions)

    override fun getLayoutId() = R.layout.activity_mental_math
    override fun getGameViewId() = R.id.mental_math_view
}
