package com.tdpham.games.trex

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tdpham.games.common.GameView
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private enum class DinoMember(
        val scale: Float,
        val jump: Float,
        val gravityMult: Float = 1.0f,
        val scoreMult: Float = 1.0f,
        val canDoubleJump: Boolean = false,
        val abilityDesc: String = ""
    ) {
        DADDY(6.5f, -30f, scoreMult = 1.2f, abilityDesc = "Score x1.2"),
        NINJA(6.0f, -31f, canDoubleJump = true, abilityDesc = "Double Jump"),
        ASTRONAUT(6.0f, -30f, gravityMult = 0.6f, abilityDesc = "Low Gravity"),
        BABY(3.5f, -26f, scoreMult = 0.8f, abilityDesc = "Tiny Hitbox"),
        GRANDPA(5.8f, -24f, scoreMult = 1.5f, abilityDesc = "Score x1.5 / Slow"),
        SCIENTIST(5.8f, -28f, scoreMult = 1.3f, abilityDesc = "Score x1.3"),
        PIRATE(6.2f, -33f, abilityDesc = "Strong Jump"),
        MUMMY(6.0f, -28f, scoreMult = 1.1f, abilityDesc = "Score x1.1"),
        TEENAGER(5.5f, -29f, abilityDesc = "Fast Runner"),
        CHEF(6.0f, -28f, scoreMult = 1.25f, abilityDesc = "Score x1.25"),
        ATHLETE(6.0f, -32f, gravityMult = 1.1f, abilityDesc = "Fast Fall / High Jump"),
        DRAGON(7.0f, -31f, scoreMult = 1.4f, abilityDesc = "Giant / Score x1.4"),
        ZOMBIE(6.0f, -25f, scoreMult = 2.0f, abilityDesc = "Slow / Score x2.0"),
        ROBOT(6.0f, -29f, gravityMult = 0.8f, abilityDesc = "Steady Physics"),
        KING(6.5f, -30f, scoreMult = 1.8f, abilityDesc = "Royalty / Score x1.8")
    }

    private var currentMember = DinoMember.DADDY
    private var selectedMemberIndex = 0
    private val PREFS_NAME = "trex_settings"
    private val KEY_SELECTED_CHAR = "selected_char_index"
    private var memberName = ""
    private var nameShowFrames = 0
    private var hasDoubleJumped = false
    
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
    
    // Cached objects for optimization
    private var currentTheme: Theme? = null
    private val dinoRect = RectF()
    private val obsRect = RectF()

    // Earthquake state
    private var earthquakeShake = 0f
    private var earthquakeTimer = 0

    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isGameOver && !isPaused) {
                update()
                invalidate()
                android.view.Choreographer.getInstance().postFrameCallback(this)
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
        loadSettings()
        resetGame()
    }

    private fun loadSettings() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        selectedMemberIndex = prefs.getInt(KEY_SELECTED_CHAR, 0).coerceIn(0, DinoMember.entries.size - 1)
        currentMember = DinoMember.entries[selectedMemberIndex]
    }

    private fun saveSettings() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SELECTED_CHAR, selectedMemberIndex).apply()
    }

    override fun startGame() {
        if (isPaused) resume()
        requestFocus()
    }

    override fun pause() {
        isPaused = true
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun resume() {
        isPaused = false
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
        android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun resetGame() {
        score = 0
        highScore = ScoreManager.getHighScore(context, gameKey)
        isGameOver = false
        isPaused = true
        causeOfDeath = null
        celebrationManager.start(0f, 0f)
        
        // Use selected member
        currentMember = DinoMember.entries[selectedMemberIndex]
        applyMemberProperties()
        memberName = context.getString(when(currentMember) {
            DinoMember.DADDY -> R.string.trex_daddy
            DinoMember.NINJA -> R.string.trex_athlete 
            DinoMember.ASTRONAUT -> R.string.trex_astronaut
            DinoMember.BABY -> R.string.trex_baby
            DinoMember.GRANDPA -> R.string.trex_grandpa
            DinoMember.SCIENTIST -> R.string.trex_scientist
            DinoMember.PIRATE -> R.string.trex_pirate
            DinoMember.MUMMY -> R.string.trex_mummy
            DinoMember.TEENAGER -> R.string.trex_teenager
            DinoMember.CHEF -> R.string.trex_chef
            DinoMember.ATHLETE -> R.string.trex_athlete
            DinoMember.DRAGON -> R.string.trex_dragon
            DinoMember.ZOMBIE -> R.string.trex_zombie
            DinoMember.ROBOT -> R.string.trex_robot
            DinoMember.KING -> R.string.trex_king
        })
        nameShowFrames = 120 
        hasDoubleJumped = false

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
                if (isPaused) resume() else jump()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!isJumping) {
                    isDucking = true
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isPaused && !isGameOver) {
                    selectedMemberIndex = (selectedMemberIndex - 1 + DinoMember.entries.size) % DinoMember.entries.size
                    currentMember = DinoMember.entries[selectedMemberIndex]
                    saveSettings()
                    applyMemberProperties()
                    invalidate()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isPaused && !isGameOver) {
                    selectedMemberIndex = (selectedMemberIndex + 1) % DinoMember.entries.size
                    currentMember = DinoMember.entries[selectedMemberIndex]
                    saveSettings()
                    applyMemberProperties()
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
                jump()
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
        if (!isJumping) {
            dinoVelocityY = currentMember.jump
            isJumping = true
            hasDoubleJumped = false
            SoundManager.playClick()
        } else if (currentMember.canDoubleJump && !hasDoubleJumped) {
            dinoVelocityY = currentMember.jump * 0.8f
            hasDoubleJumped = true
            SoundManager.playClick()
        }
    }

    private fun applyMemberProperties() {
        dinoScale = currentMember.scale
        jumpStrength = currentMember.jump
    }

    private fun update() {
        if (isGameOver || isPaused) return

        if (nameShowFrames > 0) nameShowFrames--
        animationFrame++
        if (animationFrame % 6 == 0) {
            walkFrame = (walkFrame + 1) % 2
        }

        if (isNightMode && animationFrame % 10 == 0) {
            stars.forEach {
                if (random.nextFloat() > 0.95f) it.alpha = random.nextInt(255)
            }
        }

        distanceTravelled += gameSpeed * currentMember.scoreMult
        score = (distanceTravelled / 50).toInt()
        
        if (score > highScore && highScore > 0 && !isNewHighScoreBroken) {
            isNewHighScoreBroken = true
            highScoreFlash = 60
        }
        if (highScoreFlash > 0) highScoreFlash--

        updateEnvironment()

        gameSpeed += 0.0025f
        dinoVelocityY += gravity * currentMember.gravityMult
        dinoY += dinoVelocityY
        
        val dinoHeight = if (isDucking) 16 * dinoScale else 23 * dinoScale
        val actualGroundY = height * groundY - dinoHeight
        if (dinoY >= actualGroundY) {
            dinoY = actualGroundY
            dinoVelocityY = 0f
            isJumping = false
        }

        updateDecorations()
        updateParticles()
        updateEvents()

        // Obstacle Spawning and Movement
        if (score > 1500 && currentWeather == Weather.RAINY && random.nextInt(400) == 0) {
            obstacles.add(Obstacle(random.nextFloat() * width + 200f, -100f, 30f, 100f, ObstacleType.THUNDERBOLT, random.nextInt(4)))
        }
        if (score > 3000 && isNightMode && random.nextInt(800) == 0) {
            obstacles.add(Obstacle(random.nextFloat() * width + 400f, -200f, 80f, 80f, ObstacleType.METEOR, random.nextInt(4)))
        }

        nextObstacleDistance -= gameSpeed
        if (nextObstacleDistance <= 0) spawnObstacle()

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obs = iterator.next()
            updateObstaclePosition(obs)
            
            if (obs.type != ObstacleType.METEOR && obs.type != ObstacleType.THUNDERBOLT) {
                if (checkCollision(obs)) {
                    gameOver(obs.type)
                    break
                }
            }
            
            if (obs.x + obs.width < -200) iterator.remove()
        }
    }

    private fun updateParticles() {
        if (!isJumping && animationFrame % 4 == 0) {
            runParticles.add(RunParticle(120f + random.nextInt(40), height * groundY - 5, random.nextFloat() * 4 + 2, 200, -gameSpeed * 0.5f, -random.nextFloat() * 2))
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

        val eIter = explosions.iterator()
        while (eIter.hasNext()) {
            val e = eIter.next()
            e.radius += 5f
            e.alpha -= 10
            if (e.alpha <= 0) eIter.remove()
        }
    }

    private fun updateObstaclePosition(obs: Obstacle) {
        when (obs.type) {
            ObstacleType.METEOR, ObstacleType.THUNDERBOLT -> {
                val fallSpeed = if (obs.type == ObstacleType.THUNDERBOLT) 22f else 14f
                val horizontalFactor = 1.3f + (obs.variant * 0.1f)
                obs.x -= gameSpeed * horizontalFactor
                obs.y += fallSpeed
                
                if (obs.y >= height * groundY - obs.height / 2) {
                    handleObstacleImpact(obs)
                    obs.x = -1000f // Mark for removal
                }
            }
            ObstacleType.PTEROSAUR -> {
                obs.x -= gameSpeed * 1.2f
                val freqFactor = (0.1 + (score / 4000.0)).coerceAtMost(0.3)
                val ampFactor = (3f + (score / 1200f)).coerceAtMost(12f)
                obs.y += (Math.sin(animationFrame * freqFactor + obs.variant) + Math.sin(animationFrame * freqFactor * 0.5 + obs.variant * 2)).toFloat() * (ampFactor * 0.5f)
                if (score > 2000 && obs.x < width * 0.5f && obs.x > width * 0.2f && obs.variant % 2 == 0) {
                    obs.y += 4f
                }
            }
            ObstacleType.BIG_DINO -> {
                obs.x -= (gameSpeed + if (obs.variant % 2 == 0) 2f else -2f)
            }
            else -> {
                obs.x -= gameSpeed
            }
        }
    }

    private fun handleObstacleImpact(obs: Obstacle) {
        val color = if (obs.type == ObstacleType.METEOR) Color.parseColor("#FF5722") else Color.parseColor("#FFEA00")
        explosions.add(Explosion(obs.x + obs.width / 2, height * groundY, color, 20f, 255))
        
        if (obs.type == ObstacleType.METEOR) {
            craters.add(Crater(obs.x + obs.width / 4, obs.width * 1.8f, 255))
        }

        val strikeX = obs.x + obs.width / 2f
        val impactRange = if (obs.type == ObstacleType.METEOR) 200f else 150f
        var targetsFound = 0
        
        for (other in obstacles) {
            if (other != obs && (other.type == ObstacleType.CACTUS || other.type == ObstacleType.TREE || other.type == ObstacleType.ROCK)) {
                if (Math.abs(strikeX - (other.x + other.width / 2f)) < impactRange) {
                    targetsFound++
                    if (other.type == ObstacleType.ROCK) {
                        other.variant = 3
                        other.width *= 1.5f
                    } else {
                        other.type = ObstacleType.FIRE
                        other.width = 240f; other.height = 240f
                        other.y = height * groundY - 240f
                    }
                }
            }
        }
        
        if (targetsFound == 0 && obs.type == ObstacleType.THUNDERBOLT) {
            obstacles.add(Obstacle(obs.x - 100f, height * groundY - 200f, 200f, 200f, ObstacleType.FIRE, 0))
        }
        SoundManager.playClick()
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
            currentTheme = getEnvironmentTheme() // Cache new theme
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
            currentTheme = getEnvironmentTheme() // Cache new theme
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
        // Progressive group logic: more groups as score increases, up to 4 items (+15% base increase)
        val groupChance = when {
            score > 2500 -> 70
            score > 1500 -> 60
            score > 800 -> 40
            score > 300 -> 27
            else -> 15
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
        dinoRect.set(100f, dinoY, 100f + 25 * dinoScale, dinoY + dinoHeight)
        obsRect.set(obs.x, obs.y, obs.x + obs.width, obs.y + obs.height)
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

        canvas.save()
        if (earthquakeShake != 0f) {
            canvas.translate(earthquakeShake, earthquakeShake * 0.5f)
        }

        val theme = currentTheme ?: getEnvironmentTheme().also { currentTheme = it }
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

        // Draw Craters (Meteor impacts -> Water Lakes)
        for (c in craters) {
            // Water Surface Glow
            paint.shader = RadialGradient(c.x + c.width / 2f, lineY + 10f, c.width * 0.7f,
                intArrayOf(Color.parseColor("#4FC3F7"), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(c.x + c.width / 2f, lineY + 10f, c.width * 0.7f, paint)
            paint.shader = null

            // Lake Body (Blue Water)
            paint.color = Color.parseColor("#0288D1")
            paint.alpha = (c.alpha * 0.8f).toInt()
            pathBuffer.reset()
            pathBuffer.moveTo(c.x, lineY)
            pathBuffer.lineTo(c.x + c.width * 0.2f, lineY + 30f)
            pathBuffer.lineTo(c.x + c.width * 0.5f, lineY + 50f)
            pathBuffer.lineTo(c.x + c.width * 0.8f, lineY + 30f)
            pathBuffer.lineTo(c.x + c.width, lineY)
            pathBuffer.close()
            canvas.drawPath(pathBuffer, paint)

            // Ripple effects
            paint.color = Color.WHITE
            paint.alpha = (c.alpha * 0.3f).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawOval(c.x + 20f, lineY + 10f, c.x + c.width - 20f, lineY + 20f, paint)
            paint.style = Paint.Style.FILL
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
        TRexDrawer.drawDino(canvas, x, y, color, eyeColor, dinoScale, isGameOver, causeOfDeath, isDucking, isJumping, walkFrame, isNightMode, animationFrame, obstacles, paint, pathBuffer, currentMember.name)
    }

    private fun drawOverlay(canvas: Canvas, title: String, textColor: Int) {
        paint.color = if (isNightMode) Color.argb(160, 0, 0, 0) else Color.argb(160, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        
        if (isPaused && !isGameOver) {
            // Character Selection UI
            paint.textSize = 80f
            paint.color = textColor
            paint.setShadowLayer(5f, 2f, 2f, if (isNightMode) Color.BLACK else Color.WHITE)
            canvas.drawText("SELECT CHARACTER", width / 2f, height / 2f - 180f, paint)
            
            // Draw current selection preview
            val member = DinoMember.entries[selectedMemberIndex]
            val previewScale = 12f
            val previewX = width / 2f - (25 * previewScale / 2f)
            val previewY = height / 2f - (23 * previewScale / 2f)
            
            drawDinoPreview(canvas, previewX, previewY, previewScale, member)
            
            // Member Name and Stats
            paint.textSize = 50f
            val name = context.getString(when(member) {
                DinoMember.DADDY -> R.string.trex_daddy
                DinoMember.NINJA -> R.string.trex_athlete
                DinoMember.ASTRONAUT -> R.string.trex_astronaut
                DinoMember.BABY -> R.string.trex_baby
                DinoMember.GRANDPA -> R.string.trex_grandpa
                DinoMember.SCIENTIST -> R.string.trex_scientist
                DinoMember.PIRATE -> R.string.trex_pirate
                DinoMember.MUMMY -> R.string.trex_mummy
                DinoMember.TEENAGER -> R.string.trex_teenager
                DinoMember.CHEF -> R.string.trex_chef
                DinoMember.ATHLETE -> R.string.trex_athlete
                DinoMember.DRAGON -> R.string.trex_dragon
                DinoMember.ZOMBIE -> R.string.trex_zombie
                DinoMember.ROBOT -> R.string.trex_robot
                DinoMember.KING -> R.string.trex_king
            })
            canvas.drawText(name, width / 2f, height / 2f + 140f, paint)
            
            paint.textSize = 35f
            paint.color = if (isNightMode) Color.LTGRAY else Color.DKGRAY
            canvas.drawText(member.abilityDesc, width / 2f, height / 2f + 190f, paint)
            
            // Arrows
            paint.textSize = 60f
            paint.color = textColor
            canvas.drawText("<", width / 2f - 200f, height / 2f + 30f, paint)
            canvas.drawText(">", width / 2f + 200f, height / 2f + 30f, paint)
            
            paint.textSize = 35f
            canvas.drawText(context.getString(R.string.start_game), width / 2f, height / 2f + 260f, paint)
            
        } else {
            paint.textSize = 90f
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
            }
        }
        paint.clearShadowLayer()
    }

    private fun drawDinoPreview(canvas: Canvas, x: Float, y: Float, scale: Float, member: DinoMember) {
        val theme = getEnvironmentTheme()
        TRexDrawer.drawDino(canvas, x, y, theme.dinoColor, theme.bgColor, scale, false, null, false, false, 0, isNightMode, animationFrame, emptyList(), paint, pathBuffer, member.name)
    }

    private fun getEnvironmentTheme(): Theme {
        val isRainy = currentWeather == Weather.RAINY
        val isSnowy = currentWeather == Weather.SNOWY
        
        return if (isNightMode) {
            // Nighttime condition: sky is a very dark charcoal, ground is a very dark variant of the terrain
            val bgColor = Color.parseColor("#0F0F12") // Deep Midnight Sky
            val textColor = Color.WHITE
            val cloudColor = if (isRainy) Color.parseColor("#252528") else Color.parseColor("#2A2A2E")
            
            // All seasons: Cacti and Trees are DARK GREEN
            val cac = Color.parseColor("#1B3A1D")
            val tree = Color.parseColor("#122A14")
            
            when (currentSeason) {
                Season.WINTER -> {
                    Theme(bgColor, textColor, Color.parseColor("#607D8B"), cloudColor, Color.parseColor("#0A0E12"), Color.parseColor("#1A237E"), cac, tree, Color.parseColor("#9C27B0"), Color.WHITE)
                }
                Season.AUTUMN -> {
                    Theme(bgColor, textColor, Color.parseColor("#8D6E63"), cloudColor, Color.parseColor("#0D0A08"), Color.parseColor("#212121"), cac, tree, Color.parseColor("#9C27B0"), Color.WHITE)
                }
                else -> {
                    Theme(bgColor, textColor, Color.parseColor("#66BB6A"), cloudColor, Color.parseColor("#080D08"), Color.parseColor("#263238"), cac, tree, Color.parseColor("#9C27B0"), Color.WHITE)
                }
            }
        } else {
            // Daytime condition: Sky is the background (Air), Ground is the terrain.
            // All base colors are logically selected and then darkened by 20% as requested.
            
            // Cacti and Trees are always GREEN
            val cac = Color.parseColor("#2E7D32")
            val tree = Color.parseColor("#1B5E20")

            when (currentSeason) {
                Season.SPRING -> {
                    // Air: Soft Sky Blue -> #6CA4BC (-20%)
                    // Ground: Vibrant Grass -> #388E3C (-20% of #4CAF50 is ~#3D8C40)
                    val bg = if (isRainy) Color.parseColor("#607D8B") else Color.parseColor("#6CA4BC")
                    val cloud = if (isRainy) Color.parseColor("#78909C") else Color.parseColor("#BDBDBD")
                    Theme(bg, Color.BLACK, Color.parseColor("#2E7D32"), cloud, Color.parseColor("#3D8C40"), Color.parseColor("#CCAB00"), cac, tree, Color.parseColor("#AD1457"), Color.parseColor("#0277BD"))
                }
                Season.SUMMER -> {
                    // Air: Clear Deep Blue -> #0099CC (-20% of #00BFFF is ~#0099CC)
                    // Ground: Warm Field -> #6F9C3B
                    val bg = if (isRainy) Color.parseColor("#546E7A") else Color.parseColor("#0099CC")
                    val cloud = if (isRainy) Color.parseColor("#78909C") else Color.parseColor("#CFD8DC")
                    Theme(bg, Color.BLACK, Color.parseColor("#FBC02D"), cloud, Color.parseColor("#6F9C3B"), Color.parseColor("#CCAB00"), cac, tree, Color.parseColor("#C2185B"), Color.parseColor("#FBC02D"))
                }
                Season.AUTUMN -> {
                    // Air: Hazy Gold -> #CCA366 (-20%)
                    // Ground: Earthy Soil -> #816D65
                    val bg = if (isRainy) Color.parseColor("#795548") else Color.parseColor("#CCA366")
                    val cloud = if (isRainy) Color.parseColor("#5D4037") else Color.parseColor("#BCAAA4")
                    Theme(bg, Color.BLACK, Color.parseColor("#D84315"), cloud, Color.parseColor("#816D65"), Color.parseColor("#CCAB00"), cac, tree, Color.parseColor("#A52714"), Color.parseColor("#E65100"))
                }
                Season.WINTER -> {
                    // Air: Frosty Gray-Blue -> #78909C
                    // Ground: Snowy Slush -> #BDBFC0
                    val bg = if (isSnowy) Color.parseColor("#455A64") else Color.parseColor("#78909C")
                    val cloud = if (isSnowy) Color.parseColor("#ECEFF1") else Color.parseColor("#CFD8DC")
                    Theme(bg, Color.BLACK, Color.parseColor("#0277BD"), cloud, Color.parseColor("#BDBFC0"), Color.parseColor("#CCAB00"), cac, tree, Color.parseColor("#880E4F"), Color.WHITE)
                }
            }
        }
    }

    data class Theme(val bgColor: Int, val textColor: Int, val dinoColor: Int, val cloudColor: Int, val groundColor: Int, val secondaryColor: Int, val cactusColor: Int, val treeColor: Int, val birdColor: Int, val weatherColor: Int)
    enum class ObstacleType { CACTUS, PTEROSAUR, TREE, ROCK, CANYON, METEOR, THUNDERBOLT, STUMP, FIRE, FALLEN_TREE, RAISED_EDGE, BIG_DINO }
    data class Obstacle(var x: Float, var y: Float, var width: Float, var height: Float, var type: ObstacleType, var variant: Int = 0)
}
