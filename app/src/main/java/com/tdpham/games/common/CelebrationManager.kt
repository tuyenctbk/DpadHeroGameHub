package com.tdpham.games.common

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.content.Context
import com.tdpham.games.R
import java.util.*
import kotlin.math.*

class CelebrationManager {
    enum class CelebrationType {
        FIREWORKS, FLOWERS, BALLOONS, STARS, CONFETTI,
        HEARTS, DIAMONDS, SNOWFLAKES, MUSIC_NOTES, SPARKLES,
        SMILEYS, RAIN_COINS, 
        RAIN_CLOUDS, GHOSTS, BROKEN_HEARTS, BUBBLES,
        FALLING_LEAVES, FIRE_FLAMES, LIGHTNING_BOLTS, CLOUDS
    }

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val color: Int, var size: Float, val type: CelebrationType
    ) {
        var life = 1.0f
        var alpha = 255
        var rotation = 0f
        var vr = (Math.random() * 10 - 5).toFloat()
        val randomFactor = Math.random().toFloat()

        fun update() {
            x += vx
            y += vy
            rotation += vr
            
            when (type) {
                CelebrationType.FIREWORKS -> {
                    vy += 0.2f
                    life -= 0.015f
                }
                CelebrationType.FLOWERS -> {
                    vy += 0.15f
                    vx += (sin(y * 0.05).toFloat() * 0.8f)
                    life -= 0.005f
                }
                CelebrationType.BALLOONS -> {
                    vy -= 2f
                    vx += (sin(y * 0.03).toFloat() * 1.5f)
                    if (y < -100) life = 0f
                }
                CelebrationType.STARS -> {
                    vy += 0.2f
                    rotation += 5f
                    life -= 0.008f
                }
                CelebrationType.CONFETTI -> {
                    vy += 0.25f
                    vx += (sin(y * 0.02 + randomFactor * 10).toFloat() * 0.5f)
                    rotation += vr * 0.5f
                    life -= 0.004f
                }
                CelebrationType.HEARTS -> {
                    vy -= 1.5f
                    vx += (sin(y * 0.04).toFloat() * 1.0f)
                    life -= 0.006f
                }
                CelebrationType.DIAMONDS -> {
                    vy += 0.3f
                    rotation += 10f
                    life -= 0.007f
                }
                CelebrationType.SNOWFLAKES -> {
                    vy += 1.0f
                    vx += (cos(y * 0.02).toFloat() * 2.0f)
                    rotation += 2f
                    life -= 0.003f
                }
                CelebrationType.MUSIC_NOTES -> {
                    vy -= 1.8f
                    vx += (sin(y * 0.05).toFloat() * 2.0f)
                    life -= 0.006f
                }
                CelebrationType.SPARKLES -> {
                    life -= 0.05f
                    vr += 2f
                }
                CelebrationType.SMILEYS -> {
                    vy -= 1.2f
                    vx += (sin(y * 0.03).toFloat() * 1.2f)
                    life -= 0.005f
                }
                CelebrationType.RAIN_COINS -> {
                    vy += 0.4f
                    rotation += 8f
                    life -= 0.005f
                }
                CelebrationType.RAIN_CLOUDS -> {
                    vy += 4.0f
                    vx = (sin(y * 0.1).toFloat() * 0.5f)
                    life -= 0.008f
                }
                CelebrationType.GHOSTS -> {
                    vy -= 1.5f
                    vx += (sin(y * 0.05).toFloat() * 2.0f)
                    life -= 0.005f
                }
                CelebrationType.BROKEN_HEARTS -> {
                    vy += 1.5f
                    rotation += 2f
                    life -= 0.007f
                }
                CelebrationType.BUBBLES -> {
                    vy -= 1.2f
                    vx += (sin(y * 0.08).toFloat() * 1.5f)
                    life -= 0.004f
                }
                CelebrationType.FALLING_LEAVES -> {
                    vy += 0.8f
                    vx += (sin(y * 0.03 + randomFactor * 5).toFloat() * 2.5f)
                    rotation += vr * 0.8f
                    life -= 0.003f
                }
                CelebrationType.FIRE_FLAMES -> {
                    vy -= 2.5f
                    vx += (Math.random().toFloat() - 0.5f) * 2f
                    size *= 0.97f
                    life -= 0.02f
                }
                CelebrationType.LIGHTNING_BOLTS -> {
                    life -= 0.04f
                }
                CelebrationType.CLOUDS -> {
                    vx -= 0.5f
                    life -= 0.002f
                }
            }
            alpha = (life * 255).toInt().coerceIn(0, 255)
        }
    }

    private val particles = mutableListOf<Particle>()
    private val random = Random()
    private val paint = Paint().apply { isAntiAlias = true }
    private val path = Path()
    private val rectF = RectF()
    private var currentType = CelebrationType.STARS
    private var width = 0f
    private var height = 0f

    fun start(width: Float, height: Float, type: CelebrationType? = null) {
        this.width = width
        this.height = height
        this.currentType = type ?: listOf(CelebrationType.FIREWORKS, CelebrationType.RAIN_COINS, CelebrationType.CONFETTI, CelebrationType.BALLOONS, CelebrationType.STARS).random()
        particles.clear()
        initParticles()
    }

    fun startOutcome(width: Float, height: Float, isWin: Boolean = false, isNewHigh: Boolean = false, score: Int = 0, highScore: Int = 0) {
        val winEpic = listOf(CelebrationType.FIREWORKS, CelebrationType.RAIN_COINS, CelebrationType.STARS, CelebrationType.DIAMONDS)
        val winNormal = listOf(CelebrationType.CONFETTI, CelebrationType.BALLOONS, CelebrationType.SMILEYS, CelebrationType.MUSIC_NOTES, CelebrationType.FLOWERS, CelebrationType.SPARKLES)
        val lossMild = listOf(CelebrationType.BUBBLES, CelebrationType.CLOUDS, CelebrationType.FALLING_LEAVES, CelebrationType.SNOWFLAKES)
        val lossEpic = listOf(CelebrationType.RAIN_CLOUDS, CelebrationType.GHOSTS, CelebrationType.BROKEN_HEARTS, CelebrationType.FIRE_FLAMES, CelebrationType.LIGHTNING_BOLTS)

        val type = when {
            isNewHigh -> winEpic.random()
            isWin -> winNormal.random()
            highScore > 0 && score > highScore * 0.75f -> lossMild.random()
            else -> lossEpic.random()
        }
        start(width, height, type)
    }

    private fun initParticles() {
        when (currentType) {
            CelebrationType.FIREWORKS -> {
                repeat(5) {
                    val bx = random.nextFloat() * width
                    val by = random.nextFloat() * (height / 2)
                    val color = listOf(Color.RED, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.GREEN).random()
                    repeat(40) {
                        val angle = random.nextFloat() * 2 * PI.toFloat()
                        val speed = random.nextFloat() * 15 + 5
                        particles.add(Particle(bx, by, cos(angle.toDouble()).toFloat() * speed, sin(angle.toDouble()).toFloat() * speed, color, random.nextFloat() * 10 + 5, currentType))
                    }
                }
            }
            CelebrationType.FLOWERS -> {
                repeat(50) {
                    particles.add(createFallingParticle(currentType))
                }
            }
            CelebrationType.BALLOONS -> {
                repeat(20) {
                    particles.add(Particle(random.nextFloat() * width, height + random.nextFloat() * height, random.nextFloat() * 2 - 1, -random.nextFloat() * 3 - 2, randomColor(), random.nextFloat() * 30 + 30, currentType))
                }
            }
            CelebrationType.STARS -> {
                repeat(60) {
                    particles.add(createFallingParticle(currentType))
                }
            }
            CelebrationType.CONFETTI -> {
                repeat(100) {
                    particles.add(createFallingParticle(currentType))
                }
            }
            CelebrationType.HEARTS -> {
                repeat(30) {
                    particles.add(Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 3 - 2, Color.RED, random.nextFloat() * 20 + 15, currentType))
                }
            }
            CelebrationType.DIAMONDS -> {
                repeat(40) {
                    particles.add(createFallingParticle(currentType))
                }
            }
            CelebrationType.SNOWFLAKES -> {
                repeat(50) {
                    particles.add(createFallingParticle(currentType))
                }
            }
            CelebrationType.MUSIC_NOTES -> {
                repeat(30) {
                    particles.add(Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 4 - 2, randomColor(), random.nextFloat() * 25 + 20, currentType))
                }
            }
            CelebrationType.SPARKLES -> {
                repeat(100) {
                    particles.add(Particle(random.nextFloat() * width, random.nextFloat() * height, 0f, 0f, Color.WHITE, random.nextFloat() * 10 + 5, currentType))
                }
            }
            CelebrationType.SMILEYS -> {
                repeat(25) {
                    particles.add(Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 3 - 2, Color.YELLOW, random.nextFloat() * 30 + 25, currentType))
                }
            }
            CelebrationType.RAIN_COINS -> {
                repeat(40) {
                    particles.add(createFallingParticle(currentType))
                }
            }
            CelebrationType.RAIN_CLOUDS -> {
                repeat(40) {
                    particles.add(Particle(random.nextFloat() * width, -random.nextFloat() * height, 0f, random.nextFloat() * 5 + 5, Color.parseColor("#90CAF9"), random.nextFloat() * 5 + 5, currentType))
                }
            }
            CelebrationType.GHOSTS -> {
                repeat(15) {
                    particles.add(Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 2 - 1, Color.argb(180, 255, 255, 255), random.nextFloat() * 30 + 30, currentType))
                }
            }
            CelebrationType.BROKEN_HEARTS -> {
                repeat(25) {
                    particles.add(Particle(random.nextFloat() * width, -random.nextFloat() * height, random.nextFloat() * 2 - 1, random.nextFloat() * 2 + 2, Color.RED, random.nextFloat() * 20 + 20, currentType))
                }
            }
            CelebrationType.BUBBLES -> {
                repeat(40) {
                    particles.add(Particle(random.nextFloat() * width, height + random.nextFloat() * 100, random.nextFloat() * 2 - 1, -random.nextFloat() * 2 - 1, Color.argb(100, 174, 234, 255), random.nextFloat() * 15 + 5, currentType))
                }
            }
            CelebrationType.FALLING_LEAVES -> {
                repeat(40) {
                    particles.add(createFallingParticle(currentType))
                }
            }
            CelebrationType.FIRE_FLAMES -> {
                repeat(60) {
                    particles.add(Particle(random.nextFloat() * width, height, (random.nextFloat() - 0.5f) * 4f, -random.nextFloat() * 5 - 2, Color.parseColor("#FF5722"), random.nextFloat() * 20 + 10, currentType))
                }
            }
            CelebrationType.LIGHTNING_BOLTS -> {
                repeat(10) {
                    particles.add(Particle(random.nextFloat() * width, random.nextFloat() * height * 0.5f, 0f, 0f, Color.YELLOW, random.nextFloat() * 40 + 40, currentType))
                }
            }
            CelebrationType.CLOUDS -> {
                repeat(10) {
                    particles.add(Particle(width + random.nextFloat() * width, random.nextFloat() * height * 0.4f, -random.nextFloat() * 1 - 0.5f, 0f, Color.argb(200, 189, 189, 189), random.nextFloat() * 60 + 60, currentType))
                }
            }
        }
    }

    private fun createFallingParticle(type: CelebrationType): Particle {
        val color = when (type) {
            CelebrationType.FLOWERS -> listOf(Color.parseColor("#FF4081"), Color.parseColor("#E040FB"), Color.parseColor("#7C4DFF")).random()
            CelebrationType.STARS -> Color.YELLOW
            CelebrationType.DIAMONDS -> Color.CYAN
            CelebrationType.SNOWFLAKES -> Color.WHITE
            CelebrationType.RAIN_COINS -> Color.parseColor("#FFD700")
            CelebrationType.FALLING_LEAVES -> listOf(Color.parseColor("#F57C00"), Color.parseColor("#EF6C00"), Color.parseColor("#D84315")).random()
            else -> randomColor()
        }
        return Particle(random.nextFloat() * width, -random.nextFloat() * height, random.nextFloat() * 4 - 2, random.nextFloat() * 5 + 2, color, random.nextFloat() * 15 + 10, type)
    }

    private fun randomColor(): Int {
        return Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }

    fun update() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.update()
            if (p.life <= 0) iterator.remove()
        }

        // Replenish some types for continuous effect
        if (particles.size < 20) {
            when (currentType) {
                CelebrationType.FLOWERS, CelebrationType.STARS, CelebrationType.CONFETTI,
                CelebrationType.DIAMONDS, CelebrationType.SNOWFLAKES, CelebrationType.RAIN_COINS -> {
                    particles.add(createFallingParticle(currentType).apply { y = -50f })
                }
                CelebrationType.HEARTS, CelebrationType.BALLOONS, CelebrationType.MUSIC_NOTES, CelebrationType.SMILEYS -> {
                    if (random.nextFloat() > 0.8f) {
                        val p = if (currentType == CelebrationType.HEARTS) {
                            Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 3 - 2, Color.RED, random.nextFloat() * 20 + 15, currentType)
                        } else if (currentType == CelebrationType.BALLOONS) {
                            Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 3 - 2, randomColor(), random.nextFloat() * 30 + 30, currentType)
                        } else if (currentType == CelebrationType.MUSIC_NOTES) {
                            Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 4 - 2, randomColor(), random.nextFloat() * 25 + 20, currentType)
                        } else {
                            Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 3 - 2, Color.YELLOW, random.nextFloat() * 30 + 25, currentType)
                        }
                        particles.add(p)
                    }
                }
                CelebrationType.FIREWORKS -> {
                     if (random.nextFloat() > 0.95f) {
                         val bx = random.nextFloat() * width
                         val by = random.nextFloat() * (height / 2)
                         val color = randomColor()
                         repeat(30) {
                             val angle = random.nextFloat() * 2 * PI.toFloat()
                             val speed = random.nextFloat() * 10 + 3
                             particles.add(Particle(bx, by, cos(angle.toDouble()).toFloat() * speed, sin(angle.toDouble()).toFloat() * speed, color, random.nextFloat() * 8 + 4, currentType))
                         }
                     }
                }
                CelebrationType.SPARKLES -> {
                    repeat(5) {
                        particles.add(Particle(random.nextFloat() * width, random.nextFloat() * height, 0f, 0f, Color.WHITE, random.nextFloat() * 10 + 5, currentType))
                    }
                }
                CelebrationType.FALLING_LEAVES -> {
                    particles.add(createFallingParticle(currentType).apply { y = -50f })
                }
                CelebrationType.RAIN_CLOUDS -> {
                    particles.add(Particle(random.nextFloat() * width, -50f, 0f, random.nextFloat() * 5 + 5, Color.parseColor("#90CAF9"), random.nextFloat() * 5 + 5, currentType))
                }
                CelebrationType.FIRE_FLAMES -> {
                    particles.add(Particle(random.nextFloat() * width, height, (random.nextFloat() - 0.5f) * 4f, -random.nextFloat() * 5 - 2, Color.parseColor("#FF5722"), random.nextFloat() * 20 + 10, currentType))
                }
                CelebrationType.LIGHTNING_BOLTS -> {
                    if (random.nextFloat() > 0.9f) {
                        particles.add(Particle(random.nextFloat() * width, random.nextFloat() * height * 0.5f, 0f, 0f, Color.YELLOW, random.nextFloat() * 40 + 40, currentType))
                    }
                }
                CelebrationType.BUBBLES -> {
                    particles.add(Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 2 - 1, Color.argb(100, 174, 234, 255), random.nextFloat() * 15 + 5, currentType))
                }
                CelebrationType.GHOSTS, CelebrationType.BROKEN_HEARTS, CelebrationType.CLOUDS -> {
                    if (random.nextFloat() > 0.85f) {
                        val p = when (currentType) {
                            CelebrationType.GHOSTS -> Particle(random.nextFloat() * width, height + 50f, random.nextFloat() * 2 - 1, -random.nextFloat() * 2 - 1, Color.argb(180, 255, 255, 255), random.nextFloat() * 30 + 30, currentType)
                            CelebrationType.BROKEN_HEARTS -> Particle(random.nextFloat() * width, -50f, random.nextFloat() * 2 - 1, random.nextFloat() * 2 + 2, Color.RED, random.nextFloat() * 20 + 20, currentType)
                            else -> Particle(width + 50f, random.nextFloat() * height * 0.4f, -random.nextFloat() * 1 - 0.5f, 0f, Color.argb(200, 189, 189, 189), random.nextFloat() * 60 + 60, currentType)
                        }
                        particles.add(p)
                    }
                }
            }
        }
    }

    fun draw(canvas: Canvas) {
        particles.forEach { p ->
            paint.reset()
            paint.isAntiAlias = true
            paint.color = p.color
            paint.alpha = p.alpha
            paint.style = Paint.Style.FILL

            when (p.type) {
                CelebrationType.CONFETTI -> {
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.rotate(p.rotation)
                    canvas.drawRect(-p.size / 2, -p.size / 4, p.size / 2, p.size / 4, paint)
                    canvas.restore()
                }
                CelebrationType.FLOWERS -> {
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.rotate(p.rotation)
                    repeat(5) {
                        canvas.rotate(72f)
                        canvas.drawOval(-p.size / 2, -p.size, p.size / 2, 0f, paint)
                    }
                    paint.color = Color.YELLOW
                    canvas.drawCircle(0f, 0f, p.size / 3, paint)
                    canvas.restore()
                }
                CelebrationType.BALLOONS -> {
                    canvas.drawOval(p.x - p.size * 0.7f, p.y - p.size, p.x + p.size * 0.7f, p.y + p.size, paint)
                    paint.color = Color.WHITE
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawLine(p.x, p.y + p.size, p.x, p.y + p.size * 2, paint)
                }
                CelebrationType.STARS -> {
                    drawStar(canvas, p.x, p.y, p.size, p.rotation)
                }
                CelebrationType.HEARTS -> {
                    drawHeart(canvas, p.x, p.y, p.size)
                }
                CelebrationType.DIAMONDS -> {
                    drawDiamond(canvas, p.x, p.y, p.size, p.rotation)
                }
                CelebrationType.SNOWFLAKES -> {
                    drawSnowflake(canvas, p.x, p.y, p.size, p.rotation)
                }
                CelebrationType.MUSIC_NOTES -> {
                    drawMusicNote(canvas, p.x, p.y, p.size)
                }
                CelebrationType.SPARKLES -> {
                    drawSparkle(canvas, p.x, p.y, p.size, p.rotation)
                }
                CelebrationType.SMILEYS -> {
                    drawSmiley(canvas, p.x, p.y, p.size)
                }
                CelebrationType.RAIN_COINS -> {
                    drawCoin(canvas, p.x, p.y, p.size, p.rotation)
                }
                CelebrationType.FIREWORKS -> {
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                }
                CelebrationType.RAIN_CLOUDS -> {
                    canvas.drawLine(p.x, p.y, p.x, p.y + p.size, paint)
                }
                CelebrationType.GHOSTS -> {
                    drawGhost(canvas, p.x, p.y, p.size)
                }
                CelebrationType.BROKEN_HEARTS -> {
                    drawBrokenHeart(canvas, p.x, p.y, p.size, p.rotation)
                }
                CelebrationType.BUBBLES -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                }
                CelebrationType.FALLING_LEAVES -> {
                    drawLeaf(canvas, p.x, p.y, p.size, p.rotation)
                }
                CelebrationType.FIRE_FLAMES -> {
                    paint.color = if (Math.random() > 0.5) Color.parseColor("#FF9800") else Color.parseColor("#F44336")
                    canvas.drawCircle(p.x, p.y, p.size * p.life, paint)
                }
                CelebrationType.LIGHTNING_BOLTS -> {
                    drawLightning(canvas, p.x, p.y, p.size)
                }
                CelebrationType.CLOUDS -> {
                    canvas.drawRoundRect(p.x, p.y, p.x + p.size * 2, p.y + p.size, p.size * 0.5f, p.size * 0.5f, paint)
                }
            }
        }
    }

    fun getRandomVictoryWord(context: Context, gameKey: String? = null): String {
        val resId = when (gameKey) {
            "minesweeper" -> R.array.win_minesweeper
            "sudoku" -> R.array.win_sudoku
            "tic_tac_toe" -> R.array.win_tictactoe
            "4096" -> R.array.win_4096
            "memory" -> R.array.win_memory
            "hangman" -> R.array.win_hangman
            "brick_break" -> R.array.win_brick_break
            "word_quest" -> R.array.win_word_quest
            "solitaire" -> R.array.win_solitaire
            "sokoban" -> R.array.win_sokoban
            "trex" -> R.array.win_highscore
            "win_highscore" -> R.array.win_highscore
            "checkers" -> R.array.win_checkers
            "battle_tanks" -> R.array.win_tanks
            "dungeon_escape" -> R.array.win_dungeon
            "mental_math" -> R.array.win_mental_math
            "froggy_cross" -> R.array.win_froggy
            "slide_puzzle" -> R.array.win_slide_puzzle
            else -> R.array.win_generic
        }
        val words = context.resources.getStringArray(resId)
        return words.random()
    }

    private fun drawGhost(canvas: Canvas, x: Float, y: Float, size: Float) {
        path.reset()
        path.moveTo(x - size, y + size)
        path.lineTo(x - size, y - size * 0.5f)
        rectF.set(x - size, y - size, x + size, y)
        path.arcTo(rectF, 180f, 180f)
        path.lineTo(x + size, y + size)
        path.lineTo(x + size * 0.5f, y + size * 0.7f)
        path.lineTo(x, y + size)
        path.lineTo(x - size * 0.5f, y + size * 0.7f)
        path.close()
        canvas.drawPath(path, paint)
        
        paint.color = Color.BLACK
        canvas.drawCircle(x - size * 0.3f, y - size * 0.3f, size * 0.15f, paint)
        canvas.drawCircle(x + size * 0.3f, y - size * 0.3f, size * 0.15f, paint)
    }

    private fun drawBrokenHeart(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        
        // Left half
        path.reset()
        path.moveTo(0f, size / 4)
        path.cubicTo(0f, -size / 2, -size, -size / 2, -size, size / 4)
        path.cubicTo(-size, size, 0f, size * 1.5f, 0f, size * 2)
        path.lineTo(-5f, size) // Zigzag break
        path.lineTo(5f, size * 0.5f)
        path.close()
        canvas.drawPath(path, paint)
        
        // Right half
        canvas.translate(5f, 5f) // Separate a bit
        path.reset()
        path.moveTo(0f, size / 4)
        path.cubicTo(0f, -size / 2, size, -size / 2, size, size / 4)
        path.cubicTo(size, size, 0f, size * 1.5f, 0f, size * 2)
        path.lineTo(-5f, size)
        path.lineTo(5f, size * 0.5f)
        path.close()
        canvas.drawPath(path, paint)
        
        canvas.restore()
    }

    private fun drawLeaf(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        path.reset()
        path.moveTo(0f, -size)
        path.quadTo(size, 0f, 0f, size)
        path.quadTo(-size, 0f, 0f, -size)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(0f, -size, 0f, size * 1.2f, paint)
        canvas.restore()
    }

    private fun drawLightning(canvas: Canvas, x: Float, y: Float, size: Float) {
        path.reset()
        path.moveTo(x + size * 0.5f, y - size)
        path.lineTo(x - size * 0.5f, y)
        path.lineTo(x + size * 0.2f, y)
        path.lineTo(x - size * 0.5f, y + size)
        canvas.drawPath(path, paint)
    }

    private fun drawStar(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        path.reset()
        val outerRadius = size
        val innerRadius = size / 2
        for (i in 0 until 5) {
            val angle = PI * 2 * i / 5 - PI / 2
            path.lineTo((cos(angle) * outerRadius).toFloat(), (sin(angle) * outerRadius).toFloat())
            val nextAngle = PI * 2 * (i + 0.5) / 5 - PI / 2
            path.lineTo((cos(nextAngle) * innerRadius).toFloat(), (sin(nextAngle) * innerRadius).toFloat())
        }
        path.close()
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawHeart(canvas: Canvas, x: Float, y: Float, size: Float) {
        path.reset()
        path.moveTo(x, y + size / 4)
        path.cubicTo(x, y - size / 2, x - size, y - size / 2, x - size, y + size / 4)
        path.cubicTo(x - size, y + size, x, y + size * 1.5f, x, y + size * 2)
        path.cubicTo(x, y + size * 1.5f, x + size, y + size, x + size, y + size / 4)
        path.cubicTo(x + size, y - size / 2, x, y - size / 2, x, y + size / 4)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawDiamond(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        path.reset()
        path.moveTo(0f, -size)
        path.lineTo(size * 0.7f, 0f)
        path.lineTo(0f, size)
        path.lineTo(-size * 0.7f, 0f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawSnowflake(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size / 5
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        repeat(6) {
            canvas.rotate(60f)
            canvas.drawLine(0f, 0f, 0f, -size, paint)
            canvas.drawLine(0f, -size * 0.5f, -size * 0.3f, -size * 0.8f, paint)
            canvas.drawLine(0f, -size * 0.5f, size * 0.3f, -size * 0.8f, paint)
        }
        canvas.restore()
    }

    private fun drawMusicNote(canvas: Canvas, x: Float, y: Float, size: Float) {
        canvas.drawCircle(x, y + size, size / 3, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size / 6
        canvas.drawLine(x + size / 3, y + size, x + size / 3, y - size, paint)
        canvas.drawLine(x + size / 3, y - size, x + size, y - size * 0.5f, paint)
    }

    private fun drawSparkle(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        path.reset()
        repeat(4) {
            canvas.rotate(90f)
            path.moveTo(0f, 0f)
            path.quadTo(size * 0.2f, -size * 0.2f, 0f, -size)
            path.quadTo(-size * 0.2f, -size * 0.2f, 0f, 0f)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawSmiley(canvas: Canvas, x: Float, y: Float, size: Float) {
        canvas.drawCircle(x, y, size, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(x - size * 0.4f, y - size * 0.3f, size * 0.15f, paint)
        canvas.drawCircle(x + size * 0.4f, y - size * 0.3f, size * 0.15f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.1f
        rectF.set(x - size * 0.6f, y - size * 0.6f, x + size * 0.6f, y + size * 0.6f)
        canvas.drawArc(rectF, 30f, 120f, false, paint)
    }

    private fun drawCoin(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float) {
        canvas.save()
        canvas.translate(x, y)
        val scaleX = abs(cos(rotation * PI / 180)).toFloat()
        canvas.scale(scaleX, 1.0f)
        paint.color = Color.parseColor("#FFD700")
        canvas.drawCircle(0f, 0f, size, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#DAA520")
        paint.strokeWidth = size * 0.1f
        canvas.drawCircle(0f, 0f, size * 0.8f, paint)
        canvas.restore()
    }
}
