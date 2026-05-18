package com.tdpham.games.spinball

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class SpinballActivity : BaseGameActivity() {
    override val gameKey: String = "spinball"
    override val gameTitle: String by lazy { getString(R.string.game_spinball) }
    override val gameInstructions: String by lazy { getString(R.string.spinball_instructions) }

    override fun getLayoutId(): Int = R.layout.activity_spinball
    override fun getGameViewId(): Int = R.id.spinball_view
}
