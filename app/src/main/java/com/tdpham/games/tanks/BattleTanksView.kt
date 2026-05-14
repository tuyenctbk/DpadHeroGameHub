package com.tdpham.games.tanks

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

    private val grid = Array(rows) { IntArray(cols) } // 0:Empty, 1:Brick, 2:Steel, 3:Base
    private var player = Tank(6, 12, 0, true)
    private val enemies = mutableListOf<Tank>()
    private val bullets = mutableListOf<Bullet>()
    
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true
    private var lastUpdate = 0L

    data class Tank(var x: Int, var y: Int, var dir: Int, val isPlayer: Boolean, var lastFire: Long = 0)
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
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
        setupMap()
        player = Tank(cols / 2 - 2, rows - 1, 0, true)
        enemies.clear()
        bullets.clear()
        spawnEnemy()
        invalidate()
    }

    private fun setupMap() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c] = 0
        // Simple brick layout
        for (r in 2 until rows - 2 step 2) {
            for (c in 1 until cols - 1 step 2) {
                grid[r][c] = 1
            }
        }
        grid[rows - 1][cols / 2] = 3 // Base
    }

    private fun spawnEnemy() {
        if (enemies.size < 3) {
            enemies.add(Tank(Random.nextInt(cols), 0, 2, false))
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
        if (gamePaused && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resume()
            return true
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
        if (nx in 0 until cols && ny in 0 until rows && grid[ny][nx] == 0) {
            tank.x = nx
            tank.y = ny
        }
    }

    private fun fire(tank: Tank) {
        val now = System.currentTimeMillis()
        if (now - tank.lastFire > 500) {
            bullets.add(Bullet(tank.x.toFloat(), tank.y.toFloat(), tank.dir, tank.isPlayer))
            tank.lastFire = now
            if (tank.isPlayer) SoundManager.playClick()
        }
    }

    override fun onDraw(canvas: Canvas) {
        cellW = width.toFloat() / cols
        cellH = height.toFloat() / rows

        if (!gamePaused && !gameOver) update()

        // Draw Map
        for (r in 0 until rows) for (c in 0 until cols) {
            val type = grid[r][c]
            if (type == 0) continue
            paint.color = when (type) {
                1 -> Color.parseColor("#A1887F") // Brick
                2 -> Color.LTGRAY // Steel
                3 -> Color.WHITE // Base
                else -> Color.TRANSPARENT
            }
            canvas.drawRect(c * cellW + 2, r * cellH + 2, (c + 1) * cellW - 2, (r + 1) * cellH - 2, paint)
            if (type == 3) {
                paint.color = Color.RED
                canvas.drawCircle(c * cellW + cellW/2, r * cellH + cellH/2, cellW/4, paint)
            }
        }

        // Draw Player
        drawTank(canvas, player, Color.GREEN)

        // Draw Enemies
        for (e in enemies) drawTank(canvas, e, Color.RED)

        // Draw Bullets
        paint.color = Color.YELLOW
        for (b in bullets) {
            canvas.drawCircle(b.fx * cellW + cellW / 2, b.fy * cellH + cellH / 2, 5f, paint)
        }

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 20f, 40f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 20f, 40f, paint)

        if (gameOver) {
            drawOverlay(canvas, "GAME OVER", "Press Center to Restart")
        } else if (gamePaused) {
            drawOverlay(canvas, "BATTLE TANKS", "Press Center to Start")
        }

        if (!gamePaused && !gameOver) invalidate()
    }

    private fun drawTank(canvas: Canvas, tank: Tank, color: Int) {
        val x = tank.x * cellW
        val y = tank.y * cellH
        val centerX = x + cellW / 2
        val centerY = y + cellH / 2
        val padding = 6f

        // Tracks (left and right)
        paint.color = Color.DKGRAY
        canvas.drawRect(x + 2, y + 2, x + 12, y + cellH - 2, paint)
        canvas.drawRect(x + cellW - 12, y + 2, x + cellW - 2, y + cellH - 2, paint)
        
        // Body with simple 3D bevel
        paint.color = color
        canvas.drawRoundRect(x + 10, y + 10, x + cellW - 10, y + cellH - 10, 4f, 4f, paint)
        
        // Highlight
        paint.color = Color.argb(60, 255, 255, 255)
        canvas.drawRect(x + 12, y + 12, x + cellW - 20, y + 18, paint)

        // Turret (Center part)
        paint.color = color
        canvas.drawCircle(centerX, centerY, cellW * 0.2f, paint)
        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawCircle(centerX, centerY, cellW * 0.15f, paint)

        // Barrel with glow/shading
        paint.color = Color.WHITE
        val barrelW = 6f
        val barrelL = cellH * 0.45f
        when (tank.dir) {
            0 -> canvas.drawRect(centerX - barrelW, y, centerX + barrelW, centerY, paint)
            1 -> canvas.drawRect(centerX, centerY - barrelW, x + cellW, centerY + barrelW, paint)
            2 -> canvas.drawRect(centerX - barrelW, centerY, centerX + barrelW, y + cellH, paint)
            3 -> canvas.drawRect(x, centerY - barrelW, centerX, centerY + barrelW, paint)
        }
    }

    private fun update() {
        val now = System.currentTimeMillis()
        if (lastUpdate == 0L) lastUpdate = now
        lastUpdate = now

        // Auto Fire
        fire(player)

        // Update Bullets
        val bIter = bullets.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            val speed = 0.2f
            when (b.dir) {
                0 -> b.fy -= speed
                1 -> b.fx += speed
                2 -> b.fy += speed
                3 -> b.fx -= speed
            }

            val bx = b.fx.toInt()
            val by = b.fy.toInt()

            if (bx !in 0 until cols || by !in 0 until rows) {
                bIter.remove()
                continue
            }

            // Hit Map
            if (grid[by][bx] != 0) {
                if (grid[by][bx] == 1) grid[by][bx] = 0 // Break brick
                if (grid[by][bx] == 3) {
                    gameOver = true
                    gamePaused = true
                    SoundManager.playError()
                }
                bIter.remove()
                continue
            }

            // Hit Tanks
            if (b.isPlayer) {
                val eIter = enemies.iterator()
                var hit = false
                while (eIter.hasNext()) {
                    val e = eIter.next()
                    if (e.x == bx && e.y == by) {
                        eIter.remove()
                        hit = true
                        score += 100
                        SoundManager.playScore()
                        break
                    }
                }
                if (hit) { bIter.remove(); continue }
            } else {
                if (player.x == bx && player.y == by) {
                    gameOver = true
                    gamePaused = true
                    SoundManager.playError()
                    bIter.remove()
                    continue
                }
            }
        }

        // Update Enemies
        if (Random.nextFloat() < 0.01) spawnEnemy()
        for (e in enemies) {
            if (Random.nextFloat() < 0.05) moveTank(e, Random.nextInt(4))
            if (Random.nextFloat() < 0.02) fire(e)
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
}
