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

    enum class Difficulty(val gapHMult: Float, val speed: Float, val interval: Long) {
        LEVEL_1(0.32f, 6.0f, 2400L),
        LEVEL_2(0.27f, 7.5f, 2000L),
        LEVEL_3(0.22f, 9.0f, 1600L),
        LEVEL_4(0.18f, 11.0f, 1300L),
        LEVEL_5(0.14f, 13.5f, 1000L)
    }

    enum class BirdCharacter(
        val gravityMult: Float,
        val jumpForce: Float,
        val bodyColor: Int,
        val wingColor: Int,
        val beakColor: Int
    ) {
        CLASSIC(1.0f, -15f, Color.parseColor("#FFEB3B"), Color.parseColor("#FBC02D"), Color.parseColor("#FF9800")),
        SWIFT(1.25f, -17.5f, Color.parseColor("#2196F3"), Color.parseColor("#0D47A1"), Color.parseColor("#FF5722")),
        HEAVY(1.5f, -19f, Color.parseColor("#F44336"), Color.parseColor("#B71C1C"), Color.parseColor("#FFEB3B")),
        FLOATY(0.7f, -12f, Color.parseColor("#E91E63"), Color.parseColor("#880E4F"), Color.parseColor("#00E5FF"))
    }

    private var currentDifficulty = Difficulty.LEVEL_2
    private var currentCharacter = BirdCharacter.CLASSIC
    private val PREFS_NAME = "flappy_hero_settings"
    private val KEY_DIFFICULTY = "difficulty_index"
    private val KEY_CHARACTER = "character_index"
    private var hintShowFrames = 0
    private var isInitialized = false

    private var birdY = 0f
    private var birdV = 0f
    private val gravity = 0.8f
    private val birdSize = 40f
    private var wingFrame = 0
    private var frameCount = 0

    private val pipes = mutableListOf<Pipe>()
    private val clouds = mutableListOf<Cloud>()
    private var pipeSpeed = 5f
    private var pipeSpawnTime = 0L
    private var pipeInterval = 2500L

    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true
    private var currentVictoryWord = ""
    private var deathReason = ""
    private enum class DeathType {
        NONE, CEILING, GROUND, PIPE, OVERHEAD_PIPE, GROUND_PIPE, CRATE, SPIDER, SPIKES, CLOUD, MINE
    }
    private var currentDeathType = DeathType.NONE
    private val seenObstacleTypes = mutableSetOf<ObstacleType>()
    private val celebrationManager = CelebrationManager()
    private val animHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || gamePaused) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }
    private var lastUpdate = 0L
    private val beakPath = Path()
    private val pipeRect = RectF()
    private val pipeCapRect = RectF()
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
    
    enum class ObstacleType {
        STANDARD, MOVING, BAT, CLOSING_GATE, WIND_ZONE, FALLING_STALACTITE, SPIKED_MINE
    }

    data class Pipe(
        var x: Float,
        var gapY: Float,
        val gapH: Float,
        var passed: Boolean = false,
        val type: ObstacleType = ObstacleType.STANDARD,
        var movingDirection: Int = 1,
        var initialGapY: Float = gapY,
        var triggered: Boolean = false
    )
    data class Cloud(var x: Float, var y: Float, var speed: Float, var scale: Float)
    data class TrailParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var alpha: Int, val color: Int)
    private val trailParticles = mutableListOf<TrailParticle>()
    private var bgScrollX = 0f

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

    override fun startGame() {
        requestFocus()
        resume()
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
        animHandler.removeCallbacks(animRunnable)
    }

    override fun resetGame() {
        // Load difficulty from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val diffIndex = prefs.getInt(KEY_DIFFICULTY, 1)
        currentDifficulty = Difficulty.entries[diffIndex.coerceIn(0, 4)]
        
        val charIndex = prefs.getInt(KEY_CHARACTER, 0)
        currentCharacter = BirdCharacter.entries[charIndex.coerceIn(0, 3)]
        
        pipeSpeed = currentDifficulty.speed
        pipeInterval = currentDifficulty.interval

        birdY = height / 2f
        birdV = 0f
        pipes.clear()
        clouds.clear()
        trailParticles.clear()
        bgScrollX = 0f
        celebrationManager.start(0f, 0f)
        score = 0
        best = ScoreManager.getHighScore(context, gameKey, currentDifficulty.ordinal)
        gameOver = false
        deathReason = ""
        currentDeathType = DeathType.NONE
        seenObstacleTypes.clear()
        gamePaused = true
        pipeSpawnTime = 0L
        
        // Theme level progression starts at DAY (similar to T-Rex)
        currentTheme = FlappyTheme.DAY
        particles.clear()
        
        hintShowFrames = 100
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                resume()
                birdV = currentCharacter.jumpForce
                SoundManager.playClick()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (gamePaused) {
                resume()
            }
            birdV = currentCharacter.jumpForce
            SoundManager.playClick()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_TAB || keyCode == KeyEvent.KEYCODE_O) {
            showOptions()
            return true
        }
        
        if (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            toggleSound()
            return true
        }
        
        return super.onKeyDown(keyCode, event)
    }

    private fun showOptions() {
        pause()
        FlappyHeroOptionsDialog.show(context) {
            resetGame()
        }
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
                birdV = currentCharacter.jumpForce
                SoundManager.playClick()
                return true
            }
            if (gamePaused) {
                resume()
            }

            birdV = currentCharacter.jumpForce
            SoundManager.playClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        if (hintShowFrames > 0) {
            hintShowFrames--
            invalidate()
        }

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
            sunsetPaint.shader = LinearGradient(0f, 0f, 0f, h, colors[0], colors[1], Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, sunsetPaint)
        } else if (currentTheme == FlappyTheme.WINTER) {
             canvas.drawColor(Color.parseColor("#E1F5FE")) // Light Blue Sky
        } else {
            GameEnvironment.draw(canvas, bgType, isNight = isNight, weather = weather, paint = paint, particles = particles)
        }
        
        super.onDraw(canvas)

        // Draw Parallax Hills / Mountains (inspired by T-Rex)
        if (currentTheme == FlappyTheme.WINTER) {
            // Snowy Mountains
            paint.color = Color.parseColor("#B2DFDB")
            val mPath1 = Path()
            val mScroll1 = (bgScrollX * 0.3f) % w
            mPath1.moveTo(-mScroll1, h)
            mPath1.lineTo(w * 0.25f - mScroll1, h * 0.55f)
            mPath1.lineTo(w * 0.5f - mScroll1, h)
            mPath1.lineTo(w * 0.75f - mScroll1, h * 0.5f)
            mPath1.lineTo(w - mScroll1, h)
            mPath1.lineTo(w * 1.25f - mScroll1, h * 0.55f)
            mPath1.lineTo(w * 1.5f - mScroll1, h)
            mPath1.lineTo(w * 1.75f - mScroll1, h * 0.5f)
            mPath1.lineTo(w * 2f - mScroll1, h)
            mPath1.close()
            canvas.drawPath(mPath1, paint)

            // Snowy caps
            paint.color = Color.WHITE
            val capPath = Path().apply {
                moveTo(w * 0.25f - mScroll1, h * 0.55f)
                lineTo(w * 0.21f - mScroll1, h * 0.62f)
                lineTo(w * 0.29f - mScroll1, h * 0.62f)
                close()
                moveTo(w * 0.75f - mScroll1, h * 0.5f)
                lineTo(w * 0.70f - mScroll1, h * 0.58f)
                lineTo(w * 0.80f - mScroll1, h * 0.58f)
                close()
                moveTo(w * 1.25f - mScroll1, h * 0.55f)
                lineTo(w * 1.21f - mScroll1, h * 0.62f)
                lineTo(w * 1.29f - mScroll1, h * 0.62f)
                close()
                moveTo(w * 1.75f - mScroll1, h * 0.5f)
                lineTo(w * 1.70f - mScroll1, h * 0.58f)
                lineTo(w * 1.80f - mScroll1, h * 0.58f)
                close()
            }
            canvas.drawPath(capPath, paint)
        } else {
            val hillColor1 = when (currentTheme) {
                FlappyTheme.NIGHT -> Color.parseColor("#101525")
                FlappyTheme.SUNSET -> Color.parseColor("#8E24AA")
                else -> Color.parseColor("#81C784")
            }
            val hillColor2 = when (currentTheme) {
                FlappyTheme.NIGHT -> Color.parseColor("#080A15")
                FlappyTheme.SUNSET -> Color.parseColor("#5E35B1")
                else -> Color.parseColor("#4CAF50")
            }

            // Draw Layer 1 Hills (Far, Slower Scroll)
            paint.color = hillColor1
            val path1 = Path()
            val scroll1 = (bgScrollX * 0.3f) % w
            path1.moveTo(0f, h)
            path1.lineTo(0f, h * 0.8f)
            path1.quadTo(w * 0.25f - scroll1, h * 0.72f, w * 0.5f - scroll1, h * 0.8f)
            path1.quadTo(w * 0.75f - scroll1, h * 0.68f, w - scroll1, h * 0.8f)
            path1.quadTo(w * 1.25f - scroll1, h * 0.72f, w * 1.5f - scroll1, h * 0.8f)
            path1.quadTo(w * 1.75f - scroll1, h * 0.68f, w * 2f - scroll1, h * 0.8f)
            path1.lineTo(w, h)
            path1.close()
            canvas.drawPath(path1, paint)

            // Draw Layer 2 Hills (Near, Faster Scroll)
            paint.color = hillColor2
            val path2 = Path()
            val scroll2 = (bgScrollX * 0.6f) % w
            path2.moveTo(0f, h)
            path2.lineTo(0f, h * 0.85f)
            path2.quadTo(w * 0.15f - scroll2, h * 0.8f, w * 0.3f - scroll2, h * 0.85f)
            path2.quadTo(w * 0.55f - scroll2, h * 0.77f, w * 0.8f - scroll2, h * 0.85f)
            path2.quadTo(w * 0.95f - scroll2, h * 0.82f, w - scroll2, h * 0.85f)
            path2.quadTo(w * 1.15f - scroll2, h * 0.8f, w * 1.3f - scroll2, h * 0.85f)
            path2.quadTo(w * 1.55f - scroll2, h * 0.77f, w * 1.8f - scroll2, h * 0.85f)
            path2.quadTo(w * 1.95f - scroll2, h * 0.82f, w * 2f - scroll2, h * 0.85f)
            path2.lineTo(w, h)
            path2.close()
            canvas.drawPath(path2, paint)
        }

        // Draw Clouds (not in Night/Winter)
        if (currentTheme == FlappyTheme.DAY || currentTheme == FlappyTheme.SUNSET) {
            paint.color = Color.argb(150, 255, 255, 255)
            for (cloud in clouds) {
                canvas.drawCircle(cloud.x, cloud.y, 30f * cloud.scale, paint)
                canvas.drawCircle(cloud.x + 20f * cloud.scale, cloud.y - 10f * cloud.scale, 25f * cloud.scale, paint)
                canvas.drawCircle(cloud.x + 40f * cloud.scale, cloud.y, 30f * cloud.scale, paint)
            }
        }

        // Draw Obstacles (10 Types)
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
            paint.style = Paint.Style.FILL

            when (pipe.type) {
                ObstacleType.STANDARD, ObstacleType.MOVING -> {
                    val px1 = pipe.x + 10f
                    val px2 = pipe.x + 90f
                    
                    // Draw Top Pipe
                    paint.color = Color.parseColor(pipeColor)
                    canvas.drawRect(px1, 0f, px2, pipe.gapY, paint)
                    paint.color = Color.parseColor("#81C784")
                    canvas.drawRect(px1 + 10f, 0f, px1 + 25f, pipe.gapY, paint)
                    paint.color = Color.parseColor(pipeColorDark)
                    canvas.drawRect(px2 - 20f, 0f, px2 - 5f, pipe.gapY, paint)
                    // Cap
                    paint.color = Color.parseColor(pipeColor)
                    canvas.drawRect(pipe.x, pipe.gapY - 40f, pipe.x + 100f, pipe.gapY, paint)
                    paint.color = Color.parseColor("#81C784")
                    canvas.drawRect(pipe.x + 10f, pipe.gapY - 40f, pipe.x + 25f, pipe.gapY, paint)
                    // Cap border
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.BLACK
                    paint.strokeWidth = 4f
                    canvas.drawRect(pipe.x, pipe.gapY - 40f, pipe.x + 100f, pipe.gapY, paint)
                    paint.style = Paint.Style.FILL
                    
                    // Draw Bottom Pipe
                    paint.color = Color.parseColor(pipeColor)
                    canvas.drawRect(px1, pipe.gapY + pipe.gapH, px2, h, paint)
                    paint.color = Color.parseColor("#81C784")
                    canvas.drawRect(px1 + 10f, pipe.gapY + pipe.gapH, px1 + 25f, h, paint)
                    paint.color = Color.parseColor(pipeColorDark)
                    canvas.drawRect(px2 - 20f, pipe.gapY + pipe.gapH, px2 - 5f, h, paint)
                    // Cap
                    paint.color = Color.parseColor(pipeColor)
                    canvas.drawRect(pipe.x, pipe.gapY + pipe.gapH, pipe.x + 100f, pipe.gapY + pipe.gapH + 40f, paint)
                    paint.color = Color.parseColor("#81C784")
                    canvas.drawRect(pipe.x + 10f, pipe.gapY + pipe.gapH, pipe.x + 25f, pipe.gapY + pipe.gapH + 40f, paint)
                    // Cap border
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.BLACK
                    paint.strokeWidth = 4f
                    canvas.drawRect(pipe.x, pipe.gapY + pipe.gapH, pipe.x + 100f, pipe.gapY + pipe.gapH + 40f, paint)
                    paint.style = Paint.Style.FILL
                }
                ObstacleType.BAT -> {
                    val cx = pipe.x + 50f
                    val cy = pipe.gapY
                    
                    // Wings
                    paint.color = Color.parseColor("#37474F")
                    val wingPath = Path()
                    val flapOffset = if (frameCount % 10 < 5) -20f else 20f
                    wingPath.moveTo(cx, cy)
                    wingPath.lineTo(cx - 40f, cy + flapOffset)
                    wingPath.lineTo(cx - 20f, cy + 10f)
                    wingPath.close()
                    canvas.drawPath(wingPath, paint)
                    wingPath.reset()
                    wingPath.moveTo(cx, cy)
                    wingPath.lineTo(cx + 40f, cy + flapOffset)
                    wingPath.lineTo(cx + 20f, cy + 10f)
                    wingPath.close()
                    canvas.drawPath(wingPath, paint)
                    
                    // Body
                    paint.color = Color.parseColor("#4A148C")
                    canvas.drawCircle(cx, cy, 18f, paint)
                    
                    // Ears
                    paint.color = Color.parseColor("#4A148C")
                    val earPath = Path().apply {
                        moveTo(cx - 12f, cy - 10f)
                        lineTo(cx - 18f, cy - 25f)
                        lineTo(cx - 6f, cy - 15f)
                        close()
                        moveTo(cx + 12f, cy - 10f)
                        lineTo(cx + 18f, cy - 25f)
                        lineTo(cx + 6f, cy - 15f)
                        close()
                    }
                    canvas.drawPath(earPath, paint)
                    
                    // Glowing Red Eyes
                    paint.color = Color.RED
                    canvas.drawCircle(cx - 6f, cy - 2f, 3f, paint)
                    canvas.drawCircle(cx + 6f, cy - 2f, 3f, paint)
                }
                ObstacleType.CLOSING_GATE -> {
                    val px1 = pipe.x + 10f
                    val px2 = pipe.x + 90f
                    val cycleVal = Math.sin(frameCount * 0.15).toFloat()
                    // Horizontal closure: jaws move inward from left/right sides of the obstacle width
                    val closureWidth = (cycleVal + 1f) * 35f 
                    
                    val leftJawRight = px1 + closureWidth
                    val rightJawLeft = px2 - closureWidth
                    
                    paint.color = Color.parseColor("#607D8B")
                    // Top part (static connector)
                    canvas.drawRect(px1, 0f, px2, pipe.gapY, paint)
                    // Bottom part (static connector)
                    canvas.drawRect(px1, pipe.gapY + pipe.gapH, px2, h, paint)
                    
                    // Left moving jaw
                    canvas.drawRect(px1, pipe.gapY, leftJawRight, pipe.gapY + pipe.gapH, paint)
                    // Right moving jaw
                    canvas.drawRect(rightJawLeft, pipe.gapY, px2, pipe.gapY + pipe.gapH, paint)
                    
                    // Warning stripe band (Vertical)
                    paint.color = Color.parseColor("#FFD54F")
                    canvas.drawRect(leftJawRight - 10f, pipe.gapY, leftJawRight, pipe.gapY + pipe.gapH, paint)
                    canvas.drawRect(rightJawLeft, pipe.gapY, rightJawLeft + 10f, pipe.gapY + pipe.gapH, paint)
                    
                    // Teeth (Horizontal)
                    paint.color = Color.parseColor("#B0BEC5")
                    val toothPath = Path()
                    for (y in (pipe.gapY.toInt())..(pipe.gapY + pipe.gapH - 20).toInt() step 20) {
                        toothPath.reset()
                        toothPath.moveTo(leftJawRight, y.toFloat())
                        toothPath.lineTo(leftJawRight + 15f, y + 10f)
                        toothPath.lineTo(leftJawRight, y + 20f)
                        toothPath.close()
                        canvas.drawPath(toothPath, paint)
                        
                        toothPath.reset()
                        toothPath.moveTo(rightJawLeft, y.toFloat())
                        toothPath.lineTo(rightJawLeft - 15f, y + 10f)
                        toothPath.lineTo(rightJawLeft, y + 20f)
                        toothPath.close()
                        canvas.drawPath(toothPath, paint)
                    }
                }
                ObstacleType.WIND_ZONE -> {
                    // Translucent Cyan draft background
                    paint.color = Color.argb(40, 0, 229, 255)
                    canvas.drawRect(pipe.x, 0f, pipe.x + 100f, h, paint)
                    
                    // Storm Core / Vortex Hub (Lethal Center)
                    val cx = pipe.x + 50f
                    val cy = h / 2f
                    paint.color = Color.argb(100, 0, 229, 255)
                    canvas.drawCircle(cx, cy, 40f, paint)
                    paint.color = if (frameCount % 10 < 5) Color.WHITE else Color.CYAN
                    canvas.drawCircle(cx, cy, 15f, paint)

                    // Flowing vectors
                    paint.color = Color.argb(160, 0, 229, 255)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    val direction = pipe.movingDirection
                    for (i in 0..2) {
                        val lineX = pipe.x + 20f + i * 30f
                        val phase = (frameCount * 8f * direction) % 150f
                        for (y in -150..(h.toInt() + 150) step 150) {
                            val lineY = y + phase
                            val path = Path()
                            path.moveTo(lineX, lineY)
                            path.quadTo(lineX - 10f, lineY + 40f, lineX, lineY + 80f)
                            canvas.drawPath(path, paint)
                            
                            val arrowY = lineY + 40f
                            if (direction > 0) {
                                canvas.drawLine(lineX, arrowY, lineX - 8f, arrowY + 8f, paint)
                                canvas.drawLine(lineX, arrowY, lineX + 8f, arrowY + 8f, paint)
                            } else {
                                canvas.drawLine(lineX, arrowY, lineX - 8f, arrowY - 8f, paint)
                                canvas.drawLine(lineX, arrowY, lineX + 8f, arrowY - 8f, paint)
                            }
                        }
                    }
                    paint.style = Paint.Style.FILL
                }
                ObstacleType.FALLING_STALACTITE -> {
                    val px1 = pipe.x + 10f
                    val px2 = pipe.x + 90f
                    val tipY = pipe.gapY
                    
                    paint.color = Color.parseColor("#424242")
                    canvas.drawRect(px1 - 10f, 0f, px2 + 10f, 30f, paint)
                    
                    paint.color = Color.parseColor("#757575")
                    val bodyPath = Path().apply {
                        moveTo(px1, 30f)
                        lineTo(px2, 30f)
                        lineTo(pipe.x + 50f, tipY)
                        close()
                    }
                    canvas.drawPath(bodyPath, paint)
                    
                    paint.color = Color.parseColor("#9E9E9E")
                    val texPath = Path().apply {
                        moveTo(pipe.x + 50f, tipY)
                        lineTo(px1 + 15f, 30f)
                        lineTo(pipe.x + 40f, 30f)
                        close()
                    }
                    canvas.drawPath(texPath, paint)
                    
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.BLACK
                    paint.strokeWidth = 4f
                    canvas.drawPath(bodyPath, paint)
                    paint.style = Paint.Style.FILL
                }
                ObstacleType.SPIKED_MINE -> {
                    val cx = pipe.x + 50f
                    val cy = pipe.gapY + pipe.gapH / 2f
                    val r = 25f
                    
                    paint.color = Color.parseColor("#E53935")
                    paint.strokeWidth = 6f
                    for (i in 0 until 8) {
                        val angle = i * Math.PI / 4
                        val sx1 = (cx + Math.cos(angle) * r).toFloat()
                        val sy1 = (cy + Math.sin(angle) * r).toFloat()
                        val sx2 = (cx + Math.cos(angle) * (r + 14f)).toFloat()
                        val sy2 = (cy + Math.sin(angle) * (r + 14f)).toFloat()
                        canvas.drawLine(sx1, sy1, sx2, sy2, paint)
                    }
                    
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#37474F")
                    canvas.drawCircle(cx, cy, r, paint)
                    
                    paint.color = Color.parseColor("#263238")
                    canvas.drawCircle(cx - 10f, cy - 10f, 3f, paint)
                    canvas.drawCircle(cx + 10f, cy - 10f, 3f, paint)
                    canvas.drawCircle(cx - 10f, cy + 10f, 3f, paint)
                    canvas.drawCircle(cx + 10f, cy + 10f, 3f, paint)
                    
                    paint.color = if (frameCount % 20 < 10) Color.RED else Color.parseColor("#FFD54F")
                    canvas.drawCircle(cx, cy, 6f, paint)
                }
            }
        }
        
        // Draw Safe Path Guide for first encounters
        if (!gamePaused && !gameOver) {
            for (p in pipes) {
                if (!seenObstacleTypes.contains(p.type) && p.x > 150f && p.x < 150f + w * 0.35f) {
                    drawSafePathGuide(canvas, p, w, h)
                }
            }
        }

        // Draw Trail Particles
        paint.style = Paint.Style.FILL
        for (p in trailParticles) {
            paint.color = p.color
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }
        paint.alpha = 255

        // Draw Bird
        canvas.save()
        val rotation = (birdV * 3f).coerceIn(-30f, 90f)
        canvas.rotate(rotation, 150f, birdY)

        // 1. Pre-body decorations (drawn behind the body)
        // Swift Falcon head crest
        if (currentCharacter == BirdCharacter.SWIFT) {
            paint.color = Color.parseColor("#0D47A1")
            val crestPath = Path().apply {
                moveTo(140f, birdY - birdSize)
                lineTo(125f, birdY - birdSize - 18f)
                lineTo(152f, birdY - birdSize + 5f)
                close()
            }
            canvas.drawPath(crestPath, paint)
        }

        // Classic Bird Scarf Tail
        if (currentCharacter == BirdCharacter.CLASSIC) {
            paint.color = Color.parseColor("#E53935")
            val tailOffset = if (wingFrame == 0) 6f else -6f
            val scarfTail = Path().apply {
                moveTo(125f, birdY + 15f)
                lineTo(102f, birdY + 8f + tailOffset)
                lineTo(98f, birdY + 22f + tailOffset)
                lineTo(125f, birdY + 20f)
                close()
            }
            canvas.drawPath(scarfTail, paint)
        }

        // Swift Falcon Headband Fluttering Tails
        if (currentCharacter == BirdCharacter.SWIFT) {
            paint.color = Color.parseColor("#E53935")
            val tailOffset = if (wingFrame == 0) -4f else 4f
            val bandTail = Path().apply {
                moveTo(132f, birdY - 10f)
                lineTo(108f, birdY - 22f + tailOffset)
                lineTo(112f, birdY - 8f + tailOffset)
                close()
            }
            canvas.drawPath(bandTail, paint)
        }

        // 2. Draw Body
        paint.color = currentCharacter.bodyColor
        canvas.drawCircle(150f, birdY, birdSize, paint)

        // 3. Post-body / overlay decorations (drawn on top of the body)
        // Heavy Pelican Pouch
        if (currentCharacter == BirdCharacter.HEAVY) {
            paint.color = Color.parseColor("#FFD54F")
            canvas.drawOval(160f, birdY, 180f, birdY + 20f, paint)
        }

        // Classic Scarf Neck Piece
        if (currentCharacter == BirdCharacter.CLASSIC) {
            paint.color = Color.parseColor("#E53935")
            canvas.drawRect(128f, birdY + 10f, 144f, birdY + 22f, paint)
        }

        // Swift Falcon Lightning Bolt
        if (currentCharacter == BirdCharacter.SWIFT) {
            paint.color = Color.parseColor("#FFEB3B")
            val boltPath = Path().apply {
                moveTo(148f, birdY - 15f)
                lineTo(138f, birdY + 2f)
                lineTo(144f, birdY + 4f)
                lineTo(135f, birdY + 18f)
                lineTo(140f, birdY + 6f)
                lineTo(135f, birdY + 4f)
                close()
            }
            canvas.drawPath(boltPath, paint)
        }

        // Heavy Pelican Top Hat
        if (currentCharacter == BirdCharacter.HEAVY) {
            paint.color = Color.parseColor("#3E2723")
            canvas.drawRect(125f, birdY - birdSize - 2f, 168f, birdY - birdSize + 4f, paint)
            canvas.drawRect(132f, birdY - birdSize - 26f, 160f, birdY - birdSize - 2f, paint)
            paint.color = Color.parseColor("#FFD54F")
            canvas.drawRect(132f, birdY - birdSize - 8f, 160f, birdY - birdSize - 2f, paint)
        }

        // Floaty Hummingbird Flower Crown
        if (currentCharacter == BirdCharacter.FLOATY) {
            paint.color = Color.parseColor("#FF4081")
            canvas.drawCircle(150f, birdY - birdSize + 2f, 7f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(150f, birdY - birdSize + 2f, 2f, paint)
            paint.color = Color.parseColor("#E040FB")
            canvas.drawCircle(159f, birdY - birdSize + 5f, 7f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(159f, birdY - birdSize + 5f, 2f, paint)
            paint.color = Color.parseColor("#1DE9B6")
            canvas.drawCircle(141f, birdY - birdSize + 5f, 6f, paint)
        }

        // 4. Wing
        paint.color = currentCharacter.wingColor
        if (wingFrame == 0) {
            canvas.drawOval(130f, birdY - 5f, 160f, birdY + 15f, paint)
        } else {
            canvas.drawOval(130f, birdY - 15f, 160f, birdY + 5f, paint)
        }

        // 5. Eyes & Ninja Mask
        if (currentCharacter == BirdCharacter.SWIFT) {
            paint.color = Color.parseColor("#E53935")
            canvas.drawRect(144f, birdY - 18f, 178f, birdY - 2f, paint)
        }

        if (gameOver) {
            // Dizzy X-eyes
            paint.color = Color.BLACK
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(162f, birdY - 16f, 172f, birdY - 6f, paint)
            canvas.drawLine(172f, birdY - 16f, 162f, birdY - 6f, paint)
            paint.style = Paint.Style.FILL
        } else {
            // Normal eye
            paint.color = Color.WHITE
            canvas.drawCircle(170f, birdY - 10f, 12f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(175f, birdY - 10f, 5f, paint)
        }

        // 6. Beak
        paint.color = currentCharacter.beakColor
        beakPath.reset()
        if (currentCharacter == BirdCharacter.FLOATY) {
            beakPath.moveTo(196f, birdY)
            beakPath.lineTo(170f, birdY - 3f)
            beakPath.lineTo(170f, birdY + 3f)
        } else {
            beakPath.moveTo(185f, birdY)
            beakPath.lineTo(170f, birdY - 10f)
            beakPath.lineTo(170f, birdY + 10f)
        }
        beakPath.close()
        canvas.drawPath(beakPath, paint)

        // 6.5. Death-specific decoration overlays
        if (gameOver) {
            when (currentDeathType) {
                DeathType.CEILING -> {
                    // Dizzy stars/halo spinning above the bird
                    paint.color = Color.parseColor("#FFD54F")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    canvas.drawOval(135f, birdY - birdSize - 12f, 165f, birdY - birdSize - 4f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(132f, birdY - birdSize - 10f, 3f, paint)
                    canvas.drawCircle(168f, birdY - birdSize - 8f, 2f, paint)
                }
                DeathType.GROUND -> {
                    // Mud patches
                    paint.color = Color.parseColor("#795548")
                    canvas.drawCircle(140f, birdY + 12f, 8f, paint)
                    canvas.drawCircle(152f, birdY + 16f, 6f, paint)
                    paint.color = Color.parseColor("#4CAF50")
                    paint.strokeWidth = 3f
                    canvas.drawLine(135f, birdY + 10f, 128f, birdY + 18f, paint)
                    canvas.drawLine(158f, birdY - 8f, 164f, birdY - 15f, paint)
                }
                DeathType.PIPE, DeathType.OVERHEAD_PIPE, DeathType.GROUND_PIPE -> {
                    // Crossed Band-aid
                    paint.color = Color.parseColor("#FFE0B2")
                    canvas.drawRect(138f, birdY - 4f, 158f, birdY + 4f, paint)
                    canvas.drawRect(145f, birdY - 11f, 151f, birdY + 11f, paint)
                }
                DeathType.CRATE -> {
                    // Wood splinters piercing the bird
                    paint.color = Color.parseColor("#5D4037")
                    paint.strokeWidth = 4f
                    canvas.drawLine(135f, birdY - 18f, 122f, birdY - 26f, paint)
                    canvas.drawLine(158f, birdY + 18f, 170f, birdY + 28f, paint)
                }
                DeathType.SPIDER -> {
                    // Spider web wrapping around the bird
                    paint.color = Color.argb(160, 255, 255, 255)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawLine(150f - birdSize, birdY, 150f + birdSize, birdY, paint)
                    canvas.drawLine(150f, birdY - birdSize, 150f, birdY + birdSize, paint)
                    canvas.drawCircle(150f, birdY, 20f, paint)
                    paint.style = Paint.Style.FILL
                }
                DeathType.SPIKES -> {
                    // Sharp metallic spikes + cuts
                    paint.color = Color.parseColor("#90A4AE")
                    paint.strokeWidth = 4f
                    canvas.drawLine(140f, birdY + 15f, 134f, birdY + 30f, paint)
                    canvas.drawLine(155f, birdY + 15f, 160f, birdY + 30f, paint)
                    
                    paint.color = Color.parseColor("#D50000")
                    paint.strokeWidth = 3f
                    canvas.drawLine(135f, birdY - 5f, 142f, birdY, paint)
                    canvas.drawLine(142f, birdY + 5f, 149f, birdY + 10f, paint)
                }
                DeathType.CLOUD -> {
                    // Puffy cloud mist covering character
                    paint.color = Color.argb(170, 255, 255, 255)
                    canvas.drawCircle(145f, birdY + 10f, 16f, paint)
                    canvas.drawCircle(160f, birdY + 12f, 12f, paint)
                    canvas.drawCircle(135f, birdY + 5f, 12f, paint)
                }
                DeathType.MINE -> {
                    // Charred / blackened soot overlay with fire sparks
                    paint.color = Color.argb(200, 33, 33, 33)
                    canvas.drawCircle(150f, birdY, birdSize, paint)
                    paint.color = Color.parseColor("#FF9800")
                    canvas.drawCircle(138f, birdY - 8f, 5f, paint)
                    canvas.drawCircle(162f, birdY + 8f, 4f, paint)
                }
                else -> {}
            }
        }

        canvas.restore()

        // 7. Draw Ground Line at bottom (inspired by T-Rex)
        paint.style = Paint.Style.FILL
        paint.color = when (currentTheme) {
            FlappyTheme.NIGHT -> Color.parseColor("#1B5E20")
            FlappyTheme.WINTER -> Color.WHITE
            else -> Color.parseColor("#8D6E63")
        }
        canvas.drawRect(0f, h - 80f, w, h, paint)

        if (currentTheme != FlappyTheme.WINTER) {
            paint.color = Color.parseColor("#4CAF50")
            canvas.drawRect(0f, h - 80f, w, h - 70f, paint)
        }

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        val hudY = h * 0.05f
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 40f, hudY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", w - 40f, hudY, paint)

        val charName = context.getString(when (currentCharacter) {
            BirdCharacter.SWIFT -> R.string.flappy_char_swift
            BirdCharacter.HEAVY -> R.string.flappy_char_heavy
            BirdCharacter.FLOATY -> R.string.flappy_char_floaty
            else -> R.string.flappy_char_classic
        })
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.LTGRAY
        canvas.drawText("${context.getString(R.string.level_label)}: ${currentDifficulty.ordinal + 1} | $charName", w / 2f, hudY, paint)

        if (gameOver) {
            celebrationManager.draw(canvas)
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.crashed_label)
            val subtext = if (deathReason.isNotEmpty()) {
                "$deathReason\n${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}"
            } else {
                "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}"
            }
            drawOverlay(canvas, title, subtext)
        } else if (gamePaused) {
            drawOverlay(canvas, context.getString(R.string.game_flappy), context.getString(R.string.flap_hint))
        }

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.reset()
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, 100f, paint)
            paint.alpha = 255
        }

        if (!gamePaused && !gameOver) {
            celebrationManager.update()
            invalidate()
        }
    }

    private fun update() {
        if (gamePaused || gameOver) return
        val w = width.toFloat()
        val h = height.toFloat()
        val now = System.currentTimeMillis()
        if (lastUpdate == 0L) lastUpdate = now
        lastUpdate = now
        frameCount++
        if (frameCount % 5 == 0) {
            wingFrame = (wingFrame + 1) % 2
        }

        // Physics
        birdV += gravity * currentCharacter.gravityMult
        birdY += birdV

        if (birdY - birdSize < 0) {
            die("Flew too high and hit the ceiling!", DeathType.CEILING)
            return
        }
        if (birdY + birdSize > h - 80f) {
            die("Crashed into the grassy ground!", DeathType.GROUND)
            return
        }

        // Scroll background parallax hills
        bgScrollX = (bgScrollX + pipeSpeed * 0.4f) % w

        // Spawn trail particles
        if (frameCount % 2 == 0) {
            val color = when (currentCharacter) {
                BirdCharacter.SWIFT -> Color.parseColor("#80D8FF")
                BirdCharacter.HEAVY -> Color.parseColor("#BCAAA4")
                BirdCharacter.FLOATY -> Color.parseColor("#F8BBD0")
                else -> Color.parseColor("#FFF59D")
            }
            trailParticles.add(
                TrailParticle(
                    x = 135f,
                    y = birdY + Random.nextFloat() * 16f - 8f,
                    vx = -pipeSpeed * 0.4f - Random.nextFloat() * 2f,
                    vy = Random.nextFloat() * 1.5f - 0.75f,
                    size = Random.nextFloat() * 5f + 3f,
                    alpha = 255,
                    color = color
                )
            )
        }

        // Update trail particles
        val tIter = trailParticles.iterator()
        while (tIter.hasNext()) {
            val p = tIter.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 8
            if (p.alpha <= 0 || p.x < 0) {
                tIter.remove()
            }
        }

        // Clouds
        if (clouds.size < 5 && Random.nextFloat() < 0.01f) {
            clouds.add(Cloud(w + 100f, Random.nextFloat() * h * 0.5f, Random.nextFloat() * 2f + 1f, Random.nextFloat() * 1f + 0.5f))
        }
        val cIter = clouds.iterator()
        while (cIter.hasNext()) {
            val c = cIter.next()
            c.x -= c.speed
            if (c.x < -200f) cIter.remove()
        }

        // Pipes
        if (now - pipeSpawnTime > pipeInterval) {
            val prevPipe = pipes.lastOrNull()
            // Horizontal spacing validation: guarantee minimum horizontal separation (35% of screen width)
            val canSpawnPipe = prevPipe == null || (w - prevPipe.x) >= (w * 0.35f)
            
            if (canSpawnPipe) {
                val gapH = (h * currentDifficulty.gapHMult).coerceAtLeast(birdSize * 4f)
                val minY = 100f
                val maxY = h - gapH - 100f
                
                // Vertical spacing validation: limit vertical gap transition based on travel distance
                val horizontalDistance = if (prevPipe != null) (w - prevPipe.x) else w
                val framesToTravel = (horizontalDistance / pipeSpeed).coerceAtLeast(10f)
                val maxVerticalChange = (framesToTravel * 5f).coerceAtMost(h * 0.22f)
                
                val targetMinY = if (prevPipe != null) (prevPipe.gapY - maxVerticalChange).coerceAtLeast(minY) else minY
                val targetMaxY = if (prevPipe != null) (prevPipe.gapY + maxVerticalChange).coerceAtMost(maxY) else maxY
                
                val gapY = if (targetMaxY > targetMinY) {
                    Random.nextFloat() * (targetMaxY - targetMinY) + targetMinY
                } else {
                    targetMinY
                }
                
                val type = when (Random.nextInt(20)) {
                    0, 1 -> ObstacleType.MOVING
                    2 -> ObstacleType.BAT
                    3 -> ObstacleType.CLOSING_GATE
                    4, 5 -> ObstacleType.WIND_ZONE
                    6 -> ObstacleType.FALLING_STALACTITE
                    7 -> ObstacleType.SPIKED_MINE
                    else -> ObstacleType.STANDARD
                }
                
                val spawnX = if (prevPipe == null) w * 0.7f else w
                val windDir = if (Random.nextBoolean()) 1 else -1
                val pipeGapY = when (type) {
                    ObstacleType.FALLING_STALACTITE -> 150f
                    else -> gapY
                }
                pipes.add(Pipe(spawnX, pipeGapY, gapH, type = type, movingDirection = windDir))
                pipeSpawnTime = now
            }
        }

        val pIter = pipes.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x -= pipeSpeed
            
            if (p.x < -100) {
                pIter.remove()
                continue
            }

            // Obstacle updates
            when (p.type) {
                ObstacleType.MOVING -> {
                    p.gapY += p.movingDirection * 2f
                    if (p.gapY > p.initialGapY + 80f) {
                        p.movingDirection = -1
                    } else if (p.gapY < p.initialGapY - 80f) {
                        p.movingDirection = 1
                    }
                }
                ObstacleType.BAT -> {
                    p.gapY = p.initialGapY + Math.sin(frameCount * 0.15).toFloat() * 60f
                }
                ObstacleType.FALLING_STALACTITE -> {
                    if (p.x - 150f < 320f) {
                        p.triggered = true
                    }
                    if (p.triggered) {
                        // Slower fall (10f instead of 15f) and stop 200px above ground for fairness
                        p.gapY = (p.gapY + 10f).coerceAtMost(h - 280f)
                    }
                }
                ObstacleType.WIND_ZONE -> {
                    if (150f + birdSize > p.x && 150f - birdSize < p.x + 100f) {
                        birdV -= p.movingDirection * 0.8f
                    }
                }
                else -> {}
            }

            // Collision detection mapped per obstacle type (7 Types)
            val collides = if (150f + birdSize > p.x && 150f - birdSize < p.x + 100f) {
                when (p.type) {
                    ObstacleType.STANDARD, ObstacleType.MOVING -> {
                        birdY - birdSize < p.gapY || birdY + birdSize > p.gapY + p.gapH
                    }
                    ObstacleType.BAT -> {
                        val cx = p.x + 50f
                        val cy = p.gapY
                        val dx = 150f - cx
                        val dy = birdY - cy
                        val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        distance < (birdSize + 18f)
                    }
                    ObstacleType.CLOSING_GATE -> {
                        val px1 = p.x + 10f
                        val px2 = p.x + 90f
                        val cycleVal = Math.sin(frameCount * 0.15).toFloat()
                        val closureWidth = (cycleVal + 1f) * 35f
                        val leftJawRight = px1 + closureWidth
                        val rightJawLeft = px2 - closureWidth
                        
                        // Collision with top/bottom bars OR the moving side jaws (including teeth)
                        val hitsBase = birdY - birdSize < p.gapY || birdY + birdSize > p.gapY + p.gapH
                        val inJawX = 150f + birdSize > px1 && 150f - birdSize < px2
                        val inJawY = birdY + birdSize > p.gapY && birdY - birdSize < p.gapY + p.gapH
                        val hitsJaws = inJawY && (150f + birdSize > rightJawLeft - 15f || 150f - birdSize < leftJawRight + 15f)
                        
                        hitsBase || (inJawX && hitsJaws)
                    }
                    ObstacleType.FALLING_STALACTITE -> {
                        // Lethal if bird is above the tip AND horizontally aligned
                        birdY - birdSize < p.gapY
                    }
                    ObstacleType.SPIKED_MINE -> {
                        val cx = p.x + 50f
                        val cy = p.gapY + p.gapH / 2f
                        val dx = 150f - cx
                        val dy = birdY - cy
                        val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        distance < (birdSize + 39f)
                    }
                    ObstacleType.WIND_ZONE -> {
                        // Lethal Storm Core in the center
                        val cx = p.x + 50f
                        val cy = h / 2f
                        val dx = 150f - cx
                        val dy = birdY - cy
                        val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        distance < (birdSize + 25f)
                    }
                }
            } else {
                false
            }

            if (collides) {
                val (reason, deathType) = when (p.type) {
                    ObstacleType.STANDARD -> Pair("Crashed into a green pipe!", DeathType.PIPE)
                    ObstacleType.MOVING -> Pair("Crashed into a moving pipe!", DeathType.PIPE)
                    ObstacleType.BAT -> Pair("Collided with a patrolling bat!", DeathType.SPIDER)
                    ObstacleType.CLOSING_GATE -> Pair("Chomped by the closing metal gates!", DeathType.SPIKES)
                    ObstacleType.FALLING_STALACTITE -> Pair("Crushed by a falling stalactite!", DeathType.CRATE)
                    ObstacleType.SPIKED_MINE -> Pair("Blew up on a floating spiked mine!", DeathType.MINE)
                    ObstacleType.WIND_ZONE -> Pair("Vaporized by the storm vortex core!", DeathType.MINE)
                }
                die(reason, deathType)
                return
            }

            // Score
            if (!p.passed && p.x < 150f) {
                p.passed = true
                seenObstacleTypes.add(p.type)
                score++
                SoundManager.playScore()
                
                // Dynamic theme level progression inspired by T-Rex Run
                val nextTheme = when (score) {
                    in 0..9 -> FlappyTheme.DAY
                    in 10..19 -> FlappyTheme.SUNSET
                    in 20..29 -> FlappyTheme.NIGHT
                    else -> FlappyTheme.WINTER
                }
                if (nextTheme != currentTheme) {
                    currentTheme = nextTheme
                    SoundManager.playSuccess()
                    if (currentTheme == FlappyTheme.WINTER) {
                        particles.clear()
                        repeat(30) {
                            particles.add(GameEnvironment.Particle(random.nextFloat() * 2000, random.nextFloat() * 1000, random.nextFloat() * 5 + 2, random.nextFloat() * 2 - 1))
                        }
                    }
                }

                if (score > best) {
                    best = score
                    ScoreManager.updateHighScore(context, gameKey, best, currentDifficulty.ordinal)
                }
            }
        }
    }

    private fun die(reason: String, type: DeathType) {
        if (gameOver) return
        gameOver = true
        gamePaused = true
        deathReason = reason
        currentDeathType = type
        val oldBest = best
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, currentDifficulty.ordinal)
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

    private fun drawSafePathGuide(canvas: Canvas, p: Pipe, w: Float, h: Float) {
        paint.reset()
        paint.isAntiAlias = true
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.parseColor("#00E676") // Neon green
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
        
        val guidePath = Path()
        val textHint: String
        val textY: Float
        
        when (p.type) {
            ObstacleType.STANDARD, ObstacleType.MOVING -> {
                textHint = "FLY THROUGH"
                val targetY = p.gapY + p.gapH / 2f
                textY = targetY - 45f
                guidePath.moveTo(150f, birdY)
                guidePath.quadTo((150f + p.x) / 2f, targetY, p.x + 50f, targetY)
            }
            ObstacleType.BAT -> {
                textHint = "AVOID BAT"
                val targetY = p.gapY
                textY = targetY - 45f
                guidePath.moveTo(150f, birdY)
                guidePath.quadTo((150f + p.x) / 2f, targetY, p.x + 50f, targetY)
            }
            ObstacleType.CLOSING_GATE -> {
                textHint = "TIME THE ENTRY"
                val targetY = p.gapY + p.gapH / 2f
                textY = targetY - 45f
                guidePath.moveTo(150f, birdY)
                guidePath.quadTo((150f + p.x) / 2f, targetY, p.x + 50f, targetY)
            }
            ObstacleType.WIND_ZONE -> {
                textHint = if (p.movingDirection > 0) "AVOID CORE | FIGHT UP" else "AVOID CORE | FIGHT DOWN"
                val targetY = h / 2f
                textY = 120f
                guidePath.moveTo(150f, birdY)
                // Guide player to fly around the core
                val guideOffset = if (birdY < h / 2f) -120f else 120f
                guidePath.quadTo((150f + p.x) / 2f, h / 2f + guideOffset, p.x + 50f, h / 2f + guideOffset)
            }
            ObstacleType.FALLING_STALACTITE -> {
                textHint = "FLY UNDER"
                val targetY = h - 220f
                textY = targetY - 45f
                guidePath.moveTo(150f, birdY)
                guidePath.quadTo((150f + p.x) / 2f, targetY, p.x + 50f, targetY)
            }
            ObstacleType.SPIKED_MINE -> {
                textHint = "GO ABOVE / BELOW"
                val cy = p.gapY + p.gapH / 2f
                textY = cy - 80f
                guidePath.moveTo(150f, birdY)
                guidePath.quadTo((150f + p.x) / 2f, cy - 60f, p.x + 50f, cy - 60f)
                canvas.drawPath(guidePath, paint)
                guidePath.reset()
                guidePath.moveTo(150f, birdY)
                guidePath.quadTo((150f + p.x) / 2f, cy + 60f, p.x + 50f, cy + 60f)
            }
        }
        
        canvas.drawPath(guidePath, paint)
        
        // Draw helper text badge
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#00E676")
        paint.textSize = 26f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        
        val textWidth = paint.measureText(textHint)
        val textBgPaint = Paint().apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
        }
        val bgRect = RectF(
            (150f + p.x) / 2f - textWidth / 2f - 15f,
            textY - 30f,
            (150f + p.x) / 2f + textWidth / 2f + 15f,
            textY + 10f
        )
        canvas.drawRoundRect(bgRect, 8f, 8f, textBgPaint)
        canvas.drawText(textHint, (150f + p.x) / 2f, textY, paint)
    }
}
