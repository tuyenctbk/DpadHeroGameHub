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

    private var playerX = 0f
    private var playerY = 0f
    private val playerSize = 70f
    private var lives = 3
    private var invulnerableUntil = 0L

    private val bullets = mutableListOf<Bullet>()
    private val enemies = mutableListOf<Enemy>()
    private val powerUps = mutableListOf<PowerUp>()
    private val particles = mutableListOf<Particle>()
    private val bgParticles = mutableListOf<GameEnvironment.Particle>()
    private val random = Random()

    private var lastEnemySpawn = 0L
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

    override fun resetGame() {
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        isPaused = true
        celebrationManager.start(0f, 0f)
        gunLevel = 1
        lives = 3
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
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
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

        val step = 12f
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) playerX = (playerX - step).coerceAtLeast(playerSize)
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) playerX = (playerX + step).coerceAtMost(width - playerSize)
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP)) playerY = (playerY - step).coerceAtLeast(playerSize)
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)) playerY = (playerY + step).coerceAtMost(height - playerSize)

        val now = System.currentTimeMillis()
        
        if (now - lastEnemySpawn > (1200 - (score / 200) * 50).coerceAtLeast(600)) {
            val type = if (random.nextFloat() < 0.15) EnemyType.FAST else if (random.nextFloat() < 0.1) EnemyType.BIG else EnemyType.NORMAL
            enemies.add(Enemy(random.nextFloat() * (width - 150) + 75, -100f, type))
            lastEnemySpawn = now
        }

        val shootInterval = if (gunLevel >= 3) 250 else 350
        if (now - lastShoot > shootInterval) {
            if (now > powerUpEndTime) gunLevel = 1
            
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
            val speedY = when(e.type) { EnemyType.FAST -> 8f; EnemyType.BIG -> 2.5f; else -> 4.5f }
            e.y += speedY

            val huntSpeed = when(e.type) { EnemyType.FAST -> 4.5f; EnemyType.BIG -> 1.5f; else -> 3f }
            if (e.y > 0 && e.y < height * 0.8f) {
                if (e.x < playerX - 15) e.x += huntSpeed
                else if (e.x > playerX + 15) e.x -= huntSpeed
            }
            
            val shootChance = when(e.type) { EnemyType.BIG -> 0.02f; EnemyType.FAST -> 0.005f; else -> 0.01f }
            if (random.nextFloat() < shootChance && e.y > 0 && e.y < height * 0.7f) {
                bullets.add(Bullet(e.x, e.y + e.size, 10f, 0f, true))
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
                    bIter2.remove()
                    if (e.hp <= 0) {
                        spawnExplosion(e.x, e.y, e.color)
                        score += when(e.type) { EnemyType.BIG -> 100; EnemyType.FAST -> 50; else -> 20 }
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
        screenShake.trigger(12, 20f)
        spawnExplosion(playerX, playerY, Color.CYAN)
        SoundManager.playError()
        if (lives <= 0) {
            endGame()
        } else {
            invulnerableUntil = System.currentTimeMillis() + 2000
        }
    }

    private fun spawnExplosion(x: Float, y: Float, color: Int) {
        repeat(20) { particles.add(Particle(x, y, color, random)) }
    }

    private fun endGame() {
        gameOver = true
        val oldBest = best
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
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
        val needsInvalidate = screenShake.apply(canvas)
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
            paint.color = e.color
            when (e.type) {
                EnemyType.BIG -> {
                    drawRect.set(e.x - e.size, e.y - e.size, e.x + e.size, e.y + e.size)
                    canvas.drawRoundRect(drawRect, 15f, 15f, paint)
                    paint.color = Color.BLACK
                    paint.alpha = 100
                    drawRect.set(e.x - 20, e.y - 10, e.x - 10, e.y + 10)
                    canvas.drawRect(drawRect, paint)
                    drawRect.set(e.x + 10, e.y - 10, e.x + 20, e.y + 10)
                    canvas.drawRect(drawRect, paint)
                    paint.alpha = 255
                }
                EnemyType.FAST -> {
                    tempPath.reset()
                    tempPath.moveTo(e.x, e.y + e.size)
                    tempPath.lineTo(e.x - e.size, e.y - e.size)
                    tempPath.lineTo(e.x + e.size, e.y - e.size)
                    tempPath.close()
                    canvas.drawPath(tempPath, paint)
                }
                else -> {
                    canvas.drawCircle(e.x, e.y, e.size, paint)
                    paint.color = Color.WHITE
                    paint.alpha = 150
                    canvas.drawCircle(e.x - 10, e.y - 5, 5f, paint)
                    canvas.drawCircle(e.x + 10, e.y - 5, 5f, paint)
                    paint.alpha = 255
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
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)
        
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.RED
        val livesStr = "❤ ".repeat(lives)
        canvas.drawText(livesStr, 40f, Math.round(110f).toFloat(), paint)

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
        paint.color = Color.parseColor("#455A64")
        playerPath.reset()
        playerPath.moveTo(x - 60, y + 30)
        playerPath.lineTo(x, y - 20)
        playerPath.lineTo(x + 60, y + 30)
        playerPath.lineTo(x, y + 10)
        playerPath.close()
        canvas.drawPath(playerPath, paint)

        paint.color = Color.CYAN
        playerPath.reset()
        playerPath.moveTo(x, y - 50)
        playerPath.lineTo(x - 20, y + 40)
        playerPath.lineTo(x + 20, y + 40)
        playerPath.close()
        canvas.drawPath(playerPath, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(x, y - 10, 8f, paint)

        if ((System.currentTimeMillis() / 50) % 2 == 0L) {
            paint.color = Color.YELLOW
            canvas.drawCircle(x, y + 45, 10f, paint)
            paint.color = Color.RED
            canvas.drawCircle(x, y + 55, 6f, paint)
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
    enum class EnemyType { NORMAL, FAST, BIG }
    data class Enemy(var x: Float, var y: Float, val type: EnemyType) {
        val size = when(type) { EnemyType.BIG -> 70f; EnemyType.FAST -> 35f; else -> 50f }
        var hp = when(type) { EnemyType.BIG -> 6; else -> 1 }
        val color = when(type) { EnemyType.BIG -> Color.parseColor("#E91E63"); EnemyType.FAST -> Color.parseColor("#FFEB3B"); else -> Color.parseColor("#F44336") }
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
