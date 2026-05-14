package com.tdpham.games.froggy

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class FroggyCrossActivity : BaseGameActivity() {
    override val gameKey = "froggy_cross"
    override val gameTitle = "FROGGY CROSS GUIDE"
    override val gameInstructions =
        "• Cross the road and river.\n" +
            "• Avoid cars on the road.\n" +
            "• Jump on logs in the river.\n" +
            "• Reach the top to score!\n" +
            "• Use D-PAD to move."

    override fun getLayoutId() = R.layout.activity_froggy_cross
    override fun getGameViewId() = R.id.froggy_view
}
