package com.tdpham.games.wordquest

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class WordQuestActivity : BaseGameActivity() {
    override val gameKey = "word_quest"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_word_quest))
    override val gameInstructions get() = getString(R.string.game_word_quest_instructions)

    override fun getLayoutId() = R.layout.activity_word_quest
    override fun getGameViewId() = R.id.word_view
}
