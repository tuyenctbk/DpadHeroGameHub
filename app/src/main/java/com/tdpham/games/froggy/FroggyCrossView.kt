package com.tdpham.games.froggy

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

class FroggyCrossView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "froggy_cross"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    enum class TerrainType {
        GRASS,
        ROAD,
        RIVER,
        RAILROAD,
        LAVA
    }

    private val rowTerrains = Array(16) { TerrainType.GRASS }

    private fun getTerrainType(r: Int): TerrainType {
        if (r in 0 until 16) {
            return rowTerrains[r]
        }
        return TerrainType.GRASS
    }

    private fun generateMap() {
        for (r in 0 until 16) {
            if (r == 0 || r == 5 || r == 10 || r == 15) {
                rowTerrains[r] = TerrainType.GRASS // Safe zones
            } else {
                // Randomly choose from ROAD, RIVER, RAILROAD, LAVA
                rowTerrains[r] = TerrainType.values()[Random.nextInt(1, 5)]
            }
        }
    }

    private val rows = 16
    private val cols = 13
    private var cellW = 0f
    private var cellH = 0f

    private var frogR = rows - 1
    private var frogX = (cols / 2).toFloat()
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true
    private var isLevelCleared = false
    private var lives = 3
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "froggy_settings"
    private val KEY_DIFFICULTY = "difficulty_index"
    private val KEY_FROG_TYPE = "selected_frog_type"
    private var selectedFrogType = 0
    private var jumpStartTime = 0L
    private val JUMP_DURATION = 200L
    private var currentDifficultyIndex = 1
    private var hintShowFrames = 0
    private var isInitialized = false
    private val animHandler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || isLevelCleared) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }

    private val lanes = mutableListOf<Lane>()
    private var lastUpdate = 0L
    private val entityRect = RectF()
    private val tempRect = RectF()
    private val tempPath = Path()

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gamePaused && !gameOver) {
                update()
                invalidate()
                handler.postDelayed(this, 20)
            }
        }
    }

    data class Entity(var c: Float, val length: Int, val color: Int, val isLog: Boolean = false, val subType: Int = 0)
    data class Lane(val r: Int, val speed: Float, val entities: MutableList<Entity>, val isRiver: Boolean)

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
        handler.removeCallbacks(gameLoop)
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
        // Load difficulty from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentDifficultyIndex = prefs.getInt(KEY_DIFFICULTY, 1).coerceIn(0, 2)
        selectedFrogType = prefs.getInt(KEY_FROG_TYPE, 0).coerceIn(0, 2)

        score = 0
        lives = when (selectedFrogType) {
            1 -> 1
            2 -> 5
            else -> 3
        }
        jumpStartTime = 0L
        best = ScoreManager.getHighScore(context, gameKey, currentDifficultyIndex)
        gameOver = false
        gamePaused = true
        isLevelCleared = false
        resetFrog()
        generateMap()
        setupLanes()
        hintShowFrames = 100
        invalidate()
    }

    private fun resetFrog() {
        frogR = rows - 1
        frogX = (cols / 2).toFloat()
    }

    private fun setupLanes() {
        lanes.clear()
        val speedMult = when(currentDifficultyIndex) {
            0 -> 0.7f
            2 -> 1.4f
            else -> 1.0f
        }
        for (r in 0 until rows) {
            val terrain = getTerrainType(r)
            if (terrain == TerrainType.GRASS) continue
            
            val entities = mutableListOf<Entity>()
            val direction = if (r % 2 == 0) 1 else -1
            
            when (terrain) {
                TerrainType.ROAD -> {
                    val sub = Random.nextInt(3)
                    val length = when (sub) {
                        1 -> 3 // Truck
                        2 -> 1 // Motorcycle
                        else -> 2 // Sports Car
                    }
                    val baseSpeed = when (sub) {
                        1 -> 0.025f // Truck (slow)
                        2 -> 0.06f  // Motorcycle (very fast)
                        else -> 0.04f // Sports Car
                    }
                    val speed = (baseSpeed + Random.nextFloat() * 0.015f) * direction * speedMult
                    val count = if (sub == 1) 2 else 3
                    val spacing = if (sub == 1) 6f else 4.5f
                    repeat(count) { i ->
                        entities.add(Entity(i * spacing, length, Color.RED, subType = sub))
                    }
                    lanes.add(Lane(r, speed, entities, false))
                }
                TerrainType.RIVER -> {
                    val sub = Random.nextInt(3)
                    val length = when (sub) {
                        0 -> 3 // Log
                        1 -> 1 // Lilypad
                        else -> 2 // Turtle
                    }
                    val baseSpeed = when (sub) {
                        1 -> 0.02f // Lilypad
                        else -> 0.03f
                    }
                    val speed = (baseSpeed + Random.nextFloat() * 0.01f) * direction * speedMult
                    val spacing = when (sub) {
                        0 -> 5f
                        1 -> 4f
                        else -> 4.5f
                    }
                    repeat(3) { i ->
                        entities.add(Entity(i * spacing, length, Color.parseColor("#8D6E63"), true, subType = sub))
                    }
                    lanes.add(Lane(r, speed, entities, true))
                }
                TerrainType.RAILROAD -> {
                    val sub = Random.nextInt(3)
                    val length = when (sub) {
                        0 -> 5 // Bullet Train
                        1 -> 7 // Cargo Train
                        else -> 1 // Handcart
                    }
                    val speed = when (sub) {
                        0 -> 0.12f // Bullet Train
                        1 -> 0.08f // Cargo Train
                        else -> 0.035f // Handcart
                    } * direction * speedMult
                    
                    if (sub == 2) {
                        repeat(3) { i ->
                            entities.add(Entity(i * 5f, length, Color.LTGRAY, subType = sub))
                        }
                    } else {
                        entities.add(Entity(0f, length, Color.LTGRAY, subType = sub))
                    }
                    lanes.add(Lane(r, speed, entities, false))
                }
                TerrainType.LAVA -> {
                    val sub = Random.nextInt(3)
                    val length = when (sub) {
                        1 -> 3 // Slab
                        2 -> 1 // Skull
                        else -> 2 // Rock
                    }
                    val baseSpeed = when (sub) {
                        2 -> 0.045f // Skull
                        1 -> 0.02f  // Slab
                        else -> 0.03f
                    }
                    val speed = (baseSpeed + Random.nextFloat() * 0.01f) * direction * speedMult
                    val spacing = when (sub) {
                        1 -> 6f
                        else -> 4.5f
                    }
                    repeat(3) { i ->
                        entities.add(Entity(i * spacing, length, Color.DKGRAY, true, subType = sub))
                    }
                    lanes.add(Lane(r, speed, entities, true))
                }
                else -> {}
            }
        }
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
        if (isLevelCleared) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                isLevelCleared = false
                resetFrog()
                resume()
                return true
            }
            return true
        }
        if (gamePaused && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resume()
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveFrog(-1, 0)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveFrog(1, 0)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveFrog(0, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveFrog(0, 1)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> {
                showOptions()
                return true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun showOptions() {
        pause()
        FroggyOptionsDialog.show(context) {
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
                return true
            }
            if (gamePaused) {
                resume()
                return true
            }

            // Quadrant based moves
            val centerX = width / 2f
            val centerY = height / 2f
            val x = event.x
            val y = event.y

            if (Math.abs(x - centerX) > Math.abs(y - centerY)) {
                if (x > centerX) moveFrog(0, 1) else moveFrog(0, -1)
            } else {
                if (y > centerY) moveFrog(1, 0) else moveFrog(-1, 0)
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun moveFrog(dr: Int, dc: Int) {
        if (gamePaused || gameOver) return
        frogR = (frogR + dr).coerceIn(0, rows - 1)
        frogX = (frogX + dc).coerceIn(0f, (cols - 1).toFloat())
        jumpStartTime = System.currentTimeMillis()
        SoundManager.playJump()
        
        if (frogR == 0) {
            score += 1000
            isLevelCleared = true
            gamePaused = true
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            val oldBest = best
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, currentDifficultyIndex)
            if (isNewHigh) best = score
            celebrationManager.startOutcome(
                width = width.toFloat(),
                height = height.toFloat(),
                isWin = true,
                isNewHigh = isNewHigh,
                score = score,
                highScore = oldBest
            )
            SoundManager.playSuccess()
            animHandler.removeCallbacks(animRunnable)
            animHandler.post(animRunnable)
        }
    }

    private fun die() {
        if (gameOver || gamePaused) return
        lives--
        SoundManager.playError()
        if (lives <= 0) {
            gameOver = true
            gamePaused = true
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, currentDifficultyIndex)
            if (isNewHigh) best = score
            celebrationManager.startOutcome(
                width = width.toFloat(),
                height = height.toFloat(),
                isWin = false,
                isNewHigh = isNewHigh,
                score = score,
                highScore = best
            )
            onGameOver?.invoke(score)
        } else {
            resetFrog()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (hintShowFrames > 0) {
            hintShowFrames--
            invalidate()
        }

        cellW = width.toFloat() / cols
        cellH = height.toFloat() / rows

        // Draw Background
        for (r in 0 until rows) {
            val terrain = getTerrainType(r)
            when (terrain) {
                TerrainType.GRASS -> {
                    paint.color = Color.parseColor("#2E7D32")
                    canvas.drawRect(0f, r * cellH, width.toFloat(), (r + 1) * cellH, paint)
                    paint.color = Color.parseColor("#1B5E20")
                    paint.strokeWidth = 2f
                    repeat(4) { idx ->
                        val gx = (idx * 3 + 1) * (width / 12f)
                        canvas.drawLine(gx, r * cellH + cellH * 0.7f, gx - 5, r * cellH + cellH * 0.3f, paint)
                        canvas.drawLine(gx, r * cellH + cellH * 0.7f, gx + 5, r * cellH + cellH * 0.3f, paint)
                    }
                }
                TerrainType.ROAD -> {
                    paint.color = Color.parseColor("#212121")
                    canvas.drawRect(0f, r * cellH, width.toFloat(), (r + 1) * cellH, paint)
                    paint.color = Color.parseColor("#FFD54F")
                    paint.strokeWidth = 3f
                    paint.pathEffect = DashPathEffect(floatArrayOf(30f, 30f), 0f)
                    canvas.drawLine(0f, r * cellH + cellH / 2, width.toFloat(), r * cellH + cellH / 2, paint)
                    paint.pathEffect = null
                }
                TerrainType.RIVER -> {
                    paint.color = Color.parseColor("#0277BD")
                    canvas.drawRect(0f, r * cellH, width.toFloat(), (r + 1) * cellH, paint)
                }
                TerrainType.RAILROAD -> {
                    paint.color = Color.parseColor("#37474F")
                    canvas.drawRect(0f, r * cellH, width.toFloat(), (r + 1) * cellH, paint)
                    paint.color = Color.parseColor("#4E342E")
                    val tieWidth = 14f
                    val tieSpacing = width / 12f
                    paint.style = Paint.Style.FILL
                    for (i in 0..12) {
                        canvas.drawRect(i * tieSpacing + tieSpacing/2 - tieWidth/2, r * cellH + cellH * 0.15f, i * tieSpacing + tieSpacing/2 + tieWidth/2, r * cellH + cellH * 0.85f, paint)
                    }
                    paint.color = Color.parseColor("#B0BEC5")
                    paint.strokeWidth = 6f
                    canvas.drawLine(0f, r * cellH + cellH * 0.3f, width.toFloat(), r * cellH + cellH * 0.3f, paint)
                    canvas.drawLine(0f, r * cellH + cellH * 0.7f, width.toFloat(), r * cellH + cellH * 0.7f, paint)
                }
                TerrainType.LAVA -> {
                    paint.color = Color.parseColor("#BF360C")
                    canvas.drawRect(0f, r * cellH, width.toFloat(), (r + 1) * cellH, paint)
                    paint.color = Color.parseColor("#FF6D00")
                    paint.strokeWidth = 4f
                    val timePulse = (System.currentTimeMillis() / 200L) % 4
                    repeat(3) { idx ->
                        val lx = (idx * 4 + timePulse) * (width / 13f)
                        canvas.drawLine(lx, r * cellH + cellH * 0.2f, lx + 30f, r * cellH + cellH * 0.5f, paint)
                        canvas.drawLine(lx + 30f, r * cellH + cellH * 0.5f, lx, r * cellH + cellH * 0.8f, paint)
                    }
                }
            }
        }

        // update() - Moved to gameLoop

        // Draw Entities
        for (lane in lanes) {
            for (e in lane.entities) {
                entityRect.set(e.c * cellW, lane.r * cellH + 5, (e.c + e.length) * cellW, (lane.r + 1) * cellH - 5)
                drawEntity(canvas, entityRect, e, lane.speed, lane.r)
                
                // Wrap around drawing
                if (e.c + e.length > cols) {
                    tempRect.set((e.c - cols) * cellW, lane.r * cellH + 5, (e.c + e.length - cols) * cellW, (lane.r + 1) * cellH - 5)
                    drawEntity(canvas, tempRect, e, lane.speed, lane.r)
                }
            }
        }

        // Improved Jump Animation (Squash & Stretch + Jump Arc Translation)
        val now = System.currentTimeMillis()
        val elapsed = now - jumpStartTime
        val isJumping = elapsed < JUMP_DURATION
        
        val idlePulse = if (gamePaused || gameOver) 1.0f else (1.0f + 0.05f * Math.sin(now / 150.0).toFloat())
        var animScaleX = idlePulse
        var animScaleY = idlePulse
        var animOffsetY = 0f
        
        if (isJumping) {
            val t = elapsed.toFloat() / JUMP_DURATION
            animOffsetY = -25f * sin(t * PI.toFloat())
            if (t < 0.5f) {
                val progress = t / 0.5f
                animScaleY = 1.0f + 0.25f * sin(progress * PI.toFloat() / 2f).toFloat()
                animScaleX = 1.0f - 0.15f * sin(progress * PI.toFloat() / 2f).toFloat()
            } else {
                val progress = (t - 0.5f) / 0.5f
                animScaleY = 1.0f - 0.2f * sin(progress * PI.toFloat()).toFloat()
                animScaleX = 1.0f + 0.15f * sin(progress * PI.toFloat()).toFloat()
            }
            invalidate() // Keep animating during jump
        }
        
        val frogRadius = cellH * 0.35f
        val fx = frogX * cellW + cellW / 2
        val fy = frogR * cellH + cellH / 2 + animOffsetY

        val bodyColor = when(selectedFrogType) {
            1 -> Color.parseColor("#FFD700") // Gold (Golden Dart)
            2 -> Color.parseColor("#673AB7") // Purple (Bullfrog)
            else -> Color.parseColor("#4CAF50") // Green (Classic)
        }
        val limbColor = when(selectedFrogType) {
            1 -> Color.parseColor("#1E88E5") // Blue limbs
            2 -> Color.parseColor("#4527A0") // Darker purple limbs
            else -> Color.parseColor("#2E7D32") // Darker green limbs
        }
        val cheekColor = when(selectedFrogType) {
            1 -> Color.parseColor("#E53935") // Red spots
            2 -> Color.parseColor("#FFD54F") // Yellow throat/spots
            else -> Color.parseColor("#FFCDD2") // Pink cheeks
        }

        canvas.save()
        canvas.translate(fx, fy)
        canvas.scale(animScaleX, animScaleY)

        // Draw shadow (translate slightly down, draw translucent circle)
        paint.color = Color.parseColor("#33000000")
        canvas.drawCircle(0f, 5f, frogRadius, paint)

        // Crouch legs
        paint.color = limbColor
        canvas.drawCircle(-frogRadius * 0.7f, frogRadius * 0.5f, frogRadius * 0.35f, paint)
        canvas.drawCircle(frogRadius * 0.7f, frogRadius * 0.5f, frogRadius * 0.35f, paint)
        canvas.drawCircle(-frogRadius * 0.6f, -frogRadius * 0.3f, frogRadius * 0.25f, paint)
        canvas.drawCircle(frogRadius * 0.6f, -frogRadius * 0.3f, frogRadius * 0.25f, paint)

        // Main body
        paint.color = bodyColor
        canvas.drawCircle(0f, 0f, frogRadius, paint)

        // Spots/details
        if (selectedFrogType == 1) {
            // Gold spots on blue limbs
            paint.color = Color.parseColor("#FFD700")
            canvas.drawCircle(-frogRadius * 0.7f, frogRadius * 0.5f, 3f, paint)
            canvas.drawCircle(frogRadius * 0.7f, frogRadius * 0.5f, 3f, paint)
        } else if (selectedFrogType == 2) {
            // Yellow throat overlay
            paint.color = Color.parseColor("#FFEB3B")
            canvas.drawCircle(0f, frogRadius * 0.4f, frogRadius * 0.5f, paint)
        }

        // Eyes
        val eyeColor = if (selectedFrogType == 2) Color.parseColor("#FF9800") else Color.WHITE
        paint.color = eyeColor
        canvas.drawCircle(-frogRadius * 0.35f, -frogRadius * 0.35f, frogRadius * 0.28f, paint)
        canvas.drawCircle(frogRadius * 0.35f, -frogRadius * 0.35f, frogRadius * 0.28f, paint)

        paint.color = Color.BLACK
        canvas.drawCircle(-frogRadius * 0.35f, -frogRadius * 0.4f, frogRadius * 0.14f, paint)
        canvas.drawCircle(frogRadius * 0.35f, -frogRadius * 0.4f, frogRadius * 0.14f, paint)

        // Cheeks / Spots
        paint.color = cheekColor
        if (selectedFrogType == 1) {
            // Red warning spots on gold back
            canvas.drawCircle(-frogRadius * 0.3f, frogRadius * 0.2f, 4f, paint)
            canvas.drawCircle(frogRadius * 0.3f, frogRadius * 0.2f, 4f, paint)
            canvas.drawCircle(0f, -frogRadius * 0.1f, 5f, paint)
        } else {
            // Cheeks
            canvas.drawCircle(-frogRadius * 0.4f, frogRadius * 0.1f, frogRadius * 0.15f, paint)
            canvas.drawCircle(frogRadius * 0.4f, frogRadius * 0.1f, frogRadius * 0.15f, paint)
        }

        canvas.restore()

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.style = Paint.Style.FILL
        val hudY = height * 0.05f
        
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${context.getString(R.string.score_label)}: $score  ${context.getString(R.string.lives_label)}: $lives", 40f, hudY, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.LTGRAY
        canvas.drawText("${context.getString(R.string.level_label)}: ${currentDifficultyIndex + 1}", width / 2f, hudY, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, hudY + 45f, paint)
            paint.alpha = 255
        }

        if (gameOver) {
            drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (isLevelCleared) {
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.victory_label)
            drawOverlay(canvas, title, "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.continue_hint)}")
        } else if (gamePaused) {
            drawOverlay(canvas, context.getString(R.string.game_froggy), context.getString(R.string.start_game))
        }

        if (gameOver || isLevelCleared) {
            celebrationManager.draw(canvas)
        }
    }

    private fun drawEntity(canvas: Canvas, rect: RectF, entity: Entity, speed: Float, r: Int) {
        val terrain = getTerrainType(r)
        val sub = entity.subType

        when (terrain) {
            TerrainType.ROAD -> {
                if (sub == 1) { // Cargo Truck
                    paint.color = Color.parseColor("#F57C00")
                    canvas.drawRoundRect(rect.left, rect.top, rect.right - rect.width()*0.25f, rect.bottom, 6f, 6f, paint)
                    paint.color = Color.parseColor("#FFE082")
                    canvas.drawRoundRect(rect.right - rect.width()*0.27f, rect.top + 4, rect.right, rect.bottom - 4, 8f, 8f, paint)
                    paint.color = Color.parseColor("#37474F")
                    val wx1 = if (speed > 0) rect.right - rect.width() * 0.12f else rect.right - rect.width() * 0.22f
                    val wx2 = if (speed > 0) rect.right - rect.width() * 0.04f else rect.right - rect.width() * 0.14f
                    canvas.drawRect(wx1, rect.top + 8, wx2, rect.bottom - 8, paint)
                    paint.color = Color.BLACK
                    canvas.drawCircle(rect.left + 20, rect.top, 10f, paint)
                    canvas.drawCircle(rect.right - 20, rect.top, 10f, paint)
                    canvas.drawCircle(rect.left + 20, rect.bottom, 10f, paint)
                    canvas.drawCircle(rect.right - 20, rect.bottom, 10f, paint)
                } else if (sub == 2) { // Motorcycle
                    paint.color = Color.parseColor("#00E5FF")
                    val cy = rect.top + rect.height() / 2f
                    canvas.drawRoundRect(rect.left + 5, cy - 8, rect.right - 5, cy + 8, 4f, 4f, paint)
                    paint.color = Color.BLACK
                    canvas.drawCircle(rect.left + 12, cy, 11f, paint)
                    canvas.drawCircle(rect.right - 12, cy, 11f, paint)
                    paint.color = Color.WHITE
                    canvas.drawCircle(rect.left + 12, cy, 4f, paint)
                    canvas.drawCircle(rect.right - 12, cy, 4f, paint)
                } else { // Sports Car
                    val carColor = when (r % 3) {
                        0 -> Color.parseColor("#E53935")
                        1 -> Color.parseColor("#1E88E5")
                        else -> Color.parseColor("#8E24AA")
                    }
                    paint.color = carColor
                    canvas.drawRoundRect(rect, 10f, 10f, paint)
                    paint.color = Color.BLACK
                    canvas.drawCircle(rect.left + 15, rect.top, 8f, paint)
                    canvas.drawCircle(rect.right - 15, rect.top, 8f, paint)
                    canvas.drawCircle(rect.left + 15, rect.bottom, 8f, paint)
                    canvas.drawCircle(rect.right - 15, rect.bottom, 8f, paint)
                    paint.color = Color.parseColor("#B3E5FC")
                    val wx1 = if (speed > 0) rect.right - rect.width() * 0.4f else rect.left + rect.width() * 0.15f
                    val wx2 = if (speed > 0) rect.right - rect.width() * 0.15f else rect.left + rect.width() * 0.4f
                    canvas.drawRect(wx1, rect.top + 8, wx2, rect.bottom - 8, paint)
                    paint.color = Color.YELLOW
                    if (speed > 0) {
                        canvas.drawCircle(rect.right - 4, rect.top + 10, 5f, paint)
                        canvas.drawCircle(rect.right - 4, rect.bottom - 10, 5f, paint)
                    } else {
                        canvas.drawCircle(rect.left + 4, rect.top + 10, 5f, paint)
                        canvas.drawCircle(rect.left + 4, rect.bottom - 10, 5f, paint)
                    }
                }
            }
            TerrainType.RIVER -> {
                if (sub == 1) { // Lilypad
                    paint.color = Color.parseColor("#4CAF50")
                    val cx = rect.left + rect.width() / 2
                    val cy = rect.top + rect.height() / 2
                    val rd = rect.height() * 0.45f
                    canvas.drawCircle(cx, cy, rd, paint)
                    paint.color = Color.parseColor("#0277BD")
                    tempPath.reset()
                    tempPath.moveTo(cx, cy)
                    val angle = 30f
                    tempPath.lineTo(cx + rd * cos(Math.toRadians(angle.toDouble())).toFloat(), cy - rd * sin(Math.toRadians(angle.toDouble())).toFloat())
                    tempPath.lineTo(cx + rd * cos(Math.toRadians(-angle.toDouble())).toFloat(), cy - rd * sin(Math.toRadians(-angle.toDouble())).toFloat())
                    tempPath.close()
                    canvas.drawPath(tempPath, paint)
                } else if (sub == 2) { // Turtle
                    paint.color = Color.parseColor("#00695C")
                    val cx = rect.left + rect.width() / 2
                    val cy = rect.top + rect.height() / 2
                    val rx = rect.width() * 0.4f
                    val ry = rect.height() * 0.4f
                    canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
                    paint.color = Color.parseColor("#00897B")
                    canvas.drawCircle(cx - rx * 0.7f, cy - ry * 0.7f, 6f, paint)
                    canvas.drawCircle(cx + rx * 0.7f, cy - ry * 0.7f, 6f, paint)
                    canvas.drawCircle(cx - rx * 0.7f, cy + ry * 0.7f, 6f, paint)
                    canvas.drawCircle(cx + rx * 0.7f, cy + ry * 0.7f, 6f, paint)
                    canvas.drawCircle(if (speed > 0) cx + rx else cx - rx, cy, 8f, paint)
                } else { // Log
                    paint.color = Color.parseColor("#5D4037")
                    canvas.drawRoundRect(rect, 12f, 12f, paint)
                    paint.color = Color.parseColor("#8D6E63")
                    canvas.drawCircle(rect.left + 15, rect.top + rect.height()/2, 10f, paint)
                    canvas.drawCircle(rect.right - 15, rect.top + rect.height()/2, 10f, paint)
                    paint.color = Color.parseColor("#3E2723")
                    paint.strokeWidth = 3f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(rect.left + 35, rect.top + 12, rect.right - 35, rect.top + 12, paint)
                    canvas.drawLine(rect.left + 25, rect.bottom - 12, rect.right - 25, rect.bottom - 12, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#4CAF50")
                    canvas.drawCircle(rect.left + rect.width()/2, rect.top + 2, 8f, paint)
                }
            }
            TerrainType.RAILROAD -> {
                if (sub == 1) { // Cargo Freight Train
                    val numSegments = 5
                    val segmentW = rect.width() / numSegments
                    paint.style = Paint.Style.FILL
                    for (i in 0 until numSegments) {
                        val sl = rect.left + i * segmentW
                        val sr = sl + segmentW
                        val isFront = (speed > 0 && i == numSegments - 1) || (speed < 0 && i == 0)
                        if (isFront) {
                            paint.color = Color.parseColor("#263238")
                            canvas.drawRoundRect(sl + 2, rect.top + 2, sr - 2, rect.bottom - 2, 6f, 6f, paint)
                            paint.color = Color.parseColor("#FFD54F")
                            canvas.drawRect(if (speed > 0) sr - 8 else sl + 2, rect.top + 8, if (speed > 0) sr - 2 else sl + 8, rect.bottom - 8, paint)
                        } else {
                            paint.color = when (i % 3) {
                                0 -> Color.parseColor("#C62828")
                                1 -> Color.parseColor("#1565C0")
                                else -> Color.parseColor("#2E7D32")
                            }
                            canvas.drawRoundRect(sl + 4, rect.top + 4, sr - 4, rect.bottom - 4, 4f, 4f, paint)
                            paint.color = Color.BLACK
                            canvas.drawRect(sl, rect.top + rect.height()*0.45f, sl + 4, rect.top + rect.height()*0.55f, paint)
                        }
                    }
                } else if (sub == 2) { // Handcart
                    paint.color = Color.parseColor("#8D6E63")
                    canvas.drawRoundRect(rect.left + 5, rect.top + 6, rect.right - 5, rect.bottom - 6, 4f, 4f, paint)
                    paint.color = Color.DKGRAY
                    paint.strokeWidth = 4f
                    val cx = rect.left + rect.width() / 2f
                    val cy = rect.top + rect.height() / 2f
                    canvas.drawLine(cx - 10f, cy, cx + 10f, cy, paint)
                    paint.color = Color.parseColor("#B0BEC5")
                    val leverOffset = 10f * sin((System.currentTimeMillis() / 120f)).toFloat()
                    canvas.drawLine(cx, cy, cx, cy - 12f + leverOffset, paint)
                    canvas.drawLine(cx - 8f, cy - 12f + leverOffset, cx + 8f, cy - 12f + leverOffset, paint)
                } else { // Bullet Train
                    paint.color = Color.parseColor("#ECEFF1")
                    canvas.drawRoundRect(rect, 15f, 15f, paint)
                    paint.color = Color.parseColor("#D50000")
                    canvas.drawRect(rect.left, rect.top + rect.height() * 0.4f, rect.right, rect.top + rect.height() * 0.6f, paint)
                    paint.color = Color.parseColor("#263238")
                    val windowW = rect.width() / 8f
                    for (w in 1..6) {
                        canvas.drawRoundRect(rect.left + w * windowW + 5, rect.top + 6, rect.left + (w + 1) * windowW - 5, rect.top + rect.height() * 0.35f, 4f, 4f, paint)
                    }
                    paint.color = Color.parseColor("#37474F")
                    if (speed > 0) {
                        tempPath.reset()
                        tempPath.moveTo(rect.right - 25f, rect.top + 6)
                        tempPath.lineTo(rect.right - 5f, rect.top + rect.height()/2)
                        tempPath.lineTo(rect.right - 25f, rect.bottom - 6)
                        tempPath.close()
                        canvas.drawPath(tempPath, paint)
                    } else {
                        tempPath.reset()
                        tempPath.moveTo(rect.left + 25f, rect.top + 6)
                        tempPath.lineTo(rect.left + 5f, rect.top + rect.height()/2)
                        tempPath.lineTo(rect.left + 25f, rect.bottom - 6)
                        tempPath.close()
                        canvas.drawPath(tempPath, paint)
                    }
                }
            }
            TerrainType.LAVA -> {
                if (sub == 1) { // Obsidian Slab
                    paint.color = Color.parseColor("#263238")
                    canvas.drawRoundRect(rect, 8f, 8f, paint)
                    paint.color = Color.parseColor("#FF6D00")
                    paint.strokeWidth = 3f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(rect.left + 20, rect.top + 10, rect.right - 20, rect.bottom - 10, paint)
                    canvas.drawLine(rect.left + 40, rect.bottom - 10, rect.right - 40, rect.top + 10, paint)
                    paint.style = Paint.Style.FILL
                } else if (sub == 2) { // Magma Skull
                    val cx = rect.left + rect.width() / 2f
                    val cy = rect.top + rect.height() / 2f
                    paint.color = Color.parseColor("#FFD54F")
                    canvas.drawCircle(cx, cy - 2f, 15f, paint)
                    canvas.drawRoundRect(cx - 8f, cy + 8f, cx + 8f, cy + 16f, 2f, 2f, paint)
                    paint.color = Color.parseColor("#DD2C00")
                    canvas.drawCircle(cx - 5f, cy - 2f, 4f, paint)
                    canvas.drawCircle(cx + 5f, cy - 2f, 4f, paint)
                } else { // Obsidian Rock
                    paint.color = Color.parseColor("#212121")
                    canvas.drawRoundRect(rect, 12f, 12f, paint)
                    paint.color = Color.parseColor("#FF3D00")
                    paint.strokeWidth = 3f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(rect.left + 15, rect.top + 15, rect.right - 15, rect.bottom - 15, paint)
                    canvas.drawLine(rect.left + 15, rect.bottom - 15, rect.right - 15, rect.top + 15, paint)
                    paint.style = Paint.Style.FILL
                }
            }
            else -> {}
        }
    }

    private fun update() {
        if (gamePaused || gameOver) return
        val now = System.currentTimeMillis()
        if (lastUpdate == 0L) lastUpdate = now
        val dt = (now - lastUpdate).coerceAtMost(50L)
        lastUpdate = now

        var onLog = false
        var logSpeed = 0f
        for (lane in lanes) {
            for (e in lane.entities) {
                e.c += lane.speed
                if (e.c > cols) e.c -= cols
                if (e.c < 0) e.c += cols

                // Collision Check
                if (frogR == lane.r) {
                    val frogPos = frogX + 0.5f
                    val hit = if (e.c + e.length > cols) {
                        frogPos >= e.c || frogPos < (e.c + e.length - cols)
                    } else {
                        frogPos >= e.c && frogPos < (e.c + e.length)
                    }

                    if (lane.isRiver) {
                        if (hit) {
                            onLog = true
                            logSpeed = lane.speed
                        }
                    } else {
                        if (hit) {
                            die()
                            return
                        }
                    }
                }
            }
        }

        val terrain = getTerrainType(frogR)
        if (terrain == TerrainType.RIVER || terrain == TerrainType.LAVA) {
            if (!onLog) {
                die()
                return
            } else {
                // Move frog with platform drift
                frogX = (frogX + logSpeed).coerceIn(0f, (cols - 1).toFloat())
            }
        }
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
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 40f + i * 40f, paint)
        }
    }
}
