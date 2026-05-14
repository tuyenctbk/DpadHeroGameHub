package com.tdpham.games.starfighter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import java.util.*

class StarFighterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView, Choreographer.FrameCallback {

    override var gameKey: String = "star_fighter"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()

    // Game state
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var isPaused = true

    // Player
    private var playerX = 0f
    private var playerY = 0f
    private val playerSize = 80f
    private val playerSpeed = 15f
    private var lastShotTime = 0L
    private val shotDelay = 250L

    // Entities
    private val bullets = mutableListOf<Bullet>()
    private val enemies = mutableListOf<Enemy>()
    private val particles = mutableListOf<Particle>()
    private val stars = mutableListOf<Star>()

    private val pressedKeys = mutableSetOf<Int>()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        requestFocus()
        isPaused = false
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        if (!gameOver) {
            isPaused = false
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun resetGame() {
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        isPaused = true
        bullets.clear()
        enemies.clear()
        particles.clear()
        stars.clear()
        repeat(100) {
            stars.add(Star(random.nextFloat() * 2000, random.nextFloat() * 2000, random.nextFloat() * 3 + 1))
        }
        playerX = -1f // Will be set in onSizeChanged
        invalidate()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun doFrame(frameTimeNanos: Long) {
        if (!isPaused && !gameOver) {
            update()
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun update() {
        // Star background
        stars.forEach { 
            it.y += it.speed
            if (it.y > height) {
                it.y = -10f
                it.x = random.nextFloat() * width
            }
        }

        // Player Movement
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) playerX = (playerX - playerSpeed * 3).coerceAtLeast(playerSize)
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) playerX = (playerX + playerSpeed * 3).coerceAtMost(width - playerSize)
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP)) playerY = (playerY - playerSpeed).coerceAtLeast(height * 0.5f)
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)) playerY = (playerY + playerSpeed).coerceAtMost(height - playerSize * 2)

        // Shooting
        val now = System.currentTimeMillis()
        if (now - lastShotTime > shotDelay) {
            bullets.add(Bullet(playerX, playerY - playerSize, -20f))
            lastShotTime = now
            SoundManager.playClick()
        }

        // Bullets
        val bIterator = bullets.iterator()
        while (bIterator.hasNext()) {
            val b = bIterator.next()
            b.y += b.vy
            if (b.y < -50) bIterator.remove()
        }

        // Spawn Enemies
        if (random.nextFloat() < 0.03 + (score / 10000.0)) {
            enemies.add(Enemy(random.nextFloat() * (width - 100) + 50, -50f, 5f + random.nextFloat() * 5))
        }

        // Enemies
        val eIterator = enemies.iterator()
        while (eIterator.hasNext()) {
            val e = eIterator.next()
            e.y += e.speed
            
            // Collision with Player
            if (RectF(playerX - playerSize/2, playerY - playerSize/2, playerX + playerSize/2, playerY + playerSize/2)
                .intersects(e.x - 40, e.y - 40, e.x + 40, e.y + 40)) {
                triggerExplosion(playerX, playerY, Color.YELLOW)
                gameOver = true
                SoundManager.playError()
                ScoreManager.updateHighScore(context, gameKey, score)
                break
            }

            // Collision with Bullets
            val bIter = bullets.iterator()
            var hit = false
            while (bIter.hasNext()) {
                val b = bIter.next()
                if (Math.abs(b.x - e.x) < 50 && Math.abs(b.y - e.y) < 50) {
                    hit = true
                    bIter.remove()
                    break
                }
            }

            if (hit) {
                triggerExplosion(e.x, e.y, Color.RED)
                score += 100
                SoundManager.playScore()
                eIterator.remove()
            } else if (e.y > height + 50) {
                eIterator.remove()
            }
        }

        // Particles
        val pIterator = particles.iterator()
        while (pIterator.hasNext()) {
            val p = pIterator.next()
            p.update()
            if (p.life <= 0) pIterator.remove()
        }
    }

    private fun triggerExplosion(x: Float, y: Float, color: Int) {
        repeat(15) {
            particles.add(Particle(x, y, color, random))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (playerX == -1f) {
            playerX = w / 2f
            playerY = h * 0.85f
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                startGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (isPaused && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resume()
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, 
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                pressedKeys.add(keyCode)
                return true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.remove(keyCode)
        return super.onKeyUp(keyCode, event)
    }

    private val playerPath = Path()

    override fun onDraw(canvas: Canvas) {
        // Background
        canvas.drawColor(Color.BLACK)
        
        // Stars
        paint.color = Color.WHITE
        stars.forEach { 
            paint.alpha = (it.speed * 60).toInt().coerceIn(50, 255)
            canvas.drawCircle(it.x, it.y, it.size, paint)
        }
        paint.alpha = 255

        // Particles
        particles.forEach { 
            paint.color = it.color
            paint.alpha = (it.life * 255).toInt()
            canvas.drawCircle(it.x, it.y, 5f, paint)
        }
        paint.alpha = 255

        if (!gameOver) {
            // Player Ship (Triangular with details)
            paint.color = Color.CYAN
            playerPath.reset()
            playerPath.moveTo(playerX, playerY - playerSize)
            playerPath.lineTo(playerX - playerSize / 2, playerY + playerSize / 2)
            playerPath.lineTo(playerX + playerSize / 2, playerY + playerSize / 2)
            playerPath.close()
            
            // Subtle glow for ship
            paint.setShadowLayer(15f, 0f, 0f, Color.CYAN)
            canvas.drawPath(playerPath, paint)
            paint.clearShadowLayer()

            // Ship cockpit/details
            paint.color = Color.parseColor("#1A237E") // Dark blue
            canvas.drawCircle(playerX, playerY - playerSize * 0.2f, playerSize * 0.15f, paint)
            
            // Engine flame (Animated flicker)
            val flameSize = 15f + random.nextFloat() * 10f
            paint.color = Color.parseColor("#FF9800") // Orange
            paint.setShadowLayer(20f, 0f, 0f, Color.YELLOW)
            canvas.drawCircle(playerX, playerY + playerSize / 2 + 5, flameSize, paint)
            paint.color = Color.YELLOW
            canvas.drawCircle(playerX, playerY + playerSize / 2 + 5, flameSize * 0.6f, paint)
            paint.clearShadowLayer()
        }

        // Bullets (With glow)
        paint.color = Color.YELLOW
        paint.setShadowLayer(10f, 0f, 0f, Color.YELLOW)
        bullets.forEach { canvas.drawRect(it.x - 4, it.y - 15, it.x + 4, it.y, paint) }
        paint.clearShadowLayer()

        // Enemies (Improved look)
        enemies.forEach { 
            // Body
            paint.color = Color.RED
            paint.setShadowLayer(10f, 0f, 0f, Color.RED)
            canvas.drawRect(it.x - 30, it.y - 30, it.x + 30, it.y + 30, paint)
            paint.clearShadowLayer()

            // Details/Eyes
            paint.color = Color.BLACK
            canvas.drawRect(it.x - 20, it.y - 20, it.x - 5, it.y - 5, paint)
            canvas.drawRect(it.x + 5, it.y - 20, it.x + 20, it.y - 5, paint)
            
            // Subtle highlight
            paint.color = Color.argb(80, 255, 255, 255)
            canvas.drawRect(it.x - 25, it.y - 25, it.x - 15, it.y - 15, paint)
        }

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 40f, 60f, paint)

        if (isPaused && !gameOver) {
            drawOverlay(canvas, "STAR FIGHTER", "Press Center to Start")
        } else if (gameOver) {
            drawOverlay(canvas, "MISSION FAILED", "Press Center to Restart")
        }
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

    data class Bullet(val x: Float, var y: Float, val vy: Float)
    data class Enemy(val x: Float, var y: Float, val speed: Float)
    data class Star(var x: Float, var y: Float, val speed: Float, val size: Float = speed/2)
    class Particle(var x: Float, var y: Float, val color: Int, random: Random) {
        var vx = random.nextFloat() * 10 - 5
        var vy = random.nextFloat() * 10 - 5
        var life = 1.0f
        fun update() {
            x += vx
            y += vy
            life -= 0.02f
        }
    }
}
