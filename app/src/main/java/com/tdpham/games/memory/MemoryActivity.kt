package com.tdpham.games.memory

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MemoryActivity : BaseGameActivity() {
    override val gameKey = "memory"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_memory))
    override val gameInstructions get() = getString(R.string.game_memory_instructions)

    override fun getLayoutId() = R.layout.activity_memory
    override fun getGameViewId() = R.id.memory_view
}
