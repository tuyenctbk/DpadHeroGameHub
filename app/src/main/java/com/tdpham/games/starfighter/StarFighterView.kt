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
    private val playerSize = 60f
    private val playerPath = Path()

    private val bullets = mutableListOf<Bullet>()
    private val enemies = mutableListOf<Enemy>()
    private val powerUps = mutableListOf<PowerUp>()
    private val particles = mutableListOf<Particle>()
    private val bgParticles = mutableListOf<GameEnvironment.Particle>()
    private val random = Random()

    private var lastEnemySpawn = 0L
    private var lastShoot = 0L
    private var gunLevel = 3
    private var powerUpEndTime = 0L
    private var bgType = GameEnvironment.BackgroundType.GRADIENT

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
        gunLevel = 3
        powerUpEndTime = 0L
        bullets.clear()
        enemies.clear()
        powerUps.clear()
        particles.clear()
        bgParticles.clear()
        bgType = listOf(GameEnvironment.BackgroundType.GRADIENT, GameEnvironment.BackgroundType.STARRY, GameEnvironment.BackgroundType.SOLID).random()
        repeat(50) {
            bgParticles.add(GameEnvironment.Particle(random.nextFloat() * 2000, random.nextFloat() * 2000, random.nextFloat() * 5 + 2))
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
            KeyEvent.KEYCODE_DPAD_LEFT -> playerX = (playerX - 40).coerceAtLeast(playerSize)
            KeyEvent.KEYCODE_DPAD_RIGHT -> playerX = (playerX + 40).coerceAtMost(width - playerSize)
            KeyEvent.KEYCODE_DPAD_UP -> playerY = (playerY - 40).coerceAtLeast(playerSize)
            KeyEvent.KEYCODE_DPAD_DOWN -> playerY = (playerY + 40).coerceAtMost(height - playerSize)
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
        if (now - lastEnemySpawn > (1000 - (score / 100) * 50).coerceAtLeast(400)) {
            val type = if (random.nextFloat() < 0.2) EnemyType.FAST else if (random.nextFloat() < 0.1) EnemyType.BIG else EnemyType.NORMAL
            enemies.add(Enemy(random.nextFloat() * (width - 100) + 50, -50f, type))
            lastEnemySpawn = now
        }

        // Auto-shoot
        if (now - lastShoot > 300) {
            if (now > powerUpEndTime) {
                gunLevel = 3
            }
            
            when (gunLevel) {
                3 -> {
                    bullets.add(Bullet(playerX, playerY - 30, -18f))
                    bullets.add(Bullet(playerX - 20, playerY - 10, -18f))
                    bullets.add(Bullet(playerX + 20, playerY - 10, -18f))
                }
                5 -> {
                    bullets.add(Bullet(playerX, playerY - 30, -18f))
                    bullets.add(Bullet(playerX - 20, playerY - 10, -18f))
                    bullets.add(Bullet(playerX + 20, playerY - 10, -18f))
                    bullets.add(Bullet(playerX - 40, playerY + 10, -18f))
                    bullets.add(Bullet(playerX + 40, playerY + 10, -18f))
                }
                7 -> {
                    bullets.add(Bullet(playerX, playerY - 30, -18f))
                    bullets.add(Bullet(playerX - 20, playerY - 10, -18f))
                    bullets.add(Bullet(playerX + 20, playerY - 10, -18f))
                    bullets.add(Bullet(playerX - 40, playerY + 10, -18f))
                    bullets.add(Bullet(playerX + 40, playerY + 10, -18f))
                    bullets.add(Bullet(playerX - 60, playerY + 30, -18f))
                    bullets.add(Bullet(playerX + 60, playerY + 30, -18f))
                }
            }
            lastShoot = now
        }

        // Update bullets
        val bIter = bullets.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            b.y += b.vy
            if (b.y < -50 || b.y > height + 50) {
                bIter.remove()
                continue
            }

            if (b.isEnemy) {
                // Check collision with player
                if (RectF(playerX - 25, playerY - 25, playerX + 25, playerY + 25).contains(b.x, b.y)) {
                    endGame()
                    return
                }
            }
        }

        // Update power-ups
        val pUpIter = powerUps.iterator()
        while (pUpIter.hasNext()) {
            val pu = pUpIter.next()
            pu.y += 5f
            if (pu.y > height + 50) {
                pUpIter.remove()
            } else if (Math.abs(pu.x - playerX) < 40 && Math.abs(pu.y - playerY) < 40) {
                gunLevel = if (gunLevel < 7) gunLevel + 2 else 7
                powerUpEndTime = now + 10000 // 10 seconds
                SoundManager.playScore()
                pUpIter.remove()
            }
        }

        // Update enemies
        val eIter = enemies.iterator()
        while (eIter.hasNext()) {
            val e = eIter.next()
            val speed = when(e.type) {
                EnemyType.FAST -> 12f
                EnemyType.BIG -> 4f
                else -> 7f
            }
            e.y += speed
            
            // Enemy shooting
            if (now - e.lastShoot > 2000) {
                bullets.add(Bullet(e.x, e.y + e.size, 10f, isEnemy = true))
                e.lastShoot = now
            }
            
            // Check collision with player
            if (RectF(playerX - 30, playerY - 30, playerX + 30, playerY + 30).intersects(e.x - e.size, e.y - e.size, e.x + e.size, e.y + e.size)) {
                endGame()
                break
            }

            // Check collision with bullets
            val bIter2 = bullets.iterator()
            var destroyed = false
            while (bIter2.hasNext()) {
                val b = bIter2.next()
                if (Math.abs(b.x - e.x) < e.size && Math.abs(b.y - e.y) < e.size) {
                    e.hp--
                    bIter2.remove()
                    if (e.hp <= 0) {
                        spawnExplosion(e.x, e.y, e.color)
                        score += when(e.type) { EnemyType.BIG -> 50; EnemyType.FAST -> 30; else -> 10 }
                        if (random.nextFloat() < 0.15) {
                            powerUps.add(PowerUp(e.x, e.y))
                        }
                        SoundManager.playScore()
                        destroyed = true
                    }
                    break
                }
            }
            if (destroyed) eIter.remove()
            else if (e.y > height + 100) eIter.remove()
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

    private fun spawnExplosion(x: Float, y: Float, color: Int) {
        repeat(15) { particles.add(Particle(x, y, color, random)) }
    }

    private fun endGame() {
        gameOver = true
        SoundManager.playError()
        ScoreManager.updateHighScore(context, gameKey, score)
    }

    override fun onDraw(canvas: Canvas) {
        if (playerX == 0f) { playerX = width / 2f; playerY = height - 200f }
        
        update()
        GameEnvironment.draw(canvas, bgType, isNight = true, paint = paint, particles = bgParticles)

        // Particles
        particles.forEach { 
            paint.color = it.color
            paint.alpha = (it.life * 255).toInt()
            canvas.drawCircle(it.x, it.y, 5f, paint)
        }
        paint.alpha = 255

        if (!gameOver) {
            // Player Ship
            paint.color = Color.CYAN
            playerPath.reset()
            playerPath.moveTo(playerX, playerY - playerSize)
            playerPath.lineTo(playerX - playerSize / 2, playerY + playerSize / 2)
            playerPath.lineTo(playerX + playerSize / 2, playerY + playerSize / 2)
            playerPath.close()
            canvas.drawPath(playerPath, paint)
            
            // Cockpit
            paint.color = Color.WHITE
            canvas.drawCircle(playerX, playerY, 10f, paint)
        }

        // Bullets
        bullets.forEach {
            paint.color = if (it.isEnemy) Color.RED else Color.YELLOW
            canvas.drawRect(it.x - 4, it.y - 15, it.x + 4, it.y, paint)
        }

        // Power-ups
        powerUps.forEach {
            paint.color = Color.GREEN
            canvas.drawCircle(it.x, it.y, 15f, paint)
            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("P", it.x, it.y + 7, paint)
        }

        // Enemies
        enemies.forEach { 
            paint.color = it.color
            if (it.type == EnemyType.BIG) {
                canvas.drawRoundRect(it.x - it.size, it.y - it.size, it.x + it.size, it.y + it.size, 10f, 10f, paint)
            } else {
                canvas.drawCircle(it.x, it.y, it.size, paint)
            }
        }

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 40f, 60f, paint)

        if (gameOver) drawOverlay(canvas, "MISSION FAILED", "Press Center to Restart")
        else if (isPaused) drawOverlay(canvas, "STAR FIGHTER", "Press Center to Start")
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 80f
        paint.color = Color.WHITE
        canvas.drawText(title, width / 2f, height / 2f, paint)
        paint.textSize = 30f
        canvas.drawText(sub, width / 2f, height / 2f + 60f, paint)
    }

    data class Bullet(val x: Float, var y: Float, val vy: Float, val isEnemy: Boolean = false)
    enum class EnemyType { NORMAL, FAST, BIG }
    data class Enemy(val x: Float, var y: Float, val type: EnemyType) {
        val size = when(type) { EnemyType.BIG -> 60f; EnemyType.FAST -> 25f; else -> 40f }
        var hp = when(type) { EnemyType.BIG -> 5; else -> 1 }
        val color = when(type) { EnemyType.BIG -> Color.MAGENTA; EnemyType.FAST -> Color.YELLOW; else -> Color.RED }
        var lastShoot = System.currentTimeMillis() + (Math.random() * 1000).toLong()
    }
    data class PowerUp(val x: Float, var y: Float)
    class Particle(var x: Float, var y: Float, val color: Int, random: Random) {
        var vx = random.nextFloat() * 10 - 5
        var vy = random.nextFloat() * 10 - 5
        var life = 1.0f
        fun update() { x += vx; y += vy; life -= 0.04f }
    }
}
