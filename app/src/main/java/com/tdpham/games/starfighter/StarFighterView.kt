package com.tdpham.games.starfighter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import java.util.*

class StarFighterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "starfighter"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var isPaused = true

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

    // Reuse objects to avoid allocations in onDraw
    private val tempRect = RectF()
    private val playerPath = Path()
    private val tempPath = Path()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        isPaused = false
        invalidate()
    }

    override fun pause() { isPaused = true }
    override fun resume() { isPaused = false; invalidate() }

    override fun resetGame() {
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        isPaused = true
        gunLevel = 1
        lives = 3
        invulnerableUntil = 0L
        powerUpEndTime = 0L
        bullets.clear()
        enemies.clear()
        powerUps.clear()
        particles.clear()
        bgParticles.clear()
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

        val step = 45f
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> playerX = (playerX - step).coerceAtLeast(playerSize)
            KeyEvent.KEYCODE_DPAD_RIGHT -> playerX = (playerX + step).coerceAtMost(width - playerSize)
            KeyEvent.KEYCODE_DPAD_UP -> playerY = (playerY - step).coerceAtLeast(playerSize)
            KeyEvent.KEYCODE_DPAD_DOWN -> playerY = (playerY + step).coerceAtMost(height - playerSize)
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun update() {
        if (isPaused || gameOver) return

        val now = System.currentTimeMillis()
        
        // Spawn Enemies
        if (now - lastEnemySpawn > (1200 - (score / 200) * 50).coerceAtLeast(600)) {
            val type = if (random.nextFloat() < 0.15) EnemyType.FAST else if (random.nextFloat() < 0.1) EnemyType.BIG else EnemyType.NORMAL
            enemies.add(Enemy(random.nextFloat() * (width - 150) + 75, -100f, type))
            lastEnemySpawn = now
        }

        // Auto-shoot
        val shootInterval = if (gunLevel >= 3) 250 else 350
        if (now - lastShoot > shootInterval) {
            if (now > powerUpEndTime) {
                gunLevel = 1
            }
            
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
                else -> { // Level 4+
                    bullets.add(Bullet(playerX, playerY - 40, -22f))
                    bullets.add(Bullet(playerX - 20, playerY - 35, -22f))
                    bullets.add(Bullet(playerX + 20, playerY - 35, -22f))
                    bullets.add(Bullet(playerX - 40, playerY - 10, -22f, -4f))
                    bullets.add(Bullet(playerX + 40, playerY - 10, -22f, 4f))
                }
            }
            lastShoot = now
        }

        // Update bullets
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
                // Check collision with player
                if (now > invulnerableUntil && RectF(playerX - 25, playerY - 25, playerX + 25, playerY + 25).contains(b.x, b.y)) {
                    playerHit()
                    bIter.remove()
                    if (gameOver) return
                    continue
                }
            }
        }

        // Update power-ups
        val pUpIter = powerUps.iterator()
        while (pUpIter.hasNext()) {
            val pu = pUpIter.next()
            pu.y += 4f
            if (pu.y > height + 50) {
                pUpIter.remove()
            } else if (Math.abs(pu.x - playerX) < 50 && Math.abs(pu.y - playerY) < 50) {
                when(pu.type) {
                    PowerUpType.WEAPON -> {
                        gunLevel = (gunLevel + 1).coerceAtMost(4)
                        powerUpEndTime = now + 15000 // 15 seconds
                    }
                    PowerUpType.LIFE -> {
                        lives = (lives + 1).coerceAtMost(5)
                    }
                }
                SoundManager.playScore()
                pUpIter.remove()
            }
        }

        // Update enemies
        val eIter = enemies.iterator()
        while (eIter.hasNext()) {
            val e = eIter.next()
            val speedY = when(e.type) {
                EnemyType.FAST -> 8f
                EnemyType.BIG -> 2.5f
                else -> 4.5f
            }
            e.y += speedY

            // Hunting logic: Move towards player horizontally
            val huntSpeed = when(e.type) {
                EnemyType.FAST -> 4.5f
                EnemyType.BIG -> 1.5f
                else -> 3f
            }
            if (e.y > 0 && e.y < height * 0.8f) { // Only hunt while on screen
                if (e.x < playerX - 15) e.x += huntSpeed
                else if (e.x > playerX + 15) e.x -= huntSpeed
            }
            
            // Enemy shooting (Shoot back)
            val shootChance = when(e.type) {
                EnemyType.BIG -> 0.02f
                EnemyType.FAST -> 0.005f
                else -> 0.01f
            }
            if (random.nextFloat() < shootChance && e.y > 0 && e.y < height * 0.7f) {
                bullets.add(Bullet(e.x, e.y + e.size, 10f, 0f, true))
            }
            
            // Check collision with player
            if (now > invulnerableUntil && RectF(playerX - 35, playerY - 35, playerX + 35, playerY + 35).intersects(e.x - e.size * 0.8f, e.y - e.size * 0.8f, e.x + e.size * 0.8f, e.y + e.size * 0.8f)) {
                playerHit()
                spawnExplosion(e.x, e.y, e.color)
                eIter.remove()
                if (gameOver) break
                continue
            }

            // Check collision with bullets
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
                        // Reward system
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

        // Particles
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.update()
            if (p.life <= 0) pIter.remove()
        }
        
        invalidate()
    }

    private fun playerHit() {
        lives--
        spawnExplosion(playerX, playerY, Color.CYAN)
        SoundManager.playError()
        if (lives <= 0) {
            endGame()
        } else {
            invulnerableUntil = System.currentTimeMillis() + 2000 // 2s invulnerability
        }
    }

    private fun spawnExplosion(x: Float, y: Float, color: Int) {
        repeat(20) { particles.add(Particle(x, y, color, random)) }
    }

    private fun endGame() {
        gameOver = true
        ScoreManager.updateHighScore(context, gameKey, score)
    }

    override fun onDraw(canvas: Canvas) {
        if (playerX == 0f) { playerX = width / 2f; playerY = height - 250f }
        
        update()
        GameEnvironment.draw(canvas, bgType, isNight = true, paint = paint, particles = bgParticles)

        // Particles
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

        // Bullets
        bullets.forEach {
            paint.color = if (it.isEnemy) Color.RED else Color.CYAN
            paint.style = Paint.Style.FILL
            canvas.drawRect(it.x - 3, it.y - 12, it.x + 3, it.y + 12, paint)
            // Glow
            paint.alpha = 100
            canvas.drawCircle(it.x, it.y, 8f, paint)
            paint.alpha = 255
        }

        // Power-ups
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

        // Enemies
        enemies.forEach { e ->
            paint.style = Paint.Style.FILL
            paint.color = e.color
            when (e.type) {
                EnemyType.BIG -> {
                    tempRect.set(e.x - e.size, e.y - e.size, e.x + e.size, e.y + e.size)
                    canvas.drawRoundRect(tempRect, 15f, 15f, paint)
                    paint.color = Color.BLACK
                    canvas.drawRect(e.x - 20, e.y - 10, e.x - 10, e.y + 10, paint)
                    canvas.drawRect(e.x + 10, e.y - 10, e.x + 20, e.y + 10, paint)
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
                    canvas.drawCircle(e.x - 10, e.y - 5, 5f, paint)
                    canvas.drawCircle(e.x + 10, e.y - 5, 5f, paint)
                }
            }
        }

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 40f, 60f, paint)
        
        // Lives
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.RED
        val livesStr = "❤".repeat(lives)
        canvas.drawText(livesStr, 40f, 110f, paint)

        if (gameOver) drawOverlay(canvas, "MISSION FAILED", "Score: $score\nPress Center to Restart")
        else if (isPaused) drawOverlay(canvas, "STAR FIGHTER", "Use DPAD to move\nPress Center to Start")
    }

    private fun drawPlayerShip(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        // Wings
        paint.color = Color.parseColor("#455A64")
        playerPath.reset()
        playerPath.moveTo(x - 60, y + 30)
        playerPath.lineTo(x, y - 20)
        playerPath.lineTo(x + 60, y + 30)
        playerPath.lineTo(x, y + 10)
        playerPath.close()
        canvas.drawPath(playerPath, paint)

        // Body
        paint.color = Color.CYAN
        playerPath.reset()
        playerPath.moveTo(x, y - 50)
        playerPath.lineTo(x - 20, y + 40)
        playerPath.lineTo(x + 20, y + 40)
        playerPath.close()
        canvas.drawPath(playerPath, paint)

        // Cockpit
        paint.color = Color.WHITE
        canvas.drawCircle(x, y - 10, 8f, paint)

        // Thruster flame
        if ((System.currentTimeMillis() / 50) % 2 == 0L) {
            paint.color = Color.YELLOW
            canvas.drawCircle(x, y + 45, 10f, paint)
            paint.color = Color.RED
            canvas.drawCircle(x, y + 55, 6f, paint)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
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
