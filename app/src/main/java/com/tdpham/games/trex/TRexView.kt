package com.tdpham.games.trex

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameView
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import java.util.*

class TRexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "trex"
    override var onGameOver: ((Int) -> Unit)? = null
    
    // Game State
    private var score = 0
    private var highScore = 0
    private var isGameOver = false
    private var isPaused = false
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private var gameSpeed = 16f
    private var distanceTravelled = 0f

    // Dino Properties
    private var dinoScale = 6f
    private var dinoY = 0f
    private var dinoVelocityY = 0f
    private val gravity = 1.3f
    private var jumpStrength = -28f
    private val groundY = 0.8f 
    private var isJumping = false
    private var isDucking = false
    private var animationFrame = 0
    private var walkFrame = 0

    private enum class DinoMember { DADDY, MUMMY, BABY, GRANDPA, TEENAGER, SCIENTIST, ATHLETE, PIRATE, CHEF, ASTRONAUT }
    private var currentMember = DinoMember.DADDY
    private var memberName = ""
    private var nameShowFrames = 0
    
    private var highScoreFlash = 0
    private var isNewHighScoreBroken = false
    
    // Environment State
    private var isNightMode = false
    private var lastNightToggle = 0
    private var currentSeason = Season.SPRING
    private var currentWeather = Weather.SUNNY
    
    // Particles/Decorations
    private val clouds = mutableListOf<PointF>()
    private val stars = mutableListOf<Star>()
    private val groundDots = mutableListOf<PointF>()
    private val runParticles = mutableListOf<RunParticle>()
    private val particles = mutableListOf<GameEnvironment.Particle>()
    private val pathBuffer = Path()
    
    // Obstacles
    private val obstacles = mutableListOf<Obstacle>()
    private val explosions = mutableListOf<Explosion>()
    private val random = Random()
    private var nextObstacleDistance = 0f
    private var lastObstacleHeight = 0f

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameOver && !isPaused) {
                update()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    enum class Season { SPRING, SUMMER, AUTUMN, WINTER }
    enum class Weather { SUNNY, RAINY, SNOWY }
    data class Star(val x: Float, val y: Float, val size: Float, var alpha: Int)
    data class RunParticle(var x: Float, var y: Float, var size: Float, var alpha: Int, val vx: Float, val vy: Float)
    data class Explosion(val x: Float, val y: Float, val color: Int, var radius: Float, var alpha: Int)

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
    }

    override fun resetGame() {
        score = 0
        highScore = ScoreManager.getHighScore(context, gameKey)
        isGameOver = false
        isPaused = true
        celebrationManager.start(0f, 0f)
        
        // Pick random family member for this run
        currentMember = DinoMember.entries.random()
        applyMemberProperties()
        memberName = context.getString(when(currentMember) {
            DinoMember.DADDY -> R.string.trex_daddy
            DinoMember.MUMMY -> R.string.trex_mummy
            DinoMember.BABY -> R.string.trex_baby
            DinoMember.GRANDPA -> R.string.trex_grandpa
            DinoMember.TEENAGER -> R.string.trex_teenager
            DinoMember.SCIENTIST -> R.string.trex_scientist
            DinoMember.ATHLETE -> R.string.trex_athlete
            DinoMember.PIRATE -> R.string.trex_pirate
            DinoMember.CHEF -> R.string.trex_chef
            DinoMember.ASTRONAUT -> R.string.trex_astronaut
        })
        nameShowFrames = 120 // Show for ~2 seconds

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
        stars.clear()
        runParticles.clear()
        explosions.clear()
        particles.clear()
        repeat(40) {
            stars.add(Star(random.nextFloat() * 2000, random.nextFloat() * 400, random.nextFloat() * 3 + 1, random.nextInt(255)))
        }
        repeat(30) {
            particles.add(GameEnvironment.Particle(random.nextFloat() * 2000, random.nextFloat() * 1000, random.nextFloat() * 10 + 5))
        }
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (isGameOver) {
                resetGame()
                startGame()
                return true
            }
            if (isPaused) {
                resume()
                return true
            }

            if (event.y < height * 0.5f) {
                if (!isJumping && !isDucking) jump()
            } else {
                if (!isJumping) {
                    isDucking = true
                    invalidate()
                }
            }
            return true
        } else if (event.action == android.view.MotionEvent.ACTION_UP) {
            if (isDucking) {
                isDucking = false
                invalidate()
            }
            return true
        }
        return super.onTouchEvent(event)
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

    private fun applyMemberProperties() {
        dinoScale = when(currentMember) {
            DinoMember.DADDY -> 6.5f
            DinoMember.MUMMY -> 6.0f
            DinoMember.BABY -> 3.5f
            DinoMember.GRANDPA -> 5.8f
            DinoMember.TEENAGER -> 5.5f
            DinoMember.SCIENTIST -> 5.8f
            DinoMember.ATHLETE -> 6.0f
            DinoMember.PIRATE -> 6.2f
            DinoMember.CHEF -> 6.0f
            DinoMember.ASTRONAUT -> 6.0f
        }
        
        jumpStrength = when(currentMember) {
            DinoMember.BABY -> -26f // Slightly stronger jump for baby to clear ~200px trees
            DinoMember.DADDY -> -30f // Heavier jump
            DinoMember.ATHLETE -> -32f // High jump
            else -> -28f
        }
    }

    private fun update() {
        if (isGameOver || isPaused) return

        if (nameShowFrames > 0) nameShowFrames--
        animationFrame++
        if (animationFrame % 6 == 0) {
            walkFrame = (walkFrame + 1) % 2
        }

        if (isNightMode) {
            stars.forEach {
                if (random.nextFloat() > 0.95f) it.alpha = random.nextInt(255)
            }
        }

        distanceTravelled += gameSpeed
        score = (distanceTravelled / 50).toInt()
        
        if (score > highScore && highScore > 0 && !isNewHighScoreBroken) {
            isNewHighScoreBroken = true
            highScoreFlash = 60
        }
        if (highScoreFlash > 0) highScoreFlash--

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
        
        // Run particles
        if (!isJumping) {
            if (animationFrame % 4 == 0) {
                runParticles.add(RunParticle(120f + random.nextInt(40), height * groundY - 5, random.nextFloat() * 4 + 2, 200, -gameSpeed * 0.5f, -random.nextFloat() * 2))
            }
        }
        val pIter = runParticles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 8
            p.size *= 0.95f
            if (p.alpha <= 0) pIter.remove()
        }

        // Explosions
        val eIter = explosions.iterator()
        while (eIter.hasNext()) {
            val e = eIter.next()
            e.radius += 5f
            e.alpha -= 10
            if (e.alpha <= 0) eIter.remove()
        }

        nextObstacleDistance -= gameSpeed
        if (nextObstacleDistance <= 0) spawnObstacle()

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obs = iterator.next()
            
            if (obs.type == ObstacleType.METEOR || obs.type == ObstacleType.BOMB || obs.type == ObstacleType.THUNDERBOLT) {
                obs.x -= gameSpeed * 0.5f
                obs.y += 12f
                
                if (obs.y >= height * groundY - obs.height / 2) {
                    // Impact!
                    val color = when(obs.type) {
                        ObstacleType.METEOR -> Color.parseColor("#FF5722")
                        ObstacleType.THUNDERBOLT -> Color.parseColor("#FFEA00")
                        else -> Color.parseColor("#424242")
                    }
                    explosions.add(Explosion(obs.x + obs.width / 2, height * groundY, color, 10f, 255))
                    SoundManager.playClick() // Thud/Impact
                    iterator.remove()
                    continue
                }
            } else {
                obs.x -= gameSpeed
            }

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
    }

    private fun spawnCloud(x: Float) = clouds.add(PointF(x, 80f + random.nextInt(150)))
    private fun spawnGroundDot(x: Float) = groundDots.add(PointF(x, height * groundY + 5 + random.nextInt(40)))

    private fun spawnObstacle() {
        val type = when (random.nextInt(20)) {
            in 0..1 -> if (score > 400) ObstacleType.BIRD else ObstacleType.CACTUS
            in 2..5 -> ObstacleType.TREE
            in 6..7 -> ObstacleType.ROCK
            in 8..10 -> ObstacleType.BUSH
            in 11..13 -> if (score > 600) ObstacleType.METEOR else ObstacleType.CACTUS
            14 -> if (score > 800) ObstacleType.BOMB else ObstacleType.TREE
            15 -> if (score > 1000) ObstacleType.THUNDERBOLT else ObstacleType.BUSH
            else -> ObstacleType.CACTUS
        }
        
        val variant = random.nextInt(3)
        
        var width = 0f
        var height = 0f
        
        when(type) {
            ObstacleType.BIRD -> { width = 80f; height = 50f }
            ObstacleType.TREE -> {
                height = 140f + random.nextInt(60) // Reduced from 260 to ~200 max
                width = 60f + random.nextInt(40)
            }
            ObstacleType.BUSH -> { width = 70f + random.nextInt(100); height = 40f + random.nextInt(30) }
            ObstacleType.ROCK -> { width = 60f + random.nextInt(40); height = 30f + random.nextInt(20) }
            ObstacleType.CACTUS -> { width = 40f + random.nextInt(40); height = 80f + random.nextInt(80) }
            ObstacleType.METEOR -> { width = 50f; height = 50f }
            ObstacleType.BOMB -> { width = 45f; height = 45f }
            ObstacleType.THUNDERBOLT -> { width = 30f; height = 100f }
        }
        
        var ox = this.width.toFloat()
        val y = if (type == ObstacleType.BIRD) {
            val h = if (random.nextBoolean()) 180f else 100f
            (this.height * groundY) - h - random.nextInt(80)
        } else if (type == ObstacleType.METEOR || type == ObstacleType.BOMB || type == ObstacleType.THUNDERBOLT) {
            ox += random.nextInt(400)
            -200f // Start from sky
        } else {
            (this.height * groundY) - height
        }
        
        obstacles.add(Obstacle(ox, y, width, height, type, variant))
        
        // --- Wise Difficulty Calculation ---
        // 1. Calculate the physical jump distance of the current Dino member
        val airTime = 2.0f * Math.abs(jumpStrength) / gravity
        val jumpDistance = gameSpeed * airTime
        
        // 2. Minimum ground time (reaction window)
        // We want at least 15-20 frames of ground time for the player to react
        val reactionDistance = gameSpeed * 18f 
        
        // 3. Spacing logic
        val baseGap = if (height > 120f || lastObstacleHeight > 120f) {
            // If the current or last obstacle is tall, ensure a full landing + reaction window
            jumpDistance + reactionDistance
        } else {
            // For smaller obstacles, allow slightly tighter spacing but never less than a comfortable jump
            jumpDistance * 0.85f + reactionDistance
        }

        // 4. Sky Obstacle Buffer
        // If it's a meteor/bomb/thunderbolt, they arrive diagonally. 
        // We add extra distance to avoid them overlapping with ground obstacles.
        val skyBuffer = if (type == ObstacleType.METEOR || type == ObstacleType.BOMB || type == ObstacleType.THUNDERBOLT) 300f else 0f

        nextObstacleDistance = (baseGap + skyBuffer + random.nextInt(600)).coerceAtLeast(500f)
        
        lastObstacleHeight = height
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
        if (ScoreManager.updateHighScore(context, gameKey, score)) {
            highScore = score
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
            celebrationManager.start(width.toFloat(), height.toFloat())
        } else {
            currentVictoryWord = ""
        }
        onGameOver?.invoke(score)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // update() - Moved to gameLoop

        val theme = getEnvironmentTheme()
        val envWeather = when(currentWeather) {
            Weather.RAINY -> GameEnvironment.WeatherType.RAIN
            Weather.SNOWY -> GameEnvironment.WeatherType.SNOW
            else -> GameEnvironment.WeatherType.NONE
        }

        // Draw background theme first
        canvas.drawColor(theme.bgColor)

        // Then draw environment details
        GameEnvironment.draw(
            canvas, 
            GameEnvironment.BackgroundType.SOLID, 
            isNight = isNightMode, 
            weather = envWeather, 
            paint = paint, 
            particles = particles
        )

        // Draw Sun/Moon
        paint.style = Paint.Style.FILL
        if (isNightMode) {
            // Draw Stars
            for (star in stars) {
                paint.color = Color.WHITE
                paint.alpha = star.alpha
                canvas.drawCircle(star.x % width, star.y, star.size, paint)
            }
            paint.alpha = 255
            
            // Refined Crescent Moon
            paint.color = theme.secondaryColor
            canvas.drawCircle(width - 150f, 150f, 45f, paint) // Moon base
            paint.color = theme.bgColor
            canvas.drawCircle(width - 125f, 140f, 40f, paint) // Shadow cut
        } else if (currentWeather == Weather.SUNNY) {
            // Sun with subtle glow
            paint.color = Color.argb(40, 255, 235, 59)
            canvas.drawCircle(width - 150f, 150f, 80f, paint)
            paint.color = theme.secondaryColor
            canvas.drawCircle(width - 150f, 150f, 55f, paint)
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

        // Draw Run Particles
        for (p in runParticles) {
            paint.color = theme.groundColor
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }
        paint.alpha = 255

        // Draw Explosions
        for (e in explosions) {
            paint.color = e.color
            paint.alpha = e.alpha
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 10f
            canvas.drawCircle(e.x, e.y, e.radius, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(e.x, e.y, e.radius * 0.6f, paint)
        }
        paint.alpha = 255

        // Draw Obstacles
        for (obs in obstacles) {
            drawObstacle(canvas, obs, theme)
        }

        // Draw Dino
        drawDino(canvas, 100f, dinoY, theme.dinoColor, theme.bgColor)

        // Draw Character Name
        if (nameShowFrames > 0) {
            paint.reset()
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 50f
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.color = theme.textColor
            paint.alpha = (nameShowFrames * 4).coerceAtMost(255)
            canvas.drawText(memberName, width / 2f, height * 0.4f, paint)
        }

        // Score
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 45f
        
        // Adaptive score color with flash effect
        if (highScoreFlash > 0 && (highScoreFlash / 5) % 2 == 0) {
            paint.color = Color.YELLOW
        } else {
            paint.color = theme.textColor
        }

        paint.style = Paint.Style.FILL
        val scoreX = Math.round(width - 50f).toFloat()
        val scoreY = Math.round(80f).toFloat()
        canvas.drawText("${context.getString(R.string.high_score_prefix)} ${String.format("%05d", highScore)}  ${String.format("%05d", score)}", scoreX, scoreY, paint)

        if (isGameOver) {
            celebrationManager.update()
            celebrationManager.draw(canvas)
            invalidate()
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.game_over)
            drawOverlay(canvas, title, theme.textColor)
        }
        else if (isPaused) drawOverlay(canvas, context.getString(R.string.game_trex), theme.textColor)
    }

    private fun drawObstacle(canvas: Canvas, obs: Obstacle, theme: Theme) {
        paint.style = Paint.Style.FILL
        when (obs.type) {
            ObstacleType.CACTUS -> {
                paint.color = theme.cactusColor
                canvas.drawRect(obs.x, obs.y, obs.x + obs.width, obs.y + obs.height, paint)
                // Detail
                paint.color = Color.BLACK
                paint.alpha = 30
                canvas.drawRect(obs.x + obs.width * 0.4f, obs.y + 10, obs.x + obs.width * 0.6f, obs.y + obs.height - 10, paint)
                paint.alpha = 255
                paint.color = theme.cactusColor
                if (obs.height > 60f) {
                    canvas.drawRect(obs.x - 12f, obs.y + 20f, obs.x, obs.y + 40f, paint)
                    canvas.drawRect(obs.x + obs.width, obs.y + 15f, obs.x + obs.width + 12f, obs.y + 35f, paint)
                }
            }
            ObstacleType.TREE -> {
                // Trunk
                paint.color = Color.parseColor("#5D4037")
                val trunkW = obs.width * 0.2f
                canvas.drawRect(obs.x + (obs.width - trunkW) / 2f, obs.y + obs.height * 0.5f, obs.x + (obs.width + trunkW) / 2f, obs.y + obs.height, paint)
                
                // Leaves (Different styles based on variant)
                paint.color = theme.treeColor
                when(obs.variant % 3) {
                    0 -> { // Pine
                        pathBuffer.reset()
                        for (i in 0..2) {
                            val ty = obs.y + (i * obs.height * 0.2f)
                            val th = obs.height * 0.4f
                            pathBuffer.moveTo(obs.x + obs.width / 2f, ty)
                            pathBuffer.lineTo(obs.x, ty + th)
                            pathBuffer.lineTo(obs.x + obs.width, ty + th)
                        }
                        pathBuffer.close()
                        canvas.drawPath(pathBuffer, paint)
                    }
                    1 -> { // Round
                        canvas.drawCircle(obs.x + obs.width / 2f, obs.y + obs.height * 0.3f, obs.width * 0.5f, paint)
                        canvas.drawCircle(obs.x + obs.width * 0.3f, obs.y + obs.height * 0.45f, obs.width * 0.4f, paint)
                        canvas.drawCircle(obs.x + obs.width * 0.7f, obs.y + obs.height * 0.45f, obs.width * 0.4f, paint)
                    }
                    else -> { // Pointy
                        pathBuffer.reset()
                        pathBuffer.moveTo(obs.x + obs.width * 0.5f, obs.y)
                        pathBuffer.lineTo(obs.x, obs.y + obs.height * 0.7f)
                        pathBuffer.lineTo(obs.x + obs.width, obs.y + obs.height * 0.7f)
                        pathBuffer.close()
                        canvas.drawPath(pathBuffer, paint)
                    }
                }
            }
            ObstacleType.BIRD -> {
                // Different bird types
                val birdColor = when(obs.variant % 3) {
                    0 -> Color.parseColor("#424242") // Crow/Raven
                    1 -> Color.parseColor("#D32F2F") // Red Cardinal-like
                    else -> Color.parseColor("#1976D2") // Blue Jay-like
                }
                paint.color = birdColor
                
                // Body
                canvas.drawOval(obs.x, obs.y + 10, obs.x + obs.width - 20, obs.y + obs.height - 10, paint)
                
                // Head
                canvas.drawCircle(obs.x + obs.width - 20, obs.y + 15, 15f, paint)
                
                // Beak
                paint.color = Color.parseColor("#FFD600")
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x + obs.width - 5, obs.y + 10)
                pathBuffer.lineTo(obs.x + obs.width + 10, obs.y + 15)
                pathBuffer.lineTo(obs.x + obs.width - 5, obs.y + 20)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
                
                // Wing
                paint.color = birdColor
                val wingY = if (walkFrame == 0) obs.y - 25f else obs.y + obs.height + 5f
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x + 20, obs.y + 25)
                pathBuffer.lineTo(obs.x + 40, wingY)
                pathBuffer.lineTo(obs.x + 60, obs.y + 25)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
            }
            ObstacleType.ROCK -> {
                paint.color = Color.parseColor("#757575")
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x, obs.y + obs.height)
                pathBuffer.lineTo(obs.x + obs.width * 0.2f, obs.y)
                pathBuffer.lineTo(obs.x + obs.width * 0.8f, obs.y + obs.height * 0.1f)
                pathBuffer.lineTo(obs.x + obs.width, obs.y + obs.height)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
                // Highlight
                paint.color = Color.WHITE
                paint.alpha = 40
                canvas.drawCircle(obs.x + 20, obs.y + 10, 8f, paint)
                paint.alpha = 255
            }
            ObstacleType.BUSH -> {
                paint.color = theme.treeColor
                val numCircles = (obs.width / 30).toInt().coerceAtLeast(3)
                for (i in 0 until numCircles) {
                    val progress = i.toFloat() / (numCircles - 1)
                    val cx = obs.x + progress * obs.width
                    // Organic height variation
                    val heightVar = (Math.sin(i * 1.5).toFloat() * 10f)
                    val cy = obs.y + obs.height * 0.6f + heightVar
                    val radius = obs.height * (0.5f + Math.abs(Math.cos(i.toDouble())).toFloat() * 0.3f)
                    
                    canvas.drawCircle(cx, cy, radius, paint)
                    
                    // Internal leaf detail
                    paint.color = Color.BLACK
                    paint.alpha = 20
                    canvas.drawCircle(cx - radius * 0.2f, cy - radius * 0.2f, radius * 0.3f, paint)
                    paint.alpha = 255
                    paint.color = theme.treeColor
                }
            }
            ObstacleType.METEOR -> {
                paint.color = Color.parseColor("#FF5722") // Deep Orange
                canvas.drawCircle(obs.x + obs.width / 2, obs.y + obs.height / 2, obs.width / 2, paint)
                // Tail
                paint.shader = LinearGradient(obs.x + obs.width / 2, obs.y, obs.x + obs.width / 2, obs.y - 100f, 
                    Color.parseColor("#FF5722"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(obs.x, obs.y - 100f, obs.x + obs.width, obs.y + obs.height / 2, paint)
                paint.shader = null
            }
            ObstacleType.BOMB -> {
                paint.color = Color.BLACK
                canvas.drawCircle(obs.x + obs.width / 2, obs.y + obs.height / 2, obs.width * 0.4f, paint)
                // Fuse
                paint.color = Color.parseColor("#795548")
                canvas.drawRect(obs.x + obs.width / 2 - 2, obs.y, obs.x + obs.width / 2 + 2, obs.y + obs.height / 2, paint)
                paint.color = if (animationFrame % 10 < 5) Color.YELLOW else Color.RED
                canvas.drawCircle(obs.x + obs.width / 2, obs.y, 6f, paint)
            }
            ObstacleType.THUNDERBOLT -> {
                paint.color = Color.parseColor("#FFEA00") // Bright Yellow
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x + obs.width, obs.y)
                pathBuffer.lineTo(obs.x, obs.y + obs.height * 0.6f)
                pathBuffer.lineTo(obs.x + obs.width * 0.8f, obs.y + obs.height * 0.5f)
                pathBuffer.lineTo(obs.x + obs.width * 0.2f, obs.y + obs.height)
                canvas.drawPath(pathBuffer, paint)
            }
        }
    }

    private fun drawDino(canvas: Canvas, x: Float, y: Float, color: Int, eyeColor: Int) {
        // Special Colors per Member
        val bodyColor = when(currentMember) {
            DinoMember.DADDY -> color // Theme default
            DinoMember.MUMMY -> Color.parseColor("#FF80AB") // Pinkish
            DinoMember.BABY -> Color.parseColor("#B2FF59") // Bright Green
            DinoMember.GRANDPA -> Color.parseColor("#9E9E9E") // Gray
            DinoMember.TEENAGER -> Color.parseColor("#FFFF00") // Neon Yellow
            DinoMember.SCIENTIST -> Color.parseColor("#E0E0E0") // Light Gray (Lab coat)
            DinoMember.ATHLETE -> Color.parseColor("#2196F3") // Blue
            DinoMember.PIRATE -> Color.parseColor("#4E342E") // Brown
            DinoMember.CHEF -> Color.WHITE
            DinoMember.ASTRONAUT -> Color.parseColor("#BDBDBD") // Silver
        }
        
        paint.color = bodyColor
        paint.style = Paint.Style.FILL
        val p = dinoScale
        
        canvas.save()
        canvas.translate(x, y)
        
        if (isDucking) {
            // Detailed Ducking Dino
            pathBuffer.reset()
            // Body
            pathBuffer.moveTo(0f, 8*p)
            pathBuffer.lineTo(20*p, 8*p)
            pathBuffer.lineTo(26*p, 4*p)
            pathBuffer.lineTo(32*p, 4*p)
            pathBuffer.lineTo(32*p, 10*p)
            pathBuffer.lineTo(24*p, 14*p)
            pathBuffer.lineTo(0f, 14*p)
            pathBuffer.close()
            canvas.drawPath(pathBuffer, paint)
            
            // Tail
            pathBuffer.reset()
            pathBuffer.moveTo(0f, 10*p)
            pathBuffer.lineTo(-5*p, 12*p)
            pathBuffer.lineTo(0f, 14*p)
            pathBuffer.close()
            canvas.drawPath(pathBuffer, paint)
            
            // Eye
            paint.color = eyeColor
            canvas.drawRect(24*p, 5*p, 26*p, 7*p, paint)
            
            // Legs
            paint.color = bodyColor
            if (walkFrame == 0) {
                canvas.drawRect(6*p, 14*p, 10*p, 16*p, paint)
            } else {
                canvas.drawRect(14*p, 14*p, 18*p, 16*p, paint)
            }
        } else {
            // Detailed Standing Dino
            // Head & Neck
            pathBuffer.reset()
            pathBuffer.moveTo(12*p, 0f)
            pathBuffer.lineTo(26*p, 0f)
            pathBuffer.lineTo(26*p, 8*p)
            pathBuffer.lineTo(16*p, 8*p)
            pathBuffer.lineTo(16*p, 12*p)
            pathBuffer.lineTo(10*p, 12*p)
            pathBuffer.lineTo(10*p, 4*p)
            pathBuffer.close()
            canvas.drawPath(pathBuffer, paint)
            
            // Body
            pathBuffer.reset()
            pathBuffer.moveTo(2*p, 10*p)
            pathBuffer.lineTo(16*p, 10*p)
            pathBuffer.lineTo(16*p, 18*p)
            pathBuffer.lineTo(0f, 18*p)
            pathBuffer.close()
            canvas.drawPath(pathBuffer, paint)
            
            // Tail
            pathBuffer.reset()
            pathBuffer.moveTo(0f, 12*p)
            pathBuffer.lineTo(-8*p, 16*p)
            pathBuffer.lineTo(0f, 18*p)
            pathBuffer.close()
            canvas.drawPath(pathBuffer, paint)
            
            // Tiny Arms
            canvas.drawRect(16*p, 11*p, 19*p, 13*p, paint)
            
            // Eye
            paint.color = eyeColor
            canvas.drawRect(14*p, 2*p, 16*p, 4*p, paint)
            
            // Legs
            paint.color = bodyColor
            val ly = 18*p
            if (isJumping) {
                canvas.drawRect(4*p, ly, 7*p, ly + 3*p, paint)
                canvas.drawRect(10*p, ly, 13*p, ly + 3*p, paint)
            } else {
                if (walkFrame == 0) {
                    canvas.drawRect(4*p, ly, 7*p, ly + 5*p, paint) // Down
                    canvas.drawRect(10*p, ly, 13*p, ly + 2*p, paint) // Up
                } else {
                    canvas.drawRect(4*p, ly, 7*p, ly + 2*p, paint)
                    canvas.drawRect(10*p, ly, 13*p, ly + 5*p, paint)
                }
            }
        }

        // --- Accessories per Member ---
        when(currentMember) {
            DinoMember.MUMMY -> {
                // Bow on head
                paint.color = Color.RED
                canvas.drawCircle(14*p, -2*p, 3*p, paint)
                canvas.drawCircle(10*p, -2*p, 3*p, paint)
            }
            DinoMember.GRANDPA -> {
                // Glasses
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * p
                canvas.drawCircle(15*p, 3*p, 2.5f*p, paint)
                canvas.drawCircle(21*p, 3*p, 2.5f*p, paint)
                paint.style = Paint.Style.FILL
            }
            DinoMember.TEENAGER -> {
                // Cool Cap
                paint.color = Color.parseColor("#00BCD4")
                canvas.drawRect(11*p, -2*p, 22*p, 1*p, paint)
                canvas.drawRect(22*p, -1*p, 28*p, 1*p, paint) // Brim
            }
            DinoMember.SCIENTIST -> {
                // Bowtie
                paint.color = Color.BLACK
                pathBuffer.reset()
                pathBuffer.moveTo(14*p, 10*p)
                pathBuffer.lineTo(12*p, 8*p)
                pathBuffer.lineTo(12*p, 12*p)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
                pathBuffer.reset()
                pathBuffer.moveTo(14*p, 10*p)
                pathBuffer.lineTo(16*p, 8*p)
                pathBuffer.lineTo(16*p, 12*p)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
            }
            DinoMember.ATHLETE -> {
                // Headband
                paint.color = Color.RED
                canvas.drawRect(12*p, 1*p, 26*p, 3*p, paint)
            }
            DinoMember.PIRATE -> {
                // Eyepatch
                paint.color = Color.BLACK
                canvas.drawRect(13*p, 2*p, 17*p, 5*p, paint)
                paint.strokeWidth = 1f * p
                canvas.drawLine(10*p, 3*p, 26*p, 1*p, paint)
            }
            DinoMember.CHEF -> {
                // Chef Hat
                paint.color = Color.WHITE
                canvas.drawRoundRect(12*p, -8*p, 26*p, -1*p, 2*p, 2*p, paint)
                canvas.drawCircle(19*p, -8*p, 5*p, paint)
            }
            DinoMember.ASTRONAUT -> {
                // Space Helmet
                paint.color = Color.argb(100, 129, 212, 250)
                canvas.drawCircle(19*p, 4*p, 10*p, paint)
                paint.style = Paint.Style.STROKE
                paint.color = Color.WHITE
                paint.strokeWidth = 1f * p
                canvas.drawCircle(19*p, 4*p, 10*p, paint)
                paint.style = Paint.Style.FILL
            }
            else -> {}
        }

        canvas.restore()
    }

    private fun drawOverlay(canvas: Canvas, title: String, textColor: Int) {
        paint.color = if (isNightMode) Color.argb(160, 0, 0, 0) else Color.argb(160, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 90f
        paint.color = if (isGameOver) Color.RED else textColor
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        
        paint.textSize = 35f
        paint.color = textColor
        if (isGameOver) {
            canvas.drawText("${context.getString(R.string.score_label)}: $score", width / 2f, height / 2f + 50f, paint)
            canvas.drawText(context.getString(R.string.restart_hint), width / 2f, height / 2f + 100f, paint)
        } else {
            canvas.drawText(context.getString(R.string.start_game), width / 2f, height / 2f + 50f, paint)
        }
    }

    private fun getEnvironmentTheme(): Theme {
        return if (isNightMode) {
            Theme(Color.parseColor("#202124"), Color.WHITE, Color.parseColor("#BDC1C6"), Color.DKGRAY, Color.parseColor("#444444"), Color.parseColor("#F1F3F4"), Color.parseColor("#81C784"), Color.parseColor("#A5D6A7"), Color.parseColor("#F48FB1"), Color.WHITE)
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
    enum class ObstacleType { CACTUS, BIRD, TREE, ROCK, BUSH, METEOR, BOMB, THUNDERBOLT }
    data class Obstacle(var x: Float, var y: Float, val width: Float, val height: Float, val type: ObstacleType, val variant: Int = 0)
}
