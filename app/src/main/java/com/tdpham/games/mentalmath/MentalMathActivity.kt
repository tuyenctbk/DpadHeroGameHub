package com.tdpham.games.mentalmath

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MentalMathActivity : BaseGameActivity() {
    override val gameKey = "mental_math"
    override val gameTitle = "MENTAL MATH GUIDE"
    override val gameInstructions =
        "• Solve the math problem.\n" +
            "• Use DPAD to select answer.\n" +
            "• Press CENTER to confirm.\n" +
            "• Be quick! Timer is running.\n" +
            "• Wrong answer ends game."

    override fun getLayoutId() = R.layout.activity_mental_math
    override fun getGameViewId() = R.id.mental_math_view
}
