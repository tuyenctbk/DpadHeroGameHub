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
    private var causeOfDeath: ObstacleType? = null
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
    private var isNightMode = Random().nextBoolean()
    private var lastNightToggle = 0
    private var currentSeason = Season.SPRING
    private var currentWeather = Weather.SUNNY
    private var seasonOffset = 0
    private var weatherOffset = 0
    private var nightOffset = 0
    private var lastEnvironmentChangeTime = 0L
    private val ENVIRONMENT_CHANGE_INTERVAL = 90000L // 90 seconds
    
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
        causeOfDeath = null
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
        
        // Set initial state before game starts
        lastEnvironmentChangeTime = System.currentTimeMillis()
        updateEnvironment(force = true)
        
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
        
        val dinoHeight = if (isDucking) 16 * dinoScale else 23 * dinoScale
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

        // Randomly trigger conditions/events (not obstacles)
        if (score > 1500 && currentWeather == Weather.RAINY && random.nextInt(400) == 0) {
            // Spawn Thunderbolt (Strike ground/obstacles)
            val tx = random.nextFloat() * width + 200f
            obstacles.add(Obstacle(tx, -100f, 30f, 100f, ObstacleType.THUNDERBOLT, random.nextInt(4)))
        }
        if (score > 3000 && isNightMode && random.nextInt(800) == 0) {
            // Spawn Meteor (Create craters)
            val mx = random.nextFloat() * width + 400f
            obstacles.add(Obstacle(mx, -200f, 80f, 80f, ObstacleType.METEOR, random.nextInt(4)))
        }

        nextObstacleDistance -= gameSpeed
        if (nextObstacleDistance <= 0) spawnObstacle()

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obs = iterator.next()
            
            if (obs.type == ObstacleType.METEOR || obs.type == ObstacleType.THUNDERBOLT) {
                // Diagonal trajectory
                val fallSpeed = if (obs.type == ObstacleType.THUNDERBOLT) 22f else 14f
                val horizontalFactor = 1.3f + (obs.variant * 0.1f)
                obs.x -= gameSpeed * horizontalFactor
                obs.y += fallSpeed
                
                if (obs.y >= height * groundY - obs.height / 2) {
                    // Impact!
                    val color = if (obs.type == ObstacleType.METEOR) Color.parseColor("#FF5722") else Color.parseColor("#FFEA00")
                    explosions.add(Explosion(obs.x + obs.width / 2, height * groundY, color, 20f, 255))
                    
                    if (obs.type == ObstacleType.METEOR) {
                        craters.add(Crater(obs.x + obs.width / 4, obs.width * 1.8f, 255))
                    }

                    // Shared Impact Logic: Transform nearby obstacles to increase difficulty
                    var targetsFound = 0
                    val strikeX = obs.x + obs.width / 2f
                    val impactRange = if (obs.type == ObstacleType.METEOR) 200f else 150f
                    
                    for (other in obstacles) {
                        if (other != obs && (other.type == ObstacleType.CACTUS || other.type == ObstacleType.TREE || other.type == ObstacleType.ROCK)) {
                            if (Math.abs(strikeX - (other.x + other.width / 2f)) < impactRange) {
                                targetsFound++
                                if (other.type == ObstacleType.ROCK) {
                                    other.variant = 3 // Shattered/Cracked
                                    other.width *= 1.5f // Increase difficulty (larger kill zone)
                                } else {
                                    // Burn Trees/Cacti
                                    other.type = ObstacleType.FIRE
                                    other.width = 240f
                                    other.height = 240f
                                    other.y = height * groundY - 240f
                                }
                            }
                        }
                    }
                    
                    // Lightning strike on empty ground creates fire; Meteor creates Crater (already handled)
                    if (targetsFound == 0 && obs.type == ObstacleType.THUNDERBOLT) {
                        obstacles.add(Obstacle(obs.x - 100f, height * groundY - 200f, 200f, 200f, ObstacleType.FIRE, 0))
                    }
                    SoundManager.playClick()
                    iterator.remove()
                    continue
                }
                
                // Events don't kill while in the air (don't check collision yet)
                continue 
            }
else if (obs.type == ObstacleType.PTEROSAUR) {
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
            } else if (obs.type == ObstacleType.BIG_DINO) {
                // Moving big dino: relatively slow movement relative to game speed
                val dinoSpeed = if (obs.variant % 2 == 0) 2f else -2f // Some move left faster, some move right
                obs.x -= (gameSpeed + dinoSpeed)
            } else {
                obs.x -= gameSpeed
            }

            if (checkCollision(obs)) {
                gameOver(obs.type)
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
            
            // Check collision with lethal canyon
            if (!isJumping && !isDucking) {
                val dinoCenter = 100f + 12 * dinoScale
                if (dinoCenter > c.x && dinoCenter < c.x + c.width && c.alpha > 50) {
                    gameOver(ObstacleType.CANYON)
                }
            }
            
            if (c.x + c.width < -100 || c.alpha <= 0) cIter.remove()
        }

        // Earthquakes
        if (score > 6000 && random.nextInt(4000) == 0 && earthquakeTimer <= 0) {
            earthquakeTimer = 240 // ~4 seconds
            SoundManager.playError() // Rumble start
        }

        if (earthquakeTimer > 0) {
            earthquakeTimer--
            earthquakeShake = (random.nextFloat() - 0.5f) * 20f
            
            // Randomly transform existing obstacles during earthquake
            if (earthquakeTimer % 20 == 0) {
                for (other in obstacles) {
                    if (other.x > 0 && other.x < width && random.nextInt(10) == 0) {
                        when(other.type) {
                            ObstacleType.TREE -> {
                                other.type = ObstacleType.FALLEN_TREE
                                other.width = 180f
                                other.height = 40f
                                other.y = height * groundY - 40f
                            }
                            ObstacleType.ROCK -> {
                                other.variant = 3 // Shattered
                                other.width *= 1.4f
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Randomly spawn Earthquake-induced obstacles
            if (earthquakeTimer % 40 == 0) {
                val etype = if (random.nextInt(3) == 0) ObstacleType.CANYON else ObstacleType.RAISED_EDGE
                val ew = if (etype == ObstacleType.CANYON) 250f + random.nextInt(200) else 150f + random.nextInt(100)
                val eh = if (etype == ObstacleType.RAISED_EDGE) 40f + random.nextInt(40) else 30f
                obstacles.add(Obstacle(width.toFloat() + 200f, height * groundY - eh, ew, eh, etype, random.nextInt(4)))
            }
            if (earthquakeTimer % 80 == 0) {
                obstacles.add(Obstacle(width.toFloat() + 300f, height * groundY - 40f, 220f, 40f, ObstacleType.FALLEN_TREE, random.nextInt(2)))
            }
        } else {
            earthquakeShake = 0f
        }
    }

    private fun updateEnvironment(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastEnvironmentChangeTime < ENVIRONMENT_CHANGE_INTERVAL) return
        
        if (!force) {
            // Cycle one property at a time for a "logical" change
            when(random.nextInt(3)) {
                0 -> { // Toggle Day/Night
                    isNightMode = !isNightMode
                    if (isNightMode) SoundManager.playSuccess()
                }
                1 -> { // Change Season
                    currentSeason = Season.entries[(currentSeason.ordinal + 1) % Season.entries.size]
                }
                2 -> { // Change Weather
                    currentWeather = Weather.entries[(currentWeather.ordinal + 1) % Weather.entries.size]
                }
            }
            lastEnvironmentChangeTime = now
        } else {
            // Randomize but ENSURE at least one major component is different from previous run
            // and favor a light/bright start if it was dark before
            val prevNight = isNightMode
            val prevSeason = currentSeason
            val prevWeather = currentWeather
            
            // Forced change on reset
            isNightMode = !prevNight
            currentSeason = Season.entries.random()
            currentWeather = Weather.entries.random()
            
            // If somehow they are still the same (unlikely with night toggle), randomize until different
            while (isNightMode == prevNight && currentSeason == prevSeason && currentWeather == prevWeather) {
                isNightMode = random.nextBoolean()
                currentSeason = Season.entries.random()
                currentWeather = Weather.entries.random()
            }
            lastEnvironmentChangeTime = now
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
        var baseType = when (random.nextInt(30)) {
            in 0..1 -> if (score > 400) ObstacleType.PTEROSAUR else ObstacleType.CACTUS
            in 2..5 -> ObstacleType.TREE
            in 6..7 -> ObstacleType.ROCK
            in 8..10 -> ObstacleType.CANYON
            in 11..12 -> ObstacleType.STUMP
            in 13..14 -> if (score > 1000) ObstacleType.BIG_DINO else ObstacleType.ROCK
            else -> ObstacleType.CACTUS
        }
        
        val isGroupable = baseType == ObstacleType.CACTUS || baseType == ObstacleType.TREE || 
                         baseType == ObstacleType.ROCK || baseType == ObstacleType.BIG_DINO || 
                         baseType == ObstacleType.PTEROSAUR
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
                baseType == ObstacleType.BIG_DINO || baseType == ObstacleType.PTEROSAUR -> random.nextInt(2) + 2 // 2 to 3
                score > 3000 -> random.nextInt(3) + 2 // 2 to 4
                score > 2000 -> random.nextInt(2) + 2 // 2 to 3
                else -> 2
            }
        } else 1
        
        var currentGroupWidth = 0f
        var maxHeightInGroup = 0f

        for (i in 0 until count) {
            // Allow mixed obstacles in a group
            val type = if (i > 0 && isGroupable && random.nextBoolean()) {
                val mixed = listOf(ObstacleType.CACTUS, ObstacleType.TREE, ObstacleType.ROCK, ObstacleType.STUMP)
                mixed.random()
            } else baseType

            val variant = random.nextInt(4)
            var width = 0f
            var height = 0f
            
            // Progressive size logic: obstacles get slightly bigger over time
            val sizeBoost = (score / 1500f).coerceAtMost(1f) * 30f

            when(type) {
                ObstacleType.PTEROSAUR -> { 
                    val s = 1.0f + (random.nextFloat() * 0.5f) // Variety in size
                    width = 90f * s; height = 60f * s 
                }
                ObstacleType.TREE -> {
                    height = 140f + random.nextInt(60) + sizeBoost
                    width = 60f + random.nextInt(40) + sizeBoost * 0.5f
                }
                ObstacleType.CANYON -> { 
                    width = 200f + random.nextInt(250) + sizeBoost * 2f
                    height = 35f 
                }
                ObstacleType.ROCK -> { 
                    width = 100f + random.nextInt(60) + sizeBoost
                    height = 60f + random.nextInt(60) + sizeBoost * 0.5f
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
                ObstacleType.BIG_DINO -> {
                    // Big Dino should be no shorter than T-Rex (approx 140f)
                    val s = 1.0f + (random.nextFloat() * 0.8f) // Variety in scale
                    width = 160f * s
                    height = 130f * s // Base 130 + scaling ensures tallness
                }
            }
            
            // Spacing for group members
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
            } else if (type == ObstacleType.CANYON) {
                this.height * groundY - 10f 
            } else {
                (this.height * groundY) - height
            }
            
            obstacles.add(Obstacle(ox, y, width, height, type, variant))
            currentGroupWidth += groupSpacing + width
        }
        
        // --- Wise Difficulty Calculation ---
        val reactionDistance = gameSpeed * 25f // Buffer for the player to react
        
        // Minimum gap: T-Rex has landing buffer + time to perform the next jump
        val minGap = (maxJumpDistance + reactionDistance) * 0.9f
        
        // Maximum gap: Ensures the game stays engaging without long empty stretches
        val maxExtraGap = 630f // 700f reduced by 10%
        
        val skyBuffer = if (baseType == ObstacleType.METEOR || baseType == ObstacleType.THUNDERBOLT) 300f else 0f
        
        nextObstacleDistance = (minGap + random.nextFloat() * maxExtraGap + skyBuffer + currentGroupWidth).coerceAtLeast(540f)
        
        lastObstacleHeight = maxHeightInGroup
    }

    private fun checkCollision(obs: Obstacle): Boolean {
        if (obs.type == ObstacleType.CANYON) {
            // Special collision for canyons: lethal if Dino is on ground within canyon X bounds
            val dinoHeight = if (isDucking) 16 * dinoScale else 23 * dinoScale
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
        
        val dinoHeight = if (isDucking) 16 * dinoScale else 23 * dinoScale
        val dinoRect = RectF(100f, dinoY, 100f + 25 * dinoScale, dinoY + dinoHeight)
        val obsRect = RectF(obs.x, obs.y, obs.x + obs.width, obs.y + obs.height)
        dinoRect.inset(15f, 10f)
        obsRect.inset(10f, 10f)
        return RectF.intersects(dinoRect, obsRect)
    }

    private fun gameOver(type: ObstacleType? = null) {
        causeOfDeath = type
        isGameOver = true
        SoundManager.playError()
        val oldBest = highScore
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
        if (isNewHigh) {
            highScore = score
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
        } else {
            currentVictoryWord = ""
        }
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isNewHigh = isNewHigh,
            score = score,
            highScore = oldBest
        )
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
            GameEnvironment.BackgroundType.NONE,
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
        for (cloud in clouds) {
            val cx = cloud.x
            val cy = cloud.y
            // Draw a more "fluffy" cloud using overlapping ovals
            canvas.drawOval(cx, cy, cx + 100, cy + 40, paint)
            canvas.drawOval(cx + 20, cy - 20, cx + 80, cy + 20, paint)
            canvas.drawOval(cx + 40, cy, cx + 120, cy + 40, paint)
        }

        // Draw Ground
        paint.color = theme.groundColor
        val lineY = height * groundY
        canvas.drawRect(0f, lineY, width.toFloat(), height.toFloat(), paint)
        
        paint.color = theme.groundColor
        paint.strokeWidth = 3f
        canvas.drawLine(0f, lineY, width.toFloat(), lineY, paint)
        for (dot in groundDots) canvas.drawRect(dot.x, dot.y, dot.x + 4, dot.y + 4, paint)

        // Draw Craters (Meteor impacts)
        for (c in craters) {
            paint.color = Color.BLACK
            paint.alpha = (c.alpha * 0.5f).toInt()
            canvas.drawOval(c.x - 10, lineY - 15, c.x + c.width + 10, lineY + 60, paint) // Scorched area
            
            paint.color = Color.parseColor("#1A1A1B")
            paint.alpha = c.alpha
            pathBuffer.reset()
            pathBuffer.moveTo(c.x, lineY - 5f)
            pathBuffer.lineTo(c.x + c.width * 0.2f, lineY + 40f)
            pathBuffer.lineTo(c.x + c.width * 0.5f, lineY + 80f)
            pathBuffer.lineTo(c.x + c.width * 0.8f, lineY + 40f)
            pathBuffer.lineTo(c.x + c.width, lineY - 5f)
            pathBuffer.close()
            canvas.drawPath(pathBuffer, paint)
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
        TRexDrawer.drawObstacle(canvas, obs, theme, paint, pathBuffer, isNightMode, animationFrame, walkFrame)
    }

    private fun drawDino(canvas: Canvas, x: Float, y: Float, color: Int, eyeColor: Int) {
        // Special Colors per Member
        var bodyColor = when(currentMember) {
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
        
        TRexDrawer.drawDino(canvas, x, y, bodyColor, eyeColor, dinoScale, isGameOver, causeOfDeath, isDucking, isJumping, walkFrame, isNightMode, animationFrame, obstacles, paint, pathBuffer)
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
        val isRainy = currentWeather == Weather.RAINY
        val isSnowy = currentWeather == Weather.SNOWY
        
        return if (isNightMode) {
            // Nighttime condition: sky is a very dark charcoal, ground is black (heavier)
            val bgColor = Color.parseColor("#121212") // Not pure black for sky
            val textColor = Color.WHITE
            val cloudColor = if (isRainy) Color.parseColor("#2C2C2C") else Color.parseColor("#333333")
            val ground = Color.BLACK // Ground is darker than sky
            
            // All seasons: Cacti and Trees are GREEN
            val cac = Color.parseColor("#2E7D32")
            val tree = Color.parseColor("#1B5E20")
            
            when (currentSeason) {
                Season.WINTER -> {
                    Theme(bgColor, textColor, Color.parseColor("#90CAF9"), cloudColor, ground, Color.parseColor("#1A237E"), cac, tree, Color.parseColor("#F48FB1"), Color.WHITE)
                }
                Season.AUTUMN -> {
                    Theme(bgColor, textColor, Color.parseColor("#CFD8DC"), cloudColor, ground, Color.parseColor("#212121"), cac, tree, Color.parseColor("#F48FB1"), Color.WHITE)
                }
                else -> {
                    Theme(bgColor, textColor, Color.parseColor("#BDC1C6"), cloudColor, ground, Color.parseColor("#263238"), cac, tree, Color.parseColor("#F48FB1"), Color.WHITE)
                }
            }
        } else {
            // Daytime condition: sky is dimmed for less brightness, ground remains significantly darker
            // All colors here reduced by 20% brightness for "darker air/ground" request
            val cloudColor = when {
                isRainy -> Color.parseColor("#8C989E") // B0BEC5 -20%
                isSnowy -> Color.parseColor("#CCCCCC") // White -20%
                else -> Color.parseColor("#C4C4C4") // F5F5F5 -20%
            }
            
            // All seasons: Cacti and Trees are GREEN
            val cac = Color.parseColor("#388E3C")
            val tree = Color.parseColor("#2E7D32")

            when (currentSeason) {
                Season.SPRING -> {
                    val bg = if (isRainy) Color.parseColor("#8FB2AF") else Color.parseColor("#A0B8A1")
                    Theme(bg, Color.BLACK, Color.parseColor("#1B5E20"), cloudColor, Color.parseColor("#53802D"), Color.parseColor("#CCAB00"), cac, tree, Color.parseColor("#BD3362"), Color.parseColor("#5091C5"))
                }
                Season.SUMMER -> {
                    val bg = if (isRainy) Color.parseColor("#9DB584") else Color.parseColor("#B8BE7D")
                    Theme(bg, Color.BLACK, Color.parseColor("#C99A24"), cloudColor, Color.parseColor("#C46612"), Color.parseColor("#CCAB00"), cac, tree, Color.parseColor("#AD164D"), Color.parseColor("#CCBC2F"))
                }
                Season.AUTUMN -> {
                    val bg = if (isRainy) Color.parseColor("#CCA366") else Color.parseColor("#CCB38E")
                    Theme(bg, Color.BLACK, Color.parseColor("#AD3611"), cloudColor, Color.parseColor("#992B0A"), Color.parseColor("#CCAB00"), cac, tree, Color.parseColor("#9E2020"), Color.parseColor("#CC7A00"))
                }
                Season.WINTER -> {
                    val bg = if (isSnowy) Color.parseColor("#8C989E") else Color.parseColor("#96B2C9")
                    Theme(bg, Color.BLACK, Color.parseColor("#025F96"), cloudColor, Color.parseColor("#145EA8"), Color.parseColor("#CCAB00"), cac, tree, Color.parseColor("#9B1349"), Color.parseColor("#CCCCCC"))
                }
            }
        }
    }

    data class Theme(val bgColor: Int, val textColor: Int, val dinoColor: Int, val cloudColor: Int, val groundColor: Int, val secondaryColor: Int, val cactusColor: Int, val treeColor: Int, val birdColor: Int, val weatherColor: Int)
    enum class ObstacleType { CACTUS, PTEROSAUR, TREE, ROCK, CANYON, METEOR, THUNDERBOLT, STUMP, FIRE, FALLEN_TREE, RAISED_EDGE, BIG_DINO }
    data class Obstacle(var x: Float, var y: Float, var width: Float, var height: Float, var type: ObstacleType, var variant: Int = 0)
}
