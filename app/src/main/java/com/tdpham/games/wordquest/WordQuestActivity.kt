package com.tdpham.games.wordquest

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class WordQuestActivity : BaseGameActivity() {
    override val gameKey = "word_quest"
    override val gameTitle = "WORD QUEST GUIDE"
    override val gameInstructions =
        "• Guess the 5-letter word in 6 tries.\n" +
            "• Use D-PAD to navigate keyboard.\n" +
            "• Press CENTER to select letter.\n" +
            "• GREEN: Correct letter & position.\n" +
            "• YELLOW: Correct letter, wrong position.\n" +
            "• GRAY: Letter not in word."

    override fun getLayoutId() = R.layout.activity_word_quest
    override fun getGameViewId() = R.id.word_view
}
