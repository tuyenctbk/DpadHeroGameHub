package com.tdpham.games.tetris

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class TetrisActivity : BaseGameActivity() {
    override val gameKey = "tetris"
    override val gameTitle = "TETRIS GUIDE"
    override val gameInstructions =
        "• Move piece with LEFT/RIGHT.\n" +
            "• Rotate with UP.\n" +
            "• Soft drop with DOWN.\n" +
            "• Press CENTER to pause/resume."

    override fun getLayoutId() = R.layout.activity_tetris
    override fun getGameViewId() = R.id.tetris_view
}
