package com.tdpham.games.flappy

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlin.random.Random

class FlappyHeroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "flappy_hero"
    override var onGameOver: ((Int) -> Unit)? = null
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
    private var pipeSpeed = 5f
    private var pipeSpawnTime = 0L
    private val pipeInterval = 2500L

    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private var lastUpdate = 0L
    private val beakPath = Path()
    private val sunsetPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gamePaused && !gameOver) {
                update()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    // Themes & Stages
    private enum class FlappyTheme { DAY, NIGHT, SUNSET, WINTER }
    private var currentTheme = FlappyTheme.DAY
    private val particles = mutableListOf<GameEnvironment.Particle>()
    private val random = java.util.Random()
    
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameLoop)
    }

    override fun resetGame() {
        birdY = height / 2f
        birdV = 0f
        pipes.clear()
        clouds.clear()
        celebrationManager.start(0f, 0f)
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
        pipeSpawnTime = 0L
        
        // Random Theme Selection
        currentTheme = FlappyTheme.entries.random()
        particles.clear()
        if (currentTheme == FlappyTheme.WINTER) {
            repeat(30) { 
                particles.add(GameEnvironment.Particle(random.nextFloat() * 2000, random.nextFloat() * 1000, random.nextFloat() * 5 + 2, random.nextFloat() * 2 - 1)) 
            }
        }
        
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (gameOver) {
                resetGame()
                resume()
                return true
            }
            if (gamePaused) {
                resume()
                return true
            }

            birdV = jump
            SoundManager.playClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val bgType = when (currentTheme) {
            FlappyTheme.NIGHT -> GameEnvironment.BackgroundType.STARRY
            FlappyTheme.WINTER -> GameEnvironment.BackgroundType.SOLID
            FlappyTheme.SUNSET -> GameEnvironment.BackgroundType.GRADIENT
            else -> GameEnvironment.BackgroundType.GRADIENT
        }
        
        val weather = if (currentTheme == FlappyTheme.WINTER) GameEnvironment.WeatherType.SNOW else GameEnvironment.WeatherType.NONE
        val isNight = currentTheme == FlappyTheme.NIGHT
        
        if (currentTheme == FlappyTheme.SUNSET) {
            // Special sunset draw
            val colors = intArrayOf(Color.parseColor("#FF5722"), Color.parseColor("#3F51B5"))
            sunsetPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sunsetPaint)
        } else if (currentTheme == FlappyTheme.WINTER) {
             canvas.drawColor(Color.parseColor("#E1F5FE")) // Light Blue Sky
        } else {
            GameEnvironment.draw(canvas, bgType, isNight = isNight, weather = weather, paint = paint, particles = particles)
        }
        
        super.onDraw(canvas)

        // update() - Moved to gameLoop

        // Draw Clouds (not in Night/Winter)
        if (currentTheme == FlappyTheme.DAY || currentTheme == FlappyTheme.SUNSET) {
            paint.color = Color.argb(150, 255, 255, 255)
            for (cloud in clouds) {
                canvas.drawCircle(cloud.x, cloud.y, 30f * cloud.scale, paint)
                canvas.drawCircle(cloud.x + 20f * cloud.scale, cloud.y - 10f * cloud.scale, 25f * cloud.scale, paint)
                canvas.drawCircle(cloud.x + 40f * cloud.scale, cloud.y, 30f * cloud.scale, paint)
            }
        }

        // Draw Pipes
        val pipeColor = when(currentTheme) {
            FlappyTheme.NIGHT -> "#455A64"
            FlappyTheme.SUNSET -> "#795548"
            FlappyTheme.WINTER -> "#0288D1"
            else -> "#388E3C"
        }
        val pipeColorDark = when(currentTheme) {
            FlappyTheme.NIGHT -> "#263238"
            FlappyTheme.WINTER -> "#01579B"
            else -> "#2E7D32"
        }
        
        for (pipe in pipes) {
            // Draw pipe body
            paint.color = Color.parseColor(pipeColor)
            canvas.drawRect(pipe.x + 10f, 0f, pipe.x + 90f, pipe.gapY, paint)
            canvas.drawRect(pipe.x + 10f, pipe.gapY + pipe.gapH, pipe.x + 90f, height.toFloat(), paint)

            // Draw pipe shading
            paint.color = Color.parseColor(pipeColorDark)
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
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        val hudY = Math.round(60f).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 40f, hudY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)

        if (gameOver) {
            celebrationManager.update()
            celebrationManager.draw(canvas)
            invalidate()
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.crashed_label)
            drawOverlay(canvas, title, "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (gamePaused) {
            drawOverlay(canvas, context.getString(R.string.game_flappy), context.getString(R.string.flap_hint))
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
            val gapH = height * 0.35f
            val minY = 100f
            val maxY = height - gapH - 100f
            val gapY = if (maxY > minY) {
                Random.nextFloat() * (maxY - minY) + minY
            } else {
                height / 2f - gapH / 2f
            }
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
        val oldBest = best
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
        if (isNewHigh) {
            best = score
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
        } else {
            currentVictoryWord = ""
        }
        celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = false, isNewHigh = isNewHigh, score = score, highScore = oldBest)
        SoundManager.playError()
        onGameOver?.invoke(score)
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
