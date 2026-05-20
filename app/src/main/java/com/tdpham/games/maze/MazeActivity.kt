package com.tdpham.games.maze

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MazeActivity : BaseGameActivity() {
    override val gameKey = "maze"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_maze))
    override val gameInstructions get() = getString(R.string.game_maze_instructions)

    override fun getLayoutId() = R.layout.activity_maze
    override fun getGameViewId() = R.id.maze_view
}
