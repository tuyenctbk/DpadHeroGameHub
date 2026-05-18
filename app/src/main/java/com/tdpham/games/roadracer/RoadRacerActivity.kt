package com.tdpham.games.roadracer

import com.tdpham.games.R
import com.tdpham.games.common.BaseGameActivity

class RoadRacerActivity : BaseGameActivity() {
    override val gameKey = "road_racer"
    override val gameTitle = "ROAD RACER"
    override val gameInstructions = "• Move Left/Right to avoid traffic.\n• Survive as long as you can!\n• Press Center to Start/Pause."

    override fun getLayoutId() = R.layout.activity_road_racer
    override fun getGameViewId() = R.id.road_racer_view
}
