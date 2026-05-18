package com.tdpham.games.tanks

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
import kotlin.random.Random

class BattleTanksView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "battle_tanks"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rows = 13
    private val cols = 13
    private var cellW = 0f
    private var cellH = 0f

    private val grid = Array(rows) { IntArray(cols) } // 0:Empty, 1:Brick, 2:Steel, 3:Base, 4:Trees
    private var player = Tank(6, 12, 0, true)
    private val enemies = mutableListOf<Tank>()
    private val bullets = mutableListOf<Bullet>()
    private val particles = mutableListOf<GameEnvironment.Particle>()
    
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true
    private var level = 1
    
    private var bgType = GameEnvironment.BackgroundType.SOLID
    private var isNight = false

    data class Tank(var x: Int, var y: Int, var dir: Int, val isPlayer: Boolean, var lastFire: Long = 0, var hp: Int = 1)
    data class Bullet(var fx: Float, var fy: Float, var dir: Int, val isPlayer: Boolean)

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
        score = 0
        level = 1
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
        setupLevel()
        invalidate()
    }

    private fun setupLevel() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c] = 0
        
        bgType = listOf(GameEnvironment.BackgroundType.SOLID, GameEnvironment.BackgroundType.GRID, GameEnvironment.BackgroundType.DOTS).random()
        isNight = Random.nextBoolean()
        
        // Random map generation
        for (r in 1 until rows - 1) {
            for (c in 0 until cols) {
                if (Random.nextFloat() < 0.25) grid[r][c] = 1 // Brick
                else if (Random.nextFloat() < 0.08) grid[r][c] = 2 // Steel
                else if (Random.nextFloat() < 0.05) grid[r][c] = 4 // Trees
            }
        }
        
        grid[rows - 1][cols / 2] = 3 // Base
        // Ensure path to base is clear
        grid[rows - 1][cols / 2 - 1] = 1
        grid[rows - 1][cols / 2 + 1] = 1
        grid[rows - 2][cols / 2] = 1
        
        player = Tank(cols / 2 - 2, rows - 1, 0, true)
        enemies.clear()
        bullets.clear()
        particles.clear()
        repeat(20) { particles.add(GameEnvironment.Particle(Random.nextFloat() * 2000, Random.nextFloat() * 2000, Random.nextFloat() * 5)) }
        spawnEnemy()
    }

    private fun spawnEnemy() {
        if (enemies.size < (level + 2).coerceAtMost(6)) {
            val ex = listOf(0, cols / 2, cols - 1).random()
            enemies.add(Tank(ex, 0, 2, false, hp = if (Random.nextFloat() < 0.2) 2 else 1))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame(); resume(); return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (gamePaused && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resume(); return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveTank(player, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveTank(player, 1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveTank(player, 2)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveTank(player, 3)
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun moveTank(tank: Tank, dir: Int) {
        tank.dir = dir
        var nx = tank.x
        var ny = tank.y
        when (dir) {
            0 -> ny--
            1 -> nx++
            2 -> ny++
            3 -> nx--
        }
        if (nx in 0 until cols && ny in 0 until rows && (grid[ny][nx] == 0 || grid[ny][nx] == 4)) {
            tank.x = nx
            tank.y = ny
        }
    }

    private fun fire(tank: Tank) {
        val now = System.currentTimeMillis()
        if (now - tank.lastFire > 600) {
            bullets.add(Bullet(tank.x.toFloat(), tank.y.toFloat(), tank.dir, tank.isPlayer))
            tank.lastFire = now
            if (tank.isPlayer) SoundManager.playClick()
        }
    }

    override fun onDraw(canvas: Canvas) {
        cellW = width.toFloat() / cols
        cellH = height.toFloat() / rows

        if (!gamePaused && !gameOver) update()

        GameEnvironment.draw(canvas, bgType, isNight = isNight, paint = paint, particles = particles)

        // Draw Map
        for (r in 0 until rows) for (c in 0 until cols) {
            val type = grid[r][c]
            if (type == 0) continue
            paint.style = Paint.Style.FILL
            when (type) {
                1 -> {
                    paint.color = Color.parseColor("#A1887F") // Brick
                    canvas.drawRect(c * cellW + 4, r * cellH + 4, (c + 1) * cellW - 4, (r + 1) * cellH - 4, paint)
                    paint.color = Color.parseColor("#795548")
                    canvas.drawRect(c * cellW + 4, r * cellH + cellH/2, (c + 1) * cellW - 4, r * cellH + cellH/2 + 4, paint)
                }
                2 -> {
                    paint.color = Color.LTGRAY // Steel
                    canvas.drawRect(c * cellW + 2, r * cellH + 2, (c + 1) * cellW - 2, (r + 1) * cellH - 2, paint)
                    paint.color = Color.WHITE
                    canvas.drawRect(c * cellW + 8, r * cellH + 8, (c + 1) * cellW - 8, (r + 1) * cellH - 8, paint)
                }
                3 -> {
                    paint.color = Color.parseColor("#FFD600")
                    canvas.drawCircle(c * cellW + cellW/2, r * cellH + cellH/2, cellW/3, paint)
                    paint.color = Color.BLACK
                    paint.textSize = cellW * 0.5f
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("⭐", c * cellW + cellW/2, r * cellH + cellH/2 + cellH/6, paint)
                }
                4 -> {
                    paint.color = Color.parseColor("#2E7D32")
                    canvas.drawCircle(c * cellW + cellW/2, r * cellH + cellH/2, cellW/2.5f, paint)
                }
            }
        }

        // Draw Player
        drawTank(canvas, player, Color.parseColor("#4CAF50"))

        // Draw Enemies
        for (e in enemies) drawTank(canvas, e, if (e.hp > 1) Color.parseColor("#E91E63") else Color.parseColor("#F44336"))

        // Draw Bullets
        paint.color = Color.YELLOW
        for (b in bullets) {
            canvas.drawCircle(b.fx * cellW + cellW / 2, b.fy * cellH + cellH / 2, 6f, paint)
        }

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score  LVL: $level", 20f, 40f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 20f, 40f, paint)

        if (gameOver) drawOverlay(canvas, "MISSION FAILED", "Score: $score | Center to Restart")
        else if (gamePaused) drawOverlay(canvas, "BATTLE TANKS", "Level $level | Center to Start")

        if (!gamePaused && !gameOver) invalidate()
    }

    private fun drawTank(canvas: Canvas, tank: Tank, color: Int) {
        val x = tank.x * cellW
        val y = tank.y * cellH
        val centerX = x + cellW / 2
        val centerY = y + cellH / 2

        // Tracks
        paint.color = Color.DKGRAY
        canvas.drawRect(x + 2, y + 2, x + 12, y + cellH - 2, paint)
        canvas.drawRect(x + cellW - 12, y + 2, x + cellW - 2, y + cellH - 2, paint)
        
        paint.color = color
        canvas.drawRoundRect(x + 10, y + 10, x + cellW - 10, y + cellH - 10, 4f, 4f, paint)
        
        // Turret
        paint.color = color
        canvas.drawCircle(centerX, centerY, cellW * 0.22f, paint)
        paint.color = Color.argb(50, 0, 0, 0)
        canvas.drawCircle(centerX, centerY, cellW * 0.15f, paint)

        // Barrel
        paint.color = Color.WHITE
        val bw = 5f
        when (tank.dir) {
            0 -> canvas.drawRect(centerX-bw, y + 4, centerX+bw, centerY, paint)
            1 -> canvas.drawRect(centerX, centerY-bw, x+cellW-4, centerY+bw, paint)
            2 -> canvas.drawRect(centerX-bw, centerY, centerX+bw, y+cellH-4, paint)
            3 -> canvas.drawRect(x+4, centerY-bw, centerX, centerY+bw, paint)
        }
    }

    private fun update() {
        fire(player)

        val bIter = bullets.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            val speed = 0.25f
            when (b.dir) {
                0 -> b.fy -= speed
                1 -> b.fx += speed
                2 -> b.fy += speed
                3 -> b.fx -= speed
            }

            val bx = b.fx.toInt()
            val by = b.fy.toInt()

            if (bx !in 0 until cols || by !in 0 until rows) { bIter.remove(); continue }

            val tile = grid[by][bx]
            if (tile != 0 && tile != 4) { // Hits something solid
                if (tile == 1) grid[by][bx] = 0 // Break brick
                if (tile == 3) {
                    gameOver = true; gamePaused = true; SoundManager.playError()
                }
                bIter.remove(); continue
            }

            if (b.isPlayer) {
                val eIter = enemies.iterator()
                var hit = false
                while (eIter.hasNext()) {
                    val e = eIter.next()
                    if (e.x == bx && e.y == by) {
                        e.hp--
                        if (e.hp <= 0) {
                            eIter.remove()
                            score += 100
                            SoundManager.playScore()
                            if (score % 1000 == 0) { level++; setupLevel() }
                        }
                        hit = true; break
                    }
                }
                if (hit) { bIter.remove(); continue }
            } else {
                if (player.x == bx && player.y == by) {
                    gameOver = true; gamePaused = true; SoundManager.playError()
                    bIter.remove(); continue
                }
            }
        }

        if (Random.nextFloat() < 0.02) spawnEnemy()
        for (e in enemies) {
            if (Random.nextFloat() < 0.08) moveTank(e, Random.nextInt(4))
            if (Random.nextFloat() < 0.03) fire(e)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 80f
        canvas.drawText(title, width / 2f, height / 2f, paint)
        paint.textSize = 30f
        canvas.drawText(sub, width / 2f, height / 2f + 60f, paint)
    }
}
