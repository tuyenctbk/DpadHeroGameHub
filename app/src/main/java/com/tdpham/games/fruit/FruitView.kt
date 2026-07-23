package com.tdpham.games.fruit

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlin.math.sin
import kotlin.random.Random

class FruitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "fruit"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isInitialized = false
    private var gameOver = false
    private var gamePaused = true

    private var score = 0
    private var best = 0
    private var lives = 3

    // Player Slash Cursor & Blades
    private var cursorX = 0f
    private var cursorY = 0f
    private val cursorSpeed = 65f
    private val slashTrail = mutableListOf<PointF>()
    private val maxTrailSize = 14

    // Blade Customization
    // 0: Steel Katana, 1: Flame Blade, 2: Cyber Glow, 3: Shadow Smoke
    private var selectedBlade = 0
    private val bladeNames get() = arrayOf(
        context.getString(R.string.fruit_blade_steel),
        context.getString(R.string.fruit_blade_flame),
        context.getString(R.string.fruit_blade_cyber),
        context.getString(R.string.fruit_blade_shadow)
    )
    private val trailParticles = mutableListOf<TrailParticle>()
    private val explosions = mutableListOf<BombExplosion>()

    // Game elements
    private val items = mutableListOf<ActiveItem>()
    private val slicedHalves = mutableListOf<SlicedHalf>()
    private val splats = mutableListOf<JuiceSplat>()
    private val gravity = 0.35f
    private var lastSpawnTime = 0L
    private var spawnInterval = 1400L // ms

    // Bomb scaling
    private var bombProbability = 0.12f

    // Visual feedback
    private var lastBombHitTime = 0L
    private val celebrationManager = CelebrationManager()

    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gamePaused && !gameOver) {
                update()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    private val animHandler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || gamePaused) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }

    data class ActiveItem(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val isBomb: Boolean,
        val speciesIndex: Int, // 0 to 40 for fruits
        val bombType: Int, // 0: Classic, 1: Cyber, 2: Spike
        val radius: Float
    )

    data class SlicedHalf(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val speciesIndex: Int,
        val isLeftHalf: Boolean,
        var angle: Float,
        val spinSpeed: Float,
        val color: Int
    )

    data class JuiceSplat(
        val x: Float,
        val y: Float,
        val color: Int,
        var size: Float,
        var alpha: Int
    )

    data class TrailParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        var size: Float,
        var alpha: Int
    )

    data class BombExplosion(
        var x: Float,
        var y: Float,
        var radius: Float,
        val maxRadius: Float,
        var alpha: Int
    )

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
        score = 0
        lives = 3
        cursorX = width / 2f
        cursorY = height / 2f
        items.clear()
        slicedHalves.clear()
        splats.clear()
        trailParticles.clear()
        explosions.clear()
        slashTrail.clear()
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
        bombProbability = 0.12f
        lastBombHitTime = 0L
        celebrationManager.start(0f, 0f)
        invalidate()
    }

    private fun spawnItem() {
        // Increase bomb rate as score climbs
        bombProbability = (0.12f + score / 600f).coerceAtMost(0.48f)

        val isBomb = Random.nextFloat() < bombProbability
        val radius = Random.nextFloat() * 20f + 48f // Nice large fruits (48f to 68f)

        val x = Random.nextFloat() * (width - 200f) + 100f
        val y = height + 50f
        val vx = (Random.nextFloat() * 6f - 3f)
        val vy = -(Random.nextFloat() * 5f + 13f) // Launch upwards

        if (isBomb) {
            val bombType = Random.nextInt(0, 3) // 0: Classic, 1: Cyber, 2: Spike
            items.add(ActiveItem(x, y, vx, vy, isBomb = true, speciesIndex = 0, bombType = bombType, radius = radius))
        } else {
            val speciesIndex = Random.nextInt(0, 41) // 41 fruit kinds
            items.add(ActiveItem(x, y, vx, vy, isBomb = false, speciesIndex = speciesIndex, bombType = 0, radius = radius))
        }
    }

    private fun update() {
        val now = System.currentTimeMillis()

        // 1. Spawning items
        if (now - lastSpawnTime > spawnInterval) {
            val count = if (score > 150) Random.nextInt(2, 5) else Random.nextInt(1, 3)
            repeat(count) { spawnItem() }
            lastSpawnTime = now
        }

        // 2. Trail points decay
        if (slashTrail.size > 0 && !gamePaused) {
            slashTrail.removeAt(0)
        }

        // Update trail particles
        val pIterator = trailParticles.iterator()
        while (pIterator.hasNext()) {
            val p = pIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 8
            if (p.alpha <= 0) pIterator.remove()
        }

        // Update active explosions
        val expIterator = explosions.iterator()
        while (expIterator.hasNext()) {
            val exp = expIterator.next()
            exp.radius += 12f
            exp.alpha -= 10
            if (exp.alpha <= 0 || exp.radius >= exp.maxRadius) {
                expIterator.remove()
            }
        }

        // 3. Update Active Fruits / Bombs
        val itemIterator = items.iterator()
        while (itemIterator.hasNext()) {
            val item = itemIterator.next()
            item.x += item.vx
            item.y += item.vy
            item.vy += gravity // apply gravity

            // Slash collision check
            if (checkSlashCollision(item)) {
                if (item.isBomb) {
                    // Hit bomb!
                    lives--
                    lastBombHitTime = now
                    SoundManager.playExplosion()

                    // Spawn visual explosion shockwave ring
                    explosions.add(BombExplosion(item.x, item.y, 10f, 150f, 255))

                    // Spawn 40 dense fire and smoke particles flying outward
                    val numParticles = 40
                    val angleStep = (2.0 * Math.PI) / numParticles
                    for (i in 0 until numParticles) {
                        val angle = i * angleStep + Random.nextDouble(-0.1, 0.1)
                        val speed = Random.nextFloat() * 8f + 5f
                        val vx = (Math.cos(angle) * speed).toFloat()
                        val vy = (Math.sin(angle) * speed).toFloat()
                        val color = when (Random.nextInt(4)) {
                            0 -> Color.parseColor("#FF3D00") // Red-Orange fire
                            1 -> Color.parseColor("#FF9100") // Orange fire
                            2 -> Color.parseColor("#FFEA00") // Yellow fire
                            else -> Color.parseColor("#424242") // Dark Gray Smoke
                        }
                        val size = Random.nextFloat() * 14f + 8f
                        trailParticles.add(TrailParticle(item.x, item.y, vx, vy, color, size, 255))
                    }

                    if (lives <= 0) {
                        gameOver = true
                        val isNewHigh = score > best
                        if (isNewHigh) {
                            ScoreManager.updateHighScore(context, gameKey, score)
                            best = score
                        }
                        celebrationManager.start(width / 2f, height / 2f)
                        onGameOver?.invoke(score)
                    }
                } else {
                    // Sliced fruit!
                    score += 10
                    SoundManager.playSlice()
                    
                    // Add background juice splat
                    val juiceColor = getFruitJuiceColor(item.speciesIndex)
                    splats.add(JuiceSplat(item.x, item.y, juiceColor, Random.nextFloat() * 40f + 30f, 220))

                    // Spawn flying juice particles
                    repeat(12) {
                        trailParticles.add(
                            TrailParticle(
                                x = item.x,
                                y = item.y,
                                vx = Random.nextFloat() * 8f - 4f,
                                vy = Random.nextFloat() * 8f - 4f,
                                color = juiceColor,
                                size = Random.nextFloat() * 6f + 4f,
                                alpha = 255
                            )
                        )
                    }

                    // Create two split halves flying apart
                    slicedHalves.add(
                        SlicedHalf(
                            x = item.x - 10f, y = item.y,
                            vx = item.vx - 3f - Random.nextFloat() * 3f, vy = item.vy - 2f,
                            speciesIndex = item.speciesIndex, isLeftHalf = true,
                            angle = 0f, spinSpeed = -15f, color = juiceColor
                        )
                    )
                    slicedHalves.add(
                        SlicedHalf(
                            x = item.x + 10f, y = item.y,
                            vx = item.vx + 3f + Random.nextFloat() * 3f, vy = item.vy - 2f,
                            speciesIndex = item.speciesIndex, isLeftHalf = false,
                            angle = 0f, spinSpeed = 15f, color = juiceColor
                        )
                    )
                }
                itemIterator.remove()
                continue
            }

            // Remove out of bounds
            if (item.y > height + 80f) {
                itemIterator.remove()
            }
        }

        // 4. Update Sliced Halves
        val halfIterator = slicedHalves.iterator()
        while (halfIterator.hasNext()) {
            val half = halfIterator.next()
            half.x += half.vx
            half.y += half.vy
            half.vy += gravity
            half.angle += half.spinSpeed

            if (half.y > height + 80f) {
                halfIterator.remove()
            }
        }

        // 5. Update Splats (slow slide down and fade)
        val sIterator = splats.iterator()
        while (sIterator.hasNext()) {
            val splat = sIterator.next()
            splat.alpha -= 1
            if (splat.alpha <= 0) sIterator.remove()
        }
    }

    private fun checkSlashCollision(item: ActiveItem): Boolean {
        if (slashTrail.size < 2) return false
        val r = item.radius
        
        // Check intersection of last slash segment with item bounding circle
        for (i in 0 until slashTrail.size - 1) {
            val p1 = slashTrail[i]
            val p2 = slashTrail[i+1]
            if (distToSegment(item.x, item.y, p1.x, p1.y, p2.x, p2.y) < r) {
                return true
            }
        }
        return false
    }

    private fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) {
            val sx = px - x1
            val sy = py - y1
            return Math.sqrt((sx*sx + sy*sy).toDouble()).toFloat()
        }
        
        var t = ((px - x1) * dx + (py - y1) * dy) / (dx*dx + dy*dy)
        t = t.coerceIn(0f, 1f)
        
        val closestX = x1 + t * dx
        val closestY = y1 + t * dy
        val rx = px - closestX
        val ry = py - closestY
        return Math.sqrt((rx*rx + ry*ry).toDouble()).toFloat()
    }

    // --- SPRITE RENDERING METHODS (40+ FRUITS & 3 BOMBS) ---

    override fun onDraw(canvas: Canvas) {
        if (!isInitialized) return

        // Screen Shake when hitting a bomb
        val now = System.currentTimeMillis()
        val isShaking = now - lastBombHitTime < 500L
        if (isShaking) {
            canvas.save()
            canvas.translate(Random.nextFloat() * 16f - 8f, Random.nextFloat() * 16f - 8f)
        }

        // 1. Draw Wooden Background
        drawWoodenBackground(canvas)

        // 2. Draw Juice Splats on Background
        for (splat in splats) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = splat.color
            paint.alpha = splat.alpha
            paint.style = Paint.Style.FILL
            canvas.drawCircle(splat.x, splat.y, splat.size, paint)
            // Splat drops
            canvas.drawCircle(splat.x - splat.size * 0.5f, splat.y + splat.size * 0.4f, splat.size * 0.3f, paint)
            canvas.drawCircle(splat.x + splat.size * 0.6f, splat.y + splat.size * 0.2f, splat.size * 0.25f, paint)
        }

        // 3. Draw Sliced Halves
        for (half in slicedHalves) {
            drawFruitHalf(canvas, half)
        }

        // 4. Draw Active Fruits & Bombs
        for (item in items) {
            drawActiveItem(canvas, item)
        }

        // 5. Draw Blade Trail Particles
        for (p in trailParticles) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = p.color
            paint.alpha = p.alpha
            paint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }

        // Draw Explosions
        for (exp in explosions) {
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FFD54F") // Yellow flash
            paint.alpha = exp.alpha
            canvas.drawCircle(exp.x, exp.y, exp.radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            paint.color = Color.parseColor("#FF3D00") // Red border
            paint.alpha = exp.alpha
            canvas.drawCircle(exp.x, exp.y, exp.radius, paint)
        }

        // 6. Draw Sword Blade Trail
        drawBladeTrail(canvas)

        // Draw Sword Cursor Indicator/Crosshair
        drawCursorIndicator(canvas)

        // 7. Draw HUD (Hearts & Score)
        drawHUD(canvas)

        if (isShaking) {
            canvas.restore()
        }

        // 8. Overlays
        if (gameOver) {
            celebrationManager.draw(canvas)
            drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (gamePaused) {
            drawOverlay(
                canvas,
                context.getString(R.string.game_fruit),
                "${context.getString(R.string.fruit_resume_desc, bladeNames[selectedBlade])}\n\n${context.getString(R.string.resume_hint)}"
            )
        }
    }

    private fun drawWoodenBackground(canvas: Canvas) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#4E342E") // Wood brown
        canvas.drawColor(paint.color)

        // Wooden planks lines
        paint.color = Color.parseColor("#3E2723")
        paint.strokeWidth = 4f
        val step = height / 6f
        for (i in 1..5) {
            val y = i * step
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
        }
    }

    private fun drawActiveItem(canvas: Canvas, item: ActiveItem) {
        val cx = item.x
        val cy = item.y
        val r = item.radius

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        if (item.isBomb) {
            drawBomb(canvas, cx, cy, r, item.bombType)
            return
        }

        // Draw Fruit
        drawFruitWhole(canvas, cx, cy, r, item.speciesIndex)
    }

    private fun drawBomb(canvas: Canvas, cx: Float, cy: Float, r: Float, type: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        when (type) {
            1 -> { // Cyber Mine (Neon hexagon with flashing red core)
                paint.color = Color.parseColor("#00E5FF")
                val path = Path()
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60).toDouble())
                    val px = (cx + r * Math.cos(angle)).toFloat()
                    val py = (cy + r * Math.sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)

                // Core
                paint.color = if ((System.currentTimeMillis() / 200) % 2 == 0L) Color.RED else Color.BLACK
                canvas.drawCircle(cx, cy, r * 0.4f, paint)
            }
            2 -> { // Spike Bomb (Steel sphere with spikes)
                paint.color = Color.parseColor("#37474F")
                canvas.drawCircle(cx, cy, r, paint)

                // Spikes
                paint.strokeWidth = 6f
                paint.color = Color.parseColor("#90A4AE")
                paint.style = Paint.Style.STROKE
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val sx = (cx + r * Math.cos(angle)).toFloat()
                    val sy = (cy + r * Math.sin(angle)).toFloat()
                    canvas.drawLine(cx, cy, sx + (r * 0.3f * Math.cos(angle)).toFloat(), sy + (r * 0.3f * Math.sin(angle)).toFloat(), paint)
                }
                paint.style = Paint.Style.FILL
            }
            else -> { // Classic Bomb (Black sphere + fuse + spark)
                paint.color = Color.BLACK
                canvas.drawCircle(cx, cy, r, paint)

                // Metal cap
                paint.color = Color.parseColor("#757575")
                canvas.drawRect(cx - r * 0.25f, cy - r - 3f, cx + r * 0.25f, cy - r + 3f, paint)

                // Fuse line
                paint.color = Color.parseColor("#D7CCC8")
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                val fuse = Path()
                fuse.moveTo(cx, cy - r)
                fuse.quadTo(cx + r * 0.5f, cy - r - 15f, cx + r * 0.6f, cy - r - 30f)
                canvas.drawPath(fuse, paint)

                // Spark (Yellow/Orange)
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#FFD54F")
                canvas.drawCircle(cx + r * 0.6f, cy - r - 30f, 8f, paint)
                paint.color = Color.parseColor("#FF9100")
                canvas.drawCircle(cx + r * 0.6f, cy - r - 30f, 4f, paint)
            }
        }
    }

    private fun drawFruitWhole(canvas: Canvas, cx: Float, cy: Float, r: Float, species: Int) {
        val color = getFruitOuterColor(species)
        paint.color = color

        when (species) {
            0 -> { // Watermelon: green stripe oval
                canvas.drawOval(RectF(cx - r * 1.2f, cy - r * 0.9f, cx + r * 1.2f, cy + r * 0.9f), paint)
                // Dark stripes
                paint.color = Color.parseColor("#1B5E20")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                canvas.drawArc(RectF(cx - r * 0.8f, cy - r * 0.7f, cx + r * 0.8f, cy + r * 0.7f), 0f, 360f, false, paint)
            }
            1 -> { // Apple: Red heart-round with stem
                canvas.drawCircle(cx, cy, r, paint)
                // Stem
                paint.color = Color.parseColor("#5D4037")
                paint.strokeWidth = 3.5f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx, cy - r, cx + 5f, cy - r - 15f, paint)
            }
            2 -> { // Orange: Orange circle with textured circles
                canvas.drawCircle(cx, cy, r, paint)
                paint.color = Color.parseColor("#E65100")
                canvas.drawCircle(cx, cy, r * 0.8f, paint)
            }
            3 -> { // Banana: Yellow crescent
                val path = Path()
                path.moveTo(cx - r * 0.9f, cy)
                path.quadTo(cx, cy + r * 0.7f, cx + r * 0.9f, cy - r * 0.2f)
                path.quadTo(cx, cy + r * 0.3f, cx - r * 0.9f, cy)
                path.close()
                canvas.drawPath(path, paint)
            }
            4 -> { // Strawberry: red heart shape with yellow spots
                val path = Path()
                path.moveTo(cx, cy - r)
                path.quadTo(cx - r * 0.8f, cy - r * 0.5f, cx - r * 0.5f, cy + r * 0.6f)
                path.lineTo(cx, cy + r)
                path.lineTo(cx + r * 0.5f, cy + r * 0.6f)
                path.quadTo(cx + r * 0.8f, cy - r * 0.5f, cx, cy - r)
                path.close()
                canvas.drawPath(path, paint)
            }
            5 -> { // Pineapple: yellow textured oval + green crown
                canvas.drawOval(RectF(cx - r * 0.8f, cy - r * 1.1f, cx + r * 0.8f, cy + r * 1.1f), paint)
                // Green crown leaves
                paint.color = Color.parseColor("#2E7D32")
                val crown = Path()
                crown.moveTo(cx - r * 0.4f, cy - r * 1.0f)
                crown.lineTo(cx, cy - r * 1.6f)
                crown.lineTo(cx + r * 0.4f, cy - r * 1.0f)
                crown.close()
                canvas.drawPath(crown, paint)
            }
            else -> { // Default round colored fruit
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
    }

    private fun drawFruitHalf(canvas: Canvas, half: SlicedHalf) {
        val cx = half.x
        val cy = half.y
        val r = 50f // standard half radius

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(half.angle)

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        
        // Draw Outer shell
        paint.color = getFruitOuterColor(half.speciesIndex)
        val outer = RectF(-r, -r, r, r)
        val startAngle = if (half.isLeftHalf) 90f else 270f
        canvas.drawArc(outer, startAngle, 180f, true, paint)

        // Draw Inner flesh
        paint.color = getFruitInnerColor(half.speciesIndex)
        val inner = RectF(-r * 0.82f, -r * 0.82f, r * 0.82f, r * 0.82f)
        canvas.drawArc(inner, startAngle, 180f, true, paint)

        // Draw Seeds / details for watermelon
        if (half.speciesIndex == 0) { // Watermelon seeds
            paint.color = Color.BLACK
            val offset = if (half.isLeftHalf) -r * 0.4f else r * 0.4f
            canvas.drawCircle(offset, -10f, 3f, paint)
            canvas.drawCircle(offset, 10f, 3f, paint)
        }

        canvas.restore()
    }

    private fun getFruitOuterColor(species: Int): Int {
        return when (species % 8) {
            0 -> Color.parseColor("#2E7D32") // Watermelon Green
            1 -> Color.parseColor("#B71C1C") // Apple Dark Red
            2 -> Color.parseColor("#FF9800") // Orange
            3 -> Color.parseColor("#FBC02D") // Banana Yellow
            4 -> Color.parseColor("#C2185B") // Strawberry Deep Red
            5 -> Color.parseColor("#F57F17") // Pineapple gold orange
            6 -> Color.parseColor("#4E342E") // Coconut brown
            else -> Color.parseColor("#311B92") // Grape Purple
        }
    }

    private fun getFruitInnerColor(species: Int): Int {
        return when (species % 8) {
            0 -> Color.parseColor("#FF1744") // Watermelon red flesh
            1 -> Color.parseColor("#FFF9C4") // Apple light yellow flesh
            2 -> Color.parseColor("#FF9800") // Orange
            3 -> Color.parseColor("#FFFDE7") // Banana white flesh
            4 -> Color.parseColor("#FF1744") // Strawberry Red
            5 -> Color.parseColor("#FFEE58") // Pineapple yellow
            6 -> Color.parseColor("#FFFFFF") // Coconut white inside
            else -> Color.parseColor("#7E57C2") // Grape light purple
        }
    }

    private fun getFruitJuiceColor(species: Int): Int {
        return when (species % 8) {
            0 -> Color.parseColor("#FF1744") // Watermelon Red
            1 -> Color.parseColor("#FFEB3B") // Apple Yellow-ish
            2 -> Color.parseColor("#FF9800") // Orange
            3 -> Color.parseColor("#FFFDE7") // Banana White
            4 -> Color.parseColor("#F48FB1") // Strawberry pink
            5 -> Color.parseColor("#FFEB3B") // Pineapple
            6 -> Color.parseColor("#E0E0E0") // Coconut milky
            else -> Color.parseColor("#B388FF") // Grape purple
        }
    }

    // --- SWORD BLADE TRAIL RENDERING ---

    private fun drawBladeTrail(canvas: Canvas) {
        if (slashTrail.size < 2) return

        val now = System.currentTimeMillis()
        val blinkRed = now - lastBombHitTime < 500L

        // Spawn Sword Particles when moving
        if (Random.nextFloat() < 0.35f && !gamePaused) {
            val color = when (selectedBlade) {
                1 -> Color.parseColor("#FF7043") // Fire Orange
                2 -> Color.parseColor("#00E5FF") // Cyber Cyan
                3 -> Color.parseColor("#7E57C2") // Shadow Violet
                else -> Color.WHITE
            }
            repeat(3) {
                trailParticles.add(
                    TrailParticle(
                        x = cursorX,
                        y = cursorY,
                        vx = Random.nextFloat() * 4f - 2f,
                        vy = Random.nextFloat() * 4f - 2f,
                        color = color,
                        size = Random.nextFloat() * 6f + 3f,
                        alpha = 200
                    )
                )
            }
        }

        paint.reset()
        paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        // Connect trail points
        for (i in 0 until slashTrail.size - 1) {
            val p1 = slashTrail[i]
            val p2 = slashTrail[i+1]
            val ratio = i.toFloat() / slashTrail.size

            // Fade opacity and thickness towards the start of the trail
            paint.strokeWidth = 3f + 16f * ratio
            paint.style = Paint.Style.STROKE

            if (blinkRed) {
                paint.color = Color.RED
            } else {
                paint.color = when (selectedBlade) {
                    1 -> Color.parseColor("#FF5722") // Fire Red-Orange
                    2 -> Color.parseColor("#E040FB") // Neon Pink-Magenta
                    3 -> Color.parseColor("#311B92") // Shadow Deep Purple
                    else -> Color.parseColor("#E0F7FA") // Steel white-cyan
                }
            }
            paint.alpha = (255 * ratio).toInt().coerceIn(0, 255)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }

        // Draw active sword cursor tip
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = if (blinkRed) Color.RED else Color.WHITE
        canvas.drawCircle(cursorX, cursorY, 10f, paint)
    }

    private fun drawCursorIndicator(canvas: Canvas) {
        val cx = cursorX
        val cy = cursorY
        val now = System.currentTimeMillis()
        val blinkRed = now - lastBombHitTime < 500L
        
        paint.reset()
        paint.isAntiAlias = true
        
        // Glow Color based on selected blade
        val glowColor = if (blinkRed) {
            Color.RED
        } else {
            when (selectedBlade) {
                1 -> Color.parseColor("#FF5722") // Flame Orange
                2 -> Color.parseColor("#00E5FF") // Cyber Cyan
                3 -> Color.parseColor("#7E57C2") // Shadow Violet
                else -> Color.parseColor("#FFFFFF") // Steel Katana Silver-White
            }
        }
        
        // 1. Draw outer glowing ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = glowColor
        paint.alpha = 150
        canvas.drawCircle(cx, cy, 22f, paint)
        
        // 2. Draw inner solid core
        paint.style = Paint.Style.FILL
        paint.color = glowColor
        paint.alpha = 255
        canvas.drawCircle(cx, cy, 5f, paint)
        
        // 3. Draw crosshair lines extending outward
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = glowColor
        paint.alpha = 200
        canvas.drawLine(cx - 15f, cy, cx - 8f, cy, paint)
        canvas.drawLine(cx + 8f, cy, cx + 15f, cy, paint)
        canvas.drawLine(cx, cy - 15f, cx, cy - 8f, paint)
        canvas.drawLine(cx, cy + 8f, cx, cy + 15f, paint)
    }

    private fun drawHUD(canvas: Canvas) {
        paint.reset()
        paint.isAntiAlias = true
        
        // Lives (Hearts)
        paint.color = Color.parseColor("#E53935")
        paint.textSize = 38f
        val lifeStr = "♥".repeat(lives.coerceAtLeast(0))
        canvas.drawText(lifeStr, 40f, height * 0.05f, paint)

        // Blade selected Tag
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(bladeNames[selectedBlade], width / 2f, height * 0.05f, paint)

        // Best / Score
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 36f
        canvas.drawText("BEST: $best  SCORE: $score", width - 40f, height * 0.05f, paint)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#D9000000")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(title, width / 2f, height / 2f - 70f, paint)

        paint.textSize = 32f
        paint.typeface = Typeface.DEFAULT
        val lines = subtitle.split("\n")
        var yOffset = height / 2f + 20f
        for (line in lines) {
            canvas.drawText(line, width / 2f, yOffset, paint)
            yOffset += 45f
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                resetGame()
                startGame()
                return true
            }
            return false
        }

        if (gamePaused) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    selectedBlade = (selectedBlade - 1 + 4) % 4
                    SoundManager.playClick()
                    invalidate()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    selectedBlade = (selectedBlade + 1) % 4
                    SoundManager.playClick()
                    invalidate()
                    return true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    resume()
                    return true
                }
            }
            return false
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                cursorY = (cursorY - cursorSpeed).coerceAtLeast(80f)
                slashTrail.add(PointF(cursorX, cursorY))
                if (slashTrail.size > maxTrailSize) slashTrail.removeAt(0)
                SoundManager.playSwoosh()
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                cursorY = (cursorY + cursorSpeed).coerceAtMost(height - 80f)
                slashTrail.add(PointF(cursorX, cursorY))
                if (slashTrail.size > maxTrailSize) slashTrail.removeAt(0)
                SoundManager.playSwoosh()
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                cursorX = (cursorX - cursorSpeed).coerceAtLeast(80f)
                slashTrail.add(PointF(cursorX, cursorY))
                if (slashTrail.size > maxTrailSize) slashTrail.removeAt(0)
                SoundManager.playSwoosh()
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                cursorX = (cursorX + cursorSpeed).coerceAtMost(width - 80f)
                slashTrail.add(PointF(cursorX, cursorY))
                if (slashTrail.size > maxTrailSize) slashTrail.removeAt(0)
                SoundManager.playSwoosh()
                invalidate()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
