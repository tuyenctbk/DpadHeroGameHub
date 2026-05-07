package com.tdpham.games.trex

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class TRexActivity : BaseGameActivity() {
    override val gameKey = "trex"
    override val gameTitle = "T-REX RUN GUIDE"
    override val gameInstructions = "• Press UP or CENTER to jump.\n• Avoid the cacti and birds!\n• The game gets faster over time.\n• Press DOWN to duck (if implemented).\n• Try to beat your high score!"

    override fun getLayoutId() = R.layout.activity_trex
    override fun getGameViewId() = R.id.trex_view
}
