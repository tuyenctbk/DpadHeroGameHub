package com.tdpham.games.roadracer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import java.util.*

class RoadRacerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "road_racer"
    override var onGameOver: ((Int) -> Unit)? = null

    private var score = 0
    private var highScore = 0
    private var isGameOver = false
    private var isPaused = true
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "roadracer_settings"
    private val KEY_DENSITY = "traffic_density_index"
    private var trafficDensity = 1 // 0: Low, 1: Normal, 2: High
    private var hintShowFrames = 0
    private var isInitialized = false
    private var gameSpeed = 10f
    
    private val playerWidth = 80f
    private val playerHeight = 140f
    private var playerLane = 1 // 0, 1, 2

    private val obstacles = mutableListOf<Obstacle>()
    private val random = Random()
    private var nextObstacleTimer = 0f
    
    private var roadOffset = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (isGameOver || isPaused || hintShowFrames > 0) {
                celebrationManager.update()
                invalidate()
            }
            handler.postDelayed(this, 50)
        }
    }
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameOver && !isPaused) {
                update()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    // Environment
    private var isNightMode = false
    private var currentScene = Scene.FIELD
    private var currentWeather = GameEnvironment.WeatherType.NONE
    private var lastThemeToggle = 0
    private val particles = mutableListOf<GameEnvironment.Particle>()

    enum class Scene { FIELD, CITY, DESERT, SNOWY_MTN }
    enum class ObstacleType { CAR, TRUCK, CONE, OIL_SPILL, POTHOLE, BARRIER, AMBULANCE }
    data class Obstacle(val lane: Int, var y: Float, val type: ObstacleType, val variant: Int = 0)
    data class RoadTheme(val roadColor: Int, val sideColor: Int, val dashColor: Int, val carColors: List<Int>)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        handler.post(animRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun startGame() {
        isPaused = false
        invalidate()
    }

    override fun pause() {
        isPaused = true
        handler.removeCallbacks(gameLoop)
    }
    override fun resume() {
        isPaused = false
        handler.removeCallbacks(gameLoop)
        handler.post(gameLoop)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameLoop)
        handler.removeCallbacks(animRunnable)
    }

    override fun resetGame() {
        // Load density from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        trafficDensity = prefs.getInt(KEY_DENSITY, 1).coerceIn(0, 2)

        score = 0
        highScore = ScoreManager.getHighScore(context, gameKey, trafficDensity)
        isGameOver = false
        isPaused = true
        celebrationManager.start(0f, 0f)
        gameSpeed = 10f
        playerLane = 1
        obstacles.clear()
        roadOffset = 0f
        nextObstacleTimer = 0f
        isNightMode = false
        currentScene = Scene.FIELD
        currentWeather = GameEnvironment.WeatherType.NONE
        particles.clear()
        repeat(30) {
            particles.add(GameEnvironment.Particle(random.nextFloat() * 2000, random.nextFloat() * 1000, random.nextFloat() * 10 + 5))
        }
        
        hintShowFrames = 100
        invalidate()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame(); startGame(); return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (isPaused) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                startGame(); return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (playerLane > 0) { playerLane--; SoundManager.playClick() } }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (playerLane < 2) { playerLane++; SoundManager.playClick() } }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                pause()
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> {
                showOptions()
                return true
            }
        }
        invalidate()
        return true
    }

    private fun showOptions() {
        pause()
        RoadRacerOptionsDialog.show(context) {
            resetGame()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (isGameOver) {
                resetGame(); startGame(); return true
            }
            if (isPaused) {
                startGame(); return true
            }

            if (event.x < width / 3f) {
                if (playerLane > 0) { playerLane--; SoundManager.playClick() }
            } else if (event.x > width * 2/3f) {
                if (playerLane < 2) { playerLane++; SoundManager.playClick() }
            } else {
                pause()
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun update() {
        if (isPaused || isGameOver) return

        roadOffset = (roadOffset + gameSpeed) % 200
        gameSpeed += 0.0015f
        score++

        updateEnvironment()

        nextObstacleTimer -= 1f
        if (nextObstacleTimer <= 0) {
            spawnObstacle()
            val baseInterval = when(trafficDensity) {
                0 -> 70f
                2 -> 35f
                else -> 50f
            }
            nextObstacleTimer = (baseInterval - (gameSpeed - 10) * 2.5f).coerceAtLeast(12f) + random.nextInt(25)
        }

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obs = iterator.next()
            obs.y += gameSpeed * (if (obs.type == ObstacleType.AMBULANCE) 0.6f else 1.0f)
            if (checkCollision(obs)) {
                gameOver()
                break
            }
            if (obs.y > height) iterator.remove()
        }
    }

    private fun updateEnvironment() {
        if (score > 0 && score % 1200 == 0 && score != lastThemeToggle) {
            lastThemeToggle = score
            isNightMode = !isNightMode
            currentScene = Scene.entries[(score / 1200 % Scene.entries.size)]
            currentWeather = when(currentScene) {
                Scene.SNOWY_MTN -> GameEnvironment.WeatherType.SNOW
                Scene.DESERT -> if (random.nextBoolean()) GameEnvironment.WeatherType.SANDSTORM else GameEnvironment.WeatherType.NONE
                else -> if (random.nextBoolean()) GameEnvironment.WeatherType.RAIN else GameEnvironment.WeatherType.NONE
            }
            SoundManager.playSuccess()
        }
    }

    private fun spawnObstacle() {
        val lane = random.nextInt(3)
        val type = ObstacleType.entries.random()
        obstacles.add(Obstacle(lane, -300f, type, random.nextInt(3)))
    }

    private fun checkCollision(obs: Obstacle): Boolean {
        val laneWidth = width / 3f
        val px = playerLane * laneWidth + (laneWidth - playerWidth) / 2f
        val py = height - 300f
        val ox = obs.lane * laneWidth + (laneWidth - playerWidth) / 2f
        val oy = obs.y
        
        val playerRect = RectF(px + 15, py + 15, px + playerWidth - 15, py + playerHeight - 15)
        val ow = if (obs.type == ObstacleType.CONE || obs.type == ObstacleType.POTHOLE) 40f else playerWidth
        val oh = if (obs.type == ObstacleType.CONE || obs.type == ObstacleType.POTHOLE) 40f else if (obs.type == ObstacleType.TRUCK) 220f else playerHeight
        val obsRect = RectF(ox + 15, oy + 15, ox + ow - 15, oy + oh - 15)
        
        return RectF.intersects(playerRect, obsRect)
    }

    private fun gameOver() {
        isGameOver = true
        SoundManager.playError()
        val oldBest = highScore
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, trafficDensity)
        if (isNewHigh) {
            highScore = score
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
        } else {
            currentVictoryWord = ""
        }
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isWin = false,
            isNewHigh = isNewHigh,
            score = score,
            highScore = oldBest
        )
        onGameOver?.invoke(score)
    }

    override fun onDraw(canvas: Canvas) {
        if (hintShowFrames > 0) {
            hintShowFrames--
        }
        
        super.onDraw(canvas)

        val theme = getTheme()
        GameEnvironment.draw(canvas, GameEnvironment.BackgroundType.SOLID, isNight = isNightMode, weather = currentWeather, paint = paint, particles = particles)

        // Draw Side areas
        paint.color = theme.sideColor
        val laneWidth = width / 3f
        canvas.drawRect(0f, 0f, laneWidth * 0.3f, height.toFloat(), paint)
        canvas.drawRect(width - laneWidth * 0.3f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw Road
        paint.color = theme.roadColor
        canvas.drawRect(laneWidth * 0.3f, 0f, width - laneWidth * 0.3f, height.toFloat(), paint)
        
        // Lane markers
        paint.color = theme.dashColor
        paint.pathEffect = DashPathEffect(floatArrayOf(40f, 40f), roadOffset)
        paint.strokeWidth = 8f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(laneWidth, 0f, laneWidth, height.toFloat(), paint)
        canvas.drawLine(laneWidth * 2, 0f, laneWidth * 2, height.toFloat(), paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL

        // Draw Obstacles
        for (obs in obstacles) {
            val ox = obs.lane * laneWidth + (laneWidth - playerWidth) / 2f
            drawObstacle(canvas, ox, obs.y, obs, theme)
        }

        // Draw Player
        val px = playerLane * laneWidth + (laneWidth - playerWidth) / 2f
        val py = height - 300f
        drawCar(canvas, px, py, Color.parseColor("#4CAF50"), false)

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.style = Paint.Style.FILL
        val hudY = height * 0.05f
        
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 40f, hudY, paint)
        
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.LTGRAY
        canvas.drawText("${context.getString(R.string.level_label)}: ${trafficDensity + 1}", width / 2f, hudY, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.WHITE
        canvas.drawText("${context.getString(R.string.best_label)}: $highScore", width - 40f, hudY, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, hudY + 80f, paint)
            paint.alpha = 255
        }

        if (isGameOver) {
            celebrationManager.draw(canvas)
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.crashed_label)
            drawOverlay(canvas, title, "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        }
        else if (isPaused) drawOverlay(canvas, context.getString(R.string.game_road_racer), context.getString(R.string.start_game))
    }

    private fun drawObstacle(canvas: Canvas, x: Float, y: Float, obs: Obstacle, theme: RoadTheme) {
        when (obs.type) {
            ObstacleType.CAR -> drawCar(canvas, x, y, theme.carColors[obs.variant % theme.carColors.size], true)
            ObstacleType.TRUCK -> {
                paint.color = Color.DKGRAY
                canvas.drawRoundRect(x - 10, y, x + playerWidth + 10, y + 220, 10f, 10f, paint)
                paint.color = theme.carColors[obs.variant % theme.carColors.size]
                canvas.drawRect(x, y + 20, x + playerWidth, y + 200, paint)
            }
            ObstacleType.CONE -> {
                paint.color = Color.parseColor("#FF6D00")
                val path = Path()
                path.moveTo(x + 20, y)
                path.lineTo(x, y + 40)
                path.lineTo(x + 40, y + 40)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = Color.WHITE
                canvas.drawRect(x + 10, y + 20, x + 30, y + 25, paint)
            }
            ObstacleType.OIL_SPILL -> {
                paint.color = Color.argb(180, 33, 33, 33)
                canvas.drawOval(x, y, x + 100, y + 60, paint)
            }
            ObstacleType.POTHOLE -> {
                paint.color = Color.BLACK
                canvas.drawOval(x, y, x + 80, y + 50, paint)
            }
            ObstacleType.BARRIER -> {
                paint.color = Color.parseColor("#D32F2F")
                canvas.drawRect(x - 20, y, x + playerWidth + 20, y + 30, paint)
                paint.color = Color.WHITE
                for (i in 0..3) canvas.drawRect(x - 20 + i * 30, y, x - 5 + i * 30, y + 30, paint)
            }
            ObstacleType.AMBULANCE -> {
                drawCar(canvas, x, y, Color.WHITE, true)
                paint.color = if (score % 20 < 10) Color.RED else Color.BLUE
                canvas.drawCircle(x + 20, y + 10, 10f, paint)
                canvas.drawCircle(x + 60, y + 10, 10f, paint)
            }
        }
    }

    private fun drawCar(canvas: Canvas, x: Float, y: Float, color: Int, isObstacle: Boolean) {
        paint.color = color
        canvas.drawRoundRect(x, y, x + playerWidth, y + playerHeight, 15f, 15f, paint)
        paint.color = Color.BLACK
        canvas.drawRect(x + 10, y + 40, x + playerWidth - 10, y + 100, paint)
        if (!isObstacle) { // Windshield
            paint.color = Color.parseColor("#80FFFFFF")
            canvas.drawRect(x + 15, y + 45, x + playerWidth - 15, y + 65, paint)
        }
    }

    private fun getTheme(): RoadTheme {
        val factor = if (isNightMode) 0.5f else 1.0f
        val theme = when (currentScene) {
            Scene.CITY -> RoadTheme(Color.parseColor("#37474F"), Color.parseColor("#263238"), Color.YELLOW, listOf(Color.RED, Color.BLUE, Color.MAGENTA))
            Scene.DESERT -> RoadTheme(Color.parseColor("#BCAAA4"), Color.parseColor("#FFE082"), Color.WHITE, listOf(Color.parseColor("#8D6E63"), Color.parseColor("#5D4037"), Color.BLACK))
            Scene.SNOWY_MTN -> RoadTheme(Color.parseColor("#455A64"), Color.WHITE, Color.parseColor("#BBDEFB"), listOf(Color.BLUE, Color.parseColor("#0D47A1"), Color.CYAN))
            else -> RoadTheme(Color.parseColor("#546E7A"), Color.parseColor("#2E7D32"), Color.WHITE, listOf(Color.RED, Color.YELLOW, Color.BLUE))
        }
        return RoadTheme(
            GameEnvironment.dimColor(theme.roadColor, factor),
            GameEnvironment.dimColor(theme.sideColor, factor),
            theme.dashColor,
            theme.carColors
        )
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 80f
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        
        paint.textSize = 30f
        val lines = sub.split("\n")
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 40f + i * 40f, paint)
        }
    }
}
