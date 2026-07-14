package com.tdpham.games.starfighter

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import java.util.*

class StarFighterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "starfighter"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var isPaused = true
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "starfighter_settings"
    private val KEY_DIFFICULTY = "difficulty_index"
    private var currentDifficultyIndex = 1
    private var hintShowFrames = 0
    private var isInitialized = false

    private val KEY_SHIP_TYPE = "selected_ship_index"
    private var currentShipType = 0 // 0: Balanced, 1: Fast, 2: Tank
    private var playerSpeed = 12f

    private var playerX = 0f
    private var playerY = 0f
    private val playerSize = 70f
    private var lives = 3
    private var invulnerableUntil = 0L
    private var hasPhoenixRevived = false

    private val bullets = mutableListOf<Bullet>()
    private val enemies = mutableListOf<Enemy>()
    private val powerUps = mutableListOf<PowerUp>()
    private val particles = mutableListOf<Particle>()
    private val bgParticles = mutableListOf<GameEnvironment.Particle>()
    private val random = Random()

    private var lastEnemySpawn = 0L
    private var lastBossScore = 0
    private var lastShoot = 0L
    private var gunLevel = 1
    private var powerUpEndTime = 0L
    private var bgType = GameEnvironment.BackgroundType.STARRY
    private val pressedKeys = mutableSetOf<Int>()
    private val screenShake = com.tdpham.games.common.ScreenShake()

    private val handler = Handler(Looper.getMainLooper())
    private val animHandler = Handler(Looper.getMainLooper())
    private val drawRect = RectF()
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || isPaused) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isPaused && !gameOver) {
                update()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    private val playerPath = Path()
    private val tempPath = Path()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
        animHandler.post(animRunnable)
    }

    override fun startGame() {
        isPaused = false
        handler.removeCallbacks(gameLoop)
        handler.post(gameLoop)
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun resetGame() {
        // Load difficulty and ship type from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentDifficultyIndex = prefs.getInt(KEY_DIFFICULTY, 1).coerceIn(0, 2)
        currentShipType = prefs.getInt(KEY_SHIP_TYPE, 0).coerceIn(0, 4)

        score = 0
        lastBossScore = 0
        best = ScoreManager.getHighScore(context, gameKey, currentDifficultyIndex)
        gameOver = false
        isPaused = true
        celebrationManager.start(0f, 0f)
        gunLevel = 1

        // Ship Stats
        hasPhoenixRevived = false
        when(currentShipType) {
            1 -> { // Fast
                playerSpeed = 18f
                lives = 1
            }
            2 -> { // Tank
                playerSpeed = 8f
                lives = 5
            }
            3 -> { // Solar Flare (Glass Cannon)
                playerSpeed = 15f
                lives = 1
                gunLevel = 2
            }
            4 -> { // Phoenix
                playerSpeed = 12f
                lives = 2
            }
            else -> { // Balanced
                playerSpeed = 13f
                lives = 3
            }
        }
        
        // Difficulty adjustments to health (Difficulty still overrides base lives if very high/low)
        if (currentDifficultyIndex == 0) lives = (lives + 2).coerceAtMost(7)
        if (currentDifficultyIndex == 2) lives = (lives - 1).coerceAtLeast(1)

        invulnerableUntil = 0L
        powerUpEndTime = 0L
        bullets.clear()
        enemies.clear()
        powerUps.clear()
        particles.clear()
        bgParticles.clear()
        pressedKeys.clear()
        handler.removeCallbacks(gameLoop)
        bgType = listOf(GameEnvironment.BackgroundType.GRADIENT, GameEnvironment.BackgroundType.STARRY, GameEnvironment.BackgroundType.SOLID).random()
        repeat(60) {
            bgParticles.add(GameEnvironment.Particle(random.nextFloat() * 2000, random.nextFloat() * 2000, random.nextFloat() * 4 + 1))
        }
        
        hintShowFrames = 100
        invalidate()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
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
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                pressedKeys.add(keyCode)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                pause()
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> {
                showOptions()
                return true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showOptions() {
        pause()
        StarFighterOptionsDialog.show(context) {
            resetGame()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_MOVE || event.action == android.view.MotionEvent.ACTION_DOWN) {
            if (gameOver || isPaused) {
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    performClick()
                    if (gameOver) resetGame()
                    startGame()
                }
                return true
            }
            
            // Ship follows mouse/touch
            playerX = event.x.coerceIn(playerSize, width - playerSize)
            playerY = event.y.coerceIn(playerSize, height - playerSize)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.remove(keyCode)
        return super.onKeyUp(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameLoop)
        animHandler.removeCallbacks(animRunnable)
    }

    private fun update() {
        if (isPaused || gameOver) return

        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) playerX = (playerX - playerSpeed).coerceAtLeast(playerSize)
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) playerX = (playerX + playerSpeed).coerceAtMost(width - playerSize)
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP)) playerY = (playerY - playerSpeed).coerceAtLeast(playerSize)
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)) playerY = (playerY + playerSpeed).coerceAtMost(height - playerSize)

        val now = System.currentTimeMillis()
        
        val hasBoss = enemies.any { it.type == EnemyType.BOSS }
        if (!hasBoss && score >= lastBossScore + 2000) {
            lastBossScore = (score / 2000) * 2000
            enemies.add(Enemy(width / 2f, -150f, EnemyType.BOSS))
        }

        val spawnInterval = when(currentDifficultyIndex) { 0 -> 1500; 2 -> 800; else -> 1200 }
        if (!hasBoss && now - lastEnemySpawn > (spawnInterval - (score / 200) * 50).coerceAtLeast(500)) {
            val type = if (random.nextFloat() < 0.15) EnemyType.FAST else if (random.nextFloat() < 0.1) EnemyType.BIG else EnemyType.NORMAL
            enemies.add(Enemy(random.nextFloat() * (width - 150) + 75, -100f, type))
            lastEnemySpawn = now
        }

        val shootInterval = if (gunLevel >= 3) 250 else 350
        if (now - lastShoot > shootInterval) {
            if (now > powerUpEndTime) gunLevel = if (currentShipType == 3) 2 else 1
            
            when (gunLevel) {
                1 -> bullets.add(Bullet(playerX, playerY - 40, -18f))
                2 -> {
                    bullets.add(Bullet(playerX - 15, playerY - 30, -18f))
                    bullets.add(Bullet(playerX + 15, playerY - 30, -18f))
                }
                3 -> {
                    bullets.add(Bullet(playerX, playerY - 40, -20f))
                    bullets.add(Bullet(playerX - 25, playerY - 20, -20f, -3f))
                    bullets.add(Bullet(playerX + 25, playerY - 20, -20f, 3f))
                }
                else -> {
                    bullets.add(Bullet(playerX, playerY - 40, -22f))
                    bullets.add(Bullet(playerX - 20, playerY - 35, -22f))
                    bullets.add(Bullet(playerX + 20, playerY - 35, -22f))
                    bullets.add(Bullet(playerX - 40, playerY - 10, -22f, -4f))
                    bullets.add(Bullet(playerX + 40, playerY - 10, -22f, 4f))
                }
            }
            lastShoot = now
        }

        val bIter = bullets.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            b.y += b.vy
            b.x += b.vx
            if (b.y < -100 || b.y > height + 100 || b.x < -100 || b.x > width + 100) {
                bIter.remove()
                continue
            }

            if (b.isEnemy) {
                if (now > invulnerableUntil && RectF(playerX - 25, playerY - 25, playerX + 25, playerY + 25).contains(b.x, b.y)) {
                    playerHit()
                    bIter.remove()
                    if (gameOver) return
                    continue
                }
            }
        }

        val pUpIter = powerUps.iterator()
        while (pUpIter.hasNext()) {
            val pu = pUpIter.next()
            pu.y += 4f
            if (pu.y > height + 50) {
                pUpIter.remove()
            } else if (Math.abs(pu.x - playerX) < 50 && Math.abs(pu.y - playerY) < 50) {
                when(pu.type) {
                    PowerUpType.WEAPON -> { gunLevel = (gunLevel + 1).coerceAtMost(4); powerUpEndTime = now + 15000 }
                    PowerUpType.LIFE -> { lives = (lives + 1).coerceAtMost(5) }
                }
                SoundManager.playScore()
                pUpIter.remove()
            }
        }

        val eIter = enemies.iterator()
        while (eIter.hasNext()) {
            val e = eIter.next()
            if (e.type == EnemyType.BOSS) {
                if (e.y < 200f) {
                    e.y += 1.5f
                } else {
                    e.phase += 0.02f
                    e.x = width / 2f + Math.sin(e.phase.toDouble()).toFloat() * (width / 3f)
                }
            } else {
                val speedY = when(e.type) { EnemyType.FAST -> 8f; EnemyType.BIG -> 2.5f; else -> 4.5f }
                e.y += speedY

                val huntSpeed = when(e.type) { EnemyType.FAST -> 4.5f; EnemyType.BIG -> 1.5f; else -> 3f }
                if (e.y > 0 && e.y < height * 0.8f) {
                    if (e.x < playerX - 15) e.x += huntSpeed
                    else if (e.x > playerX + 15) e.x -= huntSpeed
                }
            }
            
            val shootChance = when(e.type) { EnemyType.BOSS -> 0.04f; EnemyType.BIG -> 0.02f; EnemyType.FAST -> 0.005f; else -> 0.01f }
            if (random.nextFloat() < shootChance && e.y > 0 && e.y < height * 0.7f) {
                if (e.type == EnemyType.BOSS) {
                    bullets.add(Bullet(e.x, e.y + 60, 10f, 0f, true))
                    bullets.add(Bullet(e.x - 30, e.y + 50, 9f, -3f, true))
                    bullets.add(Bullet(e.x + 30, e.y + 50, 9f, 3f, true))
                } else {
                    bullets.add(Bullet(e.x, e.y + e.size, 10f, 0f, true))
                }
            }
            
            if (now > invulnerableUntil && RectF(playerX - 35, playerY - 35, playerX + 35, playerY + 35).intersects(e.x - e.size * 0.8f, e.y - e.size * 0.8f, e.x + e.size * 0.8f, e.y + e.size * 0.8f)) {
                playerHit()
                spawnExplosion(e.x, e.y, e.color)
                eIter.remove()
                if (gameOver) break
                continue
            }

            val bIter2 = bullets.iterator()
            var destroyed = false
            while (bIter2.hasNext()) {
                val b = bIter2.next()
                if (!b.isEnemy && Math.abs(b.x - e.x) < e.size && Math.abs(b.y - e.y) < e.size) {
                    e.hp--
                    e.blinkUntil = System.currentTimeMillis() + 1000L
                    bIter2.remove()
                    if (e.hp <= 0) {
                        spawnExplosion(e.x, e.y, e.color)
                        score += when(e.type) {
                            EnemyType.BOSS -> 500
                            EnemyType.BIG -> 100
                            EnemyType.FAST -> 50
                            else -> 20
                        }
                        if (random.nextFloat() < 0.12) {
                            val pType = if (random.nextFloat() < 0.8 || lives >= 5) PowerUpType.WEAPON else PowerUpType.LIFE
                            powerUps.add(PowerUp(e.x, e.y, pType))
                        }
                        SoundManager.playScore()
                        destroyed = true
                    }
                    break
                }
            }
            if (destroyed) eIter.remove()
            else if (e.y > height + 150) eIter.remove()
        }

        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.update()
            if (p.life <= 0) pIter.remove()
        }
    }

    private fun playerHit() {
        lives--
        if (lives <= 0 && currentShipType == 4 && !hasPhoenixRevived) {
            lives = 1
            hasPhoenixRevived = true
            invulnerableUntil = System.currentTimeMillis() + 4000L // 4s shield
            screenShake.trigger(20, 30f)
            spawnExplosion(playerX, playerY, Color.parseColor("#FF9800")) // Orange fire explosion
            SoundManager.playScore() // Play success sound as rebirth
            return
        }
        screenShake.trigger(12, 20f)
        spawnExplosion(playerX, playerY, Color.CYAN)
        SoundManager.playError()
        if (lives <= 0) {
            endGame()
        } else {
            invulnerableUntil = System.currentTimeMillis() + 2000L
        }
    }

    private fun spawnExplosion(x: Float, y: Float, color: Int) {
        repeat(20) { particles.add(Particle(x, y, color, random)) }
    }

    private fun endGame() {
        gameOver = true
        val oldBest = best
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, currentDifficultyIndex)
        if (isNewHigh) {
            best = score
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
        } else {
            currentVictoryWord = ""
        }
        celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = false, isNewHigh = isNewHigh, score = score, highScore = oldBest)
        onGameOver?.invoke(score)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val needsInvalidate = screenShake.apply(canvas)
        
        if (hintShowFrames > 0) {
            hintShowFrames--
            invalidate()
        }

        if (playerX == 0f) { playerX = width / 2f; playerY = height - 250f }
        
        GameEnvironment.draw(canvas, bgType, isNight = true, paint = paint, particles = bgParticles)

        paint.style = Paint.Style.FILL
        particles.forEach { 
            paint.color = it.color
            paint.alpha = (it.life * 255).toInt()
            canvas.drawCircle(it.x, it.y, it.size, paint)
        }
        paint.alpha = 255

        if (!gameOver) {
            val isInvulnerable = System.currentTimeMillis() < invulnerableUntil
            if (!isInvulnerable || (System.currentTimeMillis() / 100) % 2 == 0L) {
                drawPlayerShip(canvas, playerX, playerY)
            }
        }

        for (e in enemies) {
            val isBlinking = now < e.blinkUntil
            val baseColor = if (isBlinking && (now / 80) % 2 == 0L) Color.WHITE else e.color
            paint.color = baseColor
            
            when (e.type) {
                EnemyType.BOSS -> {
                    // Outer wing shapes
                    tempPath.reset()
                    tempPath.moveTo(e.x - e.size, e.y)
                    tempPath.lineTo(e.x - e.size * 0.8f, e.y - e.size * 0.5f)
                    tempPath.lineTo(e.x + e.size * 0.8f, e.y - e.size * 0.5f)
                    tempPath.lineTo(e.x + e.size, e.y)
                    tempPath.lineTo(e.x + e.size * 0.7f, e.y + e.size * 0.4f)
                    tempPath.lineTo(e.x - e.size * 0.7f, e.y + e.size * 0.4f)
                    tempPath.close()
                    canvas.drawPath(tempPath, paint)

                    // Side engines (Darker purple/accent)
                    paint.color = Color.parseColor("#4A148C")
                    canvas.drawRect(e.x - e.size * 0.8f, e.y - e.size * 0.4f, e.x - e.size * 0.5f, e.y + e.size * 0.3f, paint)
                    canvas.drawRect(e.x + e.size * 0.5f, e.y - e.size * 0.4f, e.x + e.size * 0.8f, e.y + e.size * 0.3f, paint)

                    // Glowing energy core (Red/Orange pulsing)
                    paint.color = if ((now / 150) % 2 == 0L) Color.RED else Color.YELLOW
                    canvas.drawCircle(e.x, e.y, e.size * 0.25f, paint)

                    // Core center
                    paint.color = Color.WHITE
                    canvas.drawCircle(e.x, e.y, e.size * 0.12f, paint)

                    // Front turrets
                    paint.color = Color.DKGRAY
                    canvas.drawRect(e.x - 30, e.y + e.size * 0.3f, e.x - 15, e.y + e.size * 0.5f, paint)
                    canvas.drawRect(e.x + 15, e.y + e.size * 0.3f, e.x + 30, e.y + e.size * 0.5f, paint)
                }
                EnemyType.BIG -> {
                    tempPath.reset()
                    // Angular wing shape
                    tempPath.moveTo(e.x - e.size, e.y - e.size * 0.2f)
                    tempPath.lineTo(e.x - e.size * 0.4f, e.y - e.size)
                    tempPath.lineTo(e.x + e.size * 0.4f, e.y - e.size)
                    tempPath.lineTo(e.x + e.size, e.y - e.size * 0.2f)
                    tempPath.lineTo(e.x + e.size * 0.6f, e.y + e.size * 0.8f)
                    tempPath.lineTo(e.x - e.size * 0.6f, e.y + e.size * 0.8f)
                    tempPath.close()
                    canvas.drawPath(tempPath, paint)

                    // Armor plates (Darker red/pink)
                    paint.color = Color.parseColor("#880E4F")
                    canvas.drawRect(e.x - e.size * 0.3f, e.y - e.size * 0.6f, e.x + e.size * 0.3f, e.y + e.size * 0.4f, paint)

                    // Bright yellow cockpit
                    paint.color = Color.YELLOW
                    canvas.drawCircle(e.x, e.y - e.size * 0.1f, 15f, paint)
                }
                EnemyType.FAST -> {
                    tempPath.reset()
                    tempPath.moveTo(e.x, e.y + e.size * 1.2f)
                    tempPath.lineTo(e.x - e.size, e.y - e.size * 0.6f)
                    tempPath.lineTo(e.x - e.size * 0.3f, e.y - e.size)
                    tempPath.lineTo(e.x + e.size * 0.3f, e.y - e.size)
                    tempPath.lineTo(e.x + e.size, e.y - e.size * 0.6f)
                    tempPath.close()
                    canvas.drawPath(tempPath, paint)

                    // Side stripe details
                    paint.color = Color.parseColor("#F57F17") // Gold
                    canvas.drawCircle(e.x - e.size * 0.4f, e.y - e.size * 0.2f, 6f, paint)
                    canvas.drawCircle(e.x + e.size * 0.4f, e.y - e.size * 0.2f, 6f, paint)
                }
                else -> { // NORMAL (Bug/Invader style)
                    tempPath.reset()
                    // Head and side antennas
                    tempPath.moveTo(e.x - e.size * 0.5f, e.y + e.size)
                    tempPath.lineTo(e.x - e.size, e.y)
                    tempPath.lineTo(e.x - e.size * 0.8f, e.y - e.size * 0.6f)
                    tempPath.lineTo(e.x - e.size * 0.3f, e.y - e.size)
                    tempPath.lineTo(e.x + e.size * 0.3f, e.y - e.size)
                    tempPath.lineTo(e.x + e.size * 0.8f, e.y - e.size * 0.6f)
                    tempPath.lineTo(e.x + e.size, e.y)
                    tempPath.lineTo(e.x + e.size * 0.5f, e.y + e.size)
                    tempPath.close()
                    canvas.drawPath(tempPath, paint)

                    // Glowing eyes
                    paint.color = Color.WHITE
                    canvas.drawCircle(e.x - 12, e.y - 10, 8f, paint)
                    canvas.drawCircle(e.x + 12, e.y - 10, 8f, paint)
                    paint.color = Color.RED
                    canvas.drawCircle(e.x - 12, e.y - 10, 3f, paint)
                    canvas.drawCircle(e.x + 12, e.y - 10, 3f, paint)
                }
            }
        }

        bullets.forEach {
            paint.color = if (it.isEnemy) Color.RED else Color.CYAN
            paint.style = Paint.Style.FILL
            drawRect.set(it.x - 3, it.y - 12, it.x + 3, it.y + 12)
            canvas.drawRect(drawRect, paint)
            paint.alpha = 100
            canvas.drawCircle(it.x, it.y, 8f, paint)
            paint.alpha = 255
        }

        powerUps.forEach { pu ->
            paint.style = Paint.Style.FILL
            paint.color = if (pu.type == PowerUpType.LIFE) Color.RED else Color.GREEN
            canvas.drawCircle(pu.x, pu.y, 22f, paint)
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawCircle(pu.x, pu.y, 26f, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            val label = if (pu.type == PowerUpType.LIFE) "❤" else "W"
            canvas.drawText(label, pu.x, pu.y + 10f, paint)
        }

        // HUD
        paint.reset()
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        val hudY = Math.round(60f).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 40f, hudY, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.LTGRAY
        val modeStr = context.getString(when(currentDifficultyIndex) {
            0 -> R.string.starfighter_difficulty_1
            2 -> R.string.starfighter_difficulty_3
            else -> R.string.starfighter_difficulty_2
        })
        canvas.drawText("${context.getString(R.string.mode_label)}: $modeStr", width / 2f, hudY, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, hudY + 100f, paint)
            paint.alpha = 255
        }
        
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.RED
        val livesStr = "❤ ".repeat(lives)
        canvas.drawText(livesStr, 40f, Math.round(110f).toFloat(), paint)

        // Draw Boss Health Bar if Boss exists
        val boss = enemies.firstOrNull { it.type == EnemyType.BOSS }
        if (boss != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.WHITE
            val barW = width * 0.6f
            val barX = width * 0.2f
            val barY = hudY + 50f
            drawRect.set(barX, barY, barX + barW, barY + 24f)
            canvas.drawRoundRect(drawRect, 6f, 6f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#9C27B0")
            val hpPct = boss.hp.toFloat() / 30f
            drawRect.set(barX + 4, barY + 4, barX + 4 + (barW - 8) * hpPct, barY + 20f)
            canvas.drawRoundRect(drawRect, 4f, 4f, paint)

            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.WHITE
            canvas.drawText("BOSS CLASS MOTHERSHIP", width / 2f, barY - 10f, paint)
        }

        if (gameOver) {
            celebrationManager.draw(canvas)
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.mission_failed_label)
            drawOverlay(canvas, title, "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        }
        else if (isPaused) drawOverlay(canvas, context.getString(R.string.game_starfighter), context.getString(R.string.start_game))
        
        if (!isPaused && !gameOver) {
            celebrationManager.update()
            invalidate()
        }
        if (needsInvalidate) invalidate()
    }

    private fun drawPlayerShip(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        
        // Tilt effect based on horizontal movement
        var tilt = 0f
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) tilt = -15f
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) tilt = 15f
        canvas.rotate(tilt, x, y)

        paint.style = Paint.Style.FILL
        
        // Colors based on ship type
        val primaryColor = when(currentShipType) {
            1 -> Color.parseColor("#78909C") // Light Steel (Fast)
            2 -> Color.parseColor("#37474F") // Dark Metal (Tank)
            3 -> Color.parseColor("#E65100") // Orange (Solar Flare)
            4 -> Color.parseColor("#D84315") // Flame Orange (Phoenix)
            else -> Color.parseColor("#455A64") // Blue Gray (Balanced)
        }
        val accentColor = when(currentShipType) {
            1 -> Color.YELLOW // Fast
            2 -> Color.parseColor("#E91E63") // Tank
            3 -> Color.YELLOW // Solar Flare
            4 -> Color.parseColor("#FFD54F") // Phoenix
            else -> Color.CYAN // Balanced
        }

        paint.color = primaryColor
        playerPath.reset()
        
        // Shapes based on ship type
        when(currentShipType) {
            1 -> { // Fast - Sleek/Pointy
                playerPath.moveTo(x - 40, y + 20)
                playerPath.lineTo(x, y - 50)
                playerPath.lineTo(x + 40, y + 20)
                playerPath.lineTo(x, y + 5)
            }
            2 -> { // Tank - Bulky/Wide
                playerPath.moveTo(x - 70, y + 40)
                playerPath.lineTo(x - 60, y - 20)
                playerPath.lineTo(x, y - 40)
                playerPath.lineTo(x + 60, y - 20)
                playerPath.lineTo(x + 70, y + 40)
                playerPath.lineTo(x, y + 25)
            }
            3 -> { // Solar Flare - Trident shape
                playerPath.moveTo(x - 55, y + 30)
                playerPath.lineTo(x - 55, y - 30) // Side cannon left
                playerPath.lineTo(x - 35, y)
                playerPath.lineTo(x, y - 50) // Central nose
                playerPath.lineTo(x + 35, y)
                playerPath.lineTo(x + 55, y - 30) // Side cannon right
                playerPath.lineTo(x + 55, y + 30)
                playerPath.lineTo(x, y + 15)
            }
            4 -> { // Phoenix - Winged shape
                playerPath.moveTo(x - 65, y + 30)
                playerPath.lineTo(x - 50, y - 10)
                playerPath.lineTo(x - 30, y - 15)
                playerPath.lineTo(x, y - 45) // Central nose
                playerPath.lineTo(x + 30, y - 15)
                playerPath.lineTo(x + 50, y - 10)
                playerPath.lineTo(x + 65, y + 30)
                playerPath.lineTo(x, y + 20)
            }
            else -> { // Balanced
                playerPath.moveTo(x - 60, y + 30)
                playerPath.lineTo(x, y - 20)
                playerPath.lineTo(x + 60, y + 30)
                playerPath.lineTo(x, y + 10)
            }
        }
        playerPath.close()
        canvas.drawPath(playerPath, paint)

        paint.color = accentColor
        playerPath.reset()
        when(currentShipType) {
            1 -> {
                playerPath.moveTo(x, y - 60)
                playerPath.lineTo(x - 10, y + 30)
                playerPath.lineTo(x + 10, y + 30)
            }
            2 -> {
                playerPath.moveTo(x, y - 50)
                playerPath.lineTo(x - 30, y + 40)
                playerPath.lineTo(x + 30, y + 40)
            }
            3 -> {
                playerPath.moveTo(x, y - 60)
                playerPath.lineTo(x - 15, y + 35)
                playerPath.lineTo(x + 15, y + 35)
            }
            4 -> {
                playerPath.moveTo(x, y - 55)
                playerPath.lineTo(x - 25, y + 35)
                playerPath.lineTo(x + 25, y + 35)
            }
            else -> {
                playerPath.moveTo(x, y - 50)
                playerPath.lineTo(x - 20, y + 40)
                playerPath.lineTo(x + 20, y + 40)
            }
        }
        playerPath.close()
        canvas.drawPath(playerPath, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(x, y - 10, 8f, paint)

        if ((System.currentTimeMillis() / 50) % 2 == 0L) {
            paint.color = Color.YELLOW
            val engineY = if (currentShipType == 2) 55f else 45f
            canvas.drawCircle(x, y + engineY, 10f, paint)
            paint.color = Color.RED
            canvas.drawCircle(x, y + engineY + 10, 6f, paint)
        }
        
        canvas.restore()
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        drawRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(drawRect, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 80f
        paint.color = Color.WHITE
        canvas.drawText(title, width / 2f, height / 2f, paint)
        paint.textSize = 35f
        val lines = sub.split("\n")
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 60f + i * 45f, paint)
        }
    }

    data class Bullet(var x: Float, var y: Float, val vy: Float, var vx: Float = 0f, val isEnemy: Boolean = false)
    enum class EnemyType { NORMAL, FAST, BIG, BOSS }
    data class Enemy(var x: Float, var y: Float, val type: EnemyType) {
        val size = when(type) { EnemyType.BOSS -> 120f; EnemyType.BIG -> 70f; EnemyType.FAST -> 35f; else -> 50f }
        var hp = when(type) { EnemyType.BOSS -> 30; EnemyType.BIG -> 6; else -> 1 }
        val color = when(type) { EnemyType.BOSS -> Color.parseColor("#9C27B0"); EnemyType.BIG -> Color.parseColor("#E91E63"); EnemyType.FAST -> Color.parseColor("#FFEB3B"); else -> Color.parseColor("#F44336") }
        var blinkUntil: Long = 0L
        var phase: Float = 0f
    }
    enum class PowerUpType { WEAPON, LIFE }
    data class PowerUp(val x: Float, var y: Float, val type: PowerUpType)
    class Particle(var x: Float, var y: Float, val color: Int, random: Random) {
        var vx = random.nextFloat() * 12 - 6
        var vy = random.nextFloat() * 12 - 6
        var life = 1.0f
        val size = random.nextFloat() * 6 + 2
        fun update() { x += vx; y += vy; life -= 0.03f }
    }
}
