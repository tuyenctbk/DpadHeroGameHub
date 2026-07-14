package com.tdpham.games.spinball

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import java.util.*
import kotlin.math.*

class SpinballView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "spinball"
    override var onGameOver: ((Int) -> Unit)? = null
    private var isPaused = true
    private var isGameOver = false
    private var score = 0
    private var highScore = 0
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "spinball_settings"
    private val KEY_DIFFICULTY = "difficulty_index"
    private var currentDifficultyIndex = 1
    private var hintShowFrames = 0

    // Physics constants
    private val radius = 300f
    private val ballRadius = 15f
    private val rotationSpeed = 5f
    private var currentRotation = 0f
    private val pressedKeys = mutableSetOf<Int>()

    // Ball properties
    private var ballX = 0f
    private var ballY = 0f
    private var ballVX = 5f
    private var ballVY = 5f

    enum class StarType { GOLD, RUBY, DIAMOND }
    data class StarItem(var x: Float, var y: Float, val type: StarType)
    enum class SpikeStyle { TRIANGLE, SAWBLADE, LASER }
    data class SpikeItem(val angle: Float, val style: SpikeStyle)
    data class TrailParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var alpha: Int, var color: Int, var size: Float)

    private val stars = mutableListOf<StarItem>()
    private val spikes = mutableListOf<SpikeItem>()
    private val trailParticles = mutableListOf<TrailParticle>()
    private var selectedBallType = 0
    private val KEY_BALL_TYPE = "selected_ball_type"
    private val random = Random()
    private val spikePath = Path()
    private val tempPath = Path()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (isGameOver || isPaused || hintShowFrames > 0) {
                celebrationManager.update()
                invalidate()
            }
            mainHandler.postDelayed(this, 50)
        }
    }
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && !isGameOver) {
                update()
                invalidate()
                mainHandler.postDelayed(this, 16)
            }
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
        mainHandler.post(animRunnable)
        
        // Pre-create spike path
        spikePath.moveTo(radius - 30f, -15f)
        spikePath.lineTo(radius, 0f)
        spikePath.lineTo(radius - 30f, 15f)
        spikePath.close()
    }

    private fun resetBall() {
        ballX = 0f
        ballY = 0f
        val angle = random.nextFloat() * 2 * PI.toFloat()
        val speed = when(currentDifficultyIndex) {
            0 -> 4.5f
            2 -> 8.5f
            else -> 6.5f
        }
        ballVX = cos(angle) * speed
        ballVY = sin(angle) * speed
    }

    private fun spawnItems() {
        stars.clear()
        spikes.clear()
        for (i in 0 until 5) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val dist = random.nextFloat() * (radius - 50f)
            val type = when(random.nextInt(10)) {
                in 0..6 -> StarType.GOLD
                in 7..8 -> StarType.RUBY
                else -> StarType.DIAMOND
            }
            stars.add(StarItem(cos(angle) * dist, sin(angle) * dist, type))
        }
        for (i in 0 until 3) {
            val style = when(random.nextInt(3)) {
                0 -> SpikeStyle.TRIANGLE
                1 -> SpikeStyle.SAWBLADE
                else -> SpikeStyle.LASER
            }
            spikes.add(SpikeItem(random.nextFloat() * 360f, style))
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(updateRunnable)
        mainHandler.removeCallbacks(animRunnable)
    }

    override fun startGame() {
        requestFocus()
        isPaused = false
        isGameOver = false
        score = 0
        trailParticles.clear()
        resetBall()
        spawnItems()
        mainHandler.removeCallbacks(updateRunnable)
        mainHandler.post(updateRunnable)
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        if (isPaused && !isGameOver) {
            isPaused = false
            mainHandler.removeCallbacks(updateRunnable)
            mainHandler.post(updateRunnable)
        }
    }

    override fun resetGame() {
        // Load difficulty from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentDifficultyIndex = prefs.getInt(KEY_DIFFICULTY, 1).coerceIn(0, 2)
        selectedBallType = prefs.getInt(KEY_BALL_TYPE, 0).coerceIn(0, 2)

        highScore = ScoreManager.getHighScore(context, gameKey, currentDifficultyIndex)
        hintShowFrames = 100
        startGame()
        isPaused = true
        invalidate()
    }

    override fun toggleSound(): Boolean {
        return SoundManager.toggleSound()
    }

    private fun update() {
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) {
            currentRotation = (currentRotation - rotationSpeed) % 360f
        }
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) {
            currentRotation = (currentRotation + rotationSpeed) % 360f
        }

        ballX += ballVX
        ballY += ballVY

        val distSq = ballX * ballX + ballY * ballY
        if (distSq > (radius - ballRadius).pow(2)) {
            // Collision with boundary
            val angleToBall = Math.toDegrees(atan2(ballY.toDouble(), ballX.toDouble())).toFloat()
            var normalizedAngle = (angleToBall - currentRotation) % 360
            if (normalizedAngle < 0) normalizedAngle += 360

            // Check spikes
            for (spike in spikes) {
                val diff = abs(normalizedAngle - spike.angle)
                if (diff < 15 || diff > 345) {
                    gameOver()
                    return
                }
            }

            // Simple bounce physics
            val normalX = ballX / sqrt(distSq)
            val normalY = ballY / sqrt(distSq)
            val dot = ballVX * normalX + ballVY * normalY
            ballVX -= 2 * dot * normalX
            ballVY -= 2 * dot * normalY

            // Add tiny random bounce perturbation to break repeating paths
            val bounceAngle = atan2(ballVY, ballVX) + (random.nextFloat() - 0.5f) * 0.15f
            val currentSpeed = sqrt(ballVX * ballVX + ballVY * ballVY)
            ballVX = cos(bounceAngle) * currentSpeed
            ballVY = sin(bounceAngle) * currentSpeed

            // Keep inside
            val scale = (radius - ballRadius) / sqrt(distSq)
            ballX *= scale
            ballY *= scale
            
            SoundManager.playClick()
        }

        // Spawn fireball trail particles
        if (selectedBallType == 1 && random.nextFloat() < 0.4f) {
            val vx = (random.nextFloat() - 0.5f) * 2f - ballVX * 0.2f
            val vy = (random.nextFloat() - 0.5f) * 2f - ballVY * 0.2f
            trailParticles.add(TrailParticle(ballX, ballY, vx, vy, 255, Color.parseColor("#FF5722"), 8f + random.nextFloat() * 8f))
        }

        // Update trail particles
        val pIter = trailParticles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 10
            if (p.alpha <= 0) {
                pIter.remove()
            }
        }

        // Star collection in rotated space
        val iterator = stars.iterator()
        var needsSpawn = false
        val rad = Math.toRadians(currentRotation.toDouble())
        val cosRot = cos(rad).toFloat()
        val sinRot = sin(rad).toFloat()
        while (iterator.hasNext()) {
            val star = iterator.next()
            val starScreenX = star.x * cosRot - star.y * sinRot
            val starScreenY = star.x * sinRot + star.y * cosRot
            
            val dx = ballX - starScreenX
            val dy = ballY - starScreenY
            if (dx * dx + dy * dy < (ballRadius + 22f).pow(2)) {
                iterator.remove()
                score += when(star.type) {
                    StarType.GOLD -> 10
                    StarType.RUBY -> 25
                    StarType.DIAMOND -> 50
                }
                SoundManager.playScore()
                if (stars.isEmpty()) needsSpawn = true
            }
        }
        if (needsSpawn) spawnItems()
    }

    private fun gameOver() {
        isPaused = true
        isGameOver = true
        SoundManager.playError()
        val finalScore = score
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, finalScore, currentDifficultyIndex)
        if (isNewHigh) {
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
        } else {
            currentVictoryWord = ""
        }
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isNewHigh = isNewHigh,
            score = finalScore,
            highScore = highScore
        )
        highScore = ScoreManager.getHighScore(context, gameKey, currentDifficultyIndex)
        onGameOver?.invoke(finalScore)
    }

    override fun onDraw(canvas: Canvas) {
        if (hintShowFrames > 0) {
            hintShowFrames--
        }

        super.onDraw(canvas)
        
        canvas.save()
        canvas.translate(width / 2f, height / 2f)

        // Draw Boundary with neon glow effect
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 16f
        paint.color = Color.parseColor("#4400E5FF")
        canvas.drawCircle(0f, 0f, radius, paint)
        paint.strokeWidth = 6f
        paint.color = Color.WHITE
        canvas.drawCircle(0f, 0f, radius, paint)

        // Draw Spikes (Rotating with currentRotation)
        canvas.save()
        canvas.rotate(currentRotation)
        for (spike in spikes) {
            canvas.save()
            canvas.rotate(spike.angle)
            when (spike.style) {
                SpikeStyle.SAWBLADE -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#FF5722")
                    canvas.drawCircle(radius - 10f, 0f, 15f, paint)
                    paint.color = Color.parseColor("#E64A19")
                    val teethCount = 8
                    val timeAngle = (System.currentTimeMillis() / 4L) % 360f
                    canvas.save()
                    canvas.translate(radius - 10f, 0f)
                    canvas.rotate(timeAngle)
                    for (t in 0 until teethCount) {
                        canvas.save()
                        canvas.rotate(t * (360f / teethCount))
                        tempPath.reset()
                        tempPath.moveTo(0f, -15f)
                        tempPath.lineTo(8f, -22f)
                        tempPath.lineTo(0f, -18f)
                        tempPath.close()
                        canvas.drawPath(tempPath, paint)
                        canvas.restore()
                    }
                    canvas.restore()
                }
                SpikeStyle.LASER -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#78909C")
                    tempPath.reset()
                    tempPath.moveTo(radius - 15f, -10f)
                    tempPath.lineTo(radius, -10f)
                    tempPath.lineTo(radius, 10f)
                    tempPath.lineTo(radius - 15f, 10f)
                    tempPath.close()
                    canvas.drawPath(tempPath, paint)
                    paint.color = Color.RED
                    canvas.drawCircle(radius - 12f, 0f, 4f, paint)
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.color = Color.parseColor("#AAFF0000")
                    paint.strokeWidth = 10f
                    canvas.drawLine(radius - 12f, 0f, radius - 45f, 0f, paint)
                    paint.color = Color.WHITE
                    paint.strokeWidth = 4f
                    canvas.drawLine(radius - 12f, 0f, radius - 45f, 0f, paint)
                    paint.strokeCap = Paint.Cap.BUTT
                }
                else -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.RED
                    canvas.drawPath(spikePath, paint)
                }
            }
            canvas.restore()
        }
        canvas.restore()

        // Draw Stars (Rotating with currentRotation so they move along when player rotates circle!)
        canvas.save()
        canvas.rotate(currentRotation)
        for (star in stars) {
            drawStarItem(canvas, star)
        }
        canvas.restore()

        // Draw Ball
        when (selectedBallType) {
            1 -> { // Fireball with spark trail
                for (p in trailParticles) {
                    paint.style = Paint.Style.FILL
                    paint.color = p.color
                    paint.alpha = p.alpha
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                }
                paint.alpha = 255
                paint.color = Color.parseColor("#FF3D00")
                canvas.drawCircle(ballX, ballY, ballRadius + 2f, paint)
                paint.color = Color.parseColor("#FFEA00")
                canvas.drawCircle(ballX, ballY, ballRadius * 0.7f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(ballX, ballY, ballRadius * 0.3f, paint)
            }
            2 -> { // Plasma pulse
                val pulse = (1.0f + 0.15f * sin(System.currentTimeMillis() / 80.0).toFloat())
                val size = ballRadius * pulse
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#E040FB")
                canvas.drawCircle(ballX, ballY, size, paint)
                paint.color = Color.parseColor("#00E676")
                canvas.drawCircle(ballX, ballY, size * 0.6f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(ballX, ballY, size * 0.25f, paint)
            }
            else -> { // Neon Classic
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(ballX, ballY, ballRadius, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(ballX, ballY, ballRadius * 0.4f, paint)
            }
        }

        // Score
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        val hudY = Math.round(-radius - 80f).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score  ${context.getString(R.string.best_label)}: $highScore", 0f, hudY, paint)
        
        paint.color = Color.LTGRAY
        val modeStr = context.getString(when(currentDifficultyIndex) {
            0 -> R.string.spinball_difficulty_1
            2 -> R.string.spinball_difficulty_3
            else -> R.string.spinball_difficulty_2
        })
        canvas.drawText(modeStr, 0f, hudY + 40f, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), -width/2 + 30f, -height/2 + 60f, paint)
            paint.alpha = 255
        }
        canvas.restore()

        if (isGameOver || isPaused) {
            val title = if (isGameOver) (if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.game_over)) else context.getString(R.string.paused)
            val sub = if (isGameOver) "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}" else context.getString(R.string.resume_hint)
            
            if (isGameOver) {
                celebrationManager.draw(canvas)
            }
            
            drawOverlay(canvas, title, sub)
        }
    }

    private fun drawStarItem(canvas: Canvas, star: StarItem) {
        val color = when(star.type) {
            StarType.GOLD -> Color.parseColor("#FFD600")
            StarType.RUBY -> Color.parseColor("#E91E63")
            StarType.DIAMOND -> Color.parseColor("#00E5FF")
        }
        paint.color = color
        paint.style = Paint.Style.FILL
        
        val cx = star.x
        val cy = star.y
        val outerRadius = 14f
        val innerRadius = 6f
        tempPath.reset()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val angle = i * PI.toFloat() / 5f - PI.toFloat() / 2f
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            if (i == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
        }
        tempPath.close()
        canvas.drawPath(tempPath, paint)
        
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 3f, paint)
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 80f
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        paint.textSize = 35f
        val lines = sub.split("\n")
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 50f + i * 45f, paint)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                startGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (isPaused) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resume()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                pressedKeys.add(keyCode)
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> {
                showOptions()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.remove(keyCode)
        return super.onKeyUp(keyCode, event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            requestFocus()
        } else {
            pressedKeys.clear()
            pause()
        }
    }

    private fun showOptions() {
        pause()
        SpinballOptionsDialog.show(context) {
            resetGame()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                performClick()
                if (isGameOver) {
                    resetGame()
                    startGame()
                    return true
                }
                if (isPaused) {
                    resume()
                    return true
                }
            }
            
            // Mouse/Touch controls: Rotate shield towards touch angle
            val dx = event.x - width / 2f
            val dy = event.y - height / 2f
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            currentRotation = angle
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }
}
