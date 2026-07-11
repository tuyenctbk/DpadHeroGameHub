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
    private val craters = mutableListOf<Crater>()
    private val random = Random()
    private var nextObstacleDistance = 0f
    private var lastObstacleHeight = 0f
    
    // Earthquake state
    private var earthquakeShake = 0f
    private var earthquakeTimer = 0

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
    data class Crater(var x: Float, val width: Float, var alpha: Int)

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

        earthquakeShake = 0f
        earthquakeTimer = 0
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
        explosions.clear()
        craters.clear()
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

        // Craters and Earthquake
        updateEvents()

        nextObstacleDistance -= gameSpeed
        if (nextObstacleDistance <= 0) spawnObstacle()

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obs = iterator.next()
            
            if (obs.type == ObstacleType.METEOR || obs.type == ObstacleType.THUNDERBOLT) {
                // Diagonal trajectory - tuned to ensure they can actually reach and hit the Dino
                val fallSpeed = when(obs.type) {
                    ObstacleType.THUNDERBOLT -> 20f
                    ObstacleType.METEOR -> 12f + (obs.variant * 2f) // Varied fall speed
                    else -> 10f
                }
                // Randomize horizontal speed slightly per obstacle (using variant) to vary landing angles
                val horizontalFactor = 1.2f + (obs.variant * 0.2f)
                obs.x -= gameSpeed * horizontalFactor
                obs.y += fallSpeed
                
                if (obs.y >= height * groundY - obs.height / 2) {
                    // Impact!
                    val color = when(obs.type) {
                        ObstacleType.METEOR -> Color.parseColor("#FF5722")
                        ObstacleType.THUNDERBOLT -> Color.parseColor("#FFEA00")
                        else -> Color.parseColor("#424242")
                    }
                    explosions.add(Explosion(obs.x + obs.width / 2, height * groundY, color, 15f, 255))
                    
                    if (obs.type == ObstacleType.METEOR) {
                        craters.add(Crater(obs.x + obs.width / 4, obs.width * 1.5f, 255))
                    } else if (obs.type == ObstacleType.THUNDERBOLT) {
                        // Lightning strikes! Check for ground obstacles to transform
                        var targetFound = false
                        for (other in obstacles) {
                            if (other != obs && (other.type == ObstacleType.CACTUS || other.type == ObstacleType.TREE || other.type == ObstacleType.ROCK)) {
                                // Hit detection: if lightning lands within the horizontal bounds of the obstacle
                                val midX = obs.x + obs.width / 2f
                                if (midX > other.x && midX < other.x + other.width) {
                                    targetFound = true
                                    if (other.type == ObstacleType.ROCK) {
                                        other.variant = 3 // Shattered variant
                                        other.width *= 1.8f
                                    } else {
                                        // Burn Tree or Cactus
                                        other.type = ObstacleType.FIRE
                                        other.width = 180f // Grow wider
                                        other.height = 180f // and higher than the tree
                                        other.y = height * groundY - 180f
                                    }
                                    break
                                }
                            }
                        }
                        
                        if (!targetFound) {
                            // Standard fire on empty ground
                            obstacles.add(Obstacle(obs.x, height * groundY - 120f, 120f, 120f, ObstacleType.FIRE, random.nextInt(3)))
                        }
                    }
                    
                    SoundManager.playClick() // Thud/Impact
                    iterator.remove()
                    continue
                }
            } else if (obs.type == ObstacleType.PTEROSAUR) {
                obs.x -= gameSpeed * 1.2f
                
                // Dynamic oscillation: frequency and amplitude increase with score
                // Later in the run, they move in much less predictable paths
                val freqFactor = (0.1 + (score / 4000.0)).coerceAtMost(0.3)
                val ampFactor = (3f + (score / 1200f)).coerceAtMost(12f)
                
                // Compound oscillation (two sine waves) to make it harder to guess
                val wave1 = Math.sin(animationFrame * freqFactor + obs.variant)
                val wave2 = Math.sin(animationFrame * (freqFactor * 0.5) + obs.variant * 2)
                obs.y += (wave1 + wave2).toFloat() * (ampFactor * 0.5f)
                
                // Random "Diving" behavior for elite scores
                if (score > 2000 && obs.x < width * 0.5f && obs.x > width * 0.2f) {
                    if (obs.variant % 2 == 0) {
                        obs.y += 4f // Sudden dip
                    }
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

    private fun updateEvents() {
        // Craters
        val cIter = craters.iterator()
        while (cIter.hasNext()) {
            val c = cIter.next()
            c.x -= gameSpeed
            c.alpha -= 1
            
            // Check collision with lethal crater
            if (!isJumping && !isDucking) {
                val dinoCenter = 100f + 12 * dinoScale
                if (dinoCenter > c.x && dinoCenter < c.x + c.width && c.alpha > 50) {
                    gameOver()
                }
            }
            
            if (c.x + c.width < -100 || c.alpha <= 0) cIter.remove()
        }

        // Earthquakes
        if (score > 500 && random.nextInt(1000) == 0 && earthquakeTimer <= 0) {
            earthquakeTimer = 180 // ~3 seconds
            SoundManager.playError() // Rumble start
        }

        if (earthquakeTimer > 0) {
            earthquakeTimer--
            earthquakeShake = (random.nextFloat() - 0.5f) * 20f
            
            // Randomly spawn Earthquake-induced obstacles
            if (earthquakeTimer % 40 == 0) {
                val etype = if (random.nextBoolean()) ObstacleType.CRATER else ObstacleType.RAISED_EDGE
                val ew = 150f + random.nextInt(100)
                val eh = if (etype == ObstacleType.RAISED_EDGE) 40f + random.nextInt(40) else 30f
                obstacles.add(Obstacle(width.toFloat() + 200f, height * groundY - eh, ew, eh, etype, random.nextInt(4)))
            }
            if (earthquakeTimer % 60 == 0) {
                obstacles.add(Obstacle(width.toFloat() + 300f, height * groundY - 40f, 180f, 40f, ObstacleType.FALLEN_TREE, random.nextInt(2)))
            }
        } else {
            earthquakeShake = 0f
        }
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
        val type = when (random.nextInt(25)) {
            in 0..1 -> if (score > 400) ObstacleType.PTEROSAUR else ObstacleType.CACTUS
            in 2..5 -> ObstacleType.TREE
            in 6..7 -> ObstacleType.ROCK
            in 8..10 -> ObstacleType.CRATER
            in 11..12 -> ObstacleType.STUMP
            13 -> if (score > 200) ObstacleType.STUMP else ObstacleType.ROCK
            in 14..18 -> if (score > 600) ObstacleType.METEOR else ObstacleType.CACTUS
            19 -> if (score > 1000) ObstacleType.THUNDERBOLT else ObstacleType.CRATER
            else -> ObstacleType.CACTUS
        }
        
        val isGroupable = type == ObstacleType.CACTUS || type == ObstacleType.TREE || type == ObstacleType.ROCK
        // Progressive group logic: more groups as score increases, up to 4 items
        val groupChance = when {
            score > 2500 -> 55
            score > 1500 -> 45
            score > 800 -> 25
            score > 300 -> 12
            else -> 0
        }
        
        // Calculate max allowed group width based on current jump ability
        val airTime = 2.0f * Math.abs(jumpStrength) / gravity
        val maxJumpDistance = gameSpeed * airTime
        val safeJumpWidth = maxJumpDistance * 0.75f // Leave 25% safety margin

        val count = if (isGroupable && random.nextInt(100) < groupChance) {
            when {
                score > 3000 -> random.nextInt(3) + 2 // 2 to 4
                score > 2000 -> random.nextInt(2) + 2 // 2 to 3
                else -> 2
            }
        } else 1
        
        var currentGroupWidth = 0f
        var maxHeightInGroup = 0f

        for (i in 0 until count) {
            val variant = random.nextInt(4)
            var width = 0f
            var height = 0f
            
            // Progressive size logic: obstacles get slightly bigger over time
            val sizeBoost = (score / 1500f).coerceAtMost(1f) * 30f

            when(type) {
                ObstacleType.PTEROSAUR -> { width = 90f; height = 60f }
                ObstacleType.TREE -> {
                    height = 140f + random.nextInt(60) + sizeBoost
                    width = 60f + random.nextInt(40) + sizeBoost * 0.5f
                }
                ObstacleType.CRATER -> { 
                    width = 100f + random.nextInt(150) + sizeBoost * 2f
                    height = 35f 
                }
                ObstacleType.ROCK -> { 
                    width = 70f + random.nextInt(50) + sizeBoost
                    height = 40f + random.nextInt(40) + sizeBoost * 0.5f
                }
                ObstacleType.CACTUS -> { 
                    width = 40f + random.nextInt(40)
                    height = 80f + random.nextInt(80) + sizeBoost
                }
                ObstacleType.STUMP -> { width = 50f + sizeBoost * 0.5f; height = 40f }
                ObstacleType.METEOR -> { 
                    width = 70f + random.nextInt(40)
                    height = width
                }
                ObstacleType.THUNDERBOLT -> { width = 30f; height = 100f }
                ObstacleType.FIRE -> { width = 120f; height = 120f }
                ObstacleType.FALLEN_TREE -> { width = 180f; height = 40f }
                ObstacleType.RAISED_EDGE -> { width = 150f; height = 60f }
            }
            
            // Check if adding this obstacle exceeds safe jump width
            val groupSpacing = if (i > 0) 5f + random.nextInt(15) else 0f
            if (i > 0 && currentGroupWidth + groupSpacing + width > safeJumpWidth) {
                break // Stop adding to this group
            }

            maxHeightInGroup = maxHeightInGroup.coerceAtLeast(height)
            
            // Spacing for group members
            val ox = this.width.toFloat() + currentGroupWidth + groupSpacing
            
            val y = if (type == ObstacleType.PTEROSAUR) {
                val h = if (random.nextBoolean()) 180f else 100f
                (this.height * groundY) - h - random.nextInt(80)
            } else if (type == ObstacleType.METEOR || type == ObstacleType.THUNDERBOLT) {
                val meteorOx = this.width.toFloat() * 0.8f + random.nextInt(400)
                obstacles.add(Obstacle(meteorOx, -200f, width, height, type, variant))
                continue // Meteor spawning is different, don't add to ground group logic
            } else if (type == ObstacleType.CRATER) {
                this.height * groundY - 10f 
            } else {
                (this.height * groundY) - height
            }
            
            obstacles.add(Obstacle(ox, y, width, height, type, variant))
            currentGroupWidth += groupSpacing + width
        }
        
        // --- Wise Difficulty Calculation ---
        val reactionDistance = gameSpeed * 20f // Increased reaction window for large groups
        
        val baseGap = if (maxHeightInGroup > 120f || lastObstacleHeight > 120f || currentGroupWidth > 200f) {
            maxJumpDistance + reactionDistance
        } else {
            maxJumpDistance * 0.85f + reactionDistance
        }

        val skyBuffer = if (type == ObstacleType.METEOR || type == ObstacleType.THUNDERBOLT) 300f else 0f
        nextObstacleDistance = (baseGap + skyBuffer + random.nextInt(600) + currentGroupWidth).coerceAtLeast(500f)
        
        lastObstacleHeight = maxHeightInGroup
    }

    private fun checkCollision(obs: Obstacle): Boolean {
        if (obs.type == ObstacleType.CRATER) {
            // Special collision for craters: lethal if Dino is on ground within crater X bounds
            val dinoHeight = if (isDucking) 15 * dinoScale else 21 * dinoScale
            val actualGroundY = height * groundY - dinoHeight
            val isOnGround = dinoY >= actualGroundY - 5f
            
            if (isOnGround) {
                val dinoCenter = 100f + 12 * dinoScale
                if (dinoCenter > obs.x + 10f && dinoCenter < obs.x + obs.width - 10f) {
                    return true
                }
            }
            return false
        }
        
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
        if (isNewHigh) {
            highScore = score
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
        } else {
            currentVictoryWord = ""
        }
        celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isNewHigh, score, highScore)
        onGameOver?.invoke(score)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // update() - Moved to gameLoop

        canvas.save()
        if (earthquakeShake != 0f) {
            canvas.translate(earthquakeShake, earthquakeShake * 0.5f)
        }

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

        // Draw Craters
        for (c in craters) {
            paint.color = Color.BLACK
            paint.alpha = (c.alpha * 0.4f).toInt()
            canvas.drawOval(c.x, lineY - 10, c.x + c.width, lineY + 30, paint)
            paint.color = theme.bgColor
            paint.alpha = (c.alpha * 0.6f).toInt()
            canvas.drawOval(c.x + 10, lineY - 5, c.x + c.width - 10, lineY + 15, paint)
        }
        paint.alpha = 255

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
            // Add shadow for better contrast
            paint.setShadowLayer(3f, 0f, 0f, if (isNightMode) Color.BLACK else Color.WHITE)
            canvas.drawText(memberName, width / 2f, height * 0.4f, paint)
            paint.clearShadowLayer()
        }

        // Score
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 45f
        
        // Adaptive score color with flash effect
        if (highScoreFlash > 0 && (highScoreFlash / 5) % 2 == 0) {
            paint.color = if (isNightMode) Color.YELLOW else Color.parseColor("#F57F17") // Deep orange/gold for light themes
        } else {
            paint.color = theme.textColor
        }

        paint.style = Paint.Style.FILL
        // Subtle shadow for HUD elements
        paint.setShadowLayer(2f, 1f, 1f, if (isNightMode) Color.BLACK else Color.argb(100, 255, 255, 255))
        
        val scoreX = Math.round(width - 50f).toFloat()
        val scoreY = Math.round(80f).toFloat()
        canvas.drawText("${context.getString(R.string.high_score_prefix)} ${String.format("%05d", highScore)}  ${String.format("%05d", score)}", scoreX, scoreY, paint)
        paint.clearShadowLayer()

        if (isGameOver) {
            celebrationManager.update()
            celebrationManager.draw(canvas)
            invalidate()
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.game_over)
            drawOverlay(canvas, title, theme.textColor)
        }
        else if (isPaused) drawOverlay(canvas, context.getString(R.string.game_trex), theme.textColor)
        
        // Earthquake warning
        if (earthquakeTimer > 150) {
            paint.color = Color.RED
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("EARTHQUAKE!", width / 2f, height * 0.3f, paint)
        }

        canvas.restore()
    }

    private fun drawObstacle(canvas: Canvas, obs: Obstacle, theme: Theme) {
        paint.style = Paint.Style.FILL
        when (obs.type) {
            ObstacleType.CACTUS -> {
                paint.color = theme.cactusColor
                // Better Cactus Shape
                val centerX = obs.x + obs.width / 2f
                val stemW = obs.width * 0.4f
                // Main stem
                canvas.drawRoundRect(centerX - stemW / 2, obs.y, centerX + stemW / 2, obs.y + obs.height, 10f, 10f, paint)
                
                // arms
                if (obs.height > 60f) {
                    // Left arm
                    val armY = obs.y + obs.height * 0.4f
                    canvas.drawRect(centerX - stemW / 2 - 15f, armY, centerX - stemW / 2, armY + 12f, paint)
                    canvas.drawRect(centerX - stemW / 2 - 15f, armY - 15f, centerX - stemW / 2 - 5f, armY, paint)
                    // Right arm
                    val armY2 = obs.y + obs.height * 0.3f
                    canvas.drawRect(centerX + stemW / 2, armY2, centerX + stemW / 2 + 15f, armY2 + 12f, paint)
                    canvas.drawRect(centerX + stemW / 2 + 5f, armY2 - 20f, centerX + stemW / 2 + 15f, armY2, paint)
                }

                // Blossoms (if variant is 2)
                if (obs.variant == 2) {
                    paint.color = Color.parseColor("#F48FB1") // Pink
                    canvas.drawCircle(centerX, obs.y, 8f, paint)
                    if (obs.height > 60f) {
                        canvas.drawCircle(centerX - stemW / 2 - 15f, obs.y + obs.height * 0.4f - 15f, 6f, paint)
                        canvas.drawCircle(centerX + stemW / 2 + 15f, obs.y + obs.height * 0.3f - 20f, 6f, paint)
                    }
                    paint.color = theme.cactusColor
                }

                // Spikes/Detail
                paint.color = Color.BLACK
                paint.alpha = 40
                canvas.drawRect(centerX - 2f, obs.y + 10f, centerX + 2f, obs.y + obs.height - 10f, paint)
                paint.alpha = 255
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
            ObstacleType.PTEROSAUR -> {
                // Pterosaur styling
                val pterosaurColor = when(obs.variant % 3) {
                    0 -> Color.parseColor("#5D4037") // Brown
                    1 -> Color.parseColor("#455A64") // Slate
                    else -> Color.parseColor("#37474F") // Dark Slate
                }
                paint.color = pterosaurColor
                
                // Body/Torso
                canvas.drawOval(obs.x + 20, obs.y + 20, obs.x + obs.width - 20, obs.y + obs.height - 10, paint)
                
                // Long Head/Beak
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x + 40, obs.y + 25)
                pathBuffer.lineTo(obs.x - 10, obs.y + 15) // Beak tip
                pathBuffer.lineTo(obs.x + 40, obs.y + 45)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
                
                // Crest on back of head
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x + 40, obs.y + 25)
                pathBuffer.lineTo(obs.x + 60, obs.y + 5)
                pathBuffer.lineTo(obs.x + 50, obs.y + 35)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)

                // Large Wings
                paint.color = pterosaurColor
                val wingSpan = if (walkFrame == 0) -40f else 40f
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x + 30, obs.y + 30)
                pathBuffer.lineTo(obs.x + 50, obs.y + 30 + wingSpan)
                pathBuffer.lineTo(obs.x + 70, obs.y + 30)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
            }
            ObstacleType.ROCK -> {
                paint.color = Color.parseColor("#757575")
                // More impressive, jagged rock shapes with variants
                pathBuffer.reset()
                
                if (obs.variant == 3) {
                    // Shattered Rock pieces - use deterministic values to avoid jitter
                    for (j in 0..4) {
                        val rx = obs.x + (j * obs.width / 5f)
                        val ry = obs.y + obs.height - 15f - (j * 3f % 10f)
                        val rw = 15f + (j * 7f % 10f)
                        canvas.drawRect(rx, ry, rx + rw, obs.y + obs.height, paint)
                    }
                } else {
                    pathBuffer.moveTo(obs.x, obs.y + obs.height)
                    when(obs.variant % 4) {
                        0 -> {
                            pathBuffer.lineTo(obs.x + obs.width * 0.1f, obs.y + obs.height * 0.4f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.3f, obs.y)
                            pathBuffer.lineTo(obs.x + obs.width * 0.5f, obs.y + obs.height * 0.2f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.7f, obs.y + obs.height * 0.05f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.9f, obs.y + obs.height * 0.5f)
                        }
                        1 -> {
                            pathBuffer.lineTo(obs.x + obs.width * 0.2f, obs.y + obs.height * 0.2f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.4f, obs.y + obs.height * 0.1f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.5f, obs.y)
                            pathBuffer.lineTo(obs.x + obs.width * 0.8f, obs.y + obs.height * 0.3f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.9f, obs.y + obs.height * 0.1f)
                        }
                        2 -> {
                            pathBuffer.lineTo(obs.x + obs.width * 0.05f, obs.y + obs.height * 0.3f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.15f, obs.y + obs.height * 0.1f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.4f, obs.y + obs.height * 0.4f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.7f, obs.y)
                            pathBuffer.lineTo(obs.x + obs.width * 0.95f, obs.y + obs.height * 0.2f)
                        }
                        else -> {
                            pathBuffer.lineTo(obs.x + obs.width * 0.1f, obs.y + obs.height * 0.6f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.2f, obs.y + obs.height * 0.2f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.6f, obs.y + obs.height * 0.1f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.8f, obs.y)
                            pathBuffer.lineTo(obs.x + obs.width * 0.95f, obs.y + obs.height * 0.4f)
                        }
                    }
                    pathBuffer.lineTo(obs.x + obs.width, obs.y + obs.height)
                    pathBuffer.close()
                    canvas.drawPath(pathBuffer, paint)
                    
                    // Inner details/Cracks
                    paint.color = Color.BLACK
                    paint.alpha = 50
                    canvas.drawLine(obs.x + obs.width * 0.3f, obs.y, obs.x + obs.width * 0.4f, obs.y + obs.height * 0.6f, paint)
                    canvas.drawLine(obs.x + obs.width * 0.7f, obs.y + obs.height * 0.05f, obs.x + obs.width * 0.6f, obs.y + obs.height * 0.7f, paint)
                    
                    // Highlight for 3D look
                    paint.color = Color.WHITE
                    paint.alpha = 40
                    canvas.drawCircle(obs.x + obs.width * 0.3f, obs.y + obs.height * 0.25f, obs.width * 0.15f, paint)
                }
                paint.alpha = 255
            }
            ObstacleType.CRATER -> {
                val lineY = height * groundY
                
                // Deep, impressive crater with various shapes based on variant
                pathBuffer.reset()
                when(obs.variant % 3) {
                    0 -> { // Wide jagged impact
                        pathBuffer.moveTo(obs.x, lineY - 8f)
                        pathBuffer.lineTo(obs.x + obs.width * 0.2f, lineY + 15f)
                        pathBuffer.lineTo(obs.x + obs.width * 0.5f, lineY + 35f)
                        pathBuffer.lineTo(obs.x + obs.width * 0.8f, lineY + 18f)
                        pathBuffer.lineTo(obs.x + obs.width, lineY - 10f)
                    }
                    1 -> { // Deep steep hole
                        pathBuffer.moveTo(obs.x + 10f, lineY - 5f)
                        pathBuffer.lineTo(obs.x + obs.width * 0.3f, lineY + 40f)
                        pathBuffer.lineTo(obs.x + obs.width * 0.7f, lineY + 40f)
                        pathBuffer.lineTo(obs.x + obs.width - 10f, lineY - 5f)
                    }
                    else -> { // Irregular double-dent crater
                        pathBuffer.moveTo(obs.x, lineY - 12f)
                        pathBuffer.lineTo(obs.x + obs.width * 0.3f, lineY + 20f)
                        pathBuffer.lineTo(obs.x + obs.width * 0.5f, lineY + 5f)
                        pathBuffer.lineTo(obs.x + obs.width * 0.7f, lineY + 25f)
                        pathBuffer.lineTo(obs.x + obs.width, lineY - 8f)
                    }
                }
                
                // Outer scorched area
                paint.color = Color.BLACK
                paint.alpha = 160
                canvas.drawOval(obs.x - 10f, lineY - 15f, obs.x + obs.width + 10f, lineY + 38f, paint)
                
                // Inner deep hole
                paint.color = Color.parseColor("#1A1A1B") // Darker than theme BG
                paint.alpha = 255
                canvas.drawPath(pathBuffer, paint)
                
                // Inner rim for depth/shading
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.parseColor("#37474F")
                canvas.drawPath(pathBuffer, paint)
                paint.style = Paint.Style.FILL
                
                // Ejecta/Scorched debris (varied by variant) - deterministic to avoid jitter
                paint.color = Color.DKGRAY
                val debrisCount = 3 + (obs.variant % 4)
                for (j in 0 until debrisCount) {
                    val dx = obs.x - 20f - (j * 15f)
                    canvas.drawRect(dx, lineY - 5f, dx + 10f, lineY, paint)
                    val dx2 = obs.x + obs.width + 10f + (j * j * 6f)
                    canvas.drawRect(dx2, lineY - 5f, dx2 + 10f, lineY, paint)
                    
                    if (obs.variant % 2 == 1) { // Add some vertical debris shards
                        val vx = obs.x + obs.width * 0.5f + (j * 25f) - (debrisCount * 12f)
                        canvas.drawRect(vx, lineY - 15f, vx + 6f, lineY - 5f, paint)
                    }
                }
            }
            ObstacleType.STUMP -> {
                paint.color = Color.parseColor("#5D4037")
                canvas.drawRect(obs.x, obs.y + 10f, obs.x + obs.width, obs.y + obs.height, paint)
                paint.color = Color.parseColor("#8D6E63") // Top part
                canvas.drawOval(obs.x, obs.y, obs.x + obs.width, obs.y + 20f, paint)
            }
            ObstacleType.METEOR -> {
                val cx = obs.x + obs.width / 2f
                val cy = obs.y + obs.height / 2f
                val r = obs.width / 2f
                
                // Outer glow/aura
                paint.shader = RadialGradient(cx, cy, r * 1.5f, 
                    intArrayOf(Color.argb(150, 255, 111, 0), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, r * 1.5f, paint)
                
                // Burning Tail
                paint.shader = LinearGradient(cx, cy, cx + obs.width * 1.5f, cy - obs.height * 1.5f, 
                    intArrayOf(Color.parseColor("#FF5722"), Color.parseColor("#FFD600"), Color.TRANSPARENT), 
                    floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
                
                pathBuffer.reset()
                pathBuffer.moveTo(cx, cy - r)
                pathBuffer.lineTo(cx + obs.width * 2f, cy - obs.height * 2f)
                pathBuffer.lineTo(cx + r, cy)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
                
                // Meteor Rock Body (Various shapes based on variant)
                paint.shader = null
                paint.color = Color.parseColor("#4E342E") // Dark brown/rock
                when(obs.variant) {
                    0 -> canvas.drawCircle(cx, cy, r, paint)
                    1 -> canvas.drawOval(obs.x, obs.y + r * 0.2f, obs.x + obs.width, obs.y + obs.height - r * 0.2f, paint)
                    else -> {
                        pathBuffer.reset()
                        pathBuffer.moveTo(obs.x + r, obs.y)
                        pathBuffer.lineTo(obs.x + obs.width, obs.y + r)
                        pathBuffer.lineTo(obs.x + r * 1.2f, obs.y + obs.height)
                        pathBuffer.lineTo(obs.x, obs.y + r * 1.2f)
                        pathBuffer.close()
                        canvas.drawPath(pathBuffer, paint)
                    }
                }
                
                // Shining Core/Cracks
                paint.color = Color.parseColor("#FFEB3B")
                paint.alpha = 200
                canvas.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.4f, paint)
                paint.alpha = 255
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
            ObstacleType.FIRE -> {
                val cx = obs.x + obs.width / 2f
                val cy = obs.y + obs.height
                // Flickering dynamic fire
                for (i in 0..4) {
                    val fx = cx + (i - 2) * 25f + (Math.sin(animationFrame * 0.2 + i).toFloat() * 10f)
                    val fy = cy - 20f - random.nextInt(100)
                    paint.color = if (random.nextBoolean()) Color.parseColor("#FFD600") else Color.parseColor("#FF3D00")
                    canvas.drawCircle(fx, fy, 20f + random.nextInt(20), paint)
                }
                // Base glow
                paint.color = Color.parseColor("#FF6D00")
                paint.alpha = 150
                canvas.drawRect(obs.x, cy - 20f, obs.x + obs.width, cy, paint)
                paint.alpha = 255
            }
            ObstacleType.FALLEN_TREE -> {
                // Log/Trunk on ground
                paint.color = Color.parseColor("#5D4037")
                canvas.drawRoundRect(obs.x, obs.y + obs.height * 0.5f, obs.x + obs.width, obs.y + obs.height, 10f, 10f, paint)
                // Broken branch jagged edges
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x, obs.y + obs.height * 0.5f)
                pathBuffer.lineTo(obs.x + 20f, obs.y)
                pathBuffer.lineTo(obs.x + 40f, obs.y + obs.height * 0.5f)
                canvas.drawPath(pathBuffer, paint)
            }
            ObstacleType.RAISED_EDGE -> {
                paint.color = theme.groundColor
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x, obs.y + obs.height)
                pathBuffer.lineTo(obs.x + obs.width * 0.2f, obs.y)
                pathBuffer.lineTo(obs.x + obs.width * 0.8f, obs.y + 10f)
                pathBuffer.lineTo(obs.x + obs.width, obs.y + obs.height)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
                // Crack detail
                paint.color = Color.BLACK
                paint.alpha = 100
                canvas.drawLine(obs.x + obs.width * 0.5f, obs.y + 5f, obs.x + obs.width * 0.5f, obs.y + obs.height, paint)
                paint.alpha = 255
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
        
        // Ensure text is visible even in game over state
        paint.color = if (isGameOver) {
            if (isNightMode) Color.parseColor("#FF5252") else Color.RED
        } else textColor
        
        paint.setShadowLayer(5f, 2f, 2f, if (isNightMode) Color.BLACK else Color.WHITE)
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        
        paint.textSize = 35f
        paint.color = textColor
        if (isGameOver) {
            canvas.drawText("${context.getString(R.string.score_label)}: $score", width / 2f, height / 2f + 50f, paint)
            canvas.drawText(context.getString(R.string.restart_hint), width / 2f, height / 2f + 100f, paint)
        } else {
            canvas.drawText(context.getString(R.string.start_game), width / 2f, height / 2f + 50f, paint)
        }
        paint.clearShadowLayer()
    }

    private fun getEnvironmentTheme(): Theme {
        return if (isNightMode) {
            Theme(Color.parseColor("#202124"), Color.WHITE, Color.parseColor("#BDC1C6"), Color.DKGRAY, Color.parseColor("#444444"), Color.parseColor("#F1F3F4"), Color.parseColor("#388E3C"), Color.parseColor("#A5D6A7"), Color.parseColor("#F48FB1"), Color.WHITE)
        } else {
            when (currentSeason) {
                Season.SPRING -> Theme(Color.parseColor("#E8F5E9"), Color.BLACK, Color.parseColor("#2E7D32"), Color.WHITE, Color.parseColor("#A5D6A7"), Color.parseColor("#FFD600"), Color.parseColor("#388E3C"), Color.parseColor("#43A047"), Color.parseColor("#EC407A"), Color.parseColor("#64B5F6"))
                Season.SUMMER -> Theme(Color.parseColor("#FFFDE7"), Color.BLACK, Color.parseColor("#FBC02D"), Color.WHITE, Color.parseColor("#FFF59D"), Color.parseColor("#FFD600"), Color.parseColor("#2E7D32"), Color.parseColor("#388E3C"), Color.parseColor("#D81B60"), Color.parseColor("#FFEB3B"))
                Season.AUTUMN -> Theme(Color.parseColor("#FBE9E7"), Color.BLACK, Color.parseColor("#D84315"), Color.WHITE, Color.parseColor("#FFAB91"), Color.parseColor("#FFD600"), Color.parseColor("#558B2F"), Color.parseColor("#A1887F"), Color.parseColor("#C62828"), Color.parseColor("#FF9800"))
                Season.WINTER -> Theme(Color.parseColor("#F5F5F5"), Color.BLACK, Color.parseColor("#0277BD"), Color.WHITE, Color.parseColor("#B3E5FC"), Color.parseColor("#FFD600"), Color.parseColor("#455A64"), Color.parseColor("#90A4AE"), Color.parseColor("#C2185B"), Color.WHITE)
            }
        }
    }

    data class Theme(val bgColor: Int, val textColor: Int, val dinoColor: Int, val cloudColor: Int, val groundColor: Int, val secondaryColor: Int, val cactusColor: Int, val treeColor: Int, val birdColor: Int, val weatherColor: Int)
    enum class ObstacleType { CACTUS, PTEROSAUR, TREE, ROCK, CRATER, METEOR, THUNDERBOLT, STUMP, FIRE, FALLEN_TREE, RAISED_EDGE }
    data class Obstacle(var x: Float, var y: Float, var width: Float, var height: Float, var type: ObstacleType, var variant: Int = 0)
}
