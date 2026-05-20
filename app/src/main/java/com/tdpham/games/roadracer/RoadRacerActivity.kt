package com.tdpham.games.roadracer

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class RoadRacerActivity : BaseGameActivity() {
    override val gameKey = "road_racer"
    override val gameTitle get() = getString(R.string.game_road_racer)
    override val gameInstructions get() = getString(R.string.game_road_racer_instructions)

    override fun getLayoutId() = R.layout.activity_road_racer
    override fun getGameViewId() = R.id.road_racer_view
}
