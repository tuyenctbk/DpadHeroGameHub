package com.tdpham.games.tictactoe

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class TicTacToeActivity : BaseGameActivity() {
    override val gameKey = "tic_tac_toe"
    override val gameTitle = "TIC-TAC-TOE GUIDE"
    override val gameInstructions =
        "• Move cursor with D-PAD.\n" +
            "• Press CENTER to place X.\n" +
            "• Beat the CPU by matching a row.\n" +
            "• Turn alternates each match.\n" +
            "• Change Board Size (3x3, 4x4, 5x5)\n" +
            "  with UP/DOWN when Game Over\n" +
            "  or press MENU any time."

    override fun getLayoutId() = R.layout.activity_tic_tac_toe
    override fun getGameViewId() = R.id.tic_tac_toe_view
}
