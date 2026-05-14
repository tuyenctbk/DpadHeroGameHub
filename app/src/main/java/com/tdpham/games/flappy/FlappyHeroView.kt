package com.tdpham.games.flappy

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import kotlin.random.Random

class FlappyHeroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "flappy_hero"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var birdY = 0f
    private var birdV = 0f
    private val gravity = 0.8f
    private val jump = -15f
    private val birdSize = 40f
    private var wingFrame = 0
    private var frameCount = 0

    private val pipes = mutableListOf<Pipe>()
    private val clouds = mutableListOf<Cloud>()
    private var pipeSpeed = 8f
    private var pipeSpawnTime = 0L
    private val pipeInterval = 1500L

    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true
    private var lastUpdate = 0L
    private val beakPath = Path()

    data class Pipe(var x: Float, val gapY: Float, val gapH: Float, var passed: Boolean = false)
    data class Cloud(var x: Float, var y: Float, var speed: Float, var scale: Float)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        requestFocus()
        gamePaused = false
        invalidate()
    }

    override fun pause() { gamePaused = true }
    override fun resume() { gamePaused = false; invalidate() }
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        birdY = height / 2f
        birdV = 0f
        pipes.clear()
        clouds.clear()
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
        pipeSpawnTime = 0L
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                resume()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (gamePaused && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resume()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            birdV = jump
            SoundManager.playClick()
            return true
        }
        
        if (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            toggleSound()
            return true
        }
        
        return super.onKeyDown(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#4FC3F7")) // Sky Blue

        if (!gamePaused && !gameOver) update()

        // Draw Clouds
        paint.color = Color.argb(150, 255, 255, 255)
        for (cloud in clouds) {
            canvas.drawCircle(cloud.x, cloud.y, 30f * cloud.scale, paint)
            canvas.drawCircle(cloud.x + 20f * cloud.scale, cloud.y - 10f * cloud.scale, 25f * cloud.scale, paint)
            canvas.drawCircle(cloud.x + 40f * cloud.scale, cloud.y, 30f * cloud.scale, paint)
        }

        // Draw Pipes
        for (pipe in pipes) {
            // Draw pipe body
            paint.color = Color.parseColor("#388E3C") // Pipe Green
            canvas.drawRect(pipe.x + 10f, 0f, pipe.x + 90f, pipe.gapY, paint)
            canvas.drawRect(pipe.x + 10f, pipe.gapY + pipe.gapH, pipe.x + 90f, height.toFloat(), paint)

            // Draw pipe shading
            paint.color = Color.parseColor("#2E7D32")
            canvas.drawRect(pipe.x + 70f, 0f, pipe.x + 85f, pipe.gapY, paint)
            canvas.drawRect(pipe.x + 70f, pipe.gapY + pipe.gapH, pipe.x + 85f, height.toFloat(), paint)

            paint.color = Color.parseColor("#4CAF50")
            canvas.drawRect(pipe.x + 15f, 0f, pipe.x + 25f, pipe.gapY, paint)
            canvas.drawRect(pipe.x + 15f, pipe.gapY + pipe.gapH, pipe.x + 25f, height.toFloat(), paint)

            // Draw pipe caps
            paint.color = Color.parseColor("#388E3C")
            canvas.drawRect(pipe.x, pipe.gapY - 40f, pipe.x + 100f, pipe.gapY, paint)
            canvas.drawRect(pipe.x, pipe.gapY + pipe.gapH, pipe.x + 100f, pipe.gapY + pipe.gapH + 40f, paint)
            
            // Cap outline
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#1B5E20")
            paint.strokeWidth = 4f
            canvas.drawRect(pipe.x, pipe.gapY - 40f, pipe.x + 100f, pipe.gapY, paint)
            canvas.drawRect(pipe.x, pipe.gapY + pipe.gapH, pipe.x + 100f, pipe.gapY + pipe.gapH + 40f, paint)
            paint.style = Paint.Style.FILL
        }

        // Draw Bird
        canvas.save()
        val rotation = (birdV * 3f).coerceIn(-30f, 90f)
        canvas.rotate(rotation, 150f, birdY)

        // Body
        paint.color = Color.YELLOW
        canvas.drawCircle(150f, birdY, birdSize, paint)
        
        // Wing
        paint.color = Color.WHITE
        if (wingFrame == 0) {
            canvas.drawOval(130f, birdY - 5f, 160f, birdY + 15f, paint)
        } else {
            canvas.drawOval(130f, birdY - 15f, 160f, birdY + 5f, paint)
        }

        // Eye
        paint.color = Color.WHITE
        canvas.drawCircle(170f, birdY - 10f, 12f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(175f, birdY - 10f, 5f, paint)

        // Beak
        paint.color = Color.parseColor("#FF9800") // Orange
        beakPath.reset()
        beakPath.moveTo(185f, birdY)
        beakPath.lineTo(170f, birdY - 10f)
        beakPath.lineTo(170f, birdY + 10f)
        beakPath.close()
        canvas.drawPath(beakPath, paint)

        canvas.restore()

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 40f, 60f, paint)

        if (gameOver) {
            drawOverlay(canvas, "CRASHED!", "Score: $score\nPress Center to Restart")
        } else if (gamePaused) {
            drawOverlay(canvas, "FLAPPY HERO", "Press Center to Flap")
        }

        if (!gamePaused && !gameOver) invalidate()
    }

    private fun update() {
        val now = System.currentTimeMillis()
        if (lastUpdate == 0L) lastUpdate = now
        lastUpdate = now
        frameCount++
        if (frameCount % 5 == 0) {
            wingFrame = (wingFrame + 1) % 2
        }

        // Physics
        birdV += gravity
        birdY += birdV

        if (birdY < 0 || birdY > height) {
            die()
        }

        // Clouds
        if (clouds.size < 5 && Random.nextFloat() < 0.01f) {
            clouds.add(Cloud(width.toFloat() + 100f, Random.nextFloat() * height * 0.5f, Random.nextFloat() * 2f + 1f, Random.nextFloat() * 1f + 0.5f))
        }
        val cIter = clouds.iterator()
        while (cIter.hasNext()) {
            val c = cIter.next()
            c.x -= c.speed
            if (c.x < -200f) cIter.remove()
        }

        // Pipes
        if (now - pipeSpawnTime > pipeInterval) {
            val gapH = 300f
            val gapY = Random.nextFloat() * (height - gapH - 200f) + 100f
            pipes.add(Pipe(width.toFloat(), gapY, gapH))
            pipeSpawnTime = now
        }

        val pIter = pipes.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x -= pipeSpeed
            
            if (p.x < -100) {
                pIter.remove()
                continue
            }

            // Collision
            if (150f + birdSize > p.x && 150f - birdSize < p.x + 100f) {
                if (birdY - birdSize < p.gapY || birdY + birdSize > p.gapY + p.gapH) {
                    die()
                }
            }

            // Score
            if (!p.passed && p.x < 150f) {
                p.passed = true
                score++
                SoundManager.playScore()
                if (score > best) {
                    best = score
                    ScoreManager.updateHighScore(context, gameKey, best)
                }
            }
        }
    }

    private fun die() {
        gameOver = true
        gamePaused = true
        SoundManager.playError()
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 80f
        paint.color = Color.WHITE
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        paint.textSize = 30f
        val lines = sub.split("\n")
        for (i in lines.indices) {
            canvas.drawText(lines[i], width / 2f, height / 2f + 40f + i * 40f, paint)
        }
    }
}
