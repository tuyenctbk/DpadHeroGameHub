package com.tdpham.games.maze

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MazeActivity : BaseGameActivity() {
    override val gameKey = "maze"
    override val gameTitle = "MAZE GUIDE"
    override val gameInstructions =
        "• Study the maze carefully.\n" +
            "• Find which exit (image) is connected to the green dot.\n" +
            "• Use LEFT/RIGHT to select.\n" +
            "• Press CENTER to confirm.\n" +
            "• One chance per stage!"

    override fun getLayoutId() = R.layout.activity_maze
    override fun getGameViewId() = R.id.maze_view
}
