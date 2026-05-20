package com.tdpham.games.dungeon

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class DungeonEscapeActivity : BaseGameActivity() {
    override val gameKey = "dungeon_escape"
    override val gameTitle get() = getString(R.string.how_to_play_guide, getString(R.string.game_dungeon))
    override val gameInstructions get() = getString(R.string.game_dungeon_instructions)

    override fun getLayoutId() = R.layout.activity_dungeon_escape
    override fun getGameViewId() = R.id.dungeon_view
}
