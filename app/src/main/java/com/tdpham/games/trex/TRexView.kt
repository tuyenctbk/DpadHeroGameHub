package com.tdpham.games.trex

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import java.util.*

class TRexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "trex"
    
    // Game State
    private var score = 0
    private var highScore = 0
    private var isGameOver = false
    private var isPaused = false
    private var gameSpeed = 16f
    private var distanceTravelled = 0f

    // Dino Properties
    private val dinoScale = 6f
    private var dinoY = 0f
    private var dinoVelocityY = 0f
    private val gravity = 1.3f
    private val jumpStrength = -28f
    private val groundY = 0.8f 
    private var isJumping = false
    private var isDucking = false
    
    // Environment State
    private var isNightMode = false
    private var lastNightToggle = 0
    private var currentSeason = Season.SPRING
    private var currentWeather = Weather.SUNNY
    
    // Particles/Decorations
    private val clouds = mutableListOf<PointF>()
    private val groundDots = mutableListOf<PointF>()
    private val weatherParticles = mutableListOf<PointF>()
    private val treePath = Path()
    
    // Obstacles
    private val obstacles = mutableListOf<Obstacle>()
    private val random = Random()
    private var nextObstacleDistance = 0f

    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    enum class Season { SPRING, SUMMER, AUTUMN, WINTER }
    enum class Weather { SUNNY, RAINY, SNOWY }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        if (isPaused) resume()
        requestFocus()
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        isPaused = false
        invalidate()
    }

    override fun resetGame() {
        score = 0
        highScore = ScoreManager.getHighScore(context, gameKey)
        isGameOver = false
        isPaused = true
        isNightMode = false
        lastNightToggle = 0
        currentSeason = Season.SPRING
        currentWeather = Weather.SUNNY
        gameSpeed = 16f
        distanceTravelled = 0f
        dinoY = 0f
        dinoVelocityY = 0f
        isJumping = false
        isDucking = false
        obstacles.clear()
        clouds.clear()
        groundDots.clear()
        weatherParticles.clear()
        for (i in 0..5) spawnCloud(random.nextFloat() * 2000)
        for (i in 0..15) spawnGroundDot(random.nextFloat() * 2000)
        nextObstacleDistance = 600f
        invalidate()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                startGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                if (isPaused) resume() else if (!isJumping && !isDucking) jump()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!isJumping) {
                    isDucking = true
                    invalidate()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            isDucking = false
            invalidate()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun jump() {
        dinoVelocityY = jumpStrength
        isJumping = true
        SoundManager.playClick()
    }

    private fun update() {
        if (isGameOver || isPaused) return

        distanceTravelled += gameSpeed
        score = (distanceTravelled / 50).toInt()
        
        // Progression Cycles
        updateEnvironment()

        gameSpeed += 0.0025f
        dinoVelocityY += gravity
        dinoY += dinoVelocityY
        
        val dinoHeight = if (isDucking) 15 * dinoScale else 21 * dinoScale
        val actualGroundY = height * groundY - dinoHeight
        if (dinoY >= actualGroundY) {
            dinoY = actualGroundY
            dinoVelocityY = 0f
            isJumping = false
        }

        updateDecorations()
        
        nextObstacleDistance -= gameSpeed
        if (nextObstacleDistance <= 0) spawnObstacle()

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obs = iterator.next()
            obs.x -= gameSpeed
            if (checkCollision(obs)) {
                gameOver()
                break
            }
            if (obs.x + obs.width < -100) iterator.remove()
        }
        invalidate()
    }

    private fun updateEnvironment() {
        // Night every 700
        if (score > 0 && score % 700 == 0 && score != lastNightToggle) {
            isNightMode = !isNightMode
            lastNightToggle = score
            SoundManager.playSuccess()
        }
        
        // Seasons every 1500
        currentSeason = when ((score / 1500) % 4) {
            0 -> Season.SPRING
            1 -> Season.SUMMER
            2 -> Season.AUTUMN
            else -> Season.WINTER
        }
        
        // Weather change every 1000
        currentWeather = when ((score / 1000) % 3) {
            0 -> Weather.SUNNY
            1 -> Weather.RAINY
            else -> Weather.SNOWY
        }
    }

    private fun updateDecorations() {
        // Clouds
        clouds.forEach { it.x -= gameSpeed * 0.2f }
        clouds.removeAll { it.x < -200 }
        if (clouds.size < 3) spawnCloud(width.toFloat() + random.nextInt(500))

        // Ground
        groundDots.forEach { it.x -= gameSpeed }
        groundDots.removeAll { it.x < -50 }
        if (groundDots.size < 15) spawnGroundDot(width.toFloat())
        
        // Weather Particles
        if (currentWeather != Weather.SUNNY) {
            repeat(3) {
                weatherParticles.add(PointF(random.nextFloat() * width, -20f))
            }
        }
        val pIterator = weatherParticles.iterator()
        while (pIterator.hasNext()) {
            val p = pIterator.next()
            if (currentWeather == Weather.RAINY) {
                p.y += 25f
                p.x -= 5f
            } else if (currentWeather == Weather.SNOWY) {
                p.y += 5f
                p.x += random.nextFloat() * 4 - 2
            }
            if (p.y > height) pIterator.remove()
        }
    }

    private fun spawnCloud(x: Float) = clouds.add(PointF(x, 80f + random.nextInt(150)))
    private fun spawnGroundDot(x: Float) = groundDots.add(PointF(x, height * groundY + 5 + random.nextInt(40)))

    private fun spawnObstacle() {
        val type = when (random.nextInt(10)) {
            in 0..1 -> if (score > 400) ObstacleType.BIRD else ObstacleType.CACTUS
            in 2..4 -> ObstacleType.TREE
            else -> ObstacleType.CACTUS
        }
        val width = if (type == ObstacleType.BIRD) 70f else 40f + random.nextInt(40)
        val height = if (type == ObstacleType.BIRD) 50f else 60f + random.nextInt(60)
        val y = if (type == ObstacleType.BIRD) (this.height * groundY) - 160f - random.nextInt(100) else (this.height * groundY) - height
        obstacles.add(Obstacle(this.width.toFloat(), y, width, height, type))
        nextObstacleDistance = 700f + random.nextInt(800).toFloat()
    }

    private fun checkCollision(obs: Obstacle): Boolean {
        val dinoHeight = if (isDucking) 15 * dinoScale else 21 * dinoScale
        val dinoRect = RectF(100f, dinoY, 100f + 25 * dinoScale, dinoY + dinoHeight)
        val obsRect = RectF(obs.x, obs.y, obs.x + obs.width, obs.y + obs.height)
        dinoRect.inset(15f, 10f)
        obsRect.inset(10f, 10f)
        return RectF.intersects(dinoRect, obsRect)
    }

    private fun gameOver() {
        isGameOver = true
        SoundManager.playError()
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
        if (isNewHigh) highScore = score
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        update()

        val theme = getEnvironmentTheme()
        canvas.drawColor(theme.bgColor)

        // Draw Sun/Moon
        paint.color = theme.secondaryColor
        if (isNightMode) {
            canvas.drawCircle(width - 150f, 150f, 50f, paint) // Moon
        } else if (currentWeather == Weather.SUNNY) {
            canvas.drawCircle(width - 150f, 150f, 60f, paint) // Sun
        }

        // Draw Clouds
        paint.color = theme.cloudColor
        for (cloud in clouds) canvas.drawRoundRect(cloud.x, cloud.y, cloud.x + 120, cloud.y + 45, 15f, 15f, paint)

        // Draw Ground
        paint.color = theme.groundColor
        paint.strokeWidth = 3f
        val lineY = height * groundY
        canvas.drawLine(0f, lineY, width.toFloat(), lineY, paint)
        for (dot in groundDots) canvas.drawRect(dot.x, dot.y, dot.x + 4, dot.y + 4, paint)

        // Draw Weather
        if (currentWeather != Weather.SUNNY) {
            paint.color = theme.weatherColor
            for (p in weatherParticles) {
                if (currentWeather == Weather.RAINY) canvas.drawLine(p.x, p.y, p.x - 5, p.y + 15, paint)
                else canvas.drawCircle(p.x, p.y, 4f, paint)
            }
        }

        // Draw Obstacles (Distinct colors per season)
        for (obs in obstacles) {
            drawObstacle(canvas, obs, theme)
        }

        // Draw Dino (Always high contrast)
        drawDino(canvas, 100f, dinoY, theme.dinoColor, theme.bgColor)

        // Score
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 45f
        paint.color = theme.textColor
        canvas.drawText("HI ${String.format("%05d", highScore)}  ${String.format("%05d", score)}", width - 50f, 80f, paint)

        if (isGameOver) drawOverlay(canvas, "G A M E  O V E R", theme.textColor)
        else if (isPaused) drawOverlay(canvas, "T - R E X  R U N", theme.textColor)
    }

    private fun drawObstacle(canvas: Canvas, obs: Obstacle, theme: Theme) {
        paint.color = when (obs.type) {
            ObstacleType.CACTUS -> theme.cactusColor
            ObstacleType.TREE -> theme.treeColor
            ObstacleType.BIRD -> theme.birdColor
        }
        paint.style = Paint.Style.FILL
        if (obs.type == ObstacleType.TREE) {
            // Trunk
            paint.color = Color.parseColor("#795548")
            canvas.drawRect(obs.x + obs.width * 0.4f, obs.y + obs.height * 0.6f, obs.x + obs.width * 0.6f, obs.y + obs.height, paint)
            // Leaves
            paint.color = theme.treeColor
            treePath.reset()
            treePath.moveTo(obs.x + obs.width * 0.5f, obs.y)
            treePath.lineTo(obs.x, obs.y + obs.height * 0.7f)
            treePath.lineTo(obs.x + obs.width, obs.y + obs.height * 0.7f)
            treePath.close()
            canvas.drawPath(treePath, paint)
        } else {
            canvas.drawRect(obs.x, obs.y, obs.x + obs.width, obs.y + obs.height, paint)
            if (obs.type == ObstacleType.CACTUS && obs.height > 60f) {
                canvas.drawRect(obs.x - 12f, obs.y + 20f, obs.x, obs.y + 40f, paint)
                canvas.drawRect(obs.x + obs.width, obs.y + 15f, obs.x + obs.width + 12f, obs.y + 35f, paint)
            } else if (obs.type == ObstacleType.BIRD) {
                canvas.drawRect(obs.x + 10f, obs.y - 15f, obs.x + 40f, obs.y, paint)
            }
        }
    }

    private fun drawDino(canvas: Canvas, x: Float, y: Float, color: Int, eyeColor: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        val p = dinoScale
        if (isDucking) {
            canvas.drawRect(x, y + 7*p, x + 16*p, y + 15*p, paint) 
            canvas.drawRect(x + 16*p, y + 2*p, x + 26*p, y + 10*p, paint)
            canvas.drawRect(x + 26*p, y + 4*p, x + 30*p, y + 10*p, paint)
            paint.color = eyeColor
            canvas.drawRect(x + 18*p, y + 4*p, x + 20*p, y + 6*p, paint)
        } else {
            canvas.drawRect(x + 11*p, y, x + 22*p, y + 8*p, paint) 
            canvas.drawRect(x + 22*p, y + 3*p, x + 25*p, y + 8*p, paint)
            paint.color = eyeColor
            canvas.drawRect(x + 13*p, y + 2*p, x + 15*p, y + 4*p, paint)
            paint.color = color
            canvas.drawRect(x + 8*p, y + 8*p, x + 11*p, y + 13*p, paint)
            canvas.drawRect(x, y + 10*p, x + 15*p, y + 18*p, paint)
            canvas.drawRect(x + 15*p, y + 10*p, x + 17*p, y + 14*p, paint) 
            canvas.drawRect(x - 3*p, y + 10*p, x, y + 14*p, paint)
            canvas.drawRect(x + 4*p, y + 18*p, x + 7*p, y + 21*p, paint)
            canvas.drawRect(x + 9*p, y + 18*p, x + 12*p, y + 21*p, paint)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, textColor: Int) {
        paint.color = if (isNightMode) Color.argb(160, 0, 0, 0) else Color.argb(160, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 90f
        paint.color = if (isGameOver) Color.RED else textColor
        canvas.drawText(title, width / 2f, height / 2f, paint)
        paint.textSize = 35f
        paint.color = textColor
        canvas.drawText("Press Center to ${if (isGameOver) "Restart" else "Start"}", width / 2f, height / 2f + 70f, paint)
    }

    private fun getEnvironmentTheme(): Theme {
        return if (isNightMode) {
            Theme(Color.parseColor("#202124"), Color.WHITE, Color.parseColor("#BDC1C6"), Color.DKGRAY, Color.parseColor("#444444"), Color.parseColor("#FFD700"), Color.parseColor("#81C784"), Color.parseColor("#A5D6A7"), Color.parseColor("#F48FB1"), Color.WHITE)
        } else {
            when (currentSeason) {
                Season.SPRING -> Theme(Color.parseColor("#E8F5E9"), Color.BLACK, Color.parseColor("#2E7D32"), Color.WHITE, Color.parseColor("#A5D6A7"), Color.parseColor("#FFD600"), Color.parseColor("#F06292"), Color.parseColor("#43A047"), Color.parseColor("#EC407A"), Color.parseColor("#64B5F6"))
                Season.SUMMER -> Theme(Color.parseColor("#FFFDE7"), Color.BLACK, Color.parseColor("#FBC02D"), Color.WHITE, Color.parseColor("#FFF59D"), Color.parseColor("#FFD600"), Color.parseColor("#2E7D32"), Color.parseColor("#388E3C"), Color.parseColor("#D81B60"), Color.parseColor("#FFEB3B"))
                Season.AUTUMN -> Theme(Color.parseColor("#FBE9E7"), Color.BLACK, Color.parseColor("#D84315"), Color.WHITE, Color.parseColor("#FFAB91"), Color.parseColor("#FFD600"), Color.parseColor("#8D6E63"), Color.parseColor("#A1887F"), Color.parseColor("#C62828"), Color.parseColor("#FF9800"))
                Season.WINTER -> Theme(Color.parseColor("#F5F5F5"), Color.BLACK, Color.parseColor("#0277BD"), Color.WHITE, Color.parseColor("#B3E5FC"), Color.parseColor("#FFD600"), Color.parseColor("#78909C"), Color.parseColor("#90A4AE"), Color.parseColor("#C2185B"), Color.WHITE)
            }
        }
    }

    data class Theme(val bgColor: Int, val textColor: Int, val dinoColor: Int, val cloudColor: Int, val groundColor: Int, val secondaryColor: Int, val cactusColor: Int, val treeColor: Int, val birdColor: Int, val weatherColor: Int)
    enum class ObstacleType { CACTUS, BIRD, TREE }
    data class Obstacle(var x: Float, var y: Float, val width: Float, val height: Float, val type: ObstacleType)
}
