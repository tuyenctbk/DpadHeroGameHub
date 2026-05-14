package com.tdpham.games.memory

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class MemoryActivity : BaseGameActivity() {
    override val gameKey = "memory"
    override val gameTitle = "MEMORY GUIDE"
    override val gameInstructions =
        "• Navigate with DPAD.\n" +
            "• Flip card with CENTER.\n" +
            "• Match all pairs to win.\n" +
            "• Fewer moves is better!"

    override fun getLayoutId() = R.layout.activity_memory
    override fun getGameViewId() = R.id.memory_view
}
