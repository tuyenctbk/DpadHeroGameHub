package com.tdpham.games.dungeon

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class DungeonEscapeActivity : BaseGameActivity() {
    override val gameKey = "dungeon_escape"
    override val gameTitle = "DUNGEON ESCAPE GUIDE"
    override val gameInstructions =
        "• Navigate the dungeon with D-PAD.\n" +
            "• Collect the GOLDEN KEY.\n" +
            "• Reach the EXIT DOOR to advance.\n" +
            "• AVOID RED SPIKES at all costs!\n" +
            "• Levels get harder with more obstacles."

    override fun getLayoutId() = R.layout.activity_dungeon_escape
    override fun getGameViewId() = R.id.dungeon_view
}
