package com.tdpham.games.common

import android.graphics.*
import java.util.*

object GameEnvironment {
    private val random = Random()

    enum class BackgroundType { GRID, CHECKERBOARD, GRADIENT, STRIPES, WOOD, FELT, SOLID, DOTS, DIAMONDS, STARRY, NONE }
    enum class WeatherType { NONE, RAIN, SNOW, STORM, SANDSTORM, FOG }
    enum class SceneType { FIELD, CITY, DESERT, SPACE, NONE }

    data class Particle(var x: Float, var y: Float, var speed: Float, var vx: Float = 0f, var size: Float = 4f, var color: Int = Color.WHITE)

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastGradientHeight = -1f
    private var lastIsNight = false

    fun draw(
        canvas: Canvas,
        bgType: BackgroundType,
        scene: SceneType = SceneType.NONE,
        weather: WeatherType = WeatherType.NONE,
        isNight: Boolean = false,
        paint: Paint,
        particles: MutableList<Particle>? = null
    ) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        if (width <= 0 || height <= 0) return
        val nightDim = if (isNight) 0.4f else 1.0f

        // 1. Base Background
        when (bgType) {
            BackgroundType.SOLID -> {
                canvas.drawColor(if (isNight) Color.parseColor("#0A0A0A") else Color.BLACK)
            }
            BackgroundType.GRADIENT -> {
                if (height != lastGradientHeight || isNight != lastIsNight) {
                    val colors = if (isNight)
                        intArrayOf(Color.parseColor("#1A237E"), Color.BLACK)
                    else
                        intArrayOf(Color.parseColor("#0D47A1"), Color.parseColor("#001030"))
                    gradientPaint.shader = LinearGradient(0f, 0f, 0f, height, colors[0], colors[1], Shader.TileMode.CLAMP)
                    lastGradientHeight = height
                    lastIsNight = isNight
                }
                canvas.drawRect(0f, 0f, width, height, gradientPaint)
            }
            BackgroundType.CHECKERBOARD -> {
                val c1 = dimColor(if (scene == SceneType.FIELD) Color.parseColor("#1B5E20") else Color.parseColor("#121212"), nightDim)
                val c2 = dimColor(if (scene == SceneType.FIELD) Color.parseColor("#144E18") else Color.parseColor("#0A0A0A"), nightDim)
                canvas.drawColor(c1)
                paint.color = c2
                val size = 120f
                for (x in 0..(width / size).toInt()) {
                    for (y in 0..(height / size).toInt()) {
                        if ((x + y) % 2 == 0) canvas.drawRect(x * size, y * size, (x + 1) * size, (y + 1) * size, paint)
                    }
                }
            }
            BackgroundType.GRID -> {
                canvas.drawColor(dimColor(Color.parseColor("#101820"), nightDim))
                paint.color = dimColor(Color.parseColor("#1A2A3A"), nightDim)
                paint.strokeWidth = 2f
                val spacing = 100f
                for (i in 0..(width / spacing).toInt()) canvas.drawLine(i * spacing, 0f, i * spacing, height, paint)
                for (i in 0..(height / spacing).toInt()) canvas.drawLine(0f, i * spacing, width, i * spacing, paint)
            }
            BackgroundType.WOOD -> {
                canvas.drawColor(dimColor(Color.parseColor("#3E2723"), nightDim))
                paint.color = dimColor(Color.parseColor("#21130D"), nightDim)
                paint.strokeWidth = 4f
                for (i in 0..(height / 60).toInt()) canvas.drawLine(0f, i * 60f, width, i * 60f, paint)
            }
            BackgroundType.FELT -> {
                canvas.drawColor(dimColor(Color.parseColor("#0D3B11"), nightDim))
                paint.color = dimColor(Color.parseColor("#08260B"), nightDim)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                for (i in 0..width.toInt() step 300) canvas.drawCircle(i.toFloat(), height / 2f, 1500f, paint)
                paint.style = Paint.Style.FILL
            }
            BackgroundType.STRIPES -> {
                canvas.drawColor(dimColor(Color.parseColor("#1A1A1B"), nightDim))
                paint.color = dimColor(Color.parseColor("#000000"), nightDim)
                val sw = 80f
                for (i in 0..(width / (sw * 2)).toInt()) {
                    canvas.drawRect(i * sw * 2, 0f, i * sw * 2 + sw, height, paint)
                }
            }
            BackgroundType.DOTS -> {
                canvas.drawColor(dimColor(Color.parseColor("#0A0A0A"), nightDim))
                paint.color = dimColor(Color.parseColor("#1A237E"), nightDim)
                val r = 3f
                val step = 80f
                for (x in 0..(width/step).toInt()) {
                    for (y in 0..(height/step).toInt()) {
                        canvas.drawCircle(x * step, y * step, r, paint)
                    }
                }
            }
            BackgroundType.DIAMONDS -> {
                canvas.drawColor(dimColor(Color.parseColor("#121212"), nightDim))
                paint.color = dimColor(Color.parseColor("#0A0A0A"), nightDim)
                val size = 120f
                val path = Path()
                for (x in 0..(width / size).toInt() + 1) {
                    val cx = x * size
                    for (y in 0..(height / size).toInt() + 1) {
                        val cy = y * size + (if (x % 2 == 1) size / 2 else 0f)
                        path.reset()
                        path.moveTo(cx, cy - size / 2)
                        path.lineTo(cx + size / 2, cy)
                        path.lineTo(cx, cy + size / 2)
                        path.lineTo(cx - size / 2, cy)
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                }
            }
            BackgroundType.STARRY -> {
                canvas.drawColor(Color.BLACK)
                paint.color = Color.WHITE
                val r = Random(42)
                for (i in 0..150) {
                    val x = r.nextFloat() * width
                    val y = r.nextFloat() * height
                    val s = r.nextFloat() * 2.5f
                    paint.alpha = r.nextInt(180) + 75
                    canvas.drawCircle(x, y, s, paint)
                }
                paint.alpha = 255
            }
            BackgroundType.NONE -> {}
        }

        // 2. Weather
        if (weather != WeatherType.NONE && particles != null) {
            when (weather) {
                WeatherType.RAIN -> {
                    paint.color = Color.parseColor("#80FFFFFF")
                    paint.strokeWidth = 2f
                    for (p in particles) {
                        canvas.drawLine(p.x, p.y, p.x - 2, p.y + 15, paint)
                        p.y += p.speed
                        p.x -= 2f
                        resetParticle(p, width, height)
                    }
                }
                WeatherType.SNOW -> {
                    paint.color = Color.WHITE
                    for (p in particles) {
                        canvas.drawCircle(p.x, p.y, p.size, paint)
                        p.y += p.speed * 0.5f
                        p.x += p.vx
                        resetParticle(p, width, height)
                    }
                }
                WeatherType.STORM -> {
                    paint.color = Color.parseColor("#AAFFFFFF")
                    if (random.nextInt(100) < 2) canvas.drawColor(Color.WHITE) // Lightning flash
                    for (p in particles) {
                        canvas.drawLine(p.x, p.y, p.x - 5, p.y + 25, paint)
                        p.y += p.speed * 1.5f
                        p.x -= 5f
                        resetParticle(p, width, height)
                    }
                }
                WeatherType.SANDSTORM -> {
                    paint.color = dimColor(Color.parseColor("#FFE082"), 0.6f)
                    canvas.drawColor(Color.argb(80, 121, 85, 72))
                    for (p in particles) {
                        canvas.drawRect(p.x, p.y, p.x + 8, p.y + 2, paint)
                        p.x -= p.speed * 2
                        p.y += p.vx
                        if (p.x < 0) {
                            p.x = width
                            p.y = random.nextFloat() * height
                        }
                    }
                }
                WeatherType.FOG -> {
                    paint.color = Color.argb(120, 200, 200, 200)
                    canvas.drawRect(0f, 0f, width, height, paint)
                }
                else -> {}
            }
        }
    }

    private fun resetParticle(p: Particle, w: Float, h: Float) {
        if (p.y > h) {
            p.y = -20f
            p.x = random.nextFloat() * w
        }
    }

    fun dimColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.argb(a, r, g, b)
    }

    fun getRandomType(): BackgroundType = BackgroundType.entries.random()
    fun getRandomWeather(): WeatherType = WeatherType.entries.random()
}
