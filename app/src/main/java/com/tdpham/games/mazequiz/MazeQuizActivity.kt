package com.tdpham.games.mazequiz

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MazeQuizActivity : BaseGameActivity() {
    override val gameKey = "maze_quiz"
    override val gameTitle = "MAZE QUIZ GUIDE"
    override val gameInstructions =
        "• Study the maze carefully.\n" +
            "• Find which exit (A, B, C, D) is connected to the green dot.\n" +
            "• Use LEFT/RIGHT to select.\n" +
            "• Press CENTER to confirm.\n" +
            "• One chance per stage!"

    override fun getLayoutId() = R.layout.activity_maze_quiz
    override fun getGameViewId() = R.id.maze_quiz_view
}
