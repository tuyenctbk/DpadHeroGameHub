package com.tdpham.games.tictactoe

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class TicTacToeActivity : BaseGameActivity() {
    override val gameKey = "tic_tac_toe"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_tictactoe))
    override val gameInstructions get() = getString(R.string.game_tictactoe_instructions)

    override fun getLayoutId() = R.layout.activity_tic_tac_toe
    override fun getGameViewId() = R.id.tic_tac_toe_view
}
