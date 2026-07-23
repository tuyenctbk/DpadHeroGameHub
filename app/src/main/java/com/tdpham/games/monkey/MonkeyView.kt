package com.tdpham.games.monkey

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

class MonkeyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "monkey"
    override var onGameOver: ((Int) -> Unit)? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isInitialized = false
    private var gameOver = false
    private var gamePaused = true
    
    private var score = 0
    private var best = 0
    private var lives = 6
    private var playerDizzyUntil = 0L
    private var selectedCharacter = 0 // 0: Golden Gibbon, 1: Tarzan Climber, 2: Cyber Gorilla, 3: Punk Chimp
    private var monkeyGrabbed = false
    private var isFalling = false
    private var fallSpeed = 0f
    private var cutRopeIndex = -1
    private var cutY = 0f
    private var vineCutTime = 0L
    
    // Player position
    private var currentRope = 1 // 0: Left, 1: Center, 2: Right
    private var playerY = 0f
    private var playerRenderX = 0f // Smoothed X for sliding animation
    private var climbTime = 0f // Animation time for arm/leg wiggling
    private var hitTime = 0L // Timestamp of last hit for shake/dizzy effect

    // Scrolling environment
    private var vineScrollOffset = 0f
    private val leafOffsets = FloatArray(10) { Random.nextFloat() * 100f }
    
    // Snow particles for Winter season (REMOVED)

    // Obstacles and Items
    // type: 0: Banana, 1: Coconut, 2: Spider, 3: Snake (wavy), 4: Bird (horizontal)
    private val obstacles = mutableListOf<FallingItem>()
    private var lastSpawnTime = 0L
    private var spawnInterval = 1000L // ms
    
    private var harpyEagle: HarpyEagle? = null
    private var lastEagleSpawnTime = 0L
    private var blackJaguar: Jaguar? = null
    private var lastJaguarSpawnTime = 0L
    private var deathReason: String? = null
    private var deathReasonDisplayUntil = 0L
    
    private val drawPath = Path()
    private val drawRectF = RectF()
    private val reusablePaint = Paint()
    
    private val mangoGradient by lazy {
        LinearGradient(-15f, -15f, 15f, 15f,
            Color.parseColor("#FF3D00"), Color.parseColor("#FFEA00"), Shader.TileMode.CLAMP)
    }

    private val celebrationManager = CelebrationManager()
    
    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gamePaused && !gameOver) {
                update()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    private val animHandler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || gamePaused) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }

    data class FallingItem(
        var x: Float,
        var y: Float,
        val type: Int,
        val speed: Float,
        val rope: Int, // The target rope/vine index (for bird, representing spawn height or direction)
        val initialX: Float = x,
        var isLeftToRight: Boolean = true // for horizontal flying birds
    )

    data class HarpyEagle(
        var x: Float,
        var y: Float,
        var state: Int, // 0: Warning, 1: Swoop, 2: Retreat
        var spawnTime: Long,
        var targetRope: Int
    )

    data class Jaguar(
        var x: Float,
        var y: Float,
        var state: Int, // 0: Warning, 1: Chase, 2: Retreat
        var spawnTime: Long,
        var currentRope: Int,
        var leapTime: Long = 0L
    )

    data class FiberParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var alpha: Int)
    private val fiberParticles = mutableListOf<FiberParticle>()

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
        lives = 6
        playerDizzyUntil = 0L
        monkeyGrabbed = false
        isFalling = false
        fallSpeed = 0f
        cutRopeIndex = -1
        cutY = 0f
        vineCutTime = 0L
        currentRope = 1
        playerY = height * 0.72f
        playerRenderX = getRopeX(currentRope)
        obstacles.clear()
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
        harpyEagle = null
        lastEagleSpawnTime = System.currentTimeMillis()
        blackJaguar = null
        lastJaguarSpawnTime = System.currentTimeMillis()
        fiberParticles.clear()
        deathReason = null
        deathReasonDisplayUntil = 0L
        celebrationManager.start(0f, 0f)
        invalidate()
    }

    private fun spawnItem() {
        // Higher score = slightly faster spawning
        spawnInterval = (1200L - (score * 2L)).coerceAtLeast(650L)

        val randVal = Random.nextFloat()
        val type = when {
            randVal < 0.25f -> 0 // Banana (25%)
            randVal < 0.33f -> 5 // Pineapple (8%)
            randVal < 0.41f -> 6 // Mango (8%)
            randVal < 0.49f -> 7 // Papaya (8%)
            randVal < 0.64f -> 1 // Coconut (15%)
            randVal < 0.76f -> 2 // Spider (12%)
            randVal < 0.85f -> 3 // Snake (9%)
            randVal < 0.92f -> 4 // Bird (7%)
            else -> 8            // Vine Cutter Beetle (8%)
        }

        val rope = Random.nextInt(0, 3)
        val speed = (8f + score / 80f).coerceAtMost(18f)

        if (type == 4) { // Horizontal Bird
            val isLeft = Random.nextBoolean()
            val x = if (isLeft) -50f else width + 50f
            // Birds spawn around the upper-middle region
            val y = Random.nextFloat() * (height * 0.5f) + height * 0.15f
            obstacles.add(FallingItem(x, y, type, speed * 0.8f, rope, x, isLeft))
        } else { // Vertical items spawning on a specific rope
            val rx = getRopeX(rope)
            obstacles.add(FallingItem(rx, -50f, type, speed, rope))
        }
    }

    private fun getRopeX(rope: Int): Float {
        val step = width / 4f
        return step * (rope + 1)
    }

    private fun update() {
        val now = System.currentTimeMillis()

        // 2. Spawn Obstacles
        if (now - lastSpawnTime > spawnInterval) {
            spawnItem()
            lastSpawnTime = now
        }

        // 3. Smooth sliding for player monkey X
        val targetX = getRopeX(currentRope)
        playerRenderX += (targetX - playerRenderX) * 0.22f

        // 4. Vine scrolling animation (visual illusion of climbing)
        val isDizzy = now < playerDizzyUntil
        val currentClimbSpeed = if (isDizzy || isFalling || monkeyGrabbed) 0f else 3f + (score / 150f).coerceAtMost(5f)
        vineScrollOffset = (vineScrollOffset + currentClimbSpeed) % height
        if (currentClimbSpeed > 0f) {
            climbTime += 0.18f
        }

        // Update falling state
        if (isFalling) {
            playerY += fallSpeed
            fallSpeed += 1.2f
            if (playerY > height + 80f) {
                isFalling = false
                playerY = height * 0.72f
                lives--
                deathReason = context.getString(R.string.monkey_death_fell)
                deathReasonDisplayUntil = now + 1800L
                playerDizzyUntil = now + 1500L
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
        }

        // Update fiber particles
        val fIter = fiberParticles.iterator()
        while (fIter.hasNext()) {
            val p = fIter.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.2f
            p.alpha -= 10
            if (p.alpha <= 0) fIter.remove()
        }

        // 5. Update Harpy Eagle
        if (harpyEagle == null && now - lastEagleSpawnTime > 26000L && !gamePaused && !gameOver) {
            val targetRope = Random.nextInt(3)
            harpyEagle = HarpyEagle(
                x = getRopeX(targetRope),
                y = -120f,
                state = 0,
                spawnTime = now,
                targetRope = targetRope
            )
            lastEagleSpawnTime = now
            SoundManager.playError()
        }

        if (harpyEagle != null) {
            val eagle = harpyEagle!!
            val elapsedEagle = now - eagle.spawnTime
            if (eagle.state == 0) {
                // Warning state: hover above target
                eagle.y = -120f
                if (elapsedEagle > 1800L) {
                    eagle.state = 1 // swoop/chase
                }
            } else if (eagle.state == 1) {
                // Swooping down and chasing player X slightly
                val eagleSpeed = 15f + score / 60f
                eagle.y += eagleSpeed
                eagle.x += (playerRenderX - eagle.x) * 0.08f
                
                // Collision check with monkey
                if (!monkeyGrabbed && !isFalling && eagle.y > playerY - 35f && eagle.y < playerY + 35f) {
                    if (Math.abs(eagle.x - playerRenderX) < 60f) {
                        monkeyGrabbed = true
                        deathReason = context.getString(R.string.monkey_death_eagle)
                        deathReasonDisplayUntil = now + 2500L
                        SoundManager.playError()
                        eagle.state = 2 // retreat
                    }
                }
                
                if (eagle.y > height + 100f) {
                    eagle.state = 2 // fly away
                }
            } else {
                // Retreating upwards off-screen
                eagle.y -= 18f
                if (monkeyGrabbed) {
                    playerY = eagle.y + 40f
                    playerRenderX = eagle.x
                }
                if (eagle.y < -150f) {
                    val wasGrabbed = monkeyGrabbed
                    harpyEagle = null
                    if (wasGrabbed) {
                        lives = 0
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
            }
        }

        // Update Black Jaguar
        if (blackJaguar == null && now - lastJaguarSpawnTime > 42000L && !gamePaused && !gameOver) {
            val targetRope = Random.nextInt(3)
            blackJaguar = Jaguar(
                x = getRopeX(targetRope),
                y = height + 100f,
                state = 0,
                spawnTime = now,
                currentRope = targetRope
            )
            lastJaguarSpawnTime = now
            SoundManager.playError()
        }

        if (blackJaguar != null) {
            val jaguar = blackJaguar!!
            val elapsedJaguar = now - jaguar.spawnTime
            if (jaguar.state == 0) {
                // Warning state
                if (elapsedJaguar > 2000L) {
                    jaguar.state = 1
                }
            } else if (jaguar.state == 1) {
                // Chase up
                val jagSpeed = 12f + score / 80f
                jaguar.y -= jagSpeed
                
                // Periodically leap to player's rope
                if (now - jaguar.leapTime > 2500L && jaguar.currentRope != currentRope) {
                    jaguar.currentRope = currentRope
                    jaguar.leapTime = now
                }
                jaguar.x += (getRopeX(jaguar.currentRope) - jaguar.x) * 0.12f
                
                // Collision check
                if (!isFalling && !monkeyGrabbed && jaguar.y < playerY + 40f && jaguar.y > playerY - 40f) {
                    if (Math.abs(jaguar.x - playerRenderX) < 55f) {
                        lives = 0
                        gameOver = true
                        deathReason = context.getString(R.string.monkey_death_jaguar)
                        deathReasonDisplayUntil = now + 3000L
                        SoundManager.playError()
                        val isNewHigh = score > best
                        if (isNewHigh) {
                            ScoreManager.updateHighScore(context, gameKey, score)
                            best = score
                        }
                        celebrationManager.start(width / 2f, height / 2f)
                        onGameOver?.invoke(score)
                    }
                }
                
                if (jaguar.y < -150f) {
                    blackJaguar = null
                }
            }
        }

        // 6. Update items
        val iterator = obstacles.iterator()
        val playerRadius = 42f
        val itemRadius = 26f

        while (iterator.hasNext()) {
            val item = iterator.next()

            if (item.type == 4) { // Bird flies horizontally
                item.x += if (item.isLeftToRight) item.speed else -item.speed
            } else if (item.type == 3) { // Snake wriggles down in sine wave
                item.y += item.speed * 0.75f // Snakes are slower
                item.x = item.initialX + sin(item.y / 25.0).toFloat() * 28f
            } else if (item.type == 5) { // Pineapple (falls slower)
                item.y += item.speed * 0.6f
            } else if (item.type == 6) { // Mango
                item.y += item.speed * 0.9f
            } else if (item.type == 7) { // Papaya
                item.y += item.speed * 0.8f
            } else { // Standard coconut/spider/banana/beetle falls
                item.y += item.speed
            }

            // Collision check
            val dx = item.x - playerRenderX
            val dy = item.y - playerY
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            if (dist < playerRadius + itemRadius) {
                if (item.type == 0 || item.type in 5..7) { // Collect Banana / Fruits
                    val pts = when (item.type) {
                        0 -> 10 // Banana
                        5 -> 25 // Pineapple
                        6 -> 15 // Mango
                        else -> 20 // Papaya
                    }
                    score += pts
                    SoundManager.playMonkeyEat()
                } else { // Hit obstacle
                    if (item.type == 8) {
                        // Beetles cut the vine! Trigger falling state
                        if (!isFalling && !monkeyGrabbed) {
                            isFalling = true
                            fallSpeed = 3f
                            cutRopeIndex = currentRope
                            cutY = playerY
                            vineCutTime = now
                            hitTime = now
                            SoundManager.playError()
                            spawnFiberParticles(playerRenderX, playerY)
                        }
                    } else {
                        // Minor obstacles: lose half a life (1 point of health) and get dizzy
                        lives--
                        hitTime = now
                        playerDizzyUntil = now + 1500L
                        deathReason = when (item.type) {
                            1 -> context.getString(R.string.monkey_death_coconut)
                            2 -> context.getString(R.string.monkey_death_spider)
                            3 -> context.getString(R.string.monkey_death_snake)
                            else -> context.getString(R.string.monkey_death_bird)
                        }
                        deathReasonDisplayUntil = now + 1800L
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
                }
                iterator.remove()
                continue
            }

            // Remove out of bounds
            if (item.y > height + 80f || item.x < -100f || item.x > width + 100f) {
                iterator.remove()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isInitialized) return

        // 1. Draw Seasonal Background
        drawSeasonalBackground(canvas)

        // 2. Draw Vines (Swaying slightly + scrolling leaves)
        paint.style = Paint.Style.STROKE
        val nowTime = System.currentTimeMillis()
        val jungleVineColor = Color.parseColor("#2E7D32")
        val jungleLeafColor = Color.parseColor("#4CAF50")

        for (i in 0 until 3) {
            val rx = getRopeX(i)
            
            // Check if this vine is currently cut
            val isCut = (i == cutRopeIndex)
            val cutTimeElapsed = nowTime - vineCutTime
            
            if (isCut && cutTimeElapsed < 2500L) {
                // Severed vine core
                paint.strokeWidth = 10f
                paint.color = jungleVineColor
                
                // Upper vine piece with cracked end
                drawCrackedVineEnd(canvas, rx, 0f, cutY - 15f, true)
                
                // Lower falling vine piece with cracked end
                val fallOffset = cutTimeElapsed * 0.5f
                if (cutY + 15f + fallOffset < height.toFloat()) {
                    drawCrackedVineEnd(canvas, rx, cutY + 15f + fallOffset, height.toFloat(), false)
                }

                // Draw scrolling leaves but skip the severed gap!
                paint.style = Paint.Style.FILL
                paint.color = jungleLeafColor
                val leafS = 14f
                
                for (y in -100 until height.toInt() + 100 step 140) {
                    val scrollY = y + (vineScrollOffset % 140)
                    if (scrollY > cutY - 30f && scrollY < cutY + 30f + fallOffset) {
                        continue // Skip leaves in the snapped gap!
                    }
                    // Left Leaf
                    canvas.drawCircle(rx - 15f, scrollY, leafS, paint)
                    // Right Leaf
                    canvas.drawCircle(rx + 15f, scrollY + 45f, leafS, paint)
                }
            } else {
                // Normal continuous vine core
                paint.strokeWidth = 10f
                paint.color = jungleVineColor
                canvas.drawLine(rx, 0f, rx, height.toFloat(), paint)

                // Draw scrolling leaves
                paint.style = Paint.Style.FILL
                paint.color = jungleLeafColor
                val leafS = 14f
                
                for (y in -100 until height.toInt() + 100 step 140) {
                    val scrollY = y + (vineScrollOffset % 140)
                    // Left Leaf
                    canvas.drawCircle(rx - 15f, scrollY, leafS, paint)
                    // Right Leaf
                    canvas.drawCircle(rx + 15f, scrollY + 45f, leafS, paint)
                }
            }
            paint.style = Paint.Style.STROKE
        }

        // 4. Draw Falling Items
        for (item in obstacles) {
            paint.reset()
            paint.isAntiAlias = true
            when (item.type) {
                0 -> drawCartoonBanana(canvas, item.x, item.y)
                1 -> drawSpinningCoconut(canvas, item.x, item.y, item.speed)
                2 -> drawWebCrawlSpider(canvas, item.x, item.y)
                3 -> drawWavySnake(canvas, item.x, item.y)
                4 -> drawFlyingBird(canvas, item.x, item.y, item.isLeftToRight)
                5 -> drawCartoonPineapple(canvas, item.x, item.y)
                6 -> drawCartoonMango(canvas, item.x, item.y)
                7 -> drawCartoonPapaya(canvas, item.x, item.y)
                8 -> {
                    drawVineCutterBeetle(canvas, item.x, item.y)
                }
            }
        }

        // Draw Black Jaguar
        blackJaguar?.let {
            drawBlackJaguar(canvas, it.x, it.y, it.state, nowTime - it.spawnTime)
        }

        // Draw fiber particles
        paint.reset()
        paint.isAntiAlias = true
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#2E7D32")
        for (p in fiberParticles) {
            paint.alpha = p.alpha
            canvas.drawLine(p.x, p.y, p.x + 8f, p.y + 4f, paint)
        }

        // Draw Harpy Eagle
        val now = System.currentTimeMillis()
        if (harpyEagle != null) {
            val eagle = harpyEagle!!
            // If monkey grabbed, Eagle carries monkey
            val ex = if (monkeyGrabbed) playerRenderX else eagle.x
            val ey = if (monkeyGrabbed) playerY else eagle.y
            drawHarpyEagle(canvas, ex, ey, eagle.state, now - eagle.spawnTime)
        }

        // 5. Draw Player Monkey
        if (!monkeyGrabbed) {
            val isDizzy = now < playerDizzyUntil
            drawDetailedMonkey(canvas, playerRenderX, playerY, isDizzy)
        }

        // 6. Draw HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.style = Paint.Style.FILL
        
        // Lives (Hearts: Max 3 hearts = 6 health units)
        var remainingHealth = lives
        for (i in 0 until 3) {
            val hx = 60f + i * 50f
            val hy = height * 0.04f
            if (remainingHealth >= 2) {
                drawHeart(canvas, hx, hy, 16f, false)
                remainingHealth -= 2
            } else if (remainingHealth == 1) {
                drawHeart(canvas, hx, hy, 16f, true)
                remainingHealth -= 1
            } else {
                // Draw empty heart outline
                paint.reset()
                paint.isAntiAlias = true
                paint.color = Color.parseColor("#757575")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawCircle(hx - 16f * 0.35f, hy - 16f * 0.2f, 16f * 0.4f, paint)
                canvas.drawCircle(hx + 16f * 0.35f, hy - 16f * 0.2f, 16f * 0.4f, paint)
                drawPath.reset()
                drawPath.moveTo(hx - 16f * 0.72f, hy)
                drawPath.lineTo(hx, hy + 16f * 0.8f)
                drawPath.lineTo(hx + 16f * 0.72f, hy)
                drawPath.close()
                canvas.drawPath(drawPath, paint)
            }
        }
        
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        
        canvas.drawText("${context.getString(R.string.score_label)}: $score", width / 2f, height * 0.05f, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 40f, height * 0.05f, paint)

        // Draw Death Reason Notification
        if (now < deathReasonDisplayUntil) {
            val text = deathReason ?: ""
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.parseColor("#FF5252") // Soft warning red
            paint.textSize = 38f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            
            val textWidth = paint.measureText(text)
            reusablePaint.reset()
            reusablePaint.color = Color.parseColor("#D9000000") // 85% opacity black
            reusablePaint.style = Paint.Style.FILL
            reusablePaint.isAntiAlias = true
            
            canvas.drawRoundRect(
                width / 2f - textWidth / 2f - 24f,
                height / 2f - 40f,
                width / 2f + textWidth / 2f + 24f,
                height / 2f + 20f,
                16f, 16f,
                reusablePaint
            )
            
            canvas.drawText(text, width / 2f, height / 2f, paint)
        }

        // 7. State Overlays
        if (gameOver) {
            celebrationManager.draw(canvas)
            drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (gamePaused) {
            val charName = when (selectedCharacter) {
                0 -> context.getString(R.string.monkey_char_gibbon)
                1 -> context.getString(R.string.monkey_char_climber)
                2 -> context.getString(R.string.monkey_char_spider)
                3 -> context.getString(R.string.monkey_char_chimp)
                else -> context.getString(R.string.monkey_char_default)
            }
            drawOverlay(canvas, context.getString(R.string.game_monkey), "${context.getString(R.string.resume_hint)}\n\nCHARACTER: < $charName >\n(Press DPAD Left/Right to Switch)")
        }
    }

    private fun drawSeasonalBackground(canvas: Canvas) {
        val jungleBgTop = Color.parseColor("#0F381B")
        val jungleBgBot = Color.parseColor("#05160A")

        // Draw sky gradient
        val gradient = LinearGradient(0f, 0f, 0f, height.toFloat(), jungleBgTop, jungleBgBot, Shader.TileMode.CLAMP)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Draw background silhouette trees (distant canopy)
        paint.color = blendColors(jungleBgBot, jungleBgTop, 0.35f)
        paint.style = Paint.Style.FILL
        drawPath.reset()
        drawPath.moveTo(0f, height.toFloat())
        drawPath.lineTo(0f, height * 0.4f)
        drawPath.quadTo(width * 0.25f, height * 0.3f, width * 0.5f, height * 0.45f)
        drawPath.quadTo(width * 0.75f, height * 0.35f, width.toFloat(), height * 0.4f)
        drawPath.lineTo(width.toFloat(), height.toFloat())
        drawPath.close()
        canvas.drawPath(drawPath, paint)

        // Draw hanging background vines (parallax feel, thin and translucent)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = blendColors(jungleBgTop, Color.BLACK, 0.2f)
        val originalAlpha = paint.alpha
        paint.alpha = 130 // Translucent
        
        // Let's draw 4 curved vertical background vines
        for (i in 0..4) {
            val vx = width * (0.15f + i * 0.18f)
            drawPath.reset()
            drawPath.moveTo(vx, -50f)
            drawPath.quadTo(vx - 20f, height * 0.4f, vx + 10f, height.toFloat() + 50f)
            canvas.drawPath(drawPath, paint)
            
            // Draw background leaves along these background vines
            paint.style = Paint.Style.FILL
            val leafColor = Color.parseColor("#4CAF50")
            paint.color = leafColor
            paint.alpha = 90 // Distant translucent leaves
            for (y in 50 until height step 150) {
                val ly = y + sin(y / 30.0).toFloat() * 15f
                val lx = vx + sin(y / 40.0).toFloat() * 10f
                canvas.drawCircle(lx - 10f, ly, 8f, paint)
                canvas.drawCircle(lx + 10f, ly, 8f, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = blendColors(jungleBgTop, Color.BLACK, 0.2f)
            paint.alpha = 130
        }
        paint.alpha = originalAlpha
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val r = (Color.red(color1) * (1 - ratio) + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * (1 - ratio) + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * (1 - ratio) + Color.blue(color2) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    // --- CARTOON DRAWING METHODS ---

    private fun drawDetailedMonkey(canvas: Canvas, x: Float, y: Float, dizzy: Boolean) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        // Climbing leg/arm wiggle offset
        val legWiggle = sin(climbTime).toFloat() * 14f

        canvas.save()
        
        // Dizzy shake / spin rotation
        if (dizzy) {
            canvas.rotate((System.currentTimeMillis() % 360).toFloat(), x, y)
        }

        // 1. Draw Tail (Long curly monkey tail)
        // Gibbons (0) and Tarzan (1) don't have monkey tails, Spider Monkey (2) and Punk Chimp (3) have it!
        if (selectedCharacter == 2 || selectedCharacter == 3) {
            paint.color = if (selectedCharacter == 2) Color.parseColor("#263238") else Color.parseColor("#3E2723")
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            drawPath.reset()
            drawPath.moveTo(x, y + 15f)
            drawPath.quadTo(x - 30f, y + 25f, x - 25f + legWiggle, y + 50f)
            canvas.drawPath(drawPath, paint)
            paint.style = Paint.Style.FILL
        }

        // 2. Draw Long Limbs
        val limbWidth = if (selectedCharacter == 2) 7f else 9f // Spider monkey limbs are sleeker!
        paint.strokeWidth = limbWidth
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        
        paint.color = when (selectedCharacter) {
            0 -> Color.parseColor("#4E342E") // Golden Gibbon dark limbs
            1 -> Color.parseColor("#FFB74D") // Tarzan skin limbs
            2 -> Color.parseColor("#263238") // Spider Monkey dark grey limbs
            else -> Color.parseColor("#271712") // Punk Chimp cocoa limbs
        }

        // Back Left leg
        canvas.drawLine(x - 12f, y + 15f, x - 22f, y + 42f + legWiggle, paint)
        // Back Right leg
        canvas.drawLine(x + 12f, y + 15f, x + 22f, y + 42f - legWiggle, paint)

        // Back Left arm
        canvas.drawLine(x - 12f, y - 10f, x - 35f, y - 30f - legWiggle, paint)
        // Back Right arm
        canvas.drawLine(x + 12f, y - 10f, x + 35f, y - 30f + legWiggle, paint)

        // Hands & Feet circles
        paint.style = Paint.Style.FILL
        paint.color = when (selectedCharacter) {
            0 -> Color.parseColor("#5D4037")
            1 -> Color.parseColor("#FFCC80") // Tarzan hand color
            2 -> Color.parseColor("#37474F") // Spider Monkey grey hands
            else -> Color.parseColor("#3E2723")
        }
        canvas.drawCircle(x - 22f, y + 42f + legWiggle, 7f, paint) // left foot
        canvas.drawCircle(x + 22f, y + 42f - legWiggle, 7f, paint) // right foot
        canvas.drawCircle(x - 35f, y - 30f - legWiggle, 7f, paint) // left hand
        canvas.drawCircle(x + 35f, y - 30f + legWiggle, 7f, paint) // right hand

        // 3. Main Torso Body
        val bodyRadius = if (selectedCharacter == 2) 20f else 24f // Spider Monkey body is sleeker!
        paint.color = when (selectedCharacter) {
            0 -> Color.parseColor("#5D4037") // Golden Gibbon torso
            1 -> Color.parseColor("#FFCC80") // Tarzan skin torso
            2 -> Color.parseColor("#37474F") // Spider Monkey grey torso
            else -> Color.parseColor("#3E2723") // Punk Chimp cocoa torso
        }
        canvas.drawCircle(x, y, bodyRadius, paint)

        // Belly Patch / Loincloth / Chestplate
        if (selectedCharacter == 1) { // Tarzan loincloth
            paint.color = Color.parseColor("#4CAF50") // Green loincloth
            canvas.drawRect(x - 18f, y + 5f, x + 18f, y + bodyRadius, paint)
            // Loincloth yellow spots
            paint.color = Color.parseColor("#FFEB3B")
            canvas.drawCircle(x - 10f, y + 12f, 3.5f, paint)
            canvas.drawCircle(x + 8f, y + 18f, 3.5f, paint)
        } else if (selectedCharacter == 2) { // Spider Monkey chest mark
            paint.color = Color.parseColor("#B0BEC5") // Light grey mark
            canvas.drawCircle(x, y + 4f, 10f, paint)
        } else { // Golden Gibbon or Punk Chimp belly patch
            paint.color = if (selectedCharacter == 0) Color.parseColor("#8D6E63") else Color.parseColor("#5D4037")
            canvas.drawCircle(x, y + 5f, 15f, paint)
        }

        // 4. Head
        val headRadius = if (selectedCharacter == 2) 18f else 20f // Spider Monkey head is smaller!
        paint.color = when (selectedCharacter) {
            0 -> Color.parseColor("#5D4037")
            1 -> Color.parseColor("#FFCC80") // Tarzan head skin
            2 -> Color.parseColor("#37474F")
            else -> Color.parseColor("#3E2723")
        }
        canvas.drawCircle(x, y - 26f, headRadius, paint)

        // Ears
        if (selectedCharacter != 1) {
            paint.color = when (selectedCharacter) {
                0 -> Color.parseColor("#5D4037")
                2 -> Color.parseColor("#37474F")
                else -> Color.parseColor("#3E2723")
            }
            canvas.drawCircle(x - (headRadius + 1f), y - 26f, 7f, paint)
            canvas.drawCircle(x + (headRadius + 1f), y - 26f, 7f, paint)
            paint.color = if (selectedCharacter == 0) Color.parseColor("#8D6E63") else Color.parseColor("#4E342E")
            canvas.drawCircle(x - (headRadius + 1f), y - 26f, 4f, paint)
            canvas.drawCircle(x + (headRadius + 1f), y - 26f, 4f, paint)
        }

        // Draw Hair (Tarzan or Punk Chimp)
        if (selectedCharacter == 1) { // Tarzan wild hair/locks
            paint.color = Color.parseColor("#4E342E") // Brown wild hair
            drawPath.reset()
            drawPath.moveTo(x - 22f, y - 26f)
            drawPath.quadTo(x - 25f, y - 48f, x, y - 50f) // wild messy top
            drawPath.quadTo(x + 25f, y - 48f, x + 22f, y - 26f)
            drawPath.lineTo(x + 18f, y - 12f) // locks wrapping head
            drawPath.lineTo(x - 18f, y - 12f)
            drawPath.close()
            canvas.drawPath(drawPath, paint)
        } else if (selectedCharacter == 3) { // Punk Chimp bright red mohawk!
            paint.color = Color.parseColor("#FF1744") // Hot neon red
            canvas.drawRect(x - 4f, y - 56f, x + 4f, y - 36f, paint)
        }

        // 5. Face Muzzle
        if (selectedCharacter == 0 || selectedCharacter == 3) {
            paint.color = if (selectedCharacter == 0) Color.parseColor("#FFD180") else Color.parseColor("#A1887F")
            drawPath.reset()
            drawPath.moveTo(x, y - 16f)
            drawPath.quadTo(x - 15f, y - 18f, x - 13f, y - 30f)
            drawPath.quadTo(x, y - 24f, x + 13f, y - 30f)
            drawPath.quadTo(x + 15f, y - 18f, x, y - 16f)
            drawPath.close()
            canvas.drawPath(drawPath, paint)
        } else if (selectedCharacter == 2) { // Spider Monkey face pattern
            paint.color = Color.parseColor("#B0BEC5")
            canvas.drawCircle(x, y - 22f, 12f, paint)
        }

        // Eyes
        if (selectedCharacter == 3) { // Punk Chimp Cool Neon Shades!
            paint.color = Color.parseColor("#00E5FF") // Cyan bridge
            canvas.drawRect(x - 14f, y - 28f, x + 14f, y - 25f, paint)
            paint.color = Color.parseColor("#00E676") // Neon green lenses
            canvas.drawCircle(x - 7f, y - 26f, 6f, paint)
            canvas.drawCircle(x + 7f, y - 26f, 6f, paint)
        } else if (selectedCharacter == 2) { // Spider Monkey black bead eyes
            paint.color = Color.BLACK
            canvas.drawCircle(x - 5f, y - 25f, 3f, paint)
            canvas.drawCircle(x + 5f, y - 25f, 3f, paint)
        } else { // Golden Gibbon or Tarzan normal eyes
            if (dizzy) {
                paint.strokeWidth = 3f
                paint.color = Color.BLACK
                canvas.drawLine(x - 8f, y - 28f, x - 2f, y - 22f, paint)
                canvas.drawLine(x - 2f, y - 28f, x - 8f, y - 22f, paint)
                canvas.drawLine(x + 2f, y - 28f, x + 8f, y - 22f, paint)
                canvas.drawLine(x + 8f, y - 28f, x + 2f, y - 22f, paint)
            } else {
                paint.color = Color.BLACK
                canvas.drawCircle(x - 5f, y - 25f, 3f, paint)
                canvas.drawCircle(x + 5f, y - 25f, 3f, paint)
            }
        }

        // Nose/Mouth
        if (selectedCharacter != 3) { // Punk Chimp wears glasses so mouth is simpler
            paint.color = Color.parseColor("#8D6E63")
            canvas.drawCircle(x, y - 20f, 2f, paint)
            
            paint.color = Color.parseColor("#D32F2F")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            drawRectF.set(x - 4f, y - 22f, x + 4f, y - 16f)
            canvas.drawArc(drawRectF, 0f, 180f, false, paint)
        } else { // Punk Chimp simple smirk
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            canvas.drawLine(x - 4f, y - 17f, x + 4f, y - 18f, paint)
        }

        canvas.restore()

        // Swirling dizzy stars above head
        if (dizzy) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.parseColor("#FFD54F")
            val cycle = (System.currentTimeMillis() / 70) % 360
            for (i in 0 until 3) {
                val angle = Math.toRadians((cycle + i * 120).toDouble())
                val sx = x + cos(angle).toFloat() * 22f
                val sy = y - 52f + sin(angle).toFloat() * 8f
                canvas.drawCircle(sx, sy, 3.5f, paint)
            }
        }
    }

    private fun drawCartoonBanana(canvas: Canvas, cx: Float, cy: Float) {
        paint.reset()
        paint.isAntiAlias = true
        
        // Gentle rotation/floating animation for bananas
        val pulse = (1.0f + 0.1f * sin(System.currentTimeMillis() / 120.0).toFloat())
        val rotation = (sin(System.currentTimeMillis() / 150.0) * 15.0).toFloat()
        
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation)
        canvas.scale(pulse, pulse)

        // Yellow banana main body (crescent)
        paint.color = Color.parseColor("#FFEE58")
        paint.style = Paint.Style.FILL
        drawPath.reset()
        drawPath.moveTo(-20f, -6f)
        drawPath.quadTo(0f, 22f, 22f, -4f)
        drawPath.quadTo(5f, 12f, -20f, -6f)
        canvas.drawPath(drawPath, paint)

        // Inner lighter highlight
        paint.color = Color.parseColor("#FFF59D")
        drawPath.reset()
        drawPath.moveTo(-16f, -2f)
        drawPath.quadTo(0f, 16f, 18f, -2f)
        drawPath.quadTo(5f, 8f, -16f, -2f)
        canvas.drawPath(drawPath, paint)

        // Brown tip and stem
        paint.color = Color.parseColor("#5D4037")
        canvas.drawCircle(22f, -4f, 3f, paint) // Tip
        
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(-20f, -6f, -25f, -11f, paint) // Stem

        canvas.restore()
    }

    private fun drawSpinningCoconut(canvas: Canvas, cx: Float, cy: Float, speed: Float) {
        paint.reset()
        paint.isAntiAlias = true
        
        // Coconuts spin as they fall down
        val spinAngle = (System.currentTimeMillis() * (speed / 10f)) % 360
        
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(spinAngle)

        // Coconut shell (Brown circle)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#4E342E")
        canvas.drawCircle(0f, 0f, 26f, paint)

        // Inner texture line/highlight
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#3E2723")
        paint.strokeWidth = 2.5f
        canvas.drawCircle(0f, 0f, 22f, paint)

        // Three black dots (the "monkey face" of coconuts)
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        canvas.drawCircle(-7f, -7f, 3.5f, paint)
        canvas.drawCircle(7f, -7f, 3.5f, paint)
        canvas.drawCircle(0f, 8f, 3.5f, paint)

        canvas.restore()
    }

    private fun drawWebCrawlSpider(canvas: Canvas, cx: Float, cy: Float) {
        paint.reset()
        paint.isAntiAlias = true

        // Draw web line hanging from top
        paint.color = Color.parseColor("#CCFFFFFF")
        paint.strokeWidth = 1.5f
        canvas.drawLine(cx, 0f, cx, cy, paint)

        // Leg crawl animation
        val crawl = sin(System.currentTimeMillis() / 70.0).toFloat() * 6f

        // Body
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#212121")
        canvas.drawCircle(cx, cy, 18f, paint)

        // Skull pattern/danger warning spot
        paint.color = Color.parseColor("#D50000") // Red spot
        canvas.drawCircle(cx, cy - 2f, 7f, paint)

        // Crawling legs (8 legs)
        paint.color = Color.parseColor("#212121")
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        // Left legs
        canvas.drawLine(cx - 18f, cy, cx - 28f, cy - 8f + crawl, paint)
        canvas.drawLine(cx - 18f, cy + 4f, cx - 32f, cy + crawl, paint)
        canvas.drawLine(cx - 18f, cy - 4f, cx - 30f, cy - 14f - crawl, paint)
        canvas.drawLine(cx - 18f, cy + 8f, cx - 26f, cy + 16f - crawl, paint)

        // Right legs
        canvas.drawLine(cx + 18f, cy, cx + 28f, cy - 8f - crawl, paint)
        canvas.drawLine(cx + 18f, cy + 4f, cx + 32f, cy - crawl, paint)
        canvas.drawLine(cx + 18f, cy - 4f, cx + 30f, cy - 14f + crawl, paint)
        canvas.drawLine(cx + 18f, cy + 8f, cx + 26f, cy + 16f + crawl, paint)
    }

    private fun drawWavySnake(canvas: Canvas, cx: Float, cy: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        // Green scales body
        paint.color = Color.parseColor("#2E7D32")
        canvas.drawCircle(cx, cy, 15f, paint)
        
        // Tail (wiggly behind body)
        paint.color = Color.parseColor("#43A047")
        val wiggleTail = sin(cy / 15.0).toFloat() * 10f
        canvas.drawCircle(cx + wiggleTail, cy - 20f, 10f, paint)
        canvas.drawCircle(cx - wiggleTail, cy - 35f, 6f, paint)

        // Snake Head
        paint.color = Color.parseColor("#2E7D32")
        canvas.drawCircle(cx, cy + 14f, 17f, paint)

        // Eyes (yellow dots)
        paint.color = Color.YELLOW
        canvas.drawCircle(cx - 5f, cy + 16f, 3f, paint)
        canvas.drawCircle(cx + 5f, cy + 16f, 3f, paint)

        // Tongue (Red flicking line)
        paint.color = Color.RED
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        val tongueLength = 10f + sin(System.currentTimeMillis() / 50.0).toFloat() * 4f
        canvas.drawLine(cx, cy + 28f, cx, cy + 28f + tongueLength, paint)
    }

    private fun drawFlyingBird(canvas: Canvas, cx: Float, cy: Float, leftToRight: Boolean) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        // Flapping wing angle
        val wingFlap = sin(System.currentTimeMillis() / 60.0).toFloat() * 15f

        // Body (Blue bird)
        paint.color = Color.parseColor("#0288D1")
        canvas.drawCircle(cx, cy, 16f, paint)

        // Wing
        paint.color = Color.parseColor("#03A9F4")
        drawPath.reset()
        drawPath.moveTo(cx, cy)
        drawPath.lineTo(cx - 5f, cy - 25f + wingFlap)
        drawPath.lineTo(cx + 12f, cy - 8f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)

        // Head/Beak facing direction
        paint.color = Color.parseColor("#0288D1")
        val beakX = if (leftToRight) cx + 18f else cx - 18f
        canvas.drawCircle(beakX, cy - 4f, 11f, paint)

        // Yellow Beak triangle
        paint.color = Color.parseColor("#FFCA28")
        drawPath.reset()
        drawPath.moveTo(beakX, cy - 7f)
        if (leftToRight) {
            drawPath.lineTo(beakX + 16f, cy - 4f)
        } else {
            drawPath.lineTo(beakX - 16f, cy - 4f)
        }
        drawPath.lineTo(beakX, cy - 1f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)

        // Eye
        paint.color = Color.WHITE
        canvas.drawCircle(if (leftToRight) beakX + 2f else beakX - 2f, cy - 6f, 3.5f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(if (leftToRight) beakX + 3f else beakX - 3f, cy - 6f, 1.8f, paint)
    }

    private fun drawCartoonPineapple(canvas: Canvas, cx: Float, cy: Float) {
        paint.reset()
        paint.isAntiAlias = true
        
        // Main Body (Yellow-Orange Oval)
        paint.color = Color.parseColor("#FFB300")
        paint.style = Paint.Style.FILL
        val r = 24f
        drawRectF.set(cx - r * 0.8f, cy - r * 1.1f, cx + r * 0.8f, cy + r * 1.1f)
        canvas.drawOval(drawRectF, paint)
        
        // Draw cross-hatch lines
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#E65100")
        paint.style = Paint.Style.STROKE
        canvas.drawLine(cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f, paint)
        canvas.drawLine(cx - r * 0.6f, cy + r * 0.6f, cx + r * 0.6f, cy - r * 0.6f, paint)
        canvas.drawLine(cx - r * 0.7f, cy, cx + r * 0.7f, cy, paint)
        
        // Spiked Leaf Crown (Green Path at top)
        paint.color = Color.parseColor("#2E7D32")
        paint.style = Paint.Style.FILL
        drawPath.reset()
        drawPath.moveTo(cx, cy - r * 1.1f)
        drawPath.lineTo(cx - 15f, cy - r * 1.8f)
        drawPath.lineTo(cx - 5f, cy - r * 1.3f)
        drawPath.lineTo(cx, cy - r * 2.0f) // Tall center leaf
        drawPath.lineTo(cx + 5f, cy - r * 1.3f)
        drawPath.lineTo(cx + 15f, cy - r * 1.8f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
    }

    private fun drawCartoonMango(canvas: Canvas, cx: Float, cy: Float) {
        paint.reset()
        paint.isAntiAlias = true
        
        // Gradient color for Mango (red to yellow blend)
        paint.shader = mangoGradient
        paint.style = Paint.Style.FILL
        
        // Kidney shape/asymmetric oval
        drawPath.reset()
        drawPath.moveTo(cx - 12f, cy - 20f)
        drawPath.quadTo(cx + 25f, cy - 18f, cx + 18f, cy + 18f)
        drawPath.quadTo(cx - 5f, cy + 24f, cx - 18f, cy)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
        paint.shader = null
        
        // Small leaf on top
        paint.color = Color.parseColor("#4CAF50")
        drawPath.reset()
        drawPath.moveTo(cx - 6f, cy - 18f)
        drawPath.quadTo(cx - 15f, cy - 28f, cx - 20f, cy - 24f)
        drawPath.quadTo(cx - 12f, cy - 20f, cx - 6f, cy - 18f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
    }

    private fun drawCartoonPapaya(canvas: Canvas, cx: Float, cy: Float) {
        paint.reset()
        paint.isAntiAlias = true
        
        // Outer skin (Olive green-orange)
        paint.color = Color.parseColor("#81C784")
        paint.style = Paint.Style.FILL
        val r = 24f
        // Pear shape
        drawPath.reset()
        drawPath.moveTo(cx, cy - r * 1.1f)
        drawPath.quadTo(cx + r * 0.6f, cy - r * 0.6f, cx + r * 0.9f, cy + r * 0.4f)
        drawPath.quadTo(cx + r * 0.8f, cy + r * 1.1f, cx, cy + r * 1.1f)
        drawPath.quadTo(cx - r * 0.8f, cy + r * 1.1f, cx - r * 0.9f, cy + r * 0.4f)
        drawPath.quadTo(cx - r * 0.6f, cy - r * 0.6f, cx, cy - r * 1.1f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
        
        // Inner orange flesh
        paint.color = Color.parseColor("#FF7043")
        canvas.drawCircle(cx, cy + 8f, 14f, paint)
        canvas.drawCircle(cx, cy - 4f, 8f, paint)
        
        // Tiny black seeds
        paint.color = Color.BLACK
        canvas.drawCircle(cx - 3f, cy + 4f, 2f, paint)
        canvas.drawCircle(cx + 3f, cy + 6f, 2f, paint)
        canvas.drawCircle(cx, cy - 2f, 2f, paint)
        canvas.drawCircle(cx - 2f, cy + 10f, 1.8f, paint)
        canvas.drawCircle(cx + 2f, cy + 2f, 1.8f, paint)
    }

    private fun drawHarpyEagle(canvas: Canvas, cx: Float, cy: Float, state: Int, elapsed: Long) {
        paint.reset()
        paint.isAntiAlias = true
        
        if (state == 0) { // Warning: Draw warning target circle and arrow
            val flash = (elapsed / 200) % 2 == 0L
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = if (flash) Color.RED else Color.TRANSPARENT
            
            // Flashing warning reticle
            val r = 50f
            canvas.drawCircle(cx, cy + 150f, r, paint)
            canvas.drawLine(cx - r - 15f, cy + 150f, cx + r + 15f, cy + 150f, paint)
            canvas.drawLine(cx, cy + 150f - r - 15f, cx, cy + 150f + r + 15f, paint)
            
            // Warning text "EAGLE!"
            paint.reset()
            paint.isAntiAlias = true
            paint.color = if (flash) Color.RED else Color.parseColor("#FFD54F")
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(context.getString(R.string.monkey_eagle_warning), cx, cy + 80f, paint)
            return
        }

        // Swooping/Chasing or Retreating
        val flap = sin(System.currentTimeMillis() / 80.0).toFloat() * 20f
        
        // Large Wings (Dark grey-brown)
        paint.color = Color.parseColor("#37474F")
        paint.style = Paint.Style.FILL
        drawPath.reset()
        drawPath.moveTo(cx, cy)
        drawPath.lineTo(cx - 75f, cy - 30f + flap)
        drawPath.lineTo(cx - 30f, cy + 10f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
        
        drawPath.reset()
        drawPath.moveTo(cx, cy)
        drawPath.lineTo(cx + 75f, cy - 30f + flap)
        drawPath.lineTo(cx + 30f, cy + 10f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)

        // Main eagle body
        paint.color = Color.parseColor("#455A64")
        drawRectF.set(cx - 20f, cy - 24f, cx + 20f, cy + 24f)
        canvas.drawOval(drawRectF, paint)
        
        // White chest feathers
        paint.color = Color.parseColor("#ECEFF1")
        canvas.drawCircle(cx, cy + 4f, 12f, paint)

        // Head (White)
        canvas.drawCircle(cx, cy - 22f, 15f, paint)
        
        // Curved yellow beak pointing down
        paint.color = Color.parseColor("#FFCA28")
        drawPath.reset()
        drawPath.moveTo(cx - 6f, cy - 22f)
        drawPath.lineTo(cx + 6f, cy - 22f)
        drawPath.lineTo(cx, cy - 8f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
        
        // Fierce yellow eyes
        paint.color = Color.YELLOW
        canvas.drawCircle(cx - 5f, cy - 25f, 3f, paint)
        canvas.drawCircle(cx + 5f, cy - 25f, 3f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(cx - 5f, cy - 25f, 1.2f, paint)
        canvas.drawCircle(cx + 5f, cy - 25f, 1.2f, paint)
    }

    private fun drawCrackedVineEnd(canvas: Canvas, x: Float, y1: Float, y2: Float, isTopPiece: Boolean) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.color = Color.parseColor("#2E7D32")
        
        // Main core
        canvas.drawLine(x, y1, x, y2, paint)
        
        // Frayed/Cracked end
        val endY = y2
        paint.strokeWidth = 3f
        drawPath.reset()
        if (isTopPiece) {
            // Upper piece cracks downwards
            drawPath.moveTo(x - 5f, endY)
            drawPath.lineTo(x - 8f, endY + 15f)
            drawPath.moveTo(x, endY)
            drawPath.lineTo(x + 2f, endY + 20f)
            drawPath.moveTo(x + 5f, endY)
            drawPath.lineTo(x + 7f, endY + 12f)
        } else {
            // Lower piece cracks upwards
            drawPath.moveTo(x - 5f, y1)
            drawPath.lineTo(x - 7f, y1 - 15f)
            drawPath.moveTo(x, y1)
            drawPath.lineTo(x - 2f, y1 - 22f)
            drawPath.moveTo(x + 5f, y1)
            drawPath.lineTo(x + 9f, y1 - 10f)
        }
        canvas.drawPath(drawPath, paint)
    }

    private fun drawBlackJaguar(canvas: Canvas, cx: Float, cy: Float, state: Int, elapsed: Long) {
        if (state == 0) {
            // Flashing warning at bottom
            val blink = (elapsed / 250) % 2 == 0L
            if (blink) {
                paint.reset()
                paint.isAntiAlias = true
                paint.color = Color.parseColor("#FF1744")
                paint.textSize = 28f
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText(context.getString(R.string.monkey_jaguar_warning), width / 2f, height - 80f, paint)
            }
            return
        }

        // Draw sleek black jaguar
        canvas.save()
        canvas.translate(cx, cy)
        
        // Twitching tail
        val tailTwitch = sin(System.currentTimeMillis() * 0.01).toFloat() * 15f
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.strokeCap = Paint.Cap.ROUND
        drawPath.reset()
        drawPath.moveTo(0f, 30f)
        drawPath.quadTo(20f + tailTwitch, 45f, 15f + tailTwitch, 70f)
        canvas.drawPath(drawPath, paint)

        // Muscular body
        paint.style = Paint.Style.FILL
        drawRectF.set(-25f, -35f, 25f, 35f)
        canvas.drawOval(drawRectF, paint)

        // Limbs (climbing pose)
        paint.strokeWidth = 10f
        val legMove = sin(System.currentTimeMillis() * 0.15).toFloat() * 10f
        canvas.drawLine(-20f, -25f, -35f, -50f + legMove, paint) // Front left
        canvas.drawLine(20f, -25f, 35f, -50f - legMove, paint)  // Front right
        canvas.drawLine(-20f, 20f, -35f, 45f - legMove, paint)  // Back left
        canvas.drawLine(20f, 20f, 35f, 45f + legMove, paint)   // Back right

        // Paws
        canvas.drawCircle(-35f, -50f + legMove, 7f, paint)
        canvas.drawCircle(35f, -50f - legMove, 7f, paint)

        // Head
        canvas.drawCircle(0f, -45f, 20f, paint)
        
        // Ears
        canvas.drawCircle(-15f, -55f, 8f, paint)
        canvas.drawCircle(15f, -55f, 8f, paint)

        // Fierce Yellow Eyes
        paint.color = Color.YELLOW
        canvas.drawCircle(-8f, -50f, 4f, paint)
        canvas.drawCircle(8f, -50f, 4f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(-8f, -50f, 1.5f, paint)
        canvas.drawCircle(8f, -50f, 1.5f, paint)

        // Snout
        paint.color = Color.parseColor("#1A1A1A")
        canvas.drawCircle(0f, -40f, 6f, paint)

        canvas.restore()
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                resetGame()
                startGame()
                return true
            }
            return false
        }

        if (gamePaused) {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    resume()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    selectedCharacter = (selectedCharacter - 1 + 4) % 4
                    SoundManager.playClick()
                    invalidate()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    selectedCharacter = (selectedCharacter + 1) % 4
                    SoundManager.playClick()
                    invalidate()
                    return true
                }
            }
            return false
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (currentRope > 0) {
                    currentRope--
                    SoundManager.playClick()
                    invalidate()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (currentRope < 2) {
                    currentRope++
                    SoundManager.playClick()
                    invalidate()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun spawnFiberParticles(x: Float, y: Float) {
        repeat(15) {
            fiberParticles.add(FiberParticle(
                x, y,
                Random.nextFloat() * 10f - 5f,
                Random.nextFloat() * 10f - 5f,
                255
            ))
        }
    }

    private fun drawHeart(canvas: Canvas, x: Float, y: Float, size: Float, isHalf: Boolean) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#FF3D00") // Vibrant red
        paint.style = Paint.Style.FILL

        if (isHalf) {
            canvas.save()
            canvas.clipRect(x - size, y - size, x, y + size)
        }

        // Draw lobes
        canvas.drawCircle(x - size * 0.35f, y - size * 0.2f, size * 0.4f, paint)
        canvas.drawCircle(x + size * 0.35f, y - size * 0.2f, size * 0.4f, paint)
        
        drawPath.reset()
        drawPath.moveTo(x - size * 0.72f, y)
        drawPath.lineTo(x, y + size * 0.8f)
        drawPath.lineTo(x + size * 0.72f, y)
        drawPath.close()
        canvas.drawPath(drawPath, paint)

        if (isHalf) {
            canvas.restore()
        }
    }

    private fun drawVineCutterBeetle(canvas: Canvas, cx: Float, cy: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        // 1. Draw 6 little insect legs
        paint.color = Color.parseColor("#1A237E") // Deep dark indigo legs
        paint.strokeWidth = 3f
        val legWiggle = sin(System.currentTimeMillis() * 0.05).toFloat() * 4f
        canvas.drawLine(cx - 15f, cy - 6f, cx - 22f, cy - 10f + legWiggle, paint)
        canvas.drawLine(cx + 15f, cy - 6f, cx + 22f, cy - 10f - legWiggle, paint)
        canvas.drawLine(cx - 15f, cy,      cx - 24f, cy + legWiggle, paint)
        canvas.drawLine(cx + 15f, cy,      cx + 24f, cy - legWiggle, paint)
        canvas.drawLine(cx - 15f, cy + 6f, cx - 22f, cy + 10f + legWiggle, paint)
        canvas.drawLine(cx + 15f, cy + 6f, cx + 22f, cy + 10f - legWiggle, paint)

        // 2. Beetle body (metallic indigo shell)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#3F51B5")
        drawRectF.set(cx - 14f, cy - 18f, cx + 14f, cy + 18f)
        canvas.drawOval(drawRectF, paint)

        // Back split line
        paint.color = Color.parseColor("#1A237E")
        paint.strokeWidth = 2f
        canvas.drawLine(cx, cy - 18f, cx, cy + 18f, paint)

        // Golden warning dots
        paint.color = Color.parseColor("#FFD54F")
        canvas.drawCircle(cx - 6f, cy - 6f, 2.5f, paint)
        canvas.drawCircle(cx + 6f, cy - 6f, 2.5f, paint)
        canvas.drawCircle(cx - 6f, cy + 6f, 2.5f, paint)
        canvas.drawCircle(cx + 6f, cy + 6f, 2.5f, paint)

        // Head and pincers
        paint.color = Color.parseColor("#1A237E")
        canvas.drawCircle(cx, cy - 22f, 7f, paint)

        // Pincers
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        drawPath.reset()
        drawPath.moveTo(cx - 4f, cy - 25f)
        drawPath.quadTo(cx - 10f, cy - 32f, cx - 4f, cy - 36f)
        canvas.drawPath(drawPath, paint)

        drawPath.reset()
        drawPath.moveTo(cx + 4f, cy - 25f)
        drawPath.quadTo(cx + 10f, cy - 32f, cx + 4f, cy - 36f)
        canvas.drawPath(drawPath, paint)
    }
}

