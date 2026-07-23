package com.tdpham.games.frenzy

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

class FrenzyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "frenzy"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isInitialized = false
    private var gameOver = false
    private var gamePaused = true
    private var gameWon = false

    private var score = 0
    private var best = 0
    private var lives = 3
    private var playerSize = 1 // 1: Guppy, 2: Angelfish, 3: Lionfish, 4: Dolphin, 5: Apex/King
    private var fishEatenCount = 0
    private val targetFishToWin = 170 // 15 + 25 + 35 + 45 + 50 to win (Total 170)

    // Player position
    private var playerX = 0f
    private var playerY = 0f
    private val playerSpeed = 30f // Sped up player controls
    private var playerFacingRight = true
    private var playerIsEating = false
    private var playerEatStartTime = 0L

    // Aquatic simulation
    private val otherFish = mutableListOf<AIFish>()
    private val bubbles = mutableListOf<Bubble>()
    private var lastSpawnTime = 0L
    private val spawnInterval = 1000L // ms
    private var lastIndicatorTriggerTime = 0L

    // Hazards & Power-ups
    private val mines = mutableListOf<Mine>()
    private val mineWarnings = mutableListOf<MineWarning>()
    private val mineExplosions = mutableListOf<MineExplosion>()
    private val mineParticles = mutableListOf<MineParticle>()
    private var lastMineSpawnTime = 0L
    private var playerStunUntil = 0L
    private var playerShieldUntil = 0L
    private var deathReason: String? = null
    private var deathReasonDisplayUntil = 0L
    private var lastWarningTime = 0L

    // Dynamic Ocean Floor Seasons
    private var gameStartTime = 0L
    private var oceanTheme = 0 // 0: Sandy Reef, 1: Shipwreck, 2: Volcanic Vents, 3: Lost Atlantis
    private var themeProgress = 0f

    private val drawPath = Path()
    private val drawRectF = RectF()

    private val celebrationManager = CelebrationManager()

    private val sizeNames by lazy {
        arrayOf(
            context.getString(R.string.frenzy_guppy),
            context.getString(R.string.frenzy_angelfish),
            context.getString(R.string.frenzy_lionfish),
            context.getString(R.string.frenzy_dolphin),
            context.getString(R.string.frenzy_apex)
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gamePaused && !gameOver && !gameWon) {
                update()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    private val animHandler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || gamePaused || gameWon) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }

    data class AIFish(
        var x: Float,
        var y: Float,
        val size: Int,
        var speed: Float,
        var facingRight: Boolean,
        val color: Int,
        val speciesIndex: Int,
        val behavior: Int, // 0: Straight, 1: Schooling, 2: Bobbing, 3: Floor crawl, 4: Clam static, 5: Jellyfish pulse, 6: Stingray glide, 7: Bursty, 8: Darts shrimp
        var isStartled: Boolean = false,
        var startleTime: Long = 0L,
        var swimCycle: Float = Random.nextFloat() * 10f,
        var groupOffset: Float = 0f,
        var clamsOpen: Boolean = false,
        var pearlEaten: Boolean = false,
        var isEating: Boolean = false,
        var eatStartTime: Long = 0L
    )

    data class MineWarning(val x: Float, val startTime: Long, val duration: Long = 2000L)
    data class Bubble(var x: Float, var y: Float, val radius: Float, val speed: Float)
    data class Mine(var x: Float, var y: Float, val targetY: Float, var radius: Float, val speedY: Float, var isDropping: Boolean, var swimCycle: Float)
    data class MineExplosion(val x: Float, val y: Float, var radius: Float, val maxRadius: Float, var alpha: Int)
    data class MineParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var color: Int, var alpha: Int)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        animHandler.post(animRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameLoop)
        animHandler.removeCallbacks(animRunnable)
    }

    override fun startGame() {
        requestFocus()
        gamePaused = false
        gameStartTime = System.currentTimeMillis()
        handler.post(gameLoop)
        invalidate()
    }

    override fun pause() {
        gamePaused = true
        handler.removeCallbacks(gameLoop)
    }

    override fun resume() {
        gamePaused = false
        handler.removeCallbacks(gameLoop)
        handler.post(gameLoop)
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        score = 0
        lives = 3
        playerSize = 1
        fishEatenCount = 0
        playerX = width / 2f
        playerY = height / 2f
        playerFacingRight = true
        otherFish.clear()
        bubbles.clear()
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gameWon = false
        gamePaused = true
        gameStartTime = System.currentTimeMillis()
        lastIndicatorTriggerTime = System.currentTimeMillis()
        mines.clear()
        mineWarnings.clear()
        mineExplosions.clear()
        mineParticles.clear()
        playerStunUntil = 0L
        playerShieldUntil = System.currentTimeMillis() + 4000L
        oceanTheme = 0
        themeProgress = 0f
        celebrationManager.start(0f, 0f)

        // Spawn initial bubbles
        repeat(20) {
            bubbles.add(Bubble(Random.nextFloat() * width, Random.nextFloat() * height, Random.nextFloat() * 8f + 4f, Random.nextFloat() * 3f + 1f))
        }
        invalidate()
    }

    private fun spawnGroup(speciesIndex: Int, size: Int, behavior: Int, color: Int, speed: Float) {
        val count = Random.nextInt(4, 7)
        val facingRight = Random.nextBoolean()
        val startX = if (facingRight) -150f else width + 150f
        val startY = Random.nextFloat() * (height - 280f) + 120f
        
        for (i in 0 until count) {
            // Spawn school in close alignment
            val offsetMultiplier = 50f
            val x = startX + (if (facingRight) -i else i) * offsetMultiplier
            val y = startY + (i % 3 - 1) * 35f
            otherFish.add(
                AIFish(
                    x = x,
                    y = y,
                    size = size,
                    speed = speed,
                    facingRight = facingRight,
                    color = color,
                    speciesIndex = speciesIndex,
                    behavior = behavior,
                    groupOffset = i * 0.5f
                )
            )
        }
    }

    private fun spawnSingleFish() {
        val rand = Random.nextFloat()
        
        val eelChance = when (playerSize) {
            1 -> 0.0f
            2 -> 0.08f
            3 -> 0.12f
            else -> 0.18f
        }
        
        val tinyIndices = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 29, 30, 31)
        val smallIndices = intArrayOf(9, 10, 11, 12, 28, 33)
        val mediumIndices = intArrayOf(13, 14, 15, 16, 17)
        val largeIndices = intArrayOf(18, 19, 20, 21)
        val apexIndices = intArrayOf(22, 23, 24, 25, 26)

        val isGoldenSpawn = Random.nextFloat() < 0.03f // 3% chance
        val speciesIndex = if (isGoldenSpawn) {
            32 // Golden Rainbow Reward Fish
        } else if (Random.nextFloat() < eelChance) {
            27 // Electric Eel
        } else {
            val idx = when (playerSize) {
                1 -> { // Stage 1 (Guppy): 80% tiny prey, 15% small food, 5% medium predators (no big fish)
                    when {
                        rand < 0.80f -> tinyIndices
                        rand < 0.95f -> smallIndices
                        else -> mediumIndices
                    }
                }
                2 -> { // Stage 2 (Angelfish): 40% tiny, 30% small, 20% medium, 10% large predators (no apex)
                    when {
                        rand < 0.40f -> tinyIndices
                        rand < 0.70f -> smallIndices
                        rand < 0.90f -> mediumIndices
                        else -> largeIndices
                    }
                }
                3 -> { // Stage 3 (Lionfish): 20% tiny, 20% small, 35% medium, 20% large, 5% apex predators
                    when {
                        rand < 0.20f -> tinyIndices
                        rand < 0.40f -> smallIndices
                        rand < 0.75f -> mediumIndices
                        rand < 0.95f -> largeIndices
                        else -> apexIndices
                    }
                }
                else -> { // Stage 4/5 (Dolphin/Apex): 15% tiny, 15% small, 20% medium, 30% large, 20% apex predators
                    when {
                        rand < 0.15f -> tinyIndices
                        rand < 0.30f -> smallIndices
                        rand < 0.50f -> mediumIndices
                        rand < 0.80f -> largeIndices
                        else -> apexIndices
                    }
                }
            }
            idx.random()
        }

        // Get properties based on species
        val size = getSpeciesSize(speciesIndex)
        val behavior = getSpeciesBehavior(speciesIndex)
        val color = getSpeciesColor(speciesIndex)
        val baseSpeed = getSpeciesSpeed(speciesIndex)

        // Schooling spawn
        if (behavior == 1) {
            spawnGroup(speciesIndex, size, behavior, color, baseSpeed)
            return
        }

        val facingRight = Random.nextBoolean()
        var x = if (facingRight) -100f else width + 100f
        var y = Random.nextFloat() * (height - 260f) + 100f

        if (behavior == 3 || behavior == 4 || behavior in 12..16) { // Floor Dwellers
            y = height - 60f - Random.nextFloat() * 15f
            x = Random.nextFloat() * (width - 200f) + 100f // spawn directly on bottom
        }

        otherFish.add(AIFish(x, y, size, baseSpeed, facingRight, color, speciesIndex, behavior))
    }

    private fun spawnBiteBubbles(x: Float, y: Float) {
        repeat(6) {
            val bx = x + Random.nextFloat() * 20f - 10f
            val by = y + Random.nextFloat() * 20f - 10f
            val radius = Random.nextFloat() * 4.5f + 2.5f
            val speed = Random.nextFloat() * 3.5f + 2.5f // fast rising bubbles
            bubbles.add(Bubble(bx, by, radius, speed))
        }
    }

    private fun getSpeciesSize(index: Int): Int {
        return when (index) {
            in 0..8 -> 0   // Tiny: Guppy, Tetra, Sardine, Anchovy, Cardinal, Shrimp, Starfish, Crab, Clam
            29, 30, 31 -> 0 // Tiny: Snail, Slug, Isopod
            in 9..12 -> 1  // Small: Clownfish, Butterfly, Damselfish, Seahorse
            28, 32, 33 -> 1 // Small: Lobster, Golden Reward Fish, Pufferfish
            in 13..17 -> 2 // Medium: Angelfish, Tang, Lionfish, Squid, Octopus
            in 18..21 -> 3 // Large: Dolphin, Sea Turtle, Stingray, Swordfish
            27 -> 3        // Large: Electric Eel
            else -> 4      // Apex: Shark, Orca, Blue Whale, Giant Squid, Sea Monster
        }
    }

    private fun getSpeciesBehavior(index: Int): Int {
        return when (index) {
            1, 2, 3, 4 -> 1     // Schooling
            12 -> 2             // Seahorse bobbing
            6, 7 -> 3           // Bottom crawling (Starfish, Hermit Crab)
            8 -> 4              // Static clam
            14, 16, 25 -> 5     // Jellyfish / Squid / Giant Squid pulsing
            17 -> 16            // Octopus crawling + jet flee
            20 -> 6             // Stingray gliding
            21 -> 7             // Bursty Swordfish
            5 -> 8              // Shrimp darting
            19 -> 9             // Sea Turtle (slow swimming)
            26 -> 10            // Sea Monster (giant bobbing)
            27 -> 11            // Electric Eel serpentine behavior
            28 -> 12            // Lobster crawling + tail flip flee
            29 -> 13            // Sea Snail crawling + shell retreat
            30 -> 14            // Sea Slug slow crawl
            31 -> 15            // Giant Isopod crawl + roll ball
            33 -> 17            // Pufferfish defensive inflating behavior
            else -> 0           // Straight swimming
        }
    }

    private fun getSpeciesColor(index: Int): Int {
        return when (index) {
            0 -> Color.parseColor("#FFD54F") // Guppy yellow-gold
            1 -> Color.parseColor("#4FC3F7") // Neon Tetra Blue
            2, 3 -> Color.parseColor("#CFD8DC") // Sardine/Anchovy Silver
            4 -> Color.parseColor("#FF8A80") // Cardinal Red
            5 -> Color.parseColor("#FFAB91") // Shrimp Pink
            6 -> Color.parseColor("#FF5252") // Starfish Red
            7 -> Color.parseColor("#8D6E63") // Hermit Crab Brown
            8 -> Color.parseColor("#D7CCC8") // Clam shell
            9 -> Color.parseColor("#FF7043") // Clownfish Orange
            10 -> Color.parseColor("#FFEE58") // Butterfly yellow
            11 -> Color.parseColor("#29B6F6") // Damselfish Cyan
            12 -> Color.parseColor("#FFB74D") // Seahorse Coral
            13 -> Color.parseColor("#00E5FF") // Neon Angelfish
            14 -> Color.parseColor("#2979FF") // Blue Tang
            15 -> Color.parseColor("#FFF3E0") // Lionfish striped
            16 -> Color.parseColor("#00BCD4") // Squid Teal
            17 -> Color.parseColor("#E040FB") // Octopus Purple
            18 -> Color.parseColor("#90CAF9") // Dolphin Blue-Grey
            19 -> Color.parseColor("#81C784") // Sea Turtle Green
            20 -> Color.parseColor("#78909C") // Ray grey
            21 -> Color.parseColor("#B0BEC5") // Swordfish Steel
            22 -> Color.parseColor("#546E7A") // Shark Dark Grey
            23 -> Color.parseColor("#212121") // Orca Black
            24 -> Color.parseColor("#0288D1") // Whale Blue
            25 -> Color.parseColor("#E040FB") // Giant Squid Purple
            26 -> Color.parseColor("#004D40") // Sea Monster Deep Green
            27 -> Color.parseColor("#FFD54F") // Electric Eel Bright Yellow
            28 -> Color.parseColor("#C62828") // Lobster Red
            29 -> Color.parseColor("#D7CCC8") // Sea Snail Beige Shell
            30 -> Color.parseColor("#FF4081") // Sea Slug Pink Nudibranch
            31 -> Color.parseColor("#90A4AE") // Giant Isopod Slate Grey
            32 -> Color.parseColor("#FFD700") // Golden Reward Fish Gold
            33 -> Color.parseColor("#D4E157") // Pufferfish Coral Yellow-Green
            else -> Color.parseColor("#4CAF50")
        }
    }

    private fun getSpeciesSpeed(index: Int): Float {
        return when (index) {
            in 0..4 -> Random.nextFloat() * 1.5f + 3f
            5 -> 8f
            6, 7 -> 1.0f
            8 -> 0f
            12 -> 1.5f
            19 -> 1.8f
            26 -> 1.2f
            21 -> 7f
            27 -> 6.0f
            28 -> 1.2f // Lobster crawling speed
            29 -> 0.3f // Snail extremely slow
            30 -> 0.4f // Slug slow
            31 -> 0.8f // Isopod crawl
            32 -> 7.5f // Golden Reward Fish fast swimming
            33 -> 2.0f // Pufferfish slow drift
            in 9..11 -> Random.nextFloat() * 2f + 4f
            in 13..17 -> Random.nextFloat() * 1.5f + 3f
            in 18..20 -> Random.nextFloat() * 2f + 4.5f
            else -> 2f
        }
    }

    private fun update() {
        val now = System.currentTimeMillis()

        // 1. Spawning bubbles
        if (Random.nextFloat() < 0.06f) {
            bubbles.add(Bubble(Random.nextFloat() * width, height + 10f, Random.nextFloat() * 8f + 4f, Random.nextFloat() * 2.5f + 1f))
        }

        // Update Bubbles
        val bIterator = bubbles.iterator()
        while (bIterator.hasNext()) {
            val b = bIterator.next()
            b.y -= b.speed
            if (b.y < -20f) bIterator.remove()
        }

        // 2. Ocean Floor seasons (60 seconds per theme)
        val elapsed = now - gameStartTime
        val themeLength = 60000L
        oceanTheme = ((elapsed / themeLength) % 4).toInt()
        val rem = elapsed % themeLength
        themeProgress = if (rem > themeLength - 4000L) {
            (rem - (themeLength - 4000L)) / 4000f
        } else {
            0f
        }

        // 3. Spawning AI fish
        if (now - lastSpawnTime > spawnInterval) {
            spawnSingleFish()
            lastSpawnTime = now
        }

        // AI-to-AI Eating Logic (Rigid Size Hierarchy: larger eats strictly smaller)
        val deadFishIndices = mutableSetOf<Int>()
        for (i in 0 until otherFish.size) {
            val f1 = otherFish[i]
            if (f1.behavior == 4) continue // Clams don't hunt
            if (i in deadFishIndices) continue

            for (j in 0 until otherFish.size) {
                if (i == j) continue
                val f2 = otherFish[j]
                if (j in deadFishIndices) continue

                if (f1.size > f2.size) {
                    val dx = f2.x - f1.x
                    val dy = f2.y - f1.y
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    val r1 = getFishRadius(f1.size)
                    val r2 = getFishRadius(f2.size)

                    if (dist < r1 + r2 - 12f) {
                        // Check mouth direction of hunter (f1)
                        val f1FacingRight = f1.facingRight
                        val f1CanEat = if (f1FacingRight) dx > -12f else dx < 12f
                        if (f1CanEat) {
                            f1.isEating = true
                            f1.eatStartTime = now
                            spawnBiteBubbles(f2.x, f2.y)
                            deadFishIndices.add(j)
                        }
                    }
                }
            }
        }
        if (deadFishIndices.isNotEmpty()) {
            val temp = otherFish.filterIndexed { index, _ -> !deadFishIndices.contains(index) }
            otherFish.clear()
            otherFish.addAll(temp)
        }

        // 4. Update AI Fish & Collision Logic
        val fIterator = otherFish.iterator()
        val playerRadius = getFishRadius(playerSize)

        while (fIterator.hasNext()) {
            val fish = fIterator.next()
            fish.swimCycle += 0.08f

            // Species Custom Behaviors
            when (fish.behavior) {
                0 -> { // Straight swimming
                    val startledSpeedMult = if (fish.isStartled && now - fish.startleTime < 1500L) 2.2f else 1f
                    fish.x += (if (fish.facingRight) fish.speed else -fish.speed) * startledSpeedMult
                }
                1 -> { // Schooling group
                    val schoolYOffset = sin(fish.swimCycle.toDouble() + fish.groupOffset).toFloat() * 1.5f
                    fish.y += schoolYOffset
                    fish.x += if (fish.facingRight) fish.speed else -fish.speed
                }
                2 -> { // Seahorse Bobbing vertically
                    fish.y += sin(fish.swimCycle.toDouble()).toFloat() * 2f
                    fish.x += (if (fish.facingRight) fish.speed else -fish.speed) * 0.3f
                }
                3 -> { // Floor Crawl (Hermit Crab)
                    if (fish.isStartled && now - fish.startleTime < 2000L) {
                        // Startled: Retracted in shell, does not move
                    } else {
                        fish.x += if (fish.facingRight) fish.speed else -fish.speed
                        if (fish.x < 50f) fish.facingRight = true
                        if (fish.x > width - 50f) fish.facingRight = false
                    }
                }
                4 -> { // Clam static (opens shell periodically)
                    val cycleIndex = (fish.swimCycle / 3f).toInt() % 3
                    fish.clamsOpen = (cycleIndex == 1) // Opens shell in middle of cycle
                }
                5 -> { // Jellyfish / Squid pulsing upwards/diagonally
                    val pulse = sin(fish.swimCycle.toDouble()).toFloat()
                    if (pulse > 0f) {
                        fish.y -= fish.speed * pulse
                        if (fish.speciesIndex == 16) { // Squid pulses horizontally
                            fish.x += (if (fish.facingRight) fish.speed * 1.5f else -fish.speed * 1.5f) * pulse
                        }
                    } else {
                        fish.y += fish.speed * 0.3f // slow sinking
                    }
                }
                6 -> { // Stingray gliding along sea bed
                    fish.x += if (fish.facingRight) fish.speed else -fish.speed
                    fish.y = height - 100f + sin(fish.swimCycle.toDouble()).toFloat() * 12f
                }
                7 -> { // Bursty Barracuda / Swordfish
                    val cycle = (fish.swimCycle % 8f)
                    val burstSpeed = if (cycle > 5f) fish.speed * 2.5f else fish.speed * 0.5f
                    fish.x += if (fish.facingRight) burstSpeed else -burstSpeed
                }
                8 -> { // Shrimp darting away from player
                    val dxP = playerX - fish.x
                    val dyP = playerY - fish.y
                    val dP = Math.sqrt((dxP * dxP + dyP * dyP).toDouble()).toFloat()
                    if (dP < 220f) { // Dart backwards
                        fish.facingRight = dxP < 0f
                        fish.x += if (fish.facingRight) fish.speed * 2.5f else -fish.speed * 2.5f
                        fish.y += (if (dyP < 0f) fish.speed else -fish.speed) * 1.2f
                    } else {
                        fish.x += if (fish.facingRight) fish.speed * 0.3f else -fish.speed * 0.3f
                    }
                }
                9 -> { // Sea Turtle: Slow gentle straight swim
                    fish.x += if (fish.facingRight) fish.speed else -fish.speed
                    fish.y += sin(fish.swimCycle.toDouble() * 0.4).toFloat() * 0.8f
                }
                10 -> { // Sea Monster: Serpentine heavy bobbing
                    fish.x += if (fish.facingRight) fish.speed else -fish.speed
                    fish.y += sin(fish.swimCycle.toDouble() * 0.5).toFloat() * 3.5f
                }
                11 -> { // Electric Eel serpentine movement
                    fish.x += if (fish.facingRight) fish.speed else -fish.speed
                    fish.y += sin(fish.swimCycle.toDouble() * 0.8).toFloat() * 1.5f
                }
                12 -> { // Lobster crawling + tail flip flee
                    if (fish.isStartled && now - fish.startleTime < 1200L) {
                        // Dart backwards horizontally and rise slightly
                        fish.x += (if (fish.facingRight) -fish.speed else fish.speed) * 3.2f
                        fish.y = (fish.y - 4f).coerceAtLeast(height - 180f)
                    } else {
                        // Crawl on bottom
                        fish.y = height - 60f
                        fish.x += if (fish.facingRight) fish.speed else -fish.speed
                        if (fish.x < 50f) fish.facingRight = true
                        if (fish.x > width - 50f) fish.facingRight = false
                    }
                }
                13 -> { // Sea Snail slow crawl + shell retreat
                    if (fish.isStartled && now - fish.startleTime < 2500L) {
                        // Retreat inside shell, no movement
                    } else {
                        fish.y = height - 55f
                        fish.x += if (fish.facingRight) fish.speed else -fish.speed
                        if (fish.x < 50f) fish.facingRight = true
                        if (fish.x > width - 50f) fish.facingRight = false
                    }
                }
                14 -> { // Sea Slug slow crawl
                    if (fish.isStartled && now - fish.startleTime < 2500L) {
                        // Startled: contracts and crawls extremely slowly
                        fish.y = height - 55f
                        fish.x += (if (fish.facingRight) fish.speed else -fish.speed) * 0.2f
                    } else {
                        fish.y = height - 55f + sin(fish.swimCycle.toDouble() * 0.4).toFloat() * 2f
                        fish.x += if (fish.facingRight) fish.speed else -fish.speed
                        if (fish.x < 50f) fish.facingRight = true
                        if (fish.x > width - 50f) fish.facingRight = false
                    }
                }
                15 -> { // Giant Isopod crawling + roll ball
                    if (fish.isStartled && now - fish.startleTime < 2500L) {
                        // Rolled in a ball, stops moving
                    } else {
                        fish.y = height - 60f
                        fish.x += if (fish.facingRight) fish.speed else -fish.speed
                        if (fish.x < 50f) fish.facingRight = true
                        if (fish.x > width - 50f) fish.facingRight = false
                    }
                }
                16 -> { // Octopus crawling + jet flee
                    if (fish.isStartled && now - fish.startleTime < 2000L) {
                        // Jet pulsing away from threat
                        val pulse = sin(fish.swimCycle.toDouble() * 1.5).toFloat()
                        if (pulse > 0f) {
                            fish.y -= fish.speed * 1.3f * pulse
                            fish.x += (if (fish.facingRight) fish.speed * 1.2f else -fish.speed * 1.2f) * pulse
                        } else {
                            fish.y = (fish.y + fish.speed * 0.4f).coerceAtMost(height - 60f)
                        }
                    } else {
                        // Crawl on bottom
                        fish.y = height - 60f
                        fish.x += if (fish.facingRight) fish.speed else -fish.speed
                        if (fish.x < 50f) fish.facingRight = true
                        if (fish.x > width - 50f) fish.facingRight = false
                    }
                }
                17 -> { // Pufferfish / Blowfish defensive inflating behavior
                    val dxP = playerX - fish.x
                    val dyP = playerY - fish.y
                    val dP = Math.sqrt((dxP * dxP + dyP * dyP).toDouble()).toFloat()
                    
                    if (dP < 220f) {
                        // Threat is close: Inflate!
                        if (!fish.isStartled) {
                            fish.isStartled = true
                            fish.startleTime = now
                        }
                        // Don't run away: drift slowly
                        fish.y += sin(fish.swimCycle.toDouble() * 0.5).toFloat() * 0.2f
                    } else if (dP > 300f) {
                        // Safe: Deflate!
                        fish.isStartled = false
                        // Swim normally
                        fish.x += if (fish.facingRight) fish.speed else -fish.speed
                        fish.y += sin(fish.swimCycle.toDouble() * 0.4).toFloat() * 0.8f
                    } else {
                        // Maintain current state, drift/swim slowly
                        if (!fish.isStartled) {
                            fish.x += if (fish.facingRight) fish.speed * 0.5f else -fish.speed * 0.5f
                            fish.y += sin(fish.swimCycle.toDouble() * 0.4).toFloat() * 0.8f
                        } else {
                            fish.y += sin(fish.swimCycle.toDouble() * 0.5).toFloat() * 0.2f
                        }
                    }
                }
            }

            // AI Vision & Reaction (Flee / Chase)
            if (fish.behavior != 4 && fish.behavior != 5 && fish.behavior != 17) {
                val maxVisionDist = 300f
                val maxVisionDepth = 120f
                
                var threatOnLeft = false
                var threatOnRight = false

                var closestThreatLeftX = 0f
                var closestThreatLeftY = 0f
                var minThreatLeftDist = Float.MAX_VALUE

                var closestThreatRightX = 0f
                var closestThreatRightY = 0f
                var minThreatRightDist = Float.MAX_VALUE
                
                var closestPreyX = 0f
                var closestPreyY = 0f
                var minPreyDist = Float.MAX_VALUE

                fun checkVision(tx: Float, ty: Float, tSize: Int) {
                    val dx = tx - fish.x
                    val dy = ty - fish.y
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (dist > maxVisionDist) return
                    
                    val isInDepth = Math.abs(dy) < maxVisionDepth
                    
                    if (isInDepth && tSize > fish.size) {
                        if (dx < 0f) {
                            threatOnLeft = true
                            if (dist < minThreatLeftDist) {
                                minThreatLeftDist = dist
                                closestThreatLeftX = tx
                                closestThreatLeftY = ty
                            }
                        } else if (dx > 0f) {
                            threatOnRight = true
                            if (dist < minThreatRightDist) {
                                minThreatRightDist = dist
                                closestThreatRightX = tx
                                closestThreatRightY = ty
                            }
                        }
                    }

                    val isAhead = if (fish.facingRight) dx > 0f else dx < 0f
                    if (isAhead && isInDepth && tSize < fish.size) {
                        if (dist < minPreyDist) {
                            minPreyDist = dist
                            closestPreyX = tx
                            closestPreyY = ty
                        }
                    }
                }

                // Check Player
                if (!gameOver && !gameWon) {
                    checkVision(playerX, playerY, playerSize)
                }
                
                // Check other AI fish
                for (other in otherFish) {
                    if (other === fish) continue
                    checkVision(other.x, other.y, other.size)
                }

                // React to Threat (Flee)
                if (threatOnLeft || threatOnRight) {
                    fish.isStartled = true
                    fish.startleTime = now

                    if (threatOnLeft && threatOnRight) {
                        // Cornered! Swim up or down depending on average threat vertical position
                        if (fish.behavior != 3 && fish.behavior !in 12..16) {
                            val avgThreatY = (closestThreatLeftY + closestThreatRightY) / 2f
                            if (avgThreatY > fish.y) {
                                fish.y = (fish.y - fish.speed * 1.5f).coerceAtLeast(80f)
                            } else {
                                fish.y = (fish.y + fish.speed * 1.5f).coerceAtMost(height - 80f)
                            }
                        } else {
                            // Floor dwellers cornered!
                            when (fish.behavior) {
                                12 -> { // Lobster: sudden vertical tail-flip jump to clear threats
                                    fish.y = (fish.y - fish.speed * 4.2f).coerceAtLeast(height - 220f)
                                }
                                16 -> { // Octopus: jet propulsion straight up to escape
                                    fish.y = (fish.y - fish.speed * 3.5f).coerceAtLeast(height - 260f)
                                }
                                // Snails (13), Slugs (14), Isopods (15), Hermit Crabs (3) cannot swim, so they shelter in-place
                            }
                        }
                    } else if (threatOnLeft) {
                        // Flee to the right
                        fish.facingRight = true
                        if (fish.x < 65f) {
                            fish.facingRight = true
                        } else if (fish.x > width - 65f) {
                            fish.facingRight = false
                        }

                        if (fish.behavior != 3 && fish.behavior !in 12..16) {
                            if (closestThreatLeftY < fish.y) {
                                fish.y = (fish.y + fish.speed * 0.6f).coerceAtMost(height - 80f)
                            } else {
                                fish.y = (fish.y - fish.speed * 0.6f).coerceAtLeast(80f)
                            }
                        }
                        fish.x += if (fish.facingRight) fish.speed * 0.8f else -fish.speed * 0.8f
                    } else { // threatOnRight
                        // Flee to the left
                        fish.facingRight = false
                        if (fish.x < 65f) {
                            fish.facingRight = true
                        } else if (fish.x > width - 65f) {
                            fish.facingRight = false
                        }

                        if (fish.behavior != 3 && fish.behavior !in 12..16) {
                            if (closestThreatRightY < fish.y) {
                                fish.y = (fish.y + fish.speed * 0.6f).coerceAtMost(height - 80f)
                            } else {
                                fish.y = (fish.y - fish.speed * 0.6f).coerceAtLeast(80f)
                            }
                        }
                        fish.x += if (fish.facingRight) fish.speed * 0.8f else -fish.speed * 0.8f
                    }
                } 
                // React to Prey (Chase) - only if not fleeing
                else if (minPreyDist != Float.MAX_VALUE) {
                    fish.facingRight = closestPreyX > fish.x
                    
                    // Enforce boundary constraint to prevent edge jittering
                    if (fish.x < 65f) {
                        fish.facingRight = true
                    } else if (fish.x > width - 65f) {
                        fish.facingRight = false
                    }

                    if (fish.behavior != 3 && fish.behavior !in 12..16) {
                        if (closestPreyY > fish.y) {
                            fish.y = (fish.y + fish.speed * 0.4f).coerceAtMost(height - 80f)
                        } else {
                            fish.y = (fish.y - fish.speed * 0.4f).coerceAtLeast(80f)
                        }
                    }
                    fish.x += if (fish.facingRight) fish.speed * 0.4f else -fish.speed * 0.4f
                }
            }

            // Boundary cleanups
            if (fish.behavior != 3 && fish.behavior != 4 && fish.behavior !in 12..16) {
                if ((fish.facingRight && fish.x > width + 250f) || (!fish.facingRight && fish.x < -250f)) {
                    fIterator.remove()
                    continue
                }
            }

            // Collision check
            val dx = fish.x - playerX
            val dy = fish.y - playerY
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            var aiRadius = getFishRadius(fish.size)
            if (fish.speciesIndex == 33 && fish.isStartled) {
                aiRadius *= 1.7f
            }

            if (dist < playerRadius + aiRadius - 12f) {
                if (fish.speciesIndex == 33) { // Pufferfish / Blowfish
                    if (fish.isStartled) {
                        // Inflated pufferfish hurts player's mouth!
                        if (now >= playerShieldUntil) {
                            lives--
                            deathReason = context.getString(R.string.frenzy_death_pufferfish)
                            deathReasonDisplayUntil = now + 1800L
                            playerShieldUntil = now + 4000L // 4 seconds invulnerable shield
                            SoundManager.playError()
                            
                            if (lives <= 0) {
                                gameOver = true
                                val isNewHigh = score > best
                                if (isNewHigh) {
                                    ScoreManager.updateHighScore(context, gameKey, score)
                                    best = score
                                }
                                celebrationManager.start(width / 2f, height / 2f)
                                onGameOver?.invoke(score)
                            }
                        }
                        // bounce player away
                        playerX += if (playerFacingRight) -65f else 65f
                        continue
                    }
                }

                if (fish.speciesIndex == 27) { // Electric Eel
                    if (now >= playerShieldUntil) {
                        playerStunUntil = now + 3000L
                        SoundManager.playError()
                        deathReason = context.getString(R.string.frenzy_death_eel)
                        deathReasonDisplayUntil = now + 1800L
                    }
                    // bounce eel away
                    fish.facingRight = !fish.facingRight
                    fish.x += if (fish.facingRight) 70f else -70f
                    continue
                }
                
                if (fish.speciesIndex == 15 && playerSize < 3) { // Lionfish (Poison touch)
                    if (now >= playerShieldUntil) {
                        playerStunUntil = now + 1500L // Stunned/poisoned for 1.5s
                        SoundManager.playError()
                        deathReason = context.getString(R.string.frenzy_death_lionfish)
                        deathReasonDisplayUntil = now + 1500L
                    }
                    // bounce away
                    fish.facingRight = !fish.facingRight
                    fish.x += if (fish.facingRight) 50f else -50f
                    continue
                }
                
                // MOUTH EATING DIRECTION CONSTRAINT
                // Hunter can only eat if target is in front of its mouth direction
                val playerCanEat = if (playerFacingRight) dx > -12f else dx < 12f
                val aiCanEat = if (fish.facingRight) dx < 12f else dx > -12f

                if (playerSize > fish.size) {
                    if (playerCanEat) {
                        if (fish.speciesIndex == 32) { // Golden Rainbow Fish eaten!
                            score += 200
                            playerShieldUntil = now + 5000L // 5 seconds invulnerable shield
                            SoundManager.playSuccess()
                            repeat(15) {
                                val vx = Random.nextFloat() * 12f - 6f
                                val vy = Random.nextFloat() * 12f - 6f
                                mineParticles.add(MineParticle(fish.x, fish.y, vx, vy, Random.nextFloat() * 6f + 4f, Color.parseColor("#FFD700"), 255))
                            }
                            fIterator.remove()
                            continue
                        }

                        // Player eats AI
                        if (fish.speciesIndex == 8 && !fish.clamsOpen) { // Clam is index 8
                            // Clam is closed, cannot eat! Bounces off
                            if (now - lastWarningTime > 1500L) {
                                deathReason = context.getString(R.string.frenzy_death_clam)
                                deathReasonDisplayUntil = now + 1200L
                                lastWarningTime = now
                                SoundManager.playError()
                            }
                            continue
                        }

                        fishEatenCount++
                        playerIsEating = true
                        playerEatStartTime = now
                        spawnBiteBubbles(fish.x, fish.y)
                        score += (fish.size + 1) * 10
                        SoundManager.playScore()

                        // Growth logic
                        if (fishEatenCount >= targetFishToWin) {
                            gameWon = true
                            val isNewHigh = score > best
                            if (isNewHigh) {
                                ScoreManager.updateHighScore(context, gameKey, score)
                                best = score
                            }
                            celebrationManager.start(width / 2f, height / 2f)
                        } else if (fishEatenCount == 15) {
                            playerSize = 2
                            lastIndicatorTriggerTime = now
                            SoundManager.playSuccess()
                        } else if (fishEatenCount == 40) {
                            playerSize = 3
                            lastIndicatorTriggerTime = now
                            SoundManager.playSuccess()
                        } else if (fishEatenCount == 75) {
                            playerSize = 4
                            lastIndicatorTriggerTime = now
                            SoundManager.playSuccess()
                        } else if (fishEatenCount == 120) {
                            playerSize = 5
                            lastIndicatorTriggerTime = now
                            SoundManager.playSuccess()
                        }
                        fIterator.remove()
                        continue
                    }
                } else {
                    // Apex Predator Tail Biting Mechanic
                    // If player bites size 4 apex predator from behind (tail), player gets points, predator startled
                    val isApex = (fish.size == 4)
                    val isPlayerBehindApex = if (fish.facingRight) dx < -15f else dx > 15f

                    if (isApex && isPlayerBehindApex && playerCanEat && playerSize < 4) {
                        // Player bites apex tail!
                        score += 30
                        fishEatenCount = (fishEatenCount + 1).coerceAtMost(targetFishToWin) // boost progress
                        SoundManager.playSuccess()
                        
                        // Startle predator
                        fish.isStartled = true
                        fish.startleTime = now
                        fish.facingRight = !fish.facingRight // turn back
                        fish.x += if (fish.facingRight) 50f else -50f // bounce away
                        continue
                    }
                    if (fish.size > playerSize) {
                        if (playerCanEat) {
                            if (now - lastWarningTime > 1500L) {
                                deathReason = context.getString(R.string.frenzy_death_too_large, getSpeciesName(fish.speciesIndex).uppercase())
                                deathReasonDisplayUntil = now + 1200L
                                lastWarningTime = now
                                SoundManager.playError()
                            }
                        }
                    }

                    if (fish.size > playerSize && aiCanEat) {
                        if (now < playerShieldUntil) {
                            continue // Immune while shielded!
                        }
                        // AI eats Player
                        lives--
                        deathReason = context.getString(R.string.frenzy_death_eaten, getSpeciesName(fish.speciesIndex).uppercase())
                        deathReasonDisplayUntil = now + 1800L
                        lastIndicatorTriggerTime = now
                        playerShieldUntil = now + 4000L // 4 seconds shield after respawn
                        playerStunUntil = 0L // Clear stun
                        SoundManager.playError()
                        playerX = width / 2f
                        playerY = height / 2f
                        
                        if (lives <= 0) {
                            gameOver = true
                            val isNewHigh = score > best
                            if (isNewHigh) {
                                ScoreManager.updateHighScore(context, gameKey, score)
                                best = score
                            }
                            celebrationManager.start(width / 2f, height / 2f)
                            onGameOver?.invoke(score)
                        }
                        fIterator.remove()
                        continue
            }
        }
    }
}
        // Trigger Mine Warning (only from Stage 2/Angelfish size onwards)
        if (playerSize >= 2 && now - lastMineSpawnTime > 8000L && (mines.size + mineWarnings.size) < 3 && !gamePaused) {
            mineWarnings.add(
                MineWarning(
                    x = Random.nextFloat() * (width - 200f) + 100f,
                    startTime = now
                )
            )
            lastMineSpawnTime = now
        }

        // Update Warnings to spawn Mines
        val wIterator = mineWarnings.iterator()
        while (wIterator.hasNext()) {
            val w = wIterator.next()
            if (now - w.startTime >= w.duration) {
                mines.add(
                    Mine(
                        x = w.x,
                        y = -50f,
                        targetY = height - 120f - Random.nextFloat() * 100f,
                        radius = 26f,
                        speedY = Random.nextFloat() * 3f + 4f,
                        isDropping = true,
                        swimCycle = Random.nextFloat() * 10f
                    )
                )
                wIterator.remove()
            }
        }

        // Update Mines (dropping & collisions)
        val mIterator = mines.iterator()
        while (mIterator.hasNext()) {
            val m = mIterator.next()
            m.swimCycle += 0.04f

            if (m.isDropping) {
                m.y += m.speedY
                if (m.y >= m.targetY) {
                    m.y = m.targetY
                    m.isDropping = false
                }
            }

            // Collision check with player
            val dx = m.x - playerX
            val dy = m.y - playerY
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (dist < m.radius * 1.3f + getFishRadius(playerSize) + 5f) {
                triggerMineExplosion(m.x, m.y)
                mIterator.remove()
                continue
            }

            // Collision check with AI fish
            val fishIterator = otherFish.iterator()
            var mineExploded = false
            while (fishIterator.hasNext()) {
                val f = fishIterator.next()
                if (f.speciesIndex == 27) continue // eels are immune to mines (for clean gameplay)
                val fdx = m.x - f.x
                val fdy = m.y - f.y
                val fdist = Math.sqrt((fdx * fdx + fdy * fdy).toDouble()).toFloat()
                if (fdist < m.radius * 1.3f + getFishRadius(f.size) + 5f) {
                    triggerMineExplosion(m.x, m.y)
                    mineExploded = true
                    break
                }
            }
            if (mineExploded) {
                mIterator.remove()
                continue
            }
        }

        // Update explosions
        val expIterator = mineExplosions.iterator()
        while (expIterator.hasNext()) {
            val exp = expIterator.next()
            exp.radius += 8f
            exp.alpha -= 12
            if (exp.alpha <= 0 || exp.radius >= exp.maxRadius) {
                expIterator.remove()
            }
        }

        // Update particles
        val pIterator = mineParticles.iterator()
        while (pIterator.hasNext()) {
            val p = pIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.15f // gravity
            p.alpha -= 10
            if (p.alpha <= 0) {
                pIterator.remove()
            }
        }
    }

    private fun triggerMineExplosion(ex: Float, ey: Float) {
        SoundManager.playError()
        mineExplosions.add(MineExplosion(ex, ey, 10f, 160f, 255))

        // Spawn particles
        repeat(35) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 9f + 4f
            mineParticles.add(
                MineParticle(
                    x = ex,
                    y = ey,
                    vx = cos(angle.toDouble()).toFloat() * speed,
                    vy = sin(angle.toDouble()).toFloat() * speed,
                    size = Random.nextFloat() * 8f + 4f,
                    color = when (Random.nextInt(3)) {
                        0 -> Color.parseColor("#FF3D00") // Fire Red
                        1 -> Color.parseColor("#FFC107") // Yellow
                        else -> Color.parseColor("#757575") // Smoke Grey
                    },
                    alpha = 255
                )
            )
        }

        // Check player damage
        val dx = playerX - ex
        val dy = playerY - ey
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val now = System.currentTimeMillis()
        if (dist < 160f && now >= playerShieldUntil) {
            lives--
            deathReason = context.getString(R.string.frenzy_death_mine)
            deathReasonDisplayUntil = now + 1800L
            lastIndicatorTriggerTime = now
            playerShieldUntil = now + 4000L // 4 seconds shield
            playerStunUntil = 0L
            if (lives <= 0) {
                gameOver = true
                val isNewHigh = score > best
                if (isNewHigh) {
                    ScoreManager.updateHighScore(context, gameKey, score)
                    best = score
                }
                celebrationManager.start(width / 2f, height / 2f)
                onGameOver?.invoke(score)
            } else {
                playerX = width / 2f
                playerY = height / 2f
            }
        }

        // Check AI damage
        val affected = otherFish.filter {
            val adx = it.x - ex
            val ady = it.y - ey
            Math.sqrt((adx * adx + ady * ady).toDouble()).toFloat() < 160f
        }
        otherFish.removeAll(affected)
    }

    private fun getFishRadius(size: Int): Float {
        return when(size) {
            0 -> 15f
            1 -> 26f
            2 -> 45f
            3 -> 65f
            else -> 100f // Apex Shark/Whale/Sea Monster size
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isInitialized) return

        // 1. Draw Ocean Background
        drawOceanBackground(canvas)

        // Draw Bubbles
        paint.color = Color.parseColor("#33FFFFFF")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        for (b in bubbles) {
            canvas.drawCircle(b.x, b.y, b.radius, paint)
        }

        // 2. Draw Sea Bottom Background & Kelp
        drawSeaBed(canvas)

        // 3. Draw AI Fish
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        for (fish in otherFish) {
            val isFlashing = fish.isStartled && (System.currentTimeMillis() - fish.startleTime < 800L) && ((System.currentTimeMillis() / 100) % 2 == 0L)
            
            // Draw special indicator aura BEFORE drawing the species itself
            if (fish.speciesIndex == 27) { // Electric Eel (Yellow crackle lightning aura)
                paint.reset()
                paint.isAntiAlias = true
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = Color.parseColor("#FFF176") // Bright neon yellow
                if ((System.currentTimeMillis() / 150) % 2 == 0L) {
                    val r = getFishRadius(fish.size)
                    canvas.drawCircle(fish.x, fish.y, r + 15f, paint)
                }
            } else if (fish.speciesIndex == 15) { // Lionfish (Purple toxic bubble aura)
                paint.reset()
                paint.isAntiAlias = true
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = Color.parseColor("#E040FB") // Toxic purple glow
                val r = getFishRadius(fish.size)
                canvas.drawCircle(fish.x, fish.y, r + 12f, paint)
            } else if (fish.speciesIndex == 32) { // Golden Reward Fish (Bright golden glow ring)
                paint.reset()
                paint.isAntiAlias = true
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.parseColor("#FFD700") // Gold ring
                val r = getFishRadius(fish.size)
                canvas.drawCircle(fish.x, fish.y, r + 8f + 3f * sin(System.currentTimeMillis() / 100.0).toFloat(), paint)
            }
            
            drawSpecies(canvas, fish, isFlashing)
        }

        // 4. Draw Natural Player Locator Ripple and Bubbles (Fades away after 3 seconds)
        val now = System.currentTimeMillis()
        val elapsed = now - lastIndicatorTriggerTime
        if (elapsed < 3000L) {
            val progress = elapsed / 3000f // 0f to 1f
            // Soft glowing cyan ripple ring
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f * (1f - progress)
            paint.color = Color.parseColor("#E0F7FA") // Soft cyan water glow
            paint.alpha = (160 * (1f - progress)).toInt().coerceIn(0, 255)
            
            val radius = getFishRadius(playerSize) + 15f + progress * 60f
            canvas.drawCircle(playerX, playerY, radius, paint)

            // Orbiting rising bubbles
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = (180 * (1f - progress)).toInt().coerceIn(0, 255)
            val bubbleRadius = 5f * (1f - progress)
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45 + progress * 180f).toDouble())
                val bx = playerX + radius * Math.cos(angle).toFloat()
                val by = playerY + radius * Math.sin(angle).toFloat() - progress * 30f // float up
                canvas.drawCircle(bx, by, bubbleRadius, paint)
            }
        }

        // Draw Mine Warnings
        for (w in mineWarnings) {
            val elapsed = now - w.startTime
            val flash = (elapsed / 150) % 2 == 0L

            // Draw vertical laser guide line
            paint.reset()
            paint.isAntiAlias = true
            paint.color = if (flash) Color.parseColor("#80FF1744") else Color.parseColor("#15FF1744")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 15f), 0f)
            canvas.drawLine(w.x, 0f, w.x, height.toFloat(), paint)
            paint.pathEffect = null

            // Draw flashing warning sign at the top
            if (flash) {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#FF1744")

                // Draw warning triangle
                val tri = Path()
                val triSize = 25f
                val triY = 60f
                tri.moveTo(w.x, triY - triSize)
                tri.lineTo(w.x - triSize, triY + triSize)
                tri.lineTo(w.x + triSize, triY + triSize)
                tri.close()
                canvas.drawPath(tri, paint)

                // Exclamation mark
                paint.color = Color.WHITE
                paint.textSize = 32f
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("!", w.x, triY + triSize - 6f, paint)

                // Text label
                paint.color = Color.parseColor("#FF1744")
                paint.textSize = 18f
                canvas.drawText("WARNING", w.x, triY + triSize + 22f, paint)
            }
        }

        // Draw Undersea Mines
        for (m in mines) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.parseColor("#37474F") // Dark steel metal
            paint.style = Paint.Style.FILL
            canvas.drawCircle(m.x, m.y, m.radius, paint)
            
            // Spikes (cross lines)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            canvas.drawLine(m.x - m.radius * 1.3f, m.y, m.x + m.radius * 1.3f, m.y, paint)
            canvas.drawLine(m.x, m.y - m.radius * 1.3f, m.x, m.y + m.radius * 1.3f, paint)
            canvas.drawLine(m.x - m.radius * 0.9f, m.y - m.radius * 0.9f, m.x + m.radius * 0.9f, m.y + m.radius * 0.9f, paint)
            canvas.drawLine(m.x - m.radius * 0.9f, m.y + m.radius * 0.9f, m.x + m.radius * 0.9f, m.y - m.radius * 0.9f, paint)
            
            // Re-draw central core ball
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#37474F")
            canvas.drawCircle(m.x, m.y, m.radius * 0.9f, paint)
            
            // Flashing core red light
            if ((System.currentTimeMillis() / 250) % 2 == 0L) {
                paint.color = Color.RED
                canvas.drawCircle(m.x, m.y, 6f, paint)
            }
        }

        // 5. Draw Player Fish
        paint.style = Paint.Style.FILL
        val playerColor = when (playerSize) {
            1 -> Color.parseColor("#FF7043") // Orange Guppy
            2 -> Color.parseColor("#FFD54F") // Yellow Angelfish
            3 -> Color.parseColor("#FFF3E0") // Silver-Orange Lionfish
            4 -> Color.parseColor("#90CAF9") // Blue Dolphin
            else -> Color.parseColor("#00E5FF") // Neon Cyan Apex King
        }
        
        // Blink player if stunned
        val isStunned = now < playerStunUntil
        val shouldDrawPlayer = !isStunned || ((now / 150) % 2 == 0L)
        if (shouldDrawPlayer) {
            drawPlayerFishSprite(canvas, playerX, playerY, playerSize, playerColor, playerFacingRight)
        }

        // Draw Player bubble shield
        if (now < playerShieldUntil) {
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f + 2f * sin(now / 100.0).toFloat()
            paint.color = Color.parseColor("#00E5FF") // Light cyan shield glow
            paint.alpha = 130 + (40 * sin(now / 150.0).toFloat()).toInt().coerceIn(-40, 40)
            canvas.drawCircle(playerX, playerY, getFishRadius(playerSize) + 20f, paint)
        }

        // Draw player electric stun crackles
        if (isStunned) {
            if ((now / 100) % 2 == 0L) {
                paint.reset()
                paint.isAntiAlias = true
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3.5f
                paint.color = Color.parseColor("#00E5FF") // Cyan lightning sparks
                val pr = getFishRadius(playerSize)
                // Draw cross lightning crackles around player
                canvas.drawLine(playerX - pr * 1.3f, playerY - pr * 1.3f, playerX + pr * 1.3f, playerY + pr * 1.3f, paint)
                canvas.drawLine(playerX - pr * 1.3f, playerY + pr * 1.3f, playerX + pr * 1.3f, playerY - pr * 1.3f, paint)
            }
        }

        // Draw mine explosions
        for (exp in mineExplosions) {
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FFD54F") // Yellow core flash
            paint.alpha = exp.alpha / 2
            canvas.drawCircle(exp.x, exp.y, exp.radius * 0.7f, paint)
            
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 12f
            paint.color = Color.parseColor("#FF3D00") // Red border ring
            paint.alpha = exp.alpha
            canvas.drawCircle(exp.x, exp.y, exp.radius, paint)
        }

        // Draw mine explosion particles
        for (p in mineParticles) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = p.color
            paint.alpha = p.alpha
            paint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }

        // 6. Draw HUD Progress Bar & Details
        drawHUD(canvas)

        // Draw Death Reason Notification
        if (now < deathReasonDisplayUntil) {
            val text = deathReason ?: ""
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.parseColor("#FF5252") // Soft warning red
            paint.textSize = 38f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            
            // Draw a translucent black banner behind the text for readability
            val textWidth = paint.measureText(text)
            
            val prevColor = paint.color
            val prevStyle = paint.style
            
            paint.color = Color.parseColor("#99000000") // 60% opacity black
            paint.style = Paint.Style.FILL
            
            canvas.drawRoundRect(
                width / 2f - textWidth / 2f - 24f,
                height / 2f - 40f,
                width / 2f + textWidth / 2f + 24f,
                height / 2f + 20f,
                16f, 16f,
                paint
            )
            
            paint.color = prevColor
            paint.style = prevStyle
            canvas.drawText(text, width / 2f, height / 2f, paint)
        }

        // 7. Overlays
        if (gameWon) {
            celebrationManager.draw(canvas)
            drawOverlay(canvas, context.getString(R.string.frenzy_victory_title), "${context.getString(R.string.frenzy_victory_desc, score)}\n${context.getString(R.string.restart_hint)}")
        } else if (gameOver) {
            celebrationManager.draw(canvas)
            drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (gamePaused) {
            drawOverlay(canvas, context.getString(R.string.game_frenzy), "${context.getString(R.string.frenzy_resume_desc)}\n${context.getString(R.string.resume_hint)}")
        }
    }

    private fun drawOceanBackground(canvas: Canvas) {
        val reefBg = Color.parseColor("#01579B") // Reef blue
        val wreckBg = Color.parseColor("#0D47A1") // Dark deep navy
        val volcanicBg = Color.parseColor("#1A237E") // Indigo hot/dark
        val atlantisBg = Color.parseColor("#004D40") // Dark teal

        val currentBg = when (oceanTheme) {
            0 -> blendColors(reefBg, wreckBg, themeProgress)
            1 -> blendColors(wreckBg, volcanicBg, themeProgress)
            2 -> blendColors(volcanicBg, atlantisBg, themeProgress)
            else -> blendColors(atlantisBg, reefBg, themeProgress)
        }
        canvas.drawColor(currentBg)
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val r = (Color.red(color1) * (1 - ratio) + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * (1 - ratio) + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * (1 - ratio) + Color.blue(color2) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun drawSeaBed(canvas: Canvas) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        // Sand dunes path
        val sandY = height - 80f
        val sandPath = Path()
        sandPath.moveTo(0f, height.toFloat())
        sandPath.lineTo(0f, sandY)
        sandPath.quadTo(width * 0.25f, sandY - 20f, width * 0.5f, sandY + 15f)
        sandPath.quadTo(width * 0.75f, sandY + 45f, width.toFloat(), sandY - 10f)
        sandPath.lineTo(width.toFloat(), height.toFloat())
        sandPath.close()

        val sandColor = when (oceanTheme) {
            0 -> Color.parseColor("#D7CCC8") // Soft gold reef sand
            1 -> Color.parseColor("#5D4037") // Rusty wreck debris soil
            2 -> Color.parseColor("#212121") // Dark volcanic basalt
            else -> Color.parseColor("#00695C") // Atlantis green silt/stones
        }
        paint.color = sandColor
        canvas.drawPath(sandPath, paint)

        // Draw Swaying Kelp / Seaweed
        paint.color = Color.parseColor("#2E7D32")
        paint.strokeWidth = 14f
        paint.strokeCap = Paint.Cap.ROUND
        val timeSec = System.currentTimeMillis() / 250.0

        for (i in 0..12) {
            val kelpX = i * (width / 12f)
            val sway = sin(timeSec + i).toFloat() * 22f
            
            val kelpPath = Path()
            kelpPath.moveTo(kelpX, height.toFloat())
            kelpPath.quadTo(kelpX + sway * 0.5f, height - 80f, kelpX + sway, height - 160f)
            
            paint.style = Paint.Style.STROKE
            paint.color = if (i % 2 == 0) Color.parseColor("#1B5E20") else Color.parseColor("#2E7D32")
            canvas.drawPath(kelpPath, paint)
        }
    }

    // --- HUD AND PROGRESS RENDERING ---

    private fun drawHUD(canvas: Canvas) {
        paint.reset()
        paint.isAntiAlias = true
        
        // Lives (Draw cute fish silhouettes instead of text hearts)
        val fishColor = Color.parseColor("#FF7043") // Orange fish icons
        for (i in 0 until lives.coerceAtLeast(0)) {
            val fx = 55f + i * 48f
            val fy = height * 0.042f
            drawLifeFishIcon(canvas, fx, fy, 14f, fishColor)
        }

        // Progress Bar
        val barW = 320f
        val barH = 20f
        val barX = width / 2f - barW / 2f
        val barY = height * 0.035f

        // Draw background bar
        paint.color = Color.parseColor("#55FFFFFF")
        paint.style = Paint.Style.FILL
        drawRectF.set(barX, barY, barX + barW, barY + barH)
        canvas.drawRoundRect(drawRectF, 8f, 8f, paint)

        // Draw progress fill
        val ratio = fishEatenCount.toFloat() / targetFishToWin
        paint.color = Color.parseColor("#4CAF50")
        drawRectF.set(barX, barY, barX + (barW * ratio), barY + barH)
        canvas.drawRoundRect(drawRectF, 8f, 8f, paint)

        // Stage Tag
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        val currentLabel = sizeNames[(playerSize - 1).coerceIn(0, 4)]
        canvas.drawText(context.getString(R.string.frenzy_stage, currentLabel, fishEatenCount, targetFishToWin), width / 2f, barY + 54f, paint)

        // Best
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 36f
        canvas.drawText("${context.getString(R.string.best_label)}: $best  ${context.getString(R.string.score_label)}: $score", width - 40f, height * 0.05f, paint)
    }

    // --- DETAILED SIMULATION SPRITES DRAWING ---

    private fun drawPlayerFishSprite(canvas: Canvas, cx: Float, cy: Float, size: Int, color: Int, facingRight: Boolean) {
        val r = getFishRadius(size)
        val dir = if (facingRight) 1f else -1f
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color

        // Mouth snap open detection (lasts 200ms)
        val now = System.currentTimeMillis()
        val mouthOpen = playerIsEating && (now - playerEatStartTime < 200L)
        val cycle = (now / 100.0).toFloat()

        if (size == 4) {
            // Dolphin (Sleek body, rostrum beak, tall curved dorsal, horizontal fluke flipping up/down)
            val tailYOffset = sin(cycle.toDouble() * 0.7).toFloat() * r * 0.35f
            
            // 1. Sleek Torpedo Body
            if (mouthOpen) {
                drawPath.reset()
                val sweepAngle = 295f
                val startAngle = if (facingRight) 32f else 212f
                drawRectF.set(cx - r * 1.1f, cy - r * 0.45f, cx + r * 1.1f, cy + r * 0.45f)
                drawPath.addArc(drawRectF, startAngle, sweepAngle)
                drawPath.lineTo(cx, cy)
                drawPath.close()
                canvas.drawPath(drawPath, paint)
            } else {
                canvas.drawOval(RectF(cx - r * 1.1f, cy - r * 0.45f, cx + r * 1.1f, cy + r * 0.45f), paint)
            }

            // Rostrum Snout (Beak)
            val beak = Path()
            beak.moveTo(cx + r * 1.0f * dir, cy - r * 0.1f)
            beak.lineTo(cx + r * 1.5f * dir, cy + r * 0.05f)
            beak.lineTo(cx + r * 0.9f * dir, cy + r * 0.2f)
            beak.close()
            canvas.drawPath(beak, paint)

            // 2. Tall Curved Dorsal Fin
            val dorsal = Path()
            dorsal.moveTo(cx - r * 0.2f * dir, cy - r * 0.4f)
            dorsal.quadTo(cx - r * 0.6f * dir, cy - r * 0.95f, cx - r * 0.7f * dir, cy - r * 0.85f)
            dorsal.quadTo(cx - r * 0.4f * dir, cy - r * 0.5f, cx + r * 0.2f * dir, cy - r * 0.4f)
            dorsal.close()
            canvas.drawPath(dorsal, paint)

            // 3. Pec Flippers
            val pec = Path()
            pec.moveTo(cx + r * 0.15f * dir, cy + r * 0.2f)
            pec.lineTo(cx - r * 0.25f * dir, cy + r * 0.7f)
            pec.lineTo(cx - r * 0.35f * dir, cy + r * 0.3f)
            pec.close()
            canvas.drawPath(pec, paint)

            // 4. Horizontal Flukes (Tail flips UP and DOWN)
            val fluke = Path()
            val tx = cx - r * 1.1f * dir
            val ty = cy + tailYOffset
            fluke.moveTo(tx, ty)
            fluke.lineTo(tx - r * 0.4f * dir, ty - r * 0.5f)
            fluke.lineTo(tx - r * 0.5f * dir, ty)
            fluke.lineTo(tx - r * 0.4f * dir, ty + r * 0.5f)
            fluke.close()
            canvas.drawPath(fluke, paint)

            // Connect body to flukes
            val stock = Path()
            stock.moveTo(cx - r * 0.5f * dir, cy - r * 0.2f)
            stock.quadTo(cx - r * 0.9f * dir, cy + tailYOffset * 0.5f, tx, ty)
            stock.lineTo(tx, ty + r * 0.1f)
            stock.quadTo(cx - r * 0.9f * dir, cy + r * 0.2f + tailYOffset * 0.5f, cx - r * 0.5f * dir, cy + r * 0.2f)
            stock.close()
            canvas.drawPath(stock, paint)

            // 5. Cute Eye with white highlight
            paint.color = Color.WHITE
            canvas.drawCircle(cx + r * 0.65f * dir, cy - r * 0.15f, r * 0.16f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(cx + r * 0.65f * dir, cy - r * 0.15f, r * 0.08f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(cx + r * 0.67f * dir, cy - r * 0.17f, r * 0.04f, paint)
        } else if (size == 5) {
            // Orca style body with white belly, white eye patch, massive vertical dorsal, and horizontal fluke tail
            val tailYOffset = sin(cycle.toDouble() * 0.7).toFloat() * r * 0.2f

            // 1. White underbelly and patches
            paint.color = Color.WHITE
            canvas.drawOval(RectF(cx - r, cy, cx + r, cy + r * 0.5f), paint) // white belly
            // Eye patch
            canvas.drawOval(RectF(cx + r * 0.25f * dir, cy - r * 0.35f, cx + r * 0.6f * dir, cy - r * 0.15f), paint)

            // 2. Main Jet-Black/Dark Body
            paint.color = color
            if (mouthOpen) {
                drawPath.reset()
                val sweepAngle = 295f
                val startAngle = if (facingRight) 32f else 212f
                drawRectF.set(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.15f)
                drawPath.addArc(drawRectF, startAngle, sweepAngle)
                drawPath.lineTo(cx, cy)
                drawPath.close()
                canvas.drawPath(drawPath, paint)
            } else {
                canvas.drawOval(RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.15f), paint)
            }

            // Head/Snout (Blunt and rounded)
            val head = Path()
            head.moveTo(cx + r * 0.5f * dir, cy - r * 0.45f)
            head.quadTo(cx + r * 1.2f * dir, cy - r * 0.25f, cx + r * 1.1f * dir, cy + r * 0.1f)
            head.lineTo(cx + r * 0.5f * dir, cy + r * 0.15f)
            head.close()
            canvas.drawPath(head, paint)

            // 3. Massive Tall Dorsal Fin
            val dorsal = Path()
            dorsal.moveTo(cx - r * 0.2f * dir, cy - r * 0.4f)
            dorsal.lineTo(cx - r * 0.5f * dir, cy - r * 1.2f) // Very tall!
            dorsal.lineTo(cx + r * 0.2f * dir, cy - r * 0.4f)
            dorsal.close()
            canvas.drawPath(dorsal, paint)

            // 4. Horizontal Fluke (Tail flips UP and DOWN)
            val fluke = Path()
            val tx = cx - r * 1.0f * dir
            val ty = cy + tailYOffset
            fluke.moveTo(tx, ty)
            fluke.lineTo(tx - r * 0.4f * dir, ty - r * 0.6f)
            fluke.lineTo(tx - r * 0.5f * dir, ty)
            fluke.lineTo(tx - r * 0.4f * dir, ty + r * 0.6f)
            fluke.close()
            canvas.drawPath(fluke, paint)

            // Connect body to fluke
            val stock = Path()
            stock.moveTo(cx - r * 0.5f * dir, cy - r * 0.2f)
            stock.quadTo(cx - r * 0.8f * dir, cy + tailYOffset * 0.5f, tx, ty)
            stock.lineTo(tx, ty + r * 0.1f)
            stock.quadTo(cx - r * 0.8f * dir, cy + r * 0.2f + tailYOffset * 0.5f, cx - r * 0.5f * dir, cy + r * 0.2f)
            stock.close()
            canvas.drawPath(stock, paint)

            // 5. Eye (drawn in black with highlight)
            paint.color = Color.BLACK
            canvas.drawCircle(cx + r * 0.65f * dir, cy - r * 0.1f, r * 0.08f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(cx + r * 0.67f * dir, cy - r * 0.12f, r * 0.025f, paint)
        } else {
            // Standard sizes 1, 2, 3
            // Tail Fin with smooth swim wagging!
            val tailPath = Path()
            val tailWag = sin(cycle.toDouble() * 0.8).toFloat() * r * 0.25f
            if (size == 1) { // Guppy fan tail
                tailPath.moveTo(cx - r * 0.8f * dir, cy)
                tailPath.quadTo(cx - r * 1.4f * dir + tailWag * dir, cy - r * 0.9f, cx - r * 1.6f * dir + tailWag * dir, cy - r * 0.5f)
                tailPath.lineTo(cx - r * 1.6f * dir + tailWag * dir, cy + r * 0.5f)
                tailPath.quadTo(cx - r * 1.4f * dir + tailWag * dir, cy + r * 0.9f, cx - r * 0.8f * dir, cy)
            } else { // Standard tail
                tailPath.moveTo(cx - r * 0.8f * dir, cy)
                tailPath.lineTo(cx - r * 1.5f * dir + tailWag * dir, cy - r * 0.6f)
                tailPath.lineTo(cx - r * 1.3f * dir + tailWag * dir, cy)
                tailPath.lineTo(cx - r * 1.5f * dir + tailWag * dir, cy + r * 0.6f)
            }
            tailPath.close()
            canvas.drawPath(tailPath, paint)

            // Body Shape
            if (size == 2) { // Angelfish (Tall vertical diamond)
                val diamond = Path()
                diamond.moveTo(cx, cy - r * 1.3f)
                if (mouthOpen) {
                    if (facingRight) {
                        diamond.lineTo(cx + r * 0.5f, cy - r * 0.3f)
                        diamond.lineTo(cx + r * 0.1f, cy)
                        diamond.lineTo(cx + r * 0.5f, cy + r * 0.3f)
                    } else {
                        diamond.lineTo(cx + r * 0.9f * dir, cy)
                    }
                } else {
                    diamond.lineTo(cx + r * 0.9f * dir, cy)
                }
                diamond.lineTo(cx, cy + r * 1.3f)
                if (mouthOpen && !facingRight) {
                    diamond.lineTo(cx - r * 0.5f, cy + r * 0.3f)
                    diamond.lineTo(cx - r * 0.1f, cy)
                    diamond.lineTo(cx - r * 0.5f, cy - r * 0.3f)
                } else {
                    diamond.lineTo(cx - r * 0.9f * dir, cy)
                }
                diamond.close()
                canvas.drawPath(diamond, paint)
            } else { // Size 1 (Guppy) & 3 (Lionfish) standard oval body
                drawRectF.set(cx - r, cy - r * 0.62f, cx + r, cy + r * 0.62f)
                if (mouthOpen) {
                    drawPath.reset()
                    val sweepAngle = 295f
                    val startAngle = if (facingRight) 32f else 212f
                    drawPath.addArc(drawRectF, startAngle, sweepAngle)
                    drawPath.lineTo(cx, cy)
                    drawPath.close()
                    canvas.drawPath(drawPath, paint)
                } else {
                    canvas.drawOval(drawRectF, paint)
                }
            }

            // Details & Stripes
            if (size == 1) { // Clownfish stripes
                paint.color = Color.WHITE
                canvas.drawRect(cx - r * 0.2f * dir, cy - r * 0.5f, cx + r * 0.05f * dir, cy + r * 0.5f, paint)
                canvas.drawRect(cx + r * 0.3f * dir, cy - r * 0.45f, cx + r * 0.45f * dir, cy + r * 0.45f, paint)
            } else if (size == 2) { // Angelfish neon stripes
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawRect(cx - r * 0.3f * dir, cy - r * 0.8f, cx - r * 0.15f * dir, cy + r * 0.8f, paint)
                paint.color = Color.parseColor("#D500F9")
                canvas.drawRect(cx + r * 0.1f * dir, cy - r * 0.8f, cx + r * 0.25f * dir, cy + r * 0.8f, paint)
            } else if (size == 3) { // Lionfish spikes
                paint.color = Color.parseColor("#FF5722")
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                for (angle in 30..330 step 30) {
                    val rad = Math.toRadians(angle.toDouble())
                    val sx = (cx + r * Math.cos(rad)).toFloat()
                    val sy = (cy + r * Math.sin(rad)).toFloat()
                    canvas.drawLine(cx, cy, sx + (r * 0.6f * Math.cos(rad)).toFloat(), sy + (r * 0.6f * Math.sin(rad)).toFloat(), paint)
                }
                paint.style = Paint.Style.FILL
            }

            // Smiling eye
            val eyeX = cx + r * 0.55f * dir
            val eyeY = cy - r * 0.18f
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawCircle(eyeX, eyeY, r * 0.16f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(eyeX, eyeY, r * 0.08f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(eyeX - r * 0.04f * dir, eyeY - r * 0.04f, r * 0.04f, paint)
        }
    }

    private fun drawLifeFishIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.style = Paint.Style.FILL

        // Body oval
        drawRectF.set(cx - r, cy - r * 0.6f, cx + r, cy + r * 0.6f)
        canvas.drawOval(drawRectF, paint)

        // Tail fin
        drawPath.reset()
        drawPath.moveTo(cx - r * 0.8f, cy)
        drawPath.lineTo(cx - r * 1.4f, cy - r * 0.5f)
        drawPath.lineTo(cx - r * 1.4f, cy + r * 0.5f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)

        // Tiny eye dot
        paint.color = Color.WHITE
        canvas.drawCircle(cx + r * 0.4f, cy - r * 0.2f, r * 0.15f, paint)
    }

    private fun drawSpecies(canvas: Canvas, fish: AIFish, isFlashing: Boolean) {
        val r = getFishRadius(fish.size)
        val cx = fish.x
        val cy = fish.y
        val facingRight = fish.facingRight
        val dir = if (facingRight) 1f else -1f
        
        val now = System.currentTimeMillis()
        val mouthOpen = fish.isEating && (now - fish.eatStartTime < 200L)

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        
        if (isFlashing) {
            paint.color = Color.RED
        } else {
            paint.color = fish.color
        }

        when (fish.speciesIndex) {
            5 -> { // Shrimp (Tiny pink darting curved shell)
                canvas.drawCircle(cx, cy, 10f, paint)
                val tailP = Path()
                tailP.moveTo(cx, cy)
                tailP.quadTo(cx - 15f * dir, cy - 8f, cx - 22f * dir, cy + 12f)
                tailP.lineTo(cx - 14f * dir, cy + 4f)
                canvas.drawPath(tailP, paint)
                // Feelers
                paint.color = Color.WHITE
                paint.strokeWidth = 2f
                canvas.drawLine(cx, cy, cx + 20f * dir, cy - 10f, paint)
                canvas.drawLine(cx, cy, cx + 20f * dir, cy + 5f, paint)
            }
            6 -> { // Starfish (Red 5-pointed star)
                val star = Path()
                star.moveTo(cx, cy - r * 1.2f)
                star.lineTo(cx + r * 0.3f, cy - r * 0.3f)
                star.lineTo(cx + r * 1.2f, cy - r * 0.3f)
                star.lineTo(cx + r * 0.5f, cy + r * 0.3f)
                star.lineTo(cx + r * 0.8f, cy + r * 1.2f)
                star.lineTo(cx, cy + r * 0.6f)
                star.lineTo(cx - r * 0.8f, cy + r * 1.2f)
                star.lineTo(cx - r * 0.5f, cy + r * 0.3f)
                star.lineTo(cx - r * 1.2f, cy - r * 0.3f)
                star.lineTo(cx - r * 0.3f, cy - r * 0.3f)
                star.close()
                canvas.drawPath(star, paint)
            }
            7 -> { // Hermit Crab (Brown shell + red claws walking on floor)
                if (fish.isStartled && now - fish.startleTime < 2000L) {
                    // Startled: Retracted inside its shell! Only draw the shell.
                    paint.color = Color.parseColor("#A1887F")
                    canvas.drawCircle(cx, cy, r * 1.1f, paint)
                } else {
                    // Normal crawling
                    // Shell
                    paint.color = Color.parseColor("#A1887F")
                    canvas.drawCircle(cx - 5f * dir, cy - 5f, r * 1.1f, paint)
                    // Red legs
                    paint.color = Color.parseColor("#E53935")
                    canvas.drawRect(cx - r * 0.8f, cy + r * 0.2f, cx - r * 0.5f, cy + r * 0.9f, paint)
                    canvas.drawRect(cx + r * 0.5f, cy + r * 0.2f, cx + r * 0.8f, cy + r * 0.9f, paint)
                    // Claws
                    canvas.drawCircle(cx + r * dir, cy, r * 0.6f, paint)
                }
            }
            8 -> { // Clam with Pearl (Static shell opening/closing)
                paint.color = Color.parseColor("#BCAAA4")
                val bottomShell = RectF(cx - r * 1.2f, cy - r * 0.3f, cx + r * 1.2f, cy + r * 0.9f)
                canvas.drawOval(bottomShell, paint)

                if (fish.clamsOpen) {
                    canvas.save()
                    canvas.rotate(-35f * dir, cx - r * 1.2f * dir, cy)
                    val topShell = RectF(cx - r * 1.2f, cy - r * 1.2f, cx + r * 1.2f, cy + r * 0.1f)
                    canvas.drawOval(topShell, paint)
                    canvas.restore()

                    // Glowing pearl
                    paint.color = Color.parseColor("#FFF9C4")
                    canvas.drawCircle(cx, cy - r * 0.1f, r * 0.4f, paint)
                } else {
                    val topShell = RectF(cx - r * 1.2f, cy - r * 0.7f, cx + r * 1.2f, cy + r * 0.3f)
                    canvas.drawOval(topShell, paint)
                }
            }
            12 -> { // Seahorse (Curled coral body bobbing)
                val seahorse = Path()
                // Head
                seahorse.moveTo(cx, cy - r * 1.2f)
                seahorse.quadTo(cx + r * 0.8f * dir, cy - r * 1.2f, cx + r * 0.9f * dir, cy - r * 0.7f)
                seahorse.lineTo(cx + r * 0.3f * dir, cy - r * 0.5f)
                // Neck & curved belly
                seahorse.quadTo(cx - r * 0.4f * dir, cy, cx + r * 0.2f * dir, cy + r * 0.7f)
                // Curled tail
                seahorse.quadTo(cx - r * 0.6f * dir, cy + r * 1.3f, cx - r * 0.2f * dir, cy + r * 0.9f)
                seahorse.close()
                canvas.drawPath(seahorse, paint)

                // Back fin
                paint.color = Color.parseColor("#FFF59D")
                val backFin = Path()
                backFin.moveTo(cx - r * 0.2f * dir, cy - r * 0.2f)
                backFin.lineTo(cx - r * 0.7f * dir, cy)
                backFin.lineTo(cx - r * 0.2f * dir, cy + r * 0.4f)
                backFin.close()
                canvas.drawPath(backFin, paint)
            }
            16, 25 -> { // Squid / Giant Squid (pulsing arrow-like head + swaying tentacles)
                val wave = sin(fish.swimCycle.toDouble()).toFloat()
                
                // Draw arrow head (mantle/fins)
                val mantle = Path()
                mantle.moveTo(cx + r * dir, cy)
                mantle.lineTo(cx - r * 0.4f * dir, cy - r * 0.7f)
                mantle.lineTo(cx - r * 1.3f * dir, cy) // tip pointing backwards
                mantle.lineTo(cx - r * 0.4f * dir, cy + r * 0.7f)
                mantle.close()
                canvas.drawPath(mantle, paint)
                
                // Swaying tentacles trailing behind
                paint.strokeWidth = r * 0.14f
                paint.style = Paint.Style.STROKE
                paint.color = fish.color
                val sway = wave * r * 0.25f
                for (i in -2..2) {
                    val ty = cy + i * (r * 0.25f)
                    val path = Path()
                    path.moveTo(cx - r * 0.3f * dir, ty)
                    path.quadTo(cx - r * dir + sway, ty + wave, cx - r * 1.7f * dir, ty + sway * 0.6f)
                    canvas.drawPath(path, paint)
                }
            }
            17 -> { // Octopus (Round head + crawling tentacles or jet trailing)
                val wave = sin(fish.swimCycle.toDouble()).toFloat()
                
                // Draw round head
                canvas.drawCircle(cx, cy - r * 0.2f, r * 0.8f, paint)
                
                paint.strokeWidth = r * 0.16f
                paint.style = Paint.Style.STROKE
                paint.color = fish.color
                
                if (fish.isStartled && now - fish.startleTime < 2000L) {
                    // Jeting: tentacles trailing behind in a tight stream
                    val jetWave = sin(fish.swimCycle.toDouble() * 1.5).toFloat() * r * 0.15f
                    for (i in -3..3) {
                        val ty = cy + i * (r * 0.12f)
                        val path = Path()
                        path.moveTo(cx - r * 0.3f * dir, ty)
                        path.quadTo(cx - r * 0.9f * dir + jetWave, ty + jetWave, cx - r * 1.6f * dir, ty)
                        canvas.drawPath(path, paint)
                    }
                } else {
                    // Crawling on seabed: tentacles waving downwards onto the seabed floor
                    val crawlWave = wave * r * 0.2f
                    for (i in -3..3) {
                        val tx = cx + i * (r * 0.22f)
                        val path = Path()
                        path.moveTo(tx, cy)
                        path.quadTo(tx + crawlWave, cy + r * 0.4f, tx + crawlWave * 1.6f, cy + r * 0.8f)
                        canvas.drawPath(path, paint)
                    }
                }
            }
            28 -> { // Lobster (Red segmented shell, big claws, legs)
                // Segmented tail
                paint.color = fish.color
                for (i in 0..3) {
                    val tx = cx - i * (r * 0.32f) * dir
                    val tr = r * (1f - i * 0.15f)
                    canvas.drawOval(RectF(tx - tr * 0.5f, cy - tr * 0.4f, tx + tr * 0.5f, cy + tr * 0.4f), paint)
                }
                
                // Main carapace body
                canvas.drawOval(RectF(cx - r * 0.4f, cy - r * 0.5f, cx + r * 0.6f, cy + r * 0.5f), paint)
                
                // Walking legs
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                for (i in -1..1) {
                    val lx = cx + i * (r * 0.3f)
                    canvas.drawLine(lx, cy, lx - 8f * dir, cy + r * 0.8f, paint)
                    canvas.drawLine(lx, cy, lx + 8f * dir, cy + r * 0.8f, paint)
                }
                
                // Large claws extending forward
                paint.style = Paint.Style.FILL
                val clawOffset = r * 0.4f
                // Top claw
                canvas.drawOval(RectF(cx + r * 0.7f * dir - r * 0.3f, cy - clawOffset - r * 0.25f, cx + r * 0.7f * dir + r * 0.3f, cy - clawOffset + r * 0.25f), paint)
                // Bottom claw
                canvas.drawOval(RectF(cx + r * 0.7f * dir - r * 0.3f, cy + clawOffset - r * 0.25f, cx + r * 0.7f * dir + r * 0.3f, cy + clawOffset + r * 0.25f), paint)
            }
            29 -> { // Sea Snail (Coiled spiral shell + slow foot)
                // Crawling Foot (flesh)
                paint.color = Color.parseColor("#FFF9C4") // Light pale yellow
                canvas.drawOval(RectF(cx - r, cy, cx + r, cy + r * 0.4f), paint)
                // Eye feelers
                paint.strokeWidth = 2.5f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx + r * 0.7f * dir, cy, cx + r * 0.9f * dir, cy - r * 0.3f, paint)
                
                // Spiral Shell (Brown/Beige)
                paint.style = Paint.Style.FILL
                if (fish.isStartled && now - fish.startleTime < 2500L) {
                    // Startled: Retracted. Only draw the shell!
                    paint.color = fish.color
                    canvas.drawCircle(cx, cy, r * 0.9f, paint)
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.parseColor("#5D4037")
                    paint.strokeWidth = r * 0.1f
                    canvas.drawCircle(cx, cy, r * 0.5f, paint)
                    canvas.drawCircle(cx, cy, r * 0.2f, paint)
                } else {
                    // Normal crawling shell
                    paint.color = fish.color
                    canvas.drawCircle(cx - r * 0.2f * dir, cy - r * 0.2f, r * 0.8f, paint)
                    // Spiral pattern lines
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.parseColor("#5D4037")
                    paint.strokeWidth = r * 0.1f
                    canvas.drawCircle(cx - r * 0.2f * dir, cy - r * 0.2f, r * 0.4f, paint)
                    canvas.drawCircle(cx - r * 0.2f * dir, cy - r * 0.2f, r * 0.15f, paint)
                }
            }
            30 -> { // Sea Slug / Nudibranch (Colorful foot + rhinophores + gill plume)
                paint.color = fish.color
                val bodyScaleX = if (fish.isStartled && now - fish.startleTime < 2500L) 0.7f else 1.1f
                val bodyScaleY = if (fish.isStartled && now - fish.startleTime < 2500L) 0.6f else 0.4f
                // Elongated body (contracted if startled)
                canvas.drawOval(RectF(cx - r * bodyScaleX, cy - r * 0.3f, cx + r * bodyScaleX, cy + r * bodyScaleY), paint)
                
                // Rhinophores (bright pink sensory horns)
                paint.color = Color.parseColor("#FF1744")
                canvas.drawRect(RectF(cx + r * 0.6f * bodyScaleX * dir - 2f, cy - r * 0.6f, cx + r * 0.6f * bodyScaleX * dir + 2f, cy), paint)
                canvas.drawCircle(cx + r * 0.6f * bodyScaleX * dir, cy - r * 0.6f, 4f, paint)
                
                // Gill plume (fluffy feather cluster at back)
                paint.color = Color.parseColor("#FFD600") // Yellow plume
                val wave = sin(fish.swimCycle.toDouble() * 0.5).toFloat() * 3f
                canvas.drawCircle(cx - r * 0.7f * bodyScaleX * dir, cy - r * 0.4f + wave, r * 0.3f, paint)
                canvas.drawCircle(cx - r * 0.5f * bodyScaleX * dir, cy - r * 0.3f + wave, r * 0.2f, paint)
            }
            31 -> { // Giant Isopod (Segmented flat shield bug)
                paint.color = fish.color
                if (fish.isStartled && now - fish.startleTime < 2500L) {
                    // Startled: Rolled up into a tight protective ball!
                    canvas.drawCircle(cx, cy, r * 0.85f, paint)
                    // Segment rings
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.parseColor("#546E7A")
                    paint.strokeWidth = 3f
                    canvas.drawArc(RectF(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f), 0f, 360f, false, paint)
                    canvas.drawArc(RectF(cx - r * 0.4f, cy - r * 0.4f, cx + r * 0.4f, cy + r * 0.4f), 0f, 360f, false, paint)
                } else {
                    // Normal crawling segmented shell
                    canvas.drawRoundRect(RectF(cx - r * 1.1f, cy - r * 0.5f, cx + r * 1.1f, cy + r * 0.5f), r * 0.2f, r * 0.2f, paint)
                    // Segments
                    paint.color = Color.parseColor("#546E7A")
                    paint.strokeWidth = 3f
                    for (i in -3..3) {
                        val sx = cx + i * (r * 0.25f)
                        canvas.drawLine(sx, cy - r * 0.5f, sx, cy + r * 0.5f, paint)
                    }
                    // Tiny walking legs on bottom
                    paint.strokeWidth = 2f
                    for (i in -4..4) {
                        val lx = cx + i * (r * 0.2f)
                        canvas.drawLine(lx, cy + r * 0.4f, lx - 4f * dir, cy + r * 0.7f, paint)
                    }
                }
            }
            32 -> { // Golden Rainbow Reward Fish (flowing tail + sparkling star emissions)
                val hue = (System.currentTimeMillis() / 4) % 360f
                paint.color = Color.HSVToColor(floatArrayOf(hue, 0.9f, 1f))
                
                // Flowing body
                val bodyRect = RectF(cx - r * 1.1f, cy - r * 0.6f, cx + r * 1.1f, cy + r * 0.6f)
                canvas.drawOval(bodyRect, paint)
                
                // Flowing large tail fin
                val tail = Path()
                tail.moveTo(cx - r * dir, cy)
                tail.quadTo(cx - r * 1.8f * dir, cy - r * 0.9f, cx - r * 2.2f * dir, cy - r * 0.7f)
                tail.lineTo(cx - r * 2.2f * dir, cy + r * 0.7f)
                tail.quadTo(cx - r * 1.8f * dir, cy + r * 0.9f, cx - r * dir, cy)
                tail.close()
                canvas.drawPath(tail, paint)
                
                // Sparkle particles
                if ((System.currentTimeMillis() / 150) % 2 == 0L) {
                    paint.color = Color.WHITE
                    canvas.drawCircle(cx - r * 1.5f * dir, cy - r * 0.3f, 4f, paint)
                    canvas.drawCircle(cx - r * 1.7f * dir, cy + r * 0.4f, 3f, paint)
                }
            }
            33 -> { // Pufferfish / Blowfish (defensive spikes)
                if (fish.isStartled) {
                    // INFLATED STATE
                    val inflatedR = r * 1.5f
                    
                    // Draw spikes first
                    paint.color = Color.parseColor("#E6EE9C") // Lighter yellow-green spikes
                    paint.strokeWidth = 3f
                    paint.style = Paint.Style.STROKE
                    for (angle in 0 until 360 step 20) {
                        val rad = Math.toRadians(angle.toDouble())
                        val sx = (cx + inflatedR * Math.cos(rad)).toFloat()
                        val sy = (cy + inflatedR * Math.sin(rad)).toFloat()
                        val ex = (cx + (inflatedR + 12f) * Math.cos(rad)).toFloat()
                        val ey = (cy + (inflatedR + 12f) * Math.sin(rad)).toFloat()
                        canvas.drawLine(sx, sy, ex, ey, paint)
                    }
                    
                    // Inflated main body
                    paint.style = Paint.Style.FILL
                    paint.color = fish.color
                    canvas.drawCircle(cx, cy, inflatedR, paint)
                    
                    // Cute big eyes
                    paint.color = Color.WHITE
                    canvas.drawCircle(cx + inflatedR * 0.4f * dir, cy - inflatedR * 0.2f, inflatedR * 0.25f, paint)
                    paint.color = Color.BLACK
                    canvas.drawCircle(cx + inflatedR * 0.4f * dir, cy - inflatedR * 0.2f, inflatedR * 0.12f, paint)
                    
                    // Small tiny tail
                    paint.color = fish.color
                    val tailP = Path()
                    tailP.moveTo(cx - inflatedR * dir, cy)
                    tailP.lineTo(cx - (inflatedR + 14f) * dir, cy - 8f)
                    tailP.lineTo(cx - (inflatedR + 14f) * dir, cy + 8f)
                    tailP.close()
                    canvas.drawPath(tailP, paint)
                } else {
                    // DEFLATED / NORMAL STATE
                    val deflatedR = r * 0.9f
                    
                    // Draw small defensive spikes
                    paint.color = Color.parseColor("#C5E1A5")
                    paint.strokeWidth = 2f
                    paint.style = Paint.Style.STROKE
                    for (angle in 30..330 step 45) {
                        val rad = Math.toRadians(angle.toDouble())
                        val sx = (cx + deflatedR * Math.cos(rad)).toFloat()
                        val sy = (cy + deflatedR * Math.sin(rad)).toFloat()
                        val ex = (cx + (deflatedR + 5f) * Math.cos(rad)).toFloat()
                        val ey = (cy + (deflatedR + 5f) * Math.sin(rad)).toFloat()
                        canvas.drawLine(sx, sy, ex, ey, paint)
                    }
                    
                    // Deflated body
                    paint.style = Paint.Style.FILL
                    paint.color = fish.color
                    drawRectF.set(cx - deflatedR, cy - deflatedR * 0.8f, cx + deflatedR, cy + deflatedR * 0.8f)
                    canvas.drawOval(drawRectF, paint)
                    
                    // Tail
                    val tailP = Path()
                    tailP.moveTo(cx - deflatedR * dir, cy)
                    tailP.lineTo(cx - deflatedR * 1.5f * dir, cy - deflatedR * 0.4f)
                    tailP.lineTo(cx - deflatedR * 1.5f * dir, cy + deflatedR * 0.4f)
                    tailP.close()
                    canvas.drawPath(tailP, paint)
                    
                    // Normal eye
                    paint.color = Color.WHITE
                    canvas.drawCircle(cx + deflatedR * 0.5f * dir, cy - deflatedR * 0.2f, deflatedR * 0.22f, paint)
                    paint.color = Color.BLACK
                    canvas.drawCircle(cx + deflatedR * 0.5f * dir, cy - deflatedR * 0.2f, deflatedR * 0.1f, paint)
                }
            }
            18 -> { // Dolphin (Sleek body, rostrum beak, tall curved dorsal, horizontal fluke flipping up/down)
                val tailYOffset = sin(fish.swimCycle.toDouble() * 0.7).toFloat() * r * 0.35f
                
                // 1. Sleek Torpedo Body
                paint.color = fish.color
                if (mouthOpen) {
                    val path = Path()
                    val sweepAngle = 295f
                    val startAngle = if (facingRight) 32f else 212f
                    path.addArc(RectF(cx - r * 1.1f, cy - r * 0.45f, cx + r * 1.1f, cy + r * 0.45f), startAngle, sweepAngle)
                    path.lineTo(cx, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                } else {
                    canvas.drawOval(RectF(cx - r * 1.1f, cy - r * 0.45f, cx + r * 1.1f, cy + r * 0.45f), paint)
                }

                // Rostrum Snout (Beak)
                val beak = Path()
                beak.moveTo(cx + r * 1.0f * dir, cy - r * 0.1f)
                beak.lineTo(cx + r * 1.5f * dir, cy + r * 0.05f)
                beak.lineTo(cx + r * 0.9f * dir, cy + r * 0.2f)
                beak.close()
                canvas.drawPath(beak, paint)

                // 2. Tall Curved Dorsal Fin
                val dorsal = Path()
                dorsal.moveTo(cx - r * 0.2f * dir, cy - r * 0.4f)
                dorsal.quadTo(cx - r * 0.6f * dir, cy - r * 0.95f, cx - r * 0.7f * dir, cy - r * 0.85f)
                dorsal.quadTo(cx - r * 0.4f * dir, cy - r * 0.5f, cx + r * 0.2f * dir, cy - r * 0.4f)
                dorsal.close()
                canvas.drawPath(dorsal, paint)

                // 3. Pec Flippers
                val pec = Path()
                pec.moveTo(cx + r * 0.15f * dir, cy + r * 0.2f)
                pec.lineTo(cx - r * 0.25f * dir, cy + r * 0.7f)
                pec.lineTo(cx - r * 0.35f * dir, cy + r * 0.3f)
                pec.close()
                canvas.drawPath(pec, paint)

                // 4. Horizontal Flukes (Tail flips UP and DOWN)
                val fluke = Path()
                val tx = cx - r * 1.1f * dir
                val ty = cy + tailYOffset
                fluke.moveTo(tx, ty)
                fluke.lineTo(tx - r * 0.4f * dir, ty - r * 0.5f)
                fluke.lineTo(tx - r * 0.5f * dir, ty)
                fluke.lineTo(tx - r * 0.4f * dir, ty + r * 0.5f)
                fluke.close()
                canvas.drawPath(fluke, paint)

                // Connect body to flukes
                val stock = Path()
                stock.moveTo(cx - r * 0.5f * dir, cy - r * 0.2f)
                stock.quadTo(cx - r * 0.9f * dir, cy + tailYOffset * 0.5f, tx, ty)
                stock.lineTo(tx, ty + r * 0.1f)
                stock.quadTo(cx - r * 0.9f * dir, cy + r * 0.2f + tailYOffset * 0.5f, cx - r * 0.5f * dir, cy + r * 0.2f)
                stock.close()
                canvas.drawPath(stock, paint)

                // 5. Cute Eye with white highlight
                paint.color = Color.BLACK
                canvas.drawCircle(cx + r * 0.65f * dir, cy - r * 0.15f, r * 0.08f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx + r * 0.67f * dir, cy - r * 0.17f, r * 0.025f, paint)
            }
            19 -> { // Sea Turtle (Oval green shell + flippers swimming)
                // Flippers (rotating rotation)
                paint.color = Color.parseColor("#4CAF50") // Darker green flippers
                val flipperAngle = sin(fish.swimCycle.toDouble() * 0.5).toFloat() * 30f
                
                // Front Flipper
                canvas.save()
                canvas.translate(cx + r * 0.4f * dir, cy - r * 0.3f)
                canvas.rotate(flipperAngle * dir)
                canvas.drawOval(RectF(-r * 0.7f, -r * 0.2f, r * 0.7f, r * 0.2f), paint)
                canvas.restore()

                // Back Flipper
                canvas.save()
                canvas.translate(cx - r * 0.6f * dir, cy + r * 0.3f)
                canvas.rotate(-flipperAngle * dir)
                canvas.drawOval(RectF(-r * 0.5f, -r * 0.15f, r * 0.5f, r * 0.15f), paint)
                canvas.restore()

                // Shell
                paint.color = fish.color
                paint.style = Paint.Style.FILL
                canvas.drawOval(RectF(cx - r, cy - r * 0.6f, cx + r, cy + r * 0.6f), paint)

                // Shell patterns
                paint.color = Color.parseColor("#388E3C")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = r * 0.1f
                canvas.drawOval(RectF(cx - r * 0.7f, cy - r * 0.4f, cx + r * 0.7f, cy + r * 0.4f), paint)

                // Head
                paint.color = Color.parseColor("#4CAF50")
                paint.style = Paint.Style.FILL
                if (mouthOpen) {
                    val path = Path()
                    val sweepAngle = 295f
                    val startAngle = if (facingRight) 32f else 212f
                    path.addArc(RectF(cx + r * 1.1f * dir - r * 0.3f, cy - r * 0.1f - r * 0.3f, cx + r * 1.1f * dir + r * 0.3f, cy - r * 0.1f + r * 0.3f), startAngle, sweepAngle)
                    path.lineTo(cx + r * 1.1f * dir, cy - r * 0.1f)
                    path.close()
                    canvas.drawPath(path, paint)
                } else {
                    canvas.drawCircle(cx + r * 1.1f * dir, cy - r * 0.1f, r * 0.3f, paint)
                }

                // Eye
                paint.color = Color.BLACK
                canvas.drawCircle(cx + r * 1.18f * dir, cy - r * 0.15f, r * 0.07f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx + r * 1.2f * dir, cy - r * 0.17f, r * 0.02f, paint)
            }
            20 -> { // Stingray (Grey diamond body gliding + thin tail)
                val rayPath = Path()
                if (mouthOpen) {
                    if (facingRight) {
                        rayPath.moveTo(cx + r * 0.5f, cy - r * 0.2f)
                        rayPath.lineTo(cx + r * 0.1f, cy)
                        rayPath.lineTo(cx + r * 0.5f, cy + r * 0.2f)
                    } else {
                        rayPath.moveTo(cx + r * dir, cy)
                    }
                } else {
                    rayPath.moveTo(cx + r * dir, cy)
                }
                rayPath.lineTo(cx, cy - r * 0.8f)
                if (mouthOpen && !facingRight) {
                    rayPath.lineTo(cx - r * 0.5f, cy - r * 0.2f)
                    rayPath.lineTo(cx - r * 0.1f, cy)
                    rayPath.lineTo(cx - r * 0.5f, cy + r * 0.2f)
                } else {
                    rayPath.lineTo(cx - r * dir, cy)
                }
                rayPath.lineTo(cx, cy + r * 0.8f)
                rayPath.close()
                canvas.drawPath(rayPath, paint)
                // Whip tail
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                paint.color = Color.parseColor("#455A64")
                canvas.drawLine(cx - r * dir, cy, cx - r * 1.8f * dir, cy + r * 0.2f, paint)
            }
            21 -> { // Swordfish (Silver sleek + long sword beak)
                // Sword beak
                paint.color = Color.parseColor("#78909C")
                val sword = Path()
                sword.moveTo(cx + r * 0.8f * dir, cy)
                sword.lineTo(cx + r * 1.9f * dir, cy)
                sword.lineTo(cx + r * 0.7f * dir, cy + r * 0.1f)
                sword.close()
                canvas.drawPath(sword, paint)

                // Sleek body
                paint.color = fish.color
                if (mouthOpen) {
                    val path = Path()
                    val sweepAngle = 295f
                    val startAngle = if (facingRight) 32f else 212f
                    path.addArc(RectF(cx - r, cy - r * 0.4f, cx + r, cy + r * 0.4f), startAngle, sweepAngle)
                    path.lineTo(cx, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                } else {
                    canvas.drawOval(RectF(cx - r, cy - r * 0.4f, cx + r, cy + r * 0.4f), paint)
                }
                
                // Tail
                val tailP = Path()
                tailP.moveTo(cx - r * 0.8f * dir, cy)
                tailP.lineTo(cx - r * 1.35f * dir, cy - r * 0.7f)
                tailP.lineTo(cx - r * 1.35f * dir, cy + r * 0.7f)
                tailP.close()
                canvas.drawPath(tailP, paint)
            }
            22 -> { // Great White Shark (Pointed snout, white belly, vertical gill slits, sharp fins)
                // 1. Draw Shark Body (Slate Grey top, White bottom)
                paint.color = Color.parseColor("#B0BEC5") // White underbelly
                canvas.drawOval(RectF(cx - r, cy, cx + r, cy + r * 0.45f), paint)
                
                paint.color = fish.color // Dark Slate Grey top
                if (mouthOpen) {
                    val path = Path()
                    val sweepAngle = 295f
                    val startAngle = if (facingRight) 32f else 212f
                    path.addArc(RectF(cx - r, cy - r * 0.45f, cx + r, cy + r * 0.2f), startAngle, sweepAngle)
                    path.lineTo(cx, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                } else {
                    canvas.drawOval(RectF(cx - r, cy - r * 0.45f, cx + r, cy + r * 0.2f), paint)
                }

                // Pointed predatory head snout
                val snout = Path()
                snout.moveTo(cx + r * 0.6f * dir, cy - r * 0.35f)
                snout.lineTo(cx + r * 1.3f * dir, cy - r * 0.1f)
                snout.lineTo(cx + r * 0.6f * dir, cy + r * 0.15f)
                snout.close()
                canvas.drawPath(snout, paint)

                // 2. Gill Slits (3 vertical dark lines)
                paint.color = Color.parseColor("#37474F")
                paint.strokeWidth = r * 0.04f
                paint.style = Paint.Style.STROKE
                val gx = cx + r * 0.2f * dir
                canvas.drawLine(gx, cy - r * 0.15f, gx, cy + r * 0.1f, paint)
                canvas.drawLine(gx - r * 0.12f * dir, cy - r * 0.12f, gx - r * 0.12f * dir, cy + r * 0.08f, paint)
                canvas.drawLine(gx - r * 0.24f * dir, cy - r * 0.09f, gx - r * 0.24f * dir, cy + r * 0.06f, paint)
                paint.style = Paint.Style.FILL

                // 3. Sharp Triangular Dorsal Fin
                paint.color = fish.color
                val dorsal = Path()
                dorsal.moveTo(cx - r * 0.3f * dir, cy - r * 0.35f)
                dorsal.lineTo(cx - r * 0.7f * dir, cy - r * 1.0f)
                dorsal.lineTo(cx + r * 0.1f * dir, cy - r * 0.35f)
                dorsal.close()
                canvas.drawPath(dorsal, paint)

                // 4. Sharp Heterocercal Tail Fin (Upper lobe longer)
                val tail = Path()
                val tx = cx - r * 0.9f * dir
                tail.moveTo(tx, cy)
                tail.lineTo(cx - r * 1.5f * dir, cy - r * 0.8f) // Long upper lobe
                tail.lineTo(cx - r * 1.2f * dir, cy)
                tail.lineTo(cx - r * 1.4f * dir, cy + r * 0.5f) // Shorter lower lobe
                tail.close()
                canvas.drawPath(tail, paint)

                // 5. Pectoral Fin (pointing down and back)
                val pec = Path()
                pec.moveTo(cx + r * 0.1f * dir, cy + r * 0.1f)
                pec.lineTo(cx - r * 0.4f * dir, cy + r * 0.65f)
                pec.lineTo(cx - r * 0.4f * dir, cy + r * 0.25f)
                pec.close()
                canvas.drawPath(pec, paint)

                // 6. Eye
                paint.color = Color.BLACK
                canvas.drawCircle(cx + r * 0.7f * dir, cy - r * 0.18f, r * 0.07f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx + r * 0.72f * dir, cy - r * 0.2f, r * 0.025f, paint)

                // 7. Sharp white teeth inside open mouth
                if (mouthOpen) {
                    paint.color = Color.WHITE
                    val teeth = Path()
                    val mx = cx + r * 0.8f * dir
                    teeth.moveTo(mx - 8f * dir, cy + 2f)
                    teeth.lineTo(mx - 2f * dir, cy + 8f)
                    teeth.lineTo(mx + 4f * dir, cy + 2f)
                    teeth.close()
                    canvas.drawPath(teeth, paint)
                }
            }
            23 -> { // Killer Whale / Orca (Black body, white eye patch, white underbelly, massive dorsal fin)
                val tailYOffset = sin(fish.swimCycle.toDouble() * 0.7).toFloat() * r * 0.2f
                
                // 1. White underbelly and patches
                paint.color = Color.WHITE
                canvas.drawOval(RectF(cx - r, cy, cx + r, cy + r * 0.5f), paint) // white belly
                // Eye patch (above and behind the eye)
                canvas.drawOval(RectF(cx + r * 0.25f * dir, cy - r * 0.35f, cx + r * 0.6f * dir, cy - r * 0.15f), paint)

                // 2. Main Jet-Black Body
                paint.color = fish.color // Black
                if (mouthOpen) {
                    val path = Path()
                    val sweepAngle = 295f
                    val startAngle = if (facingRight) 32f else 212f
                    path.addArc(RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.15f), startAngle, sweepAngle)
                    path.lineTo(cx, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                } else {
                    canvas.drawOval(RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.15f), paint)
                }

                // Head/Snout (Blunt and rounded)
                val head = Path()
                head.moveTo(cx + r * 0.5f * dir, cy - r * 0.45f)
                head.quadTo(cx + r * 1.2f * dir, cy - r * 0.25f, cx + r * 1.1f * dir, cy + r * 0.1f)
                head.lineTo(cx + r * 0.5f * dir, cy + r * 0.15f)
                head.close()
                canvas.drawPath(head, paint)

                // 3. Massive Tall Dorsal Fin (Dolphin/Orca style)
                val dorsal = Path()
                dorsal.moveTo(cx - r * 0.2f * dir, cy - r * 0.4f)
                dorsal.lineTo(cx - r * 0.5f * dir, cy - r * 1.2f) // Very tall!
                dorsal.lineTo(cx + r * 0.2f * dir, cy - r * 0.4f)
                dorsal.close()
                canvas.drawPath(dorsal, paint)

                // 4. Horizontal Fluke (Tail flips UP and DOWN)
                val fluke = Path()
                val tx = cx - r * 1.0f * dir
                val ty = cy + tailYOffset
                fluke.moveTo(tx, ty)
                fluke.lineTo(tx - r * 0.4f * dir, ty - r * 0.6f)
                fluke.lineTo(tx - r * 0.5f * dir, ty)
                fluke.lineTo(tx - r * 0.4f * dir, ty + r * 0.6f)
                fluke.close()
                canvas.drawPath(fluke, paint)

                // Connect body to fluke
                val stock = Path()
                stock.moveTo(cx - r * 0.5f * dir, cy - r * 0.2f)
                stock.quadTo(cx - r * 0.8f * dir, cy + tailYOffset * 0.5f, tx, ty)
                stock.lineTo(tx, ty + r * 0.1f)
                stock.quadTo(cx - r * 0.8f * dir, cy + r * 0.2f + tailYOffset * 0.5f, cx - r * 0.5f * dir, cy + r * 0.2f)
                stock.close()
                canvas.drawPath(stock, paint)

                // 5. Eye (drawn in black)
                paint.color = Color.BLACK
                canvas.drawCircle(cx + r * 0.65f * dir, cy - r * 0.1f, r * 0.08f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx + r * 0.67f * dir, cy - r * 0.12f, r * 0.025f, paint)
            }
            24 -> { // Giant Blue Whale
                // Main giant body
                paint.color = fish.color
                if (mouthOpen) {
                    val path = Path()
                    val sweepAngle = 295f
                    val startAngle = if (facingRight) 32f else 212f
                    path.addArc(RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.55f), startAngle, sweepAngle)
                    path.lineTo(cx, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                } else {
                    canvas.drawRoundRect(RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.55f), r * 0.25f, r * 0.25f, paint)
                }

                // Giant tail fin
                val tailP = Path()
                tailP.moveTo(cx - r * 0.8f * dir, cy)
                tailP.lineTo(cx - r * 1.4f * dir, cy - r * 0.6f)
                tailP.lineTo(cx - r * 1.4f * dir, cy + r * 0.6f)
                tailP.close()
                canvas.drawPath(tailP, paint)

                // Small dorsal fin (top)
                val finP = Path()
                finP.moveTo(cx - r * 0.2f * dir, cy - r * 0.45f)
                finP.lineTo(cx - r * 0.4f * dir, cy - r * 0.7f)
                finP.lineTo(cx + r * 0.1f * dir, cy - r * 0.45f)
                finP.close()
                canvas.drawPath(finP, paint)

                // Eye
                val eyeX = cx + r * 0.65f * dir
                val eyeY = cy - r * 0.18f
                paint.color = Color.BLACK
                canvas.drawCircle(eyeX, eyeY, r * 0.08f, paint)
            }
            26 -> { // Sea Monster (Serpentine dragon body with spikes)
                // Draw 3 segments of serpentine body
                val wave = sin(fish.swimCycle.toDouble() * 0.6).toFloat() * r * 0.3f
                paint.color = fish.color
                if (mouthOpen) {
                    val path = Path()
                    val sweepAngle = 295f
                    val startAngle = if (facingRight) 32f else 212f
                    path.addArc(RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f), startAngle, sweepAngle)
                    path.lineTo(cx, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                } else {
                    canvas.drawCircle(cx, cy, r * 0.8f, paint)
                }
                canvas.drawCircle(cx - r * 0.7f * dir, cy + wave, r * 0.7f, paint)
                canvas.drawCircle(cx - r * 1.4f * dir, cy - wave, r * 0.5f, paint)

                // Tail fin
                val tailP = Path()
                tailP.moveTo(cx - r * 1.6f * dir, cy - wave)
                tailP.lineTo(cx - r * 2.2f * dir, cy - wave - r * 0.4f)
                tailP.lineTo(cx - r * 2.2f * dir, cy - wave + r * 0.4f)
                tailP.close()
                canvas.drawPath(tailP, paint)

                // Back spikes
                paint.color = Color.parseColor("#E53935") // Red spikes
                val spike = Path()
                spike.moveTo(cx - r * 0.2f * dir, cy - r * 0.8f)
                spike.lineTo(cx - r * 0.4f * dir, cy - r * 1.2f)
                spike.lineTo(cx - r * 0.6f * dir, cy - r * 0.7f)
                spike.close()
                canvas.drawPath(spike, paint)

                // Glowing red eye
                paint.color = Color.RED
                canvas.drawCircle(cx + r * 0.4f * dir, cy - r * 0.2f, r * 0.12f, paint)
            }
            27 -> { // Electric Eel (Long serpentine yellow eel with electric bolts)
                val wave = sin(fish.swimCycle.toDouble() * 0.9).toFloat() * r * 0.25f
                
                // Body segments
                paint.color = fish.color
                canvas.drawCircle(cx, cy, r * 0.4f, paint)
                canvas.drawCircle(cx - r * 0.7f * dir, cy + wave, r * 0.35f, paint)
                canvas.drawCircle(cx - r * 1.4f * dir, cy - wave, r * 0.3f, paint)
                canvas.drawCircle(cx - r * 2.1f * dir, cy + wave, r * 0.2f, paint)
                
                // Tail fin
                val tailP = Path()
                tailP.moveTo(cx - r * 2.2f * dir, cy + wave)
                tailP.lineTo(cx - r * 2.7f * dir, cy + wave - r * 0.3f)
                tailP.lineTo(cx - r * 2.7f * dir, cy + wave + r * 0.3f)
                tailP.close()
                canvas.drawPath(tailP, paint)
                
                // Electric sparks/arcs
                if ((System.currentTimeMillis() / 150) % 2 == 0L) {
                    paint.color = Color.parseColor("#00E5FF") // Electric Cyan
                    paint.strokeWidth = 3f
                    paint.style = Paint.Style.STROKE
                    
                    // Head shock bolt
                    canvas.drawLine(cx - 10f, cy - 20f, cx + 15f, cy - 15f, paint)
                    canvas.drawLine(cx + 15f, cy - 15f, cx, cy - 5f, paint)
                    
                    // Body shock bolt
                    canvas.drawLine(cx - r * 0.7f * dir - 10f, cy + wave + 15f, cx - r * 0.7f * dir + 15f, cy + wave + 20f, paint)
                    canvas.drawLine(cx - r * 0.7f * dir + 15f, cy + wave + 20f, cx - r * 0.7f * dir, cy + wave + 8f, paint)
                    paint.style = Paint.Style.FILL
                }
                
                // Angry red eye
                paint.color = Color.RED
                canvas.drawCircle(cx + r * 0.2f * dir, cy - r * 0.1f, r * 0.08f, paint)
            }
            else -> { // Standard swimming fish (Guppy, Clownfish, Butterfly, Tang, Lionfish)
                // Tail fin
                val tailPath = Path()
                if (fish.speciesIndex == 0) { // Guppy (flowing large tail)
                    tailPath.moveTo(cx - r * 0.8f * dir, cy)
                    tailPath.quadTo(cx - r * 1.4f * dir, cy - r * 0.9f, cx - r * 1.6f * dir, cy - r * 0.5f)
                    tailPath.lineTo(cx - r * 1.6f * dir, cy + r * 0.5f)
                    tailPath.quadTo(cx - r * 1.4f * dir, cy + r * 0.9f, cx - r * 0.8f * dir, cy)
                } else { // Standard tail
                    tailPath.moveTo(cx - r * 0.8f * dir, cy)
                    tailPath.lineTo(cx - r * 1.35f * dir, cy - r * 0.5f)
                    tailPath.lineTo(cx - r * 1.35f * dir, cy + r * 0.5f)
                }
                tailPath.close()
                canvas.drawPath(tailPath, paint)

                // Main body
                if (fish.speciesIndex == 10) { // Butterfly (Very flat round disc)
                    canvas.drawCircle(cx, cy, r, paint)
                } else if (fish.speciesIndex == 13) { // Neon Angelfish (Tall vertical diamond)
                    val diamond = Path()
                    diamond.moveTo(cx, cy - r * 1.3f)
                    diamond.lineTo(cx + r * 0.9f * dir, cy)
                    diamond.lineTo(cx, cy + r * 1.3f)
                    diamond.lineTo(cx - r * 0.9f * dir, cy)
                    diamond.close()
                    canvas.drawPath(diamond, paint)
                } else { // Standard oval body
                    val bodyRect = RectF(cx - r, cy - r * 0.58f, cx + r, cy + r * 0.58f)
                    if (mouthOpen) {
                        val path = Path()
                        val sweepAngle = 295f
                        val startAngle = if (facingRight) 32f else 212f
                        path.addArc(bodyRect, startAngle, sweepAngle)
                        path.lineTo(cx, cy)
                        path.close()
                        canvas.drawPath(path, paint)
                    } else {
                        canvas.drawOval(bodyRect, paint)
                    }
                }

                // Clownfish Stripes
                if (fish.speciesIndex == 9) { // Clownfish Orange vertical white stripes
                    paint.color = Color.WHITE
                    canvas.drawRect(cx - r * 0.2f * dir, cy - r * 0.5f, cx + r * 0.05f * dir, cy + r * 0.5f, paint)
                    canvas.drawRect(cx + r * 0.3f * dir, cy - r * 0.45f, cx + r * 0.45f * dir, cy + r * 0.45f, paint)
                } else if (fish.speciesIndex == 13) { // Neon Angelfish stripes
                    paint.color = Color.parseColor("#00E5FF")
                    canvas.drawRect(cx - r * 0.3f * dir, cy - r * 0.8f, cx - r * 0.15f * dir, cy + r * 0.8f, paint)
                    paint.color = Color.parseColor("#D500F9")
                    canvas.drawRect(cx + r * 0.1f * dir, cy - r * 0.8f, cx + r * 0.25f * dir, cy + r * 0.8f, paint)
                } else if (fish.speciesIndex == 15) { // Lionfish spiky lines
                    paint.color = Color.parseColor("#FF5722")
                    paint.strokeWidth = 3f
                    paint.style = Paint.Style.STROKE
                    for (angle in 30..330 step 30) {
                        val rad = Math.toRadians(angle.toDouble())
                        val sx = (cx + r * Math.cos(rad)).toFloat()
                        val sy = (cy + r * Math.sin(rad)).toFloat()
                        canvas.drawLine(cx, cy, sx + (r * 0.6f * Math.cos(rad)).toFloat(), sy + (r * 0.6f * Math.sin(rad)).toFloat(), paint)
                    }
                    paint.style = Paint.Style.FILL
                }

                // Eye
                paint.color = Color.WHITE
                val eyeX = cx + r * 0.6f * dir
                val eyeY = cy - r * 0.16f
                canvas.drawCircle(eyeX, eyeY, r * 0.15f, paint)
                paint.color = Color.BLACK
                canvas.drawCircle(eyeX, eyeY, r * 0.08f, paint)
            }
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#D9000000")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(title, width / 2f, height / 2f - 50f, paint)

        paint.textSize = 32f
        paint.typeface = Typeface.DEFAULT
        val lines = subtitle.split("\n")
        var yOffset = height / 2f + 40f
        for (line in lines) {
            canvas.drawText(line, width / 2f, yOffset, paint)
            yOffset += 45f
        }
    }

    private fun getSpeciesName(index: Int): String {
        return when (index) {
            0 -> context.getString(R.string.frenzy_guppy)
            1 -> "Neon Tetra"
            2 -> "Sardine"
            3 -> "Anchovy"
            4 -> "Cardinal Fish"
            5 -> "Shrimp"
            6 -> "Starfish"
            7 -> "Hermit Crab"
            8 -> "Clam"
            9 -> "Clownfish"
            10 -> "Butterflyfish"
            11 -> "Damselfish"
            12 -> "Seahorse"
            13 -> context.getString(R.string.frenzy_angelfish)
            14 -> "Blue Tang"
            15 -> context.getString(R.string.frenzy_lionfish)
            16 -> "Squid"
            17 -> "Octopus"
            18 -> context.getString(R.string.frenzy_dolphin)
            19 -> "Sea Turtle"
            20 -> "Stingray"
            21 -> "Swordfish"
            22 -> "Great White Shark"
            23 -> "Orca"
            24 -> "Blue Whale"
            25 -> "Giant Squid"
            26 -> "Sea Monster"
            27 -> "Electric Eel"
            28 -> "Lobster"
            29 -> "Sea Snail"
            30 -> "Sea Slug"
            31 -> "Giant Isopod"
            32 -> "Golden Rainbow Fish"
            33 -> "Pufferfish"
            else -> "Sea Creature"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver || gameWon) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                resetGame()
                startGame()
                return true
            }
            return false
        }

        if (gamePaused) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                resume()
                return true
            }
            return false
        }

        val now = System.currentTimeMillis()
        val isStunned = now < playerStunUntil
        val speedMult = if (isStunned) 0.35f else 1.0f
        val currentSpeed = playerSpeed * speedMult

        var effectiveKeyCode = keyCode
        if (isStunned) {
            effectiveKeyCode = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_DPAD_DOWN
                KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_UP
                KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_RIGHT
                KeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_LEFT
                else -> keyCode
            }
        }

        when (effectiveKeyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                playerY = (playerY - currentSpeed).coerceAtLeast(50f)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playerY = (playerY + currentSpeed).coerceAtMost(height - 50f)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                playerX = (playerX - currentSpeed).coerceAtLeast(50f)
                if (!isStunned) playerFacingRight = false else playerFacingRight = true
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                playerX = (playerX + currentSpeed).coerceAtMost(width - 50f)
                if (!isStunned) playerFacingRight = true else playerFacingRight = false
                invalidate()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
