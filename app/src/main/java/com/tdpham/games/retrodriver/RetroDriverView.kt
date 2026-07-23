package com.tdpham.games.retrodriver

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
import kotlin.math.cos
import kotlin.random.Random

class RetroDriverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "retrodriver"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isInitialized = false
    private var gameOver = false
    private var gamePaused = true

    private var skyGradSunset: LinearGradient? = null
    private var skyGradMidnight: LinearGradient? = null
    private var skyGradDawn: LinearGradient? = null
    private var skyGradDaylight: LinearGradient? = null
    private var sunGrad: RadialGradient? = null

    private val drawPath = Path()
    private val drawRectF = RectF()
    private val tempPoint3D = Point3D(0f, 0f, 0f)
    private val colorSunsetBase = Color.parseColor("#1B0A2E")
    private val colorMidnightBase = Color.parseColor("#000000")
    private val colorDawnBase = Color.parseColor("#1A237E")
    private val colorDaylightBase = Color.parseColor("#01579B")

    // Pre-parsed colors for rendering to optimize onDraw performance
    private val colorStar = Color.parseColor("#40FFFFFF")
    private val colorExhaust = Color.parseColor("#90A4AE")
    private val colorFlame = Color.parseColor("#FF9100")
    private val colorTaillight = Color.parseColor("#FF5252")
    private val colorWindshield = Color.parseColor("#00E5FF")
    private val colorSpoiler = Color.parseColor("#212121")
    private val colorTire = Color.parseColor("#151515")
    
    private val colorTrafficCabin = Color.parseColor("#4FC3F7")
    private val colorTrafficSpoiler = Color.parseColor("#212121")
    private val colorTrafficBrake = Color.parseColor("#FF1744")
    
    private val colorTrunk = Color.parseColor("#795548")
    private val colorFronds = Color.parseColor("#2E7D32")
    private val colorBush = Color.parseColor("#4CAF50")
    private val colorPost = Color.parseColor("#757575")
    private val colorSign = Color.parseColor("#FFD54F")

    private var score = 0
    private var best = 0
    private var speed = 0f
    private val maxSpeed = 150f
    private var distance = 0f
    
    // Player Position relative to road
    private var playerX = 0f // -1.0 is left edge of road, 1.0 is right edge
    private var position = 0f // camera position along Z axis
    private var steerAngle = 0f // steering rotation angle for tilting car

    // Road parameters
    private val segmentLength = 200f
    private val roadWidth = 2000f
    private val cameraHeight = 1000f
    private val drawDistance = 80 // number of segments to draw
    private val segments = mutableListOf<RoadSegment>()

    // Traffic Cars
    private val traffic = mutableListOf<TrafficCar>()
    private var lastSpawnZ = 0f

    // Hazards & Boosters lists
    private val boostPads = mutableListOf<BoostPad>()
    private val oilSlicks = mutableListOf<OilSlick>()
    private val barriers = mutableListOf<RoadBarrier>()
    private val exhaustParticles = mutableListOf<ExhaustParticle>()
    private val sparkParticles = mutableListOf<SparkParticle>()
    private var lastHazardSpawnZ = 0f

    // Player Health, Stuns, invincibility
    private var lives = 4
    private var isInvincible = false
    private var invincibleUntil = 0L
    private var turboUntil = 0L
    private var oilSpinUntil = 0L
    private var lastCrashTime = 0L

    // Motorcycle Selection (keeping selectedCarIndex for key binding compatibility)
    private var selectedCarIndex = 0 // 0: Red, 1: Yellow, 2: Cyan, 3: Green
    private val carColors = arrayOf("#FF1744", "#FFEA00", "#00E5FF", "#39FF14")
    private val carNames = arrayOf("Suzuki Red", "Yamaha Yellow", "Cyber Cyan", "Kawasaki Neon")

    // Motorcycle Tournament State
    private var countdown = 3
    private var countdownStartTime = 0L
    private var gameWon = false
    private val trackLengthSegments = 240
    private var currentLap = 1
    private val totalLaps = 3
    private var lapMessageUntil = 0L
    private var lapMessage = ""
    
    // Themes
    data class RoadThemeColors(val grass1: Int, val grass2: Int, val rumble1: Int, val rumble2: Int, val road1: Int, val road2: Int)
    private val themes = arrayOf(
        RoadThemeColors(Color.parseColor("#1B5E20"), Color.parseColor("#2E7D32"), Color.WHITE, Color.parseColor("#FF1744"), Color.parseColor("#424242"), Color.parseColor("#373737")), // Neon Sunset
        RoadThemeColors(Color.parseColor("#E59866"), Color.parseColor("#F5B041"), Color.WHITE, Color.parseColor("#E65100"), Color.parseColor("#3E2723"), Color.parseColor("#4E342E")), // Desert Sunset
        RoadThemeColors(Color.parseColor("#1B0A2E"), Color.parseColor("#100520"), Color.parseColor("#00E5FF"), Color.parseColor("#D500F9"), Color.BLACK, Color.parseColor("#121212")), // Cyber Night
        RoadThemeColors(Color.parseColor("#E0F7FA"), Color.parseColor("#ECEFF1"), Color.WHITE, Color.parseColor("#D32F2F"), Color.parseColor("#37474F"), Color.parseColor("#455A64"))  // Snowy Peak
    )
    private var selectedThemeIndex = 0
    private var timeOfDayOffset = 0f

    private val celebrationManager = CelebrationManager()

    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gamePaused && !gameOver) {
                update()
                invalidate()
                handler.postDelayed(this, 20)
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

    data class RoadSegment(
        val index: Int,
        val p1: Point3D, // bottom edge
        val p2: Point3D, // top edge
        var curve: Float,
        var color: RoadColors,
        val decorationType: Int = 0, // 0: None, 1: Palm Tree, 2: Bush, 3: Warning Sign
        val decorationSide: Float = 0f // -1.8f: Left, 1.8f: Right
    )

    data class Point3D(var x: Float, var y: Float, var z: Float) {
        var screenX = 0f
        var screenY = 0f
        var screenW = 0f

        fun project(cx: Float, cy: Float, cz: Float, cameraDepth: Float, width: Int, height: Int) {
            val zDiff = z - cz
            val scale = cameraDepth / (if (zDiff <= 0) 1f else zDiff)
            screenX = (width / 2f) + (x - cx) * scale * (width / 2f)
            screenY = (height / 2f) - (y - cy) * scale * (height / 2f)
            screenW = scale * 1000f * (width / 2f)
        }
    }

    data class RoadColors(val grass: Int, val rumble: Int, val road: Int)

    data class TrafficCar(var offset: Float, var z: Float, val speed: Float, val color: Int, var targetOffset: Float = offset, var lastLaneChangeTime: Long = 0L, val type: Int = Random.nextInt(0, 2))
    data class BoostPad(var offset: Float, var z: Float, val size: Float = 1.0f)
    data class OilSlick(var offset: Float, var z: Float, val size: Float = 1.0f)
    data class RoadBarrier(var offset: Float, var z: Float, val size: Float = 1.0f)
    data class ExhaustParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var alpha: Int)
    data class SparkParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var color: Int, var alpha: Int)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        animHandler.post(animRunnable)
        buildRoad()
    }

    private fun buildRoad() {
        segments.clear()
        val numSegments = 300
        
        val theme = themes[selectedThemeIndex]
        val colors1 = RoadColors(theme.grass1, theme.rumble1, theme.road1)
        val colors2 = RoadColors(theme.grass2, theme.rumble2, theme.road2)

        var accumCurve = 0f
        
        // Generate random curves for chunks of road (every 30 to 50 segments)
        val curvePoints = FloatArray(numSegments)
        var segmentIdx = 0
        while (segmentIdx < numSegments) {
            val chunkLen = Random.nextInt(30, 50)
            val chunkCurve = when (Random.nextInt(5)) {
                0 -> 0f      // Straight
                1 -> -8f     // Gentle Left
                2 -> 8f      // Gentle Right
                3 -> -18f    // Sharp Left
                else -> 18f  // Sharp Right
            }
            
            for (j in 0 until chunkLen) {
                if (segmentIdx + j < numSegments) {
                    curvePoints[segmentIdx + j] = chunkCurve
                }
            }
            segmentIdx += chunkLen
        }
        
        // Checkered flag colors
        val checkColors1 = RoadColors(theme.grass1, Color.BLACK, Color.WHITE)
        val checkColors2 = RoadColors(theme.grass2, Color.WHITE, Color.BLACK)

        for (i in 0 until numSegments) {
            val curveVal = curvePoints[i]
            
            val isCheckered = i in 235..240
            val color = if (isCheckered) {
                if (i % 2 == 0) checkColors1 else checkColors2
            } else {
                if ((i / 3) % 2 == 0) colors1 else colors2
            }

            val p1 = Point3D(accumCurve, 0f, i * segmentLength)
            accumCurve += curveVal
            val p2 = Point3D(accumCurve, 0f, (i + 1) * segmentLength)

            // Spawn roadside assets randomly
            var decType = 0
            var decSide = 0f
            if (i % 6 == 0 && !isCheckered) {
                decType = Random.nextInt(1, 4) // 1: Palm Tree, 2: Bush, 3: Warning Sign
                decSide = if (Random.nextBoolean()) -1.7f else 1.7f
            }

            segments.add(
                RoadSegment(
                    i,
                    p1,
                    p2,
                    curveVal,
                    color,
                    decType,
                    decSide
                )
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            skyGradSunset = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.parseColor("#1B0A2E"), Color.parseColor("#5F0F3F"), Shader.TileMode.CLAMP)
            skyGradMidnight = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.parseColor("#000000"), Color.parseColor("#1E1A3C"), Shader.TileMode.CLAMP)
            skyGradDawn = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.parseColor("#1A237E"), Color.parseColor("#FF5252"), Shader.TileMode.CLAMP)
            skyGradDaylight = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.parseColor("#01579B"), Color.parseColor("#4FC3F7"), Shader.TileMode.CLAMP)

            val sunR = w * 0.12f
            val sunX = w / 2f
            val sunY = h * 0.5f - 20f
            sunGrad = RadialGradient(sunX, sunY, sunR, Color.parseColor("#FFFF00"), Color.parseColor("#FF007F"), Shader.TileMode.CLAMP)

            if (!isInitialized) {
                resetGame()
                isInitialized = true
            }
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
        best = ScoreManager.getHighScore(context, gameKey)
        speed = 0f
        distance = 0f
        position = 0f
        playerX = 0f
        steerAngle = 0f
        currentLap = 1
        lapMessageUntil = 0L
        lapMessage = ""
        
        traffic.clear()
        boostPads.clear()
        oilSlicks.clear()
        barriers.clear()
        exhaustParticles.clear()
        sparkParticles.clear()
        
        lastSpawnZ = 0f
        lastHazardSpawnZ = 0f
        lives = 4
        gameOver = false
        gameWon = false
        gamePaused = true
        
        // Randomize theme and starting time of day offset for this run
        selectedThemeIndex = Random.nextInt(0, themes.size)
        timeOfDayOffset = Random.nextInt(0, 4) * 8000f
        
        buildRoad()
        
        // Setup 20 competitor vehicles (bikes & cars) along the track
        for (i in 0 until 20) {
            val segIdx = 15 + i * 11
            val z = segIdx * segmentLength
            val offset = if (i % 2 == 0) -0.5f else 0.5f
            val competitorSpeed = 38f + Random.nextFloat() * 14f
            val color = when (i % 4) {
                0 -> Color.parseColor("#FFC107") // Yellow
                1 -> Color.parseColor("#00E5FF") // Neon Cyan
                2 -> Color.parseColor("#9C27B0") // Purple
                else -> Color.parseColor("#4CAF50") // Green
            }
            // Alternate between bike (1) and car (0)
            val type = if (i % 2 == 0) 1 else 0
            traffic.add(TrafficCar(offset, z, competitorSpeed, color, type = type))
        }

        countdown = 3
        countdownStartTime = System.currentTimeMillis()
        celebrationManager.start(0f, 0f)
        invalidate()
    }

    private fun spawnTraffic() {
        // Tournament mode: Competitor traffic is pre-spawned statically in resetGame
    }

    private fun update() {
        val now = System.currentTimeMillis()
        
        // Handle starting countdown
        if (countdown >= 0) {
            val elapsed = now - countdownStartTime
            if (elapsed > 1000L) {
                countdown--
                countdownStartTime = now
            }
            speed = 0f
            steerAngle = 0f
            return
        }

        // Handle finish line victory condition (multi-lap loop)
        if (position >= trackLengthSegments * segmentLength && !gameWon && !gameOver) {
            if (currentLap < totalLaps) {
                currentLap++
                lapMessage = if (currentLap == totalLaps) context.getString(R.string.retro_driver_final_lap) else context.getString(R.string.retro_driver_lap_count, currentLap, totalLaps)
                lapMessageUntil = now + 2000L
                
                // Wrap player position
                position -= trackLengthSegments * segmentLength
                
                // Wrap competitor vehicles' z coordinates so they stay in the race relative to the player
                for (car in traffic) {
                    car.z -= trackLengthSegments * segmentLength
                }
            } else {
                gameWon = true
                gamePaused = true
                celebrationManager.start(width / 2f, height / 2f)
                onGameOver?.invoke(score)
                return
            }
        }

        val inTurbo = now < turboUntil
        val inOilSpin = now < oilSpinUntil
        val currentMaxSpeed = if (inTurbo) 220f else maxSpeed
        
        // Decelerate if off-road
        val targetMax = if (Math.abs(playerX) > 1.0f) 25f else currentMaxSpeed
        if (speed > targetMax) {
            speed -= 5.0f
        }
        
        // Natural coasting deceleration when no key is held
        if (speed > 30f && !inTurbo) {
            speed -= 0.6f
        } else if (speed < 30f) {
            speed += 0.8f
        }

        // Steer angle decay (tilt return to center), unless oil spinning
        if (inOilSpin) {
            steerAngle = (sin(now / 40.0).toFloat() * 15f)
        } else {
            steerAngle *= 0.78f
        }

        // Move camera position
        position += speed

        // Centrifugal force on curves (pulls player outward)
        val currentSegIndex = (position / segmentLength).toInt()
        if (currentSegIndex in 0 until segments.size) {
            val currentSeg = segments[currentSegIndex]
            playerX -= currentSeg.curve * (speed / maxSpeed) * 0.004f
            playerX = playerX.coerceIn(-2.5f, 2.5f)
        }

        distance += speed / 50f
        score = distance.toInt()
        if (score > best) {
            ScoreManager.updateHighScore(context, gameKey, score)
            best = score
        }

        // Spawn hazards
        spawnTraffic()

        // Update traffic cars
        val iterator = traffic.iterator()
        while (iterator.hasNext()) {
            val car = iterator.next()
            car.z += car.speed / 2.2f // traffic moves slower

            // Traffic AI: Smoothly change lanes toward targetOffset
            if (car.offset < car.targetOffset) {
                car.offset = (car.offset + 0.015f).coerceAtMost(car.targetOffset)
            } else if (car.offset > car.targetOffset) {
                car.offset = (car.offset - 0.015f).coerceAtLeast(car.targetOffset)
            }

            // Periodically decide to switch lanes if ahead of player
            if (now - car.lastLaneChangeTime > 4000L && car.z > position + 400f && !gamePaused) {
                if (Random.nextFloat() < 0.15f) {
                    car.targetOffset = Random.nextFloat() * 1.6f - 0.8f
                    car.lastLaneChangeTime = now
                }
            }

            if (Math.abs(car.z - position) < 140f && Math.abs(car.offset - playerX) < 0.32f) {
                if (now >= invincibleUntil) {
                    lives--
                    invincibleUntil = now + 2000L
                    lastCrashTime = now
                    if (speed > 80f) {
                        oilSpinUntil = now + (1000 + (speed / maxSpeed) * 1500).toLong()
                        speed = 10f
                    } else {
                        speed = 15f
                    }
                    SoundManager.playError()
                    
                    // Spawn metallic sparks
                    repeat(20) {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        val pSpeed = Random.nextFloat() * 11f + 3f
                        sparkParticles.add(
                            SparkParticle(
                                x = width / 2f + (car.offset - playerX) * width * 0.25f,
                                y = height * 0.85f,
                                vx = cos(angle.toDouble()).toFloat() * pSpeed,
                                vy = sin(angle.toDouble()).toFloat() * pSpeed - 4f,
                                size = Random.nextFloat() * 6f + 3f,
                                color = Color.parseColor("#FFC107"),
                                alpha = 255
                            )
                        )
                    }

                    if (lives <= 0) {
                        gameOver = true
                        celebrationManager.start(width / 2f, height / 2f)
                        onGameOver?.invoke(score)
                    }
                }
                iterator.remove()
                continue
            }

            // Remove if behind player
            if (car.z < position - 200f) {
                iterator.remove()
            }
        }



        // Update Exhaust smoke particles
        if (speed > 10f && !gamePaused) {
            val spawnRate = if (inTurbo) 3 else 1
            repeat(spawnRate) {
                val carW = width * 0.22f
                val carH = carW * 0.55f
                val carX = width / 2f
                val carY = height * 0.82f
                // Spawn smoke from left & right exhausts
                val offsetSide = if (Random.nextBoolean()) -carW * 0.26f else carW * 0.26f
                exhaustParticles.add(
                    ExhaustParticle(
                        x = carX + offsetSide,
                        y = carY + carH + 2f,
                        vx = Random.nextFloat() * 2f - 1f - (steerAngle * 0.15f),
                        vy = Random.nextFloat() * 3f + 1f,
                        size = Random.nextFloat() * 5f + 3f,
                        alpha = 180
                    )
                )
            }
        }
        val exPIterator = exhaustParticles.iterator()
        while (exPIterator.hasNext()) {
            val p = exPIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.size += 0.3f
            p.alpha -= 8
            if (p.alpha <= 0) exPIterator.remove()
        }

        // Update Spark particles
        val spPIterator = sparkParticles.iterator()
        while (spPIterator.hasNext()) {
            val p = spPIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.2f // gravity
            p.alpha -= 10
            if (p.alpha <= 0) spPIterator.remove()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isInitialized) return

        val width = width
        val height = height
        val cameraDepth = 1f / (cameraHeight / segmentLength)

        // 1. Draw Sunset/Night Sky Gradient with dynamic theme cycle
        val now = System.currentTimeMillis()
        val themePhase = (((position + timeOfDayOffset) / 8000f) % 4).toInt()
        val skyGrad = when (themePhase) {
            0 -> skyGradSunset
            1 -> skyGradMidnight
            2 -> skyGradDawn
            else -> skyGradDaylight
        }
        val skyBaseColor = when (themePhase) {
            0 -> colorSunsetBase
            1 -> colorMidnightBase
            2 -> colorDawnBase
            else -> colorDaylightBase
        }
        paint.shader = skyGrad
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.5f, paint)
        paint.shader = null

        // Draw Sky Stars (only visible during sunset/midnight/dawn)
        if (themePhase != 3) {
            paint.color = colorStar
            for (i in 0 until 40) {
                val starX = (width * ((sin(i.toDouble()) + 1) / 2)).toFloat()
                val starY = (height * 0.28f * ((sin(i * 1.6) + 1) / 2)).toFloat()
                canvas.drawCircle(starX, starY, 2f, paint)
            }
        }

        // Draw Pulsing Retro Sunset Sun at horizon (only visible during sunset/midnight/dawn)
        if (themePhase != 3) {
            val sunR = width * 0.12f
            val sunX = width / 2f
            val sunY = height * 0.5f - 20f
            
            // Draw sun body with gradient
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = sunGrad
            canvas.drawCircle(sunX, sunY, sunR, paint)
            paint.shader = null
            
            // Draw synthwave horizontal bands sliced out (using skyGrad shader so they blend with background)
            paint.shader = skyGrad
            paint.style = Paint.Style.STROKE
            for (lineY in (sunY - sunR).toInt()..(sunY + sunR).toInt() step 12) {
                val progress = (lineY - (sunY - sunR)) / (sunR * 2)
                paint.strokeWidth = 2f + progress * 6f
                canvas.drawLine(sunX - sunR, lineY.toFloat(), sunX + sunR, lineY.toFloat(), paint)
            }
            paint.shader = null
            paint.style = Paint.Style.FILL
        }

        // Find starting segment
        val startSegIndex = (position / segmentLength).toInt()
        val baseSeg = segments[startSegIndex % segments.size]
        val segmentPercent = (position % segmentLength) / segmentLength
        val baseRoadX = baseSeg.p1.x + (baseSeg.p2.x - baseSeg.p1.x) * segmentPercent
        
        val cameraX = baseRoadX + playerX * roadWidth
        var maxy = height.toFloat()

        // Draw Road segments from back to front (farthest to closest)
        for (n in drawDistance - 1 downTo 0) {
            val seg = segments[(startSegIndex + n) % segments.size]
            val cz = position
            
            var segZ1 = seg.p1.z
            var segZ2 = seg.p2.z
            val roadLength = segments.size * segmentLength
            
            // Normalize Z coordinates relative to camera position loop for infinite scroll
            val loopIndex = (cz / roadLength).toInt()
            segZ1 += loopIndex * roadLength
            segZ2 += loopIndex * roadLength
            
            if (segZ1 < cz - roadLength * 0.5f) {
                segZ1 += roadLength
                segZ2 += roadLength
            } else if (segZ1 > cz + roadLength * 0.5f) {
                segZ1 -= roadLength
                segZ2 -= roadLength
            }

            if (segZ2 <= cz) continue // completely behind camera

            // Clip near Z to camera plane to prevent rendering artifacts
            val zClip = cz + 1f
            val p1Z = if (segZ1 < zClip) zClip else segZ1
            val p2Z = segZ2

            // Create temporary Point3D for projection
            val p1Proj = Point3D(seg.p1.x, 0f, p1Z)
            val p2Proj = Point3D(seg.p2.x, 0f, p2Z)

            p1Proj.project(cameraX, cameraHeight, cz, cameraDepth, width, height)
            p2Proj.project(cameraX, cameraHeight, cz, cameraDepth, width, height)

            // Draw Road Segment
            drawSegment(canvas, width, p1Proj, p2Proj, seg.color)

            // Draw Dashed Center Lines (Alternating white stripe on odd indexes)
            if (seg.index % 2 == 0) {
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                val stripePath = Path()
                val w1 = p1Proj.screenW * 0.04f
                val w2 = p2Proj.screenW * 0.04f
                stripePath.moveTo(p1Proj.screenX - w1/2, p1Proj.screenY)
                stripePath.lineTo(p2Proj.screenX - w2/2, p2Proj.screenY)
                stripePath.lineTo(p2Proj.screenX + w2/2, p2Proj.screenY)
                stripePath.lineTo(p1Proj.screenX + w1/2, p1Proj.screenY)
                stripePath.close()
                canvas.drawPath(stripePath, paint)
            }

            // Draw Roadside Assets (Trees, Signs)
            if (seg.decorationType > 0) {
                val decWorldX = seg.p1.x + seg.decorationSide * roadWidth
                val decProj = Point3D(decWorldX, 0f, segZ1)
                decProj.project(cameraX, cameraHeight, cz, cameraDepth, width, height)
                
                val decSize = decProj.screenW * 0.65f
                drawDecoration(canvas, decProj.screenX, decProj.screenY, decSize, seg.decorationType)
            }
        }

        // Calculate ranks for all participants (Player + Traffic cars)
        // Store pairs of (carIndex, z) where player is -1
        val participants = mutableListOf<Pair<Int, Float>>()
        participants.add(-1 to position)
        for (i in 0 until traffic.size) {
            participants.add(i to traffic[i].z)
        }
        participants.sortByDescending { it.second }
        
        val rankMap = HashMap<Int, String>()
        for (rankIndex in 0 until participants.size) {
            val rankNum = rankIndex + 1
            val suffix = when (rankNum) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
            rankMap[participants[rankIndex].first] = "$rankNum$suffix"
        }

        // Draw Traffic Cars
        for (car in traffic) {
            var carZ = car.z
            val roadLength = segments.size * segmentLength
            if (carZ < position) {
                carZ += roadLength
            }
            if (carZ > position && carZ < position + drawDistance * segmentLength) {
                val segIndex = (carZ / segmentLength).toInt()
                val seg = segments[segIndex % segments.size]
                
                val carSegPercent = (carZ % segmentLength) / segmentLength
                val roadXAtCar = seg.p1.x + (seg.p2.x - seg.p1.x) * carSegPercent
                val carWorldX = roadXAtCar + car.offset * roadWidth
 
                // Project car
                tempPoint3D.x = carWorldX
                tempPoint3D.y = 0f
                tempPoint3D.z = carZ
                tempPoint3D.project(cameraX, cameraHeight, position, cameraDepth, width, height)
                
                val carW = tempPoint3D.screenW * 0.44f
                val carH = carW * 0.65f
                if (car.type == 1) {
                    drawDetailedTrafficBike(canvas, tempPoint3D.screenX, tempPoint3D.screenY, carW, carH, car.color)
                } else {
                    drawDetailedTrafficCar(canvas, tempPoint3D.screenX, tempPoint3D.screenY, carW, carH, car.color)
                }

                // Draw rank on top of the vehicle
                val rankStr = rankMap[traffic.indexOf(car)] ?: ""
                paint.reset()
                paint.isAntiAlias = true
                paint.color = Color.YELLOW
                paint.textSize = (tempPoint3D.screenW * 0.08f).coerceIn(12f, 26f)
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText(rankStr, tempPoint3D.screenX, tempPoint3D.screenY - carH * 0.65f - 8f, paint)
            }
        }
 
        // Draw Player Motorcycle & Leaning Rider
        paint.reset()
        paint.isAntiAlias = true
        val carW = width * 0.22f
        val carH = carW * 0.55f
        val carX = width / 2f
        val carY = height * 0.82f

        // Draw player position/rank on top of the player
        val playerRankStr = rankMap[-1] ?: "1st"
        paint.color = Color.GREEN
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(playerRankStr, carX, carY - carH * 0.7f, paint)

        canvas.save()
        // Tilt the motorcycle and rider based on steerAngle
        canvas.rotate(steerAngle, carX, carY + carH / 2)

        // Rear Tire (thick center vertical wheel)
        paint.color = Color.parseColor("#151515")
        drawRectF.set(carX - carW * 0.12f, carY + carH * 0.35f, carX + carW * 0.12f, carY + carH * 0.98f)
        canvas.drawRoundRect(drawRectF, 8f, 8f, paint)

        // License Plate / Tag
        paint.color = Color.YELLOW
        canvas.drawRect(carX - 18f, carY + carH * 0.32f, carX + 18f, carY + carH * 0.44f, paint)
        paint.color = Color.BLACK
        paint.textSize = 9f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("MOTO", carX, carY + carH * 0.41f, paint)

        // Chassis / Body Fairing (Neon colored frame)
        paint.color = Color.parseColor(carColors[selectedCarIndex])
        drawRectF.set(carX - carW * 0.28f, carY + carH * 0.15f, carX + carW * 0.28f, carY + carH * 0.65f)
        canvas.drawRoundRect(drawRectF, 12f, 12f, paint)

        // Exhaust Pipes (metal tubes at sides of wheel)
        paint.color = Color.parseColor("#757575")
        canvas.drawCircle(carX - carW * 0.22f, carY + carH * 0.72f, 7f, paint)
        canvas.drawCircle(carX + carW * 0.22f, carY + carH * 0.72f, 7f, paint)

        // Exhaust Flames (vibrating based on speed)
        if (!gamePaused && !gameOver && speed > 20f) {
            paint.color = colorFlame
            val flameSize = (Random.nextFloat() * 12f + 4f) * (speed / maxSpeed)
            canvas.drawCircle(carX - carW * 0.22f, carY + carH * 0.72f + 4f + flameSize / 2, flameSize / 2, paint)
            canvas.drawCircle(carX + carW * 0.22f, carY + carH * 0.72f + 4f + flameSize / 2, flameSize / 2, paint)
        }

        // Rider Torso (Black Leather Jacket)
        paint.color = Color.parseColor("#37474F")
        drawRectF.set(carX - carW * 0.24f, carY - carH * 0.15f, carX + carW * 0.24f, carY + carH * 0.3f)
        canvas.drawRoundRect(drawRectF, 14f, 14f, paint)

        // Neon stripes on Rider's Jacket
        paint.color = Color.parseColor(carColors[selectedCarIndex])
        canvas.drawRect(carX - carW * 0.20f, carY + carH * 0.05f, carX - carW * 0.14f, carY + carH * 0.25f, paint)
        canvas.drawRect(carX + carW * 0.14f, carY + carH * 0.05f, carX + carW * 0.20f, carY + carH * 0.25f, paint)

        // Rider Helmet
        paint.color = Color.parseColor("#212121")
        canvas.drawCircle(carX, carY - carH * 0.38f, carW * 0.18f, paint)

        // Helmet Visor (Glowing Neon Cyan)
        paint.color = Color.parseColor("#00E5FF")
        drawRectF.set(carX - carW * 0.12f, carY - carH * 0.44f, carX + carW * 0.12f, carY - carH * 0.34f)
        canvas.drawRoundRect(drawRectF, 4f, 4f, paint)

        canvas.restore()

        // Calculate live rank and remaining distance
        var passedCount = 0
        for (car in traffic) {
            if (position > car.z) {
                passedCount++
            }
        }
        val currentRank = (21 - passedCount).coerceIn(1, 20)
        val distRemainingMeters = ((trackLengthSegments * segmentLength - position).coerceAtLeast(0f) / 10f).toInt()

        // HUD Text
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 34f
        
        // Left text: Rank, lap, and remaining distance
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("POS: $currentRank / 20   LAP: $currentLap / $totalLaps   DIST: ${distRemainingMeters}m   SPEED: ${speed.toInt()} MPH", 40f, height * 0.06f, paint)
        
        // Right text: Best score
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 40f, height * 0.06f, paint)

        // Draw Lap Message Banner
        val nowTime = System.currentTimeMillis()
        if (nowTime < lapMessageUntil) {
            paint.reset()
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 80f
            paint.color = Color.parseColor("#FFD54F") // Neon Gold
            paint.setShadowLayer(25f, 0f, 0f, Color.parseColor("#FFD54F"))
            canvas.drawText(lapMessage, width / 2f, height / 2f - 40f, paint)
        }

        // Draw Pulsing Starting Countdown
        if (countdown >= 0) {
            paint.reset()
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            
            val elapsed = nowTime - countdownStartTime
            val scale = 1.0f + (1.0f - (elapsed.toFloat() / 1000f).coerceIn(0f, 1f)) * 0.5f
            
            paint.textSize = 100f * scale
            paint.color = Color.parseColor("#00E5FF") // Neon Cyan
            paint.setShadowLayer(25f * scale, 0f, 0f, Color.parseColor("#00E5FF"))
            
            val countStr = if (countdown == 0) context.getString(R.string.retro_driver_go) else countdown.toString()
            canvas.drawText(countStr, width / 2f, height / 2f - 40f, paint)
            paint.clearShadowLayer()
        }

        // Draw Overlays based on state
        if (gameWon) {
            celebrationManager.draw(canvas)
            val suffix = when (currentRank) {
                1 -> context.getString(R.string.rank_1st)
                2 -> context.getString(R.string.rank_2nd)
                3 -> context.getString(R.string.rank_3rd)
                else -> "${currentRank}th"
            }
            val title = context.getString(R.string.retro_driver_finish)
            val subtitle = "You finished in ${currentRank}${suffix} place!\nFinal Score: $score\n\nPress [ENTER] to restart"
            drawOverlay(canvas, title, subtitle)
        } else if (gameOver) {
            celebrationManager.draw(canvas)
            drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (gamePaused) {
            drawOverlay(
                canvas,
                context.getString(R.string.game_retrodriver),
                "${context.getString(R.string.resume_hint)}\n\nVehicle: < ${carNames[selectedCarIndex]} >\n(Press DPAD Left/Right to Switch)"
            )
        }
    }

    private fun drawSegment(canvas: Canvas, width: Int, p1: Point3D, p2: Point3D, color: RoadColors) {
        // Draw Grass
        paint.color = color.grass
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, p2.screenY, width.toFloat(), p1.screenY, paint)

        // Draw Rumble Strips (Rumble width = roadWidth * 0.1f)
        paint.color = color.rumble
        val r1w = p1.screenW * 1.1f
        val r2w = p2.screenW * 1.1f
        drawPath.reset()
        drawPath.moveTo(p1.screenX - r1w, p1.screenY)
        drawPath.lineTo(p2.screenX - r2w, p2.screenY)
        drawPath.lineTo(p2.screenX + r2w, p2.screenY)
        drawPath.lineTo(p1.screenX + r1w, p1.screenY)
        drawPath.close()
        canvas.drawPath(drawPath, paint)

        // Draw Road
        paint.color = color.road
        val w1 = p1.screenW
        val w2 = p2.screenW
        drawPath.reset()
        drawPath.moveTo(p1.screenX - w1, p1.screenY)
        drawPath.lineTo(p2.screenX - w2, p2.screenY)
        drawPath.lineTo(p2.screenX + w2, p2.screenY)
        drawPath.lineTo(p1.screenX + w1, p1.screenY)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
    }

    private fun drawDetailedTrafficCar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        // Main Body (rounded rect)
        paint.color = color
        paint.style = Paint.Style.FILL
        drawRectF.set(x - w / 2, y - h, x + w / 2, y)
        canvas.drawRoundRect(drawRectF, w * 0.12f, w * 0.12f, paint)

        // Rear Windshield / Cabin (blue-ish glass)
        paint.color = colorTrafficCabin
        drawRectF.set(x - w * 0.35f, y - h * 0.85f, x + w * 0.35f, y - h * 0.45f)
        canvas.drawRoundRect(drawRectF, w * 0.08f, w * 0.08f, paint)

        // Spoiler
        paint.color = colorTrafficSpoiler
        canvas.drawRect(x - w * 0.52f, y - h * 0.92f, x + w * 0.52f, y - h * 0.82f, paint)
        canvas.drawRect(x - w * 0.42f, y - h * 0.82f, x - w * 0.38f, y - h * 0.65f, paint)
        canvas.drawRect(x + w * 0.42f, y - h * 0.82f, x + w * 0.38f, y - h * 0.65f, paint)

        // Tires
        paint.color = Color.BLACK
        canvas.drawRect(x - w * 0.52f - 4f, y - h * 0.32f, x - w * 0.52f, y, paint)
        canvas.drawRect(x + w * 0.52f, y - h * 0.32f, x + w * 0.52f + 4f, y, paint)

        // Glowing Brake Lights (Red)
        paint.color = colorTrafficBrake
        canvas.drawRect(x - w * 0.44f, y - h * 0.52f, x - w * 0.28f, y - h * 0.38f, paint)
        canvas.drawRect(x + w * 0.28f, y - h * 0.52f, x + w * 0.44f, y - h * 0.38f, paint)
    }

    private fun drawDetailedTrafficBike(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        // Rear tire
        paint.color = Color.parseColor("#151515")
        paint.style = Paint.Style.FILL
        drawRectF.set(x - w * 0.12f, y - h * 0.6f, x + w * 0.12f, y)
        canvas.drawRoundRect(drawRectF, w * 0.05f, w * 0.05f, paint)

        // Chassis/Body
        paint.color = color
        drawRectF.set(x - w * 0.28f, y - h * 0.8f, x + w * 0.28f, y - h * 0.3f)
        canvas.drawRoundRect(drawRectF, w * 0.1f, w * 0.1f, paint)

        // Taillight
        paint.color = Color.RED
        canvas.drawRect(x - w * 0.1f, y - h * 0.85f, x + w * 0.1f, y - h * 0.8f, paint)

        // Rider body (Leather Jacket)
        paint.color = Color.parseColor("#37474F")
        drawRectF.set(x - w * 0.22f, y - h * 1.2f, x + w * 0.22f, y - h * 0.7f)
        canvas.drawRoundRect(drawRectF, w * 0.08f, w * 0.08f, paint)

        // Rider helmet
        paint.color = Color.parseColor("#212121")
        canvas.drawCircle(x, y - h * 1.45f, w * 0.16f, paint)

        // Neon Visor
        paint.color = Color.parseColor("#FFD54F")
        drawRectF.set(x - w * 0.1f, y - h * 1.5f, x + w * 0.1f, y - h * 1.42f)
        canvas.drawRoundRect(drawRectF, 2f, 2f, paint)
    }

    private fun drawDecoration(canvas: Canvas, x: Float, y: Float, size: Float, type: Int) {
        if (size <= 0) return
        paint.reset()
        paint.isAntiAlias = true

        when (type) {
            1 -> { // Palm Tree
                // Trunk
                paint.color = colorTrunk
                paint.style = Paint.Style.FILL
                val trunkW = size * 0.1f
                val trunkH = size * 0.9f
                canvas.drawRect(x - trunkW / 2, y - trunkH, x + trunkW / 2, y, paint)

                // Fronds/Leaves (Layers of green circles)
                paint.color = colorFronds
                val leafR = size * 0.28f
                canvas.drawCircle(x, y - trunkH, leafR, paint)
                canvas.drawCircle(x - leafR * 0.6f, y - trunkH + leafR * 0.2f, leafR * 0.8f, paint)
                canvas.drawCircle(x + leafR * 0.6f, y - trunkH + leafR * 0.2f, leafR * 0.8f, paint)
                canvas.drawCircle(x, y - trunkH - leafR * 0.5f, leafR * 0.7f, paint)
            }
            2 -> { // Bush
                paint.color = colorBush
                paint.style = Paint.Style.FILL
                val bushR = size * 0.35f
                canvas.drawCircle(x, y - bushR * 0.6f, bushR, paint)
                canvas.drawCircle(x - bushR * 0.5f, y - bushR * 0.4f, bushR * 0.8f, paint)
                canvas.drawCircle(x + bushR * 0.5f, y - bushR * 0.4f, bushR * 0.8f, paint)
            }
            3 -> { // Retro neon billboards / warning signs
                // Post
                paint.color = colorPost
                paint.style = Paint.Style.FILL
                val postW = size * 0.05f
                val postH = size * 0.7f
                canvas.drawRect(x - postW / 2, y - postH, x + postW / 2, y, paint)

                // Sign Board (Yellow chevron warning sign pointing to turn)
                paint.color = colorSign
                val signW = size * 0.4f
                val signH = size * 0.3f
                drawRectF.set(x - signW / 2, y - postH - signH * 0.7f, x + signW / 2, y - postH + signH * 0.3f)
                canvas.drawRoundRect(drawRectF, 4f, 4f, paint)

                // Chevron arrow (Black)
                paint.color = Color.BLACK
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                val signCenterY = y - postH - signH * 0.2f
                drawPath.reset()
                drawPath.moveTo(x - size * 0.06f, signCenterY - size * 0.06f)
                drawPath.lineTo(x + size * 0.04f, signCenterY)
                drawPath.lineTo(x - size * 0.06f, signCenterY + size * 0.06f)
                canvas.drawPath(drawPath, paint)
            }
        }
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
        canvas.drawText(title, width / 2f, height / 2f - 50f, paint)

        paint.textSize = 32f
        paint.typeface = Typeface.DEFAULT
        val lines = subtitle.split("\n")
        var yOffset = height / 2f + 40f
        for (line in lines) {
            canvas.drawText(line, width / 2f, yOffset, paint)
            yOffset += 45f
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gamePaused) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                resume()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                selectedCarIndex = (selectedCarIndex - 1 + carColors.size) % carColors.size
                invalidate()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                selectedCarIndex = (selectedCarIndex + 1) % carColors.size
                invalidate()
                return true
            }
            return false
        }

        if (countdown >= 0) {
            return true
        }

        if (gameWon) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                resetGame()
                startGame()
                return true
            }
            return false
        }

        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                resetGame()
                startGame()
                return true
            }
            return false
        }

        // Allow pausing with Center/Enter during active gameplay
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            pause()
            invalidate()
            return true
        }

        val now = System.currentTimeMillis()
        val inOilSpin = now < oilSpinUntil
        if (inOilSpin) {
            steerAngle = (sin(now / 40.0).toFloat() * 15f)
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                val inTurbo = now < turboUntil
                val currentMaxSpeed = if (inTurbo) 220f else maxSpeed
                val targetMax = if (Math.abs(playerX) > 1.0f) 45f else currentMaxSpeed
                speed = (speed + 6f).coerceAtMost(targetMax)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                speed = (speed - 9f).coerceAtLeast(0f)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val steerSpeed = when (selectedCarIndex) {
                    2 -> 0.12f // Cyber Cyan (Cyber-Runner) has higher handling!
                    else -> 0.08f
                }
                playerX = (playerX - steerSpeed).coerceAtLeast(-2f)
                steerAngle = -8f // Tilt left
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val steerSpeed = when (selectedCarIndex) {
                    2 -> 0.12f // Cyber Cyan (Cyber-Runner) has higher handling!
                    else -> 0.08f
                }
                playerX = (playerX + steerSpeed).coerceAtMost(2f)
                steerAngle = 8f // Tilt right
                invalidate()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
