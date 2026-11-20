package com.tdpham.games.retrodriver

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.content.edit
import androidx.core.graphics.withSave
import com.tdpham.games.R
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.common.GameView
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.ScreenShake
import com.tdpham.games.common.SettingsManager
import com.tdpham.games.common.SoundManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * High-Performance Canvas-based 60 FPS 3D Arcade Superbike Racing Game.
 * Features:
 * - 60 FPS Choreographer render loop with deterministic physics delta-time.
 * - Continuous D-Pad, Gamepad Analog Stick, and Keyboard input with smooth acceleration/steering curves.
 * - Manual Nitro Boost system with plasma exhaust and peripheral speed lines.
 * - Slipstream / Drafting mechanic to slingshot past rivals.
 * - Near-Miss grazing bonuses and real-time overtakes counter.
 * - 3D Rotating collectible arcade gold coins.
 * - Layered synthwave parallax horizons (Neon Grid Mountains, Cyber City Skyline, Desert Mesas, Aurora Borealis).
 * - Real-time Track Radar minimap, Lap Stopwatch, and digital LED speedometer HUD.
 * - Screen shake, metallic sparks, tire skid marks, and rich sound/haptic feedback.
 */
class RetroDriverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "retrodriver"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screenShake = ScreenShake()
    private val celebrationManager = CelebrationManager()

    private var isInitialized = false
    private var isRunning = false
    private var gameOver = false
    private var gamePaused = true
    private var gameWon = false

    // Shaders for dynamic skies & horizons
    private var skyGradSunset: LinearGradient? = null
    private var skyGradMidnight: LinearGradient? = null
    private var skyGradDawn: LinearGradient? = null
    private var skyGradDaylight: LinearGradient? = null
    private var sunGrad: RadialGradient? = null

    private val drawPath = Path()
    private val drawRectF = RectF()
    private val clipBoundsRect = Rect()
    private val tempPoint3D = Point3D(0f, 0f, 0f)

    // Pre-allocated colors for optimal onDraw allocation-free rendering
    private val colorStar = Color.parseColor("#60FFFFFF")
    private val colorFlameCore = Color.parseColor("#FFEA00")
    private val colorFlameOuter = Color.parseColor("#FF3D00")
    private val colorPlasmaCore = Color.parseColor("#00E5FF")
    private val colorPlasmaOuter = Color.parseColor("#D500F9")
    private val colorTaillight = Color.parseColor("#FF1744")
    private val colorTrafficCabin = Color.parseColor("#4FC3F7")
    private val colorTrafficSpoiler = Color.parseColor("#212121")
    private val colorTrafficBrake = Color.parseColor("#FF1744")
    private val colorTrunk = Color.parseColor("#795548")
    private val colorFronds = Color.parseColor("#2E7D32")
    private val colorBush = Color.parseColor("#4CAF50")
    private val colorPost = Color.parseColor("#757575")
    private val colorSign = Color.parseColor("#FFD54F")
    private val colorCoinGold = Color.parseColor("#FFD700")
    private val colorCoinEdge = Color.parseColor("#FFA000")
    private val colorNeonCyan = Color.parseColor("#00E5FF")
    private val colorNeonMagenta = Color.parseColor("#D500F9")
    private val colorNeonGreen = Color.parseColor("#39FF14")
    private val colorNeonGold = Color.parseColor("#FFD54F")
    private val colorHudBg = Color.parseColor("#AA000000")
    private val colorHudBarBg = Color.parseColor("#44FFFFFF")
    private val colorNitroBarBg = Color.parseColor("#22FFFFFF")
    private val colorOverlayBg = Color.parseColor("#D9000000")
    private val colorDarkTire = Color.parseColor("#151515")
    private val colorDarkJacket = Color.parseColor("#37474F")
    private val colorDarkHelmet = Color.parseColor("#212121")
    private val colorSilver = Color.parseColor("#E0E0E0")
    private val colorBronze = Color.parseColor("#CD7F32")
    private val colorShadowDark = Color.argb(180, 0, 0, 0)
    private var coinsCollectedSession = 0

    // Pre-allocated HUD & Competitor helpers to avoid per-frame GC churn
    private val hudStringBuilder = StringBuilder(96)
    private var cachedLeftHeaderText: String = ""
    private var cachedLeftHeaderKey: Long = Long.MIN_VALUE
    private var cachedRightHeaderText: String = ""
    private var cachedRightHeaderBestMs: Long = Long.MIN_VALUE
    private var cachedRightHeaderCoins: Int = -1
    private var cachedSpeedText: String = ""
    private var cachedSpeedMph: Int = -1
    private var cachedRankText: String = ""
    private var cachedRankValue: Int = -1

    // Cached localized HUD labels (loaded once to avoid per-frame resource lookups)
    private val labelDefaultCircuit: String by lazy { context.getString(R.string.retro_hud_default_circuit) }
    private val labelLap: String by lazy { context.getString(R.string.retro_hud_lap) }
    private val labelTime: String by lazy { context.getString(R.string.retro_hud_time) }
    private val labelBest: String by lazy { context.getString(R.string.retro_hud_best) }
    private val labelCoins: String by lazy { context.getString(R.string.retro_hud_coins) }
    private val labelMph: String by lazy { context.getString(R.string.retro_hud_mph) }
    private val labelPos: String by lazy { context.getString(R.string.retro_hud_pos) }
    private val labelSlipstream: String by lazy { context.getString(R.string.retro_hud_slipstream) }
    private val labelNitroBoost: String by lazy { context.getString(R.string.retro_hud_nitro_boost) }

    private val visibleCarIndices = ArrayList<Int>(24)
    private val visibleCarRelZ = FloatArray(24)
    private val rankLabels = arrayOf(
        "1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th", "9th", "10th",
        "11th", "12th", "13th", "14th", "15th", "16th", "17th", "18th", "19th", "20th", "21st"
    )
    private val cachedVehicleColors = intArrayOf(
        Color.parseColor("#FF1744"),
        Color.parseColor("#FFEA00"),
        Color.parseColor("#00E5FF"),
        Color.parseColor("#39FF14")
    )

    // Core Racing Physics
    private var score = 0
    private var best = 0
    private var speed = 0f
    private var targetSpeed = 0f
    private var baseMaxSpeed = 150f
    private var distance = 0f
    private var playerX = 0f // -1.0 is left road edge, 1.0 is right edge
    private var playerVx = 0f
    private var position = 0f // camera distance along track Z
    private var steerAngle = 0f // tilt roll angle for player motorcycle

    // Key & Controller State Tracking for Continuous Input
    private var keyUpPressed = false
    private var keyDownPressed = false
    private var keyLeftPressed = false
    private var keyRightPressed = false
    private var analogGasActive = false
    private var analogBrakeActive = false
    private var analogStickX = 0f
    private var analogStickY = 0f

    // Road parameters
    private val segmentLength = 200f
    private val roadWidth = 2000f
    private val cameraHeight = 1000f
    private val drawDistance = 85 // visible segments
    private val segments = mutableListOf<RoadSegment>()

    // Track Items & Competitors
    private val traffic = mutableListOf<TrafficCar>()
    private val boostPads = mutableListOf<BoostPad>()
    private val oilSlicks = mutableListOf<OilSlick>()
    private val barriers = mutableListOf<RoadBarrier>()
    private val trackCoins = mutableListOf<TrackCoin>()
    private val exhaustParticles = mutableListOf<ExhaustParticle>()
    private val sparkParticles = mutableListOf<SparkParticle>()
    private val coinParticles = mutableListOf<CoinParticle>()
    private val skidMarks = mutableListOf<SkidMark>()
    private val floatingPopups = mutableListOf<FloatingPopup>()

    // Nitro System
    private var nitroCharge = 1.0f // 0.0 to 1.0
    private var turboUntil = 0L
    private var isNitroActive = false

    // Slipstream / Drafting System
    private var isDrafting = false
    private var draftTimeMs = 0L
    private var slipstreamTargetCar: TrafficCar? = null

    // Loss of Control & Invincibility
    private var invincibleUntil = 0L
    private var oilSpinUntil = 0L
    private var lossOfControlUntil = 0L
    private var lastCrashTime = 0L
    private var nearMissCooldownUntil = 0L

    // Vehicle Specifications (4 Distinct Superbikes)
    private var selectedCarIndex = 0
    data class VehicleSpec(val name: String, val colorHex: String, val topSpeed: Float, val accelRate: Float, val handlingRate: Float, val nitroRate: Float)
    private val vehicleSpecs = arrayOf(
        VehicleSpec("Suzuki Red", "#FF1744", 150f, 18f, 0.09f, 1.0f),
        VehicleSpec("Yamaha Yellow", "#FFEA00", 145f, 24f, 0.08f, 1.2f),
        VehicleSpec("Cyber Cyan", "#00E5FF", 155f, 16f, 0.12f, 1.1f),
        VehicleSpec("Kawasaki Neon", "#39FF14", 165f, 20f, 0.07f, 1.4f)
    )

    // Map & Championship Configuration
    private var selectedMapIndex = 0
    private var totalLaps = 3
    private var currentLap = 1
    private val trackLengthSegments = 240
    private var lapStartTime = 0L
    private var currentLapTimeMs = 0L
    private var bestLapTimeMs = 0L
    private var lapMessageUntil = 0L
    private var lapMessage = ""

    // Starting Countdown
    private var countdown = 3
    private var countdownStartTime = 0L

    // Themes
    data class RoadThemeColors(val grass1: Int, val grass2: Int, val rumble1: Int, val rumble2: Int, val road1: Int, val road2: Int)
    private val themes = arrayOf(
        RoadThemeColors(Color.parseColor("#1B5E20"), Color.parseColor("#2E7D32"), Color.WHITE, Color.parseColor("#FF1744"), Color.parseColor("#424242"), Color.parseColor("#373737")), // Neon Sunset
        RoadThemeColors(Color.parseColor("#E59866"), Color.parseColor("#F5B041"), Color.WHITE, Color.parseColor("#E65100"), Color.parseColor("#3E2723"), Color.parseColor("#4E342E")), // Desert Sunset
        RoadThemeColors(Color.parseColor("#1B0A2E"), Color.parseColor("#100520"), Color.parseColor("#00E5FF"), Color.parseColor("#D500F9"), Color.BLACK, Color.parseColor("#121212")), // Cyber Night
        RoadThemeColors(Color.parseColor("#E0F7FA"), Color.parseColor("#ECEFF1"), Color.WHITE, Color.parseColor("#D32F2F"), Color.parseColor("#37474F"), Color.parseColor("#455A64"))  // Snowy Peak
    )
    private var selectedThemeIndex = 0
    private var cameraCurvatureOffset = 0f
    private var animationTick = 0

    // Vsync 60 FPS Choreographer Loop
    private var lastFrameTimeNanos = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning || windowVisibility != View.VISIBLE || !isAttachedToWindow) {
                isRunning = false
                return
            }

            if (gamePaused && !gameOver && !gameWon) {
                isRunning = false
                return
            }

            if (lastFrameTimeNanos == 0L) {
                lastFrameTimeNanos = frameTimeNanos
            }
            val deltaSec = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.005f, 0.04f)
            lastFrameTimeNanos = frameTimeNanos

            if (!gamePaused && !gameOver && !gameWon) {
                updatePhysics(deltaSec)
            } else if (gameOver || gameWon) {
                if (celebrationManager.isActive()) {
                    celebrationManager.update()
                } else {
                    isRunning = false
                    invalidate()
                    return
                }
            }

            animationTick++
            invalidate()
            if (isRunning) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    private fun startFrameLoop() {
        if (!isAttachedToWindow || windowVisibility != View.VISIBLE) return
        if (gamePaused && !gameOver && !gameWon) return
        if (!isRunning) {
            isRunning = true
            lastFrameTimeNanos = 0L
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    private fun stopFrameLoop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    // Data Structures
    data class RoadSegment(
        val index: Int,
        var curve: Float,
        var hill: Float,
        var color: RoadColors,
        val decorationType: Int = 0, // 0: None, 1: Palm Tree, 2: Bush/Cactus, 3: Warning Sign, 4: Cyber Streetlamp
        val decorationSide: Float = 0f
    ) {
        var screenX: Float = 0f
        var screenY: Float = 0f
        var screenW: Float = 0f
        var scale: Float = 0f
    }

    data class Point3D(var x: Float, var y: Float, var z: Float)
    data class RoadColors(val grass: Int, val rumble: Int, val road: Int)
    data class TrafficCar(var offset: Float, var z: Float, val speed: Float, val color: Int, var targetOffset: Float = offset, var lastLaneChangeTime: Long = 0L, val type: Int = Random.nextInt(0, 2))
    data class BoostPad(var offset: Float, var z: Float)
    data class OilSlick(var offset: Float, var z: Float)
    data class RoadBarrier(var offset: Float, var z: Float)
    data class TrackCoin(var offset: Float, var z: Float, var collected: Boolean = false)
    data class ExhaustParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var color: Int, var alpha: Int)
    data class SparkParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var color: Int, var alpha: Int)
    data class CoinParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var alpha: Int)
    data class SkidMark(var x: Float, var y: Float, var w: Float, var alpha: Int)
    data class FloatingPopup(val text: String, var x: Float, var y: Float, val color: Int, var alpha: Int = 255)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        textPaint.typeface = Typeface.DEFAULT_BOLD
        RetroDriverMapCatalog.validateAllMaps()
        buildRoad()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startFrameLoop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            startFrameLoop()
        } else {
            stopFrameLoop()
            resetKeyStates()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopFrameLoop()
        celebrationManager.clear()
    }

    private fun buildRoad() {
        segments.clear()
        boostPads.clear()
        oilSlicks.clear()
        barriers.clear()
        trackCoins.clear()

        val currentMap = RetroDriverMapCatalog.getMap(selectedMapIndex)
        selectedThemeIndex = currentMap.themeIndex.coerceIn(themes.indices)
        val numSegments = 300

        val theme = themes[selectedThemeIndex]
        val colors1 = RoadColors(theme.grass1, theme.rumble1, theme.road1)
        val colors2 = RoadColors(theme.grass2, theme.rumble2, theme.road2)

        val curvePoints = FloatArray(numSegments)
        val hillPoints = FloatArray(numSegments)
        var segIdx = 0
        for (chunk in currentMap.chunks) {
            for (j in 0 until chunk.length) {
                if (segIdx + j < numSegments) {
                    curvePoints[segIdx + j] = chunk.curve
                    hillPoints[segIdx + j] = chunk.hill
                }
            }
            segIdx += chunk.length
        }
        while (segIdx < numSegments) {
            curvePoints[segIdx] = 0f
            hillPoints[segIdx] = 0f
            segIdx++
        }

        val checkColors1 = RoadColors(theme.grass1, Color.BLACK, Color.WHITE)
        val checkColors2 = RoadColors(theme.grass2, Color.WHITE, Color.BLACK)

        for (i in 0 until numSegments) {
            val curveVal = curvePoints[i]
            val hillVal = hillPoints[i]
            val isCheckered = i in 235..240
            val color = if (isCheckered) {
                if (i % 2 == 0) checkColors1 else checkColors2
            } else {
                if ((i / 3) % 2 == 0) colors1 else colors2
            }

            var decType = 0
            var decSide = 0f
            if (i % 3 == 0 && !isCheckered) {
                decType = when {
                    abs(curveVal) > 5f && i % 6 == 0 -> 3 // Warning Chevron Sign on sharp curves
                    selectedThemeIndex == 2 && i % 4 == 0 -> 4 // Cyber Streetlamp
                    selectedThemeIndex == 3 -> 2 // Snowy Pine
                    selectedThemeIndex == 1 -> 2 // Desert Cactus
                    else -> if (Random.nextBoolean()) 1 else 2 // Palm tree / bush
                }
                decSide = if (curveVal > 0) -1.8f else 1.8f
            }

            segments.add(RoadSegment(i, curveVal, hillVal, color, decType, decSide))
        }

        // Spawn Handcrafted Map Obstacles
        for (obs in currentMap.obstacles) {
            val z = obs.segIndex * segmentLength
            when (obs.type) {
                0 -> boostPads.add(BoostPad(obs.offset, z))
                1 -> oilSlicks.add(OilSlick(obs.offset, z))
                2 -> barriers.add(RoadBarrier(obs.offset, z))
            }
        }

        // Procedurally spawn collectible arcade gold coins in clusters along curves and apexes
        for (i in 20 until 230 step 16) {
            val curveAtI = curvePoints[i]
            val coinOffset = when {
                curveAtI > 3f -> 0.45f
                curveAtI < -3f -> -0.45f
                else -> if (i % 32 == 0) -0.3f else 0.3f
            }
            repeat(3) { cIdx ->
                trackCoins.add(TrackCoin(coinOffset, (i + cIdx * 1.5f) * segmentLength))
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            skyGradSunset = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.parseColor("#1B0A2E"), Color.parseColor("#5F0F3F"), Shader.TileMode.CLAMP)
            skyGradMidnight = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.parseColor("#000000"), Color.parseColor("#1E1A3C"), Shader.TileMode.CLAMP)
            skyGradDawn = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.parseColor("#1A237E"), Color.parseColor("#FF5252"), Shader.TileMode.CLAMP)
            skyGradDaylight = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.parseColor("#01579B"), Color.parseColor("#4FC3F7"), Shader.TileMode.CLAMP)

            val sunR = w * 0.13f
            val sunX = w / 2f
            val sunY = h * 0.5f - 20f
            sunGrad = RadialGradient(sunX, sunY, sunR, Color.parseColor("#FFFF00"), Color.parseColor("#FF007F"), Shader.TileMode.CLAMP)

            if (!isInitialized) {
                resetGame()
                isInitialized = true
            }
        }
    }

    override fun startGame() {
        requestFocus()
        gamePaused = false
        lastFrameTimeNanos = 0L
        startFrameLoop()
        invalidate()
    }

    override fun pause() {
        gamePaused = true
        stopFrameLoop()
        resetKeyStates()
        invalidate()
    }

    override fun resume() {
        gamePaused = false
        lastFrameTimeNanos = 0L
        startFrameLoop()
        invalidate()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        speed = 0f
        targetSpeed = 0f
        distance = 0f
        position = 0f
        playerX = 0f
        playerVx = 0f
        steerAngle = 0f
        currentLap = 1
        lapStartTime = 0L
        currentLapTimeMs = 0L
        bestLapTimeMs = 0L
        lapMessageUntil = 0L
        lapMessage = ""
        cameraCurvatureOffset = 0f

        traffic.clear()
        exhaustParticles.clear()
        sparkParticles.clear()
        coinParticles.clear()
        skidMarks.clear()
        floatingPopups.clear()

        gameOver = false
        gameWon = false
        gamePaused = true
        nitroCharge = 1.0f
        isNitroActive = false
        isDrafting = false
        draftTimeMs = 0L

        resetKeyStates()

        // Load settings
        val prefs = context.getSharedPreferences(RetroDriverOptionsDialog.PREFS_NAME, Context.MODE_PRIVATE)
        val prefVehicle = prefs.getInt(RetroDriverOptionsDialog.KEY_CAR_INDEX, 0)
        selectedCarIndex = prefVehicle.coerceIn(0, vehicleSpecs.size - 1)
        baseMaxSpeed = vehicleSpecs[selectedCarIndex].topSpeed

        val prefMap = prefs.getInt(RetroDriverOptionsDialog.KEY_MAP_INDEX, 0)
        selectedMapIndex = prefMap.coerceIn(0, RetroDriverMapCatalog.maps.size - 1)

        totalLaps = prefs.getInt(RetroDriverOptionsDialog.KEY_LAPS, 3)

        buildRoad()

        // Populate 20 competitor vehicles
        for (i in 0 until 20) {
            val segIdx = 16 + i * 11
            val z = segIdx * segmentLength
            val offset = if (i % 2 == 0) -0.55f else 0.55f
            val competitorSpeed = 38f + Random.nextFloat() * 14f
            val color = when (i % 4) {
                0 -> Color.parseColor("#FFC107")
                1 -> Color.parseColor("#00E5FF")
                2 -> Color.parseColor("#9C27B0")
                else -> Color.parseColor("#4CAF50")
            }
            val type = if (i % 2 == 0) 1 else 0 // Bike (1) or Sports Car (0)
            traffic.add(TrafficCar(offset, z, competitorSpeed, color, type = type))
        }

        countdown = 3
        countdownStartTime = System.currentTimeMillis()
        celebrationManager.clear()
        invalidate()
    }

    private fun resetKeyStates() {
        keyUpPressed = false
        keyDownPressed = false
        keyLeftPressed = false
        keyRightPressed = false
        analogGasActive = false
        analogBrakeActive = false
        analogStickX = 0f
        analogStickY = 0f
    }

    // --- PHYSICS & GAMEPLAY SIMULATION (60 FPS FIXED TIMESTEP) ---
    private fun updatePhysics(dt: Float) {
        val now = System.currentTimeMillis()

        // 1. Fast, Punchy Starting Countdown (650ms intervals with bounce)
        if (countdown >= 0) {
            val elapsed = now - countdownStartTime
            if (elapsed > 650L) {
                countdown--
                countdownStartTime = now
                if (countdown > 0) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(context)
                } else if (countdown == 0) {
                    SoundManager.playSuccess()
                    HapticManager.vibrateSuccess(context)
                    lapStartTime = System.currentTimeMillis()
                }
            }
            // Engine rev idling vibration during countdown
            steerAngle = (sin(now / 40.0).toFloat() * 1.5f)
            speed = 0f
            return
        }

        if (lapStartTime == 0L) {
            lapStartTime = now
        }
        currentLapTimeMs = now - lapStartTime

        // 2. Lap & Race Completion
        val trackTotalDistance = trackLengthSegments * segmentLength
        if (position >= trackTotalDistance && !gameWon && !gameOver) {
            if (bestLapTimeMs == 0L || currentLapTimeMs < bestLapTimeMs) {
                bestLapTimeMs = currentLapTimeMs
            }

            if (currentLap < totalLaps) {
                currentLap++
                lapStartTime = now
                lapMessage = if (currentLap == totalLaps) context.getString(R.string.retro_driver_final_lap) else context.getString(R.string.retro_driver_lap_count, currentLap, totalLaps)
                lapMessageUntil = now + 2500L
                floatingPopups.add(FloatingPopup(lapMessage, width / 2f, height * 0.4f, Color.parseColor("#FFD54F")))
                SoundManager.playSuccess()
                HapticManager.vibrateSuccess(context)

                // Loop track coordinates
                position -= trackTotalDistance
                for (car in traffic) {
                    car.z -= trackTotalDistance
                }
                for (coin in trackCoins) {
                    coin.collected = false
                }
            } else {
                gameWon = true
                gamePaused = true
                SoundManager.playSuccess()
                HapticManager.vibrateSuccess(context)
                val isNewHigh = score > best
                if (isNewHigh) {
                    ScoreManager.updateHighScore(context, gameKey, score)
                    best = score
                }
                celebrationManager.start(width / 2f, height / 2f)
                onGameOver?.invoke(score)
                return
            }
        }

        val inTurbo = now < turboUntil
        val inLostControl = now < lossOfControlUntil || now < oilSpinUntil
        val spec = vehicleSpecs[selectedCarIndex]

        // 3. Acceleration & Speed Physics
        val currentMaxSpeed = when {
            inTurbo -> 225f
            isDrafting -> baseMaxSpeed + 30f
            else -> baseMaxSpeed
        }

        // Off-road penalty
        val isOffRoad = abs(playerX) > 1.05f
        val effectiveMax = if (isOffRoad) 45f else currentMaxSpeed

        // Continuous Gas / Brake handling
        val isAccelerating = (keyUpPressed || analogStickY < -0.3f) && !inLostControl
        val isBraking = (keyDownPressed || analogStickY > 0.3f) && !inLostControl

        if (isAccelerating) {
            val accelPower = spec.accelRate * (if (inTurbo) 1.6f else 1.0f)
            speed = (speed + accelPower * dt * 4.5f).coerceAtMost(effectiveMax)
        } else if (isBraking) {
            speed = (speed - 50f * dt * 4.5f).coerceAtLeast(0f)
        } else {
            // Natural drag / coasting deceleration
            if (speed > 35f && !inTurbo) {
                speed -= 8f * dt * 4.5f
            } else if (speed < 30f && !inLostControl && !isOffRoad) {
                speed += 6f * dt * 4.5f
            }
        }

        // Rapid deceleration if speed exceeds off-road max
        if (speed > effectiveMax) {
            speed -= 28f * dt * 4.5f
        }

        // 4. Steering & Leaning Physics
        val steerInput = when {
            abs(analogStickX) > 0.15f -> analogStickX
            keyLeftPressed -> -1.0f
            keyRightPressed -> 1.0f
            else -> 0f
        }

        if (inLostControl) {
            steerAngle = (sin(now / 35.0).toFloat() * 22f)
            playerX += (sin(now / 45.0).toFloat() * 0.035f)
            playerX = playerX.coerceIn(-2.2f, 2.2f)
        } else {
            val targetSteerAngle = steerInput * 24f
            steerAngle += (targetSteerAngle - steerAngle) * (14f * dt)

            val turnStrength = spec.handlingRate * (speed / baseMaxSpeed).coerceIn(0.25f, 1.3f)
            playerVx = steerInput * turnStrength * 60f * dt
            playerX = (playerX + playerVx).coerceIn(-2.2f, 2.2f)

            // Spawn tire skid marks and knee sparks when turning hard at high velocity
            if (abs(steerAngle) > 12f && speed > 90f && !isOffRoad) {
                val carX = width / 2f
                val carY = height * 0.84f
                skidMarks.add(SkidMark(carX - 25f, carY, 12f, 160))
                skidMarks.add(SkidMark(carX + 25f, carY, 12f, 160))

                if (Random.nextFloat() < 0.35f) {
                    val kneeSide = if (steerAngle > 0) 1f else -1f
                    sparkParticles.add(
                        SparkParticle(
                            x = carX + kneeSide * 35f,
                            y = carY + 8f,
                            vx = -kneeSide * (Random.nextFloat() * 4f + 2f),
                            vy = -(Random.nextFloat() * 3f + 1f),
                            size = Random.nextFloat() * 4f + 2f,
                            color = Color.parseColor("#FFD54F"),
                            alpha = 240
                        )
                    )
                }
            }
        }

        // 5. Centrifugal Curve Force (Pushes rider towards outside of bend at high speed)
        val currentSegIndex = (position / segmentLength).toInt()
        if (currentSegIndex in 0 until segments.size) {
            val currentSeg = segments[currentSegIndex]
            val curveCentrifugal = (currentSeg.curve / 10f) * (speed / baseMaxSpeed) * 0.035f * (60f * dt)
            playerX -= curveCentrifugal
            playerX = playerX.coerceIn(-2.2f, 2.2f)
            cameraCurvatureOffset += ((currentSeg.curve / 10f) * (speed / baseMaxSpeed) * 1.5f - cameraCurvatureOffset) * 0.15f
        }

        // Advance Camera Position along Track (Exhilarating 60 FPS velocity)
        position += speed * (60f * dt * 0.16f)
        distance += speed * (60f * dt * 0.05f)
        score = distance.toInt()
        if (score > best) {
            ScoreManager.updateHighScore(context, gameKey, score)
            best = score
        }

        // 6. Passive & Active Nitro Regeneration
        if (!inTurbo) {
            nitroCharge = (nitroCharge + 0.035f * dt * spec.nitroRate).coerceIn(0f, 1f)
        } else {
            nitroCharge = (nitroCharge - 0.38f * dt).coerceIn(0f, 1f)
        }

        // 7. Check Boost Pads
        for (pad in boostPads) {
            val distZ = abs(pad.z - position)
            if (distZ < 110f && abs(pad.offset - playerX) < 0.38f) {
                if (now >= turboUntil) {
                    turboUntil = now + 2400L
                    speed = 225f
                    nitroCharge = 1.0f
                    screenShake.trigger(14, 18f)
                    SoundManager.playPowerUp()
                    HapticManager.vibrateSuccess(context)
                    floatingPopups.add(FloatingPopup("MEGA TURBO!", width / 2f, height * 0.45f, Color.parseColor("#00E5FF")))
                }
            }
        }

        // 8. Check Oil Slicks
        for (oil in oilSlicks) {
            val distZ = abs(oil.z - position)
            if (distZ < 90f && abs(oil.offset - playerX) < 0.32f) {
                if (now >= oilSpinUntil && now >= invincibleUntil) {
                    oilSpinUntil = now + 1200L
                    lossOfControlUntil = now + 1200L
                    speed = (speed * 0.5f).coerceAtLeast(20f)
                    screenShake.trigger(10, 14f)
                    SoundManager.playError()
                    HapticManager.vibrateClick(context)
                    floatingPopups.add(FloatingPopup("OIL SLIP!", width / 2f, height * 0.5f, Color.parseColor("#FF5252")))
                }
            }
        }

        // 9. Check Road Barriers
        for (bar in barriers) {
            val distZ = abs(bar.z - position)
            if (distZ < 100f && abs(bar.offset - playerX) < 0.30f) {
                if (now >= invincibleUntil) {
                    invincibleUntil = now + 1200L
                    lossOfControlUntil = now + 900L
                    speed = (speed * 0.38f).coerceAtLeast(15f)
                    screenShake.trigger(16, 22f)
                    SoundManager.playError()
                    HapticManager.vibrateExplosion(context)
                    repeat(20) {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        val pSpeed = Random.nextFloat() * 12f + 3f
                        sparkParticles.add(
                            SparkParticle(
                                x = width / 2f + (bar.offset - playerX) * width * 0.25f,
                                y = height * 0.84f,
                                vx = cos(angle.toDouble()).toFloat() * pSpeed,
                                vy = sin(angle.toDouble()).toFloat() * pSpeed - 4f,
                                size = Random.nextFloat() * 6f + 3f,
                                color = Color.parseColor("#FF9800"),
                                alpha = 255
                            )
                        )
                    }
                }
            }
        }

        // 10. Check Collectible Arcade Gold Coins
        for (coin in trackCoins) {
            if (!coin.collected) {
                val distZ = abs(coin.z - position)
                if (distZ < 110f && abs(coin.offset - playerX) < 0.32f) {
                    coin.collected = true
                    coinsCollectedSession++
                    score += 100
                    nitroCharge = (nitroCharge + 0.10f).coerceAtMost(1.0f)
                    SoundManager.playScore()
                    HapticManager.vibrateClick(context)
                    floatingPopups.add(FloatingPopup("+1 COIN", width / 2f + (coin.offset - playerX) * width * 0.25f, height * 0.7f, Color.parseColor("#FFD700")))

                    // Burst coin sparkle particles
                    repeat(12) {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        val pSpeed = Random.nextFloat() * 8f + 2f
                        coinParticles.add(
                            CoinParticle(
                                x = width / 2f + (coin.offset - playerX) * width * 0.25f,
                                y = height * 0.82f,
                                vx = cos(angle.toDouble()).toFloat() * pSpeed,
                                vy = sin(angle.toDouble()).toFloat() * pSpeed - 3f,
                                size = Random.nextFloat() * 5f + 3f,
                                alpha = 255
                            )
                        )
                    }
                }
            }
        }

        // 11. Competitors AI, Slipstream Drafting & Near-Miss Detection
        var currentlyDrafting = false
        val trafficIterator = traffic.iterator()
        while (trafficIterator.hasNext()) {
            val car = trafficIterator.next()
            car.z += car.speed * (60f * dt * 0.14f)

            // AI Lane Change
            if (car.offset < car.targetOffset) {
                car.offset = (car.offset + 0.02f).coerceAtMost(car.targetOffset)
            } else if (car.offset > car.targetOffset) {
                car.offset = (car.offset - 0.02f).coerceAtLeast(car.targetOffset)
            }

            if (now - car.lastLaneChangeTime > 3500L && car.z > position + 300f) {
                if (Random.nextFloat() < 0.2f) {
                    car.targetOffset = Random.nextFloat() * 1.5f - 0.75f
                    car.lastLaneChangeTime = now
                }
            }

            val deltaZ = car.z - position
            val deltaX = abs(car.offset - playerX)

            // Slipstream Drafting: Directly behind car (within 80 to 350m, deltaX < 0.28)
            if (deltaZ in 80f..350f && deltaX < 0.28f && speed > 80f) {
                currentlyDrafting = true
                slipstreamTargetCar = car
                nitroCharge = (nitroCharge + 0.08f * dt).coerceAtMost(1.0f)
            }

            // Near-Miss Grazing Bonus: Close pass at high speed
            if (abs(deltaZ) < 80f && deltaX in 0.18f..0.36f && speed > 85f && now >= nearMissCooldownUntil) {
                nearMissCooldownUntil = now + 1200L
                score += 150
                nitroCharge = (nitroCharge + 0.15f).coerceAtMost(1.0f)
                floatingPopups.add(FloatingPopup("NEAR MISS! +150", width / 2f, height * 0.48f, Color.parseColor("#39FF14")))
                SoundManager.playSwoosh()
                HapticManager.vibrateClick(context)
            }

            // Collision with Competitor (Slows down & pushes competitor, NEVER kills player!)
            if (abs(deltaZ) < 120f && deltaX < 0.28f) {
                if (now >= invincibleUntil) {
                    invincibleUntil = now + 1200L
                    lossOfControlUntil = now + 800L
                    lastCrashTime = now
                    speed = (speed * 0.45f).coerceAtLeast(20f)
                    car.z += 90f // push competitor forward
                    screenShake.trigger(14, 18f)
                    SoundManager.playError()
                    HapticManager.vibrateExplosion(context)

                    repeat(20) {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        val pSpeed = Random.nextFloat() * 12f + 3f
                        sparkParticles.add(
                            SparkParticle(
                                x = width / 2f + (car.offset - playerX) * width * 0.25f,
                                y = height * 0.84f,
                                vx = cos(angle.toDouble()).toFloat() * pSpeed,
                                vy = sin(angle.toDouble()).toFloat() * pSpeed - 4f,
                                size = Random.nextFloat() * 6f + 3f,
                                color = Color.parseColor("#FFD54F"),
                                alpha = 255
                            )
                        )
                    }
                }
            }
        }
        isDrafting = currentlyDrafting

        // 12. Particles Update (Exhaust, Sparks, Coins, Skidmarks, Popups)
        if (speed > 10f) {
            val spawnRate = if (inTurbo) 4 else 1
            repeat(spawnRate) {
                val carW = width * 0.22f
                val carH = carW * 0.55f
                val carX = width / 2f
                val carY = height * 0.82f
                val offsetSide = if (Random.nextBoolean()) -carW * 0.26f else carW * 0.26f
                val pColor = if (inTurbo) colorPlasmaCore else colorFlameOuter
                exhaustParticles.add(
                    ExhaustParticle(
                        x = carX + offsetSide,
                        y = carY + carH + 2f,
                        vx = Random.nextFloat() * 2f - 1f - (steerAngle * 0.12f),
                        vy = Random.nextFloat() * 4f + 2f,
                        size = Random.nextFloat() * 6f + 4f,
                        color = pColor,
                        alpha = 200
                    )
                )
            }
        }

        val exPIterator = exhaustParticles.iterator()
        while (exPIterator.hasNext()) {
            val p = exPIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.size += 0.35f
            p.alpha -= 7
            if (p.alpha <= 0) exPIterator.remove()
        }

        val spPIterator = sparkParticles.iterator()
        while (spPIterator.hasNext()) {
            val p = spPIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.25f // gravity
            p.alpha -= 9
            if (p.alpha <= 0) spPIterator.remove()
        }

        val cPIterator = coinParticles.iterator()
        while (cPIterator.hasNext()) {
            val p = cPIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.15f
            p.alpha -= 8
            if (p.alpha <= 0) cPIterator.remove()
        }

        val skidIterator = skidMarks.iterator()
        while (skidIterator.hasNext()) {
            val s = skidIterator.next()
            s.alpha -= 3
            if (s.alpha <= 0) skidIterator.remove()
        }

        val popIterator = floatingPopups.iterator()
        while (popIterator.hasNext()) {
            val pop = popIterator.next()
            pop.y -= 1.2f
            pop.alpha -= 5
            if (pop.alpha <= 0) popIterator.remove()
        }
    }

    // --- RENDERING PIPELINE (SYNTHWAVE 3D CANVAS) ---
    override fun onDraw(canvas: Canvas) {
        if (!isInitialized || !isCanvasRenderable(canvas, width, height) || segments.isEmpty()) return

        val saveCount = canvas.save()
        try {
            screenShake.apply(canvas)
            val width = width
            val height = height
            val cameraDepth = 1f / (cameraHeight / segmentLength)

            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.alpha = 255

            // 1. Synthwave Horizon & Layered Parallax Background
            drawSynthwaveParallaxHorizon(canvas, width, height)

            // 2. Project Visible Segments with Dynamic Cumulative Curves & Topography
            val startSegIndex = (position / segmentLength).toInt()
            val baseSeg = segments[startSegIndex % segments.size]
            val segmentPercent = (position % segmentLength) / segmentLength
            val cameraX = playerX * roadWidth

            var dx = -(baseSeg.curve * segmentPercent)
            var dy = -(baseSeg.hill * segmentPercent)
            var accumulatedX = 0f
            var accumulatedY = 0f

            for (n in 0 until drawDistance) {
                val seg = segments[(startSegIndex + n) % segments.size]
                val zDiff = (n * segmentLength - (position % segmentLength)).coerceAtLeast(1f)
                val scale = cameraDepth / zDiff

                accumulatedX += dx
                accumulatedY += dy
                dx += seg.curve * 1.8f
                dy += seg.hill * 0.8f

                seg.scale = scale
                seg.screenX = (width / 2f) + (accumulatedX - cameraX) * scale * (width / 2f)
                seg.screenY = (height / 2f) - (cameraHeight + accumulatedY) * scale * (height / 2f)
                seg.screenW = scale * roadWidth * (width / 2f)
            }

            // 3. Draw Road Segments (Farthest to Closest)
            for (n in drawDistance - 1 downTo 1) {
                val seg1 = segments[(startSegIndex + n - 1) % segments.size]
                val seg2 = segments[(startSegIndex + n) % segments.size]
                if (seg2.screenY >= seg1.screenY) continue

                drawSegment(canvas, width, seg1.screenX, seg1.screenY, seg1.screenW, seg2.screenX, seg2.screenY, seg2.screenW, seg2.color)

                // Dashed Centerline (reuses pre-allocated drawPath)
                if (seg2.index % 2 == 0) {
                    paint.color = Color.WHITE
                    paint.style = Paint.Style.FILL
                    drawPath.reset()
                    val w1 = seg1.screenW * 0.04f
                    val w2 = seg2.screenW * 0.04f
                    drawPath.moveTo(seg1.screenX - w1 / 2, seg1.screenY)
                    drawPath.lineTo(seg2.screenX - w2 / 2, seg2.screenY)
                    drawPath.lineTo(seg2.screenX + w2 / 2, seg2.screenY)
                    drawPath.lineTo(seg1.screenX + w1 / 2, seg1.screenY)
                    drawPath.close()
                    canvas.drawPath(drawPath, paint)
                }

                // Roadside Assets
                if (seg2.decorationType > 0) {
                    val decX = seg2.screenX + seg2.decorationSide * seg2.screenW
                    val decSize = seg2.screenW * 0.75f
                    drawDecoration(canvas, decX, seg2.screenY, decSize, seg2.decorationType)
                }
            }

            // 4. Draw Skid Marks
            paint.style = Paint.Style.FILL
            for (skid in skidMarks) {
                paint.color = Color.BLACK
                paint.alpha = skid.alpha
                canvas.drawRect(skid.x - skid.w / 2, skid.y - 4f, skid.x + skid.w / 2, skid.y + 4f, paint)
            }
            paint.alpha = 255

            // 5. Draw Boost Pads, Oil Slicks, Barriers & Gold Coins
            drawTrackObjects(canvas, width, height)

            // 6. Draw Traffic & Competitor Vehicles
            drawCompetitorVehicles(canvas, width, height)

            // 7. Draw Headlight Projection Beam (On Cyber Night or Dark themes)
            if (selectedThemeIndex == 2 || selectedThemeIndex == 0) {
                drawHeadlightBeam(canvas, width, height)
            }

            // 8. Draw Player Motorcycle & Leaning Rider
            drawPlayerMotorcycle(canvas, width, height)

            // 9. High-Speed Radial Motion Speed Lines
            if (speed > 125f || (now() < turboUntil)) {
                drawSpeedLines(canvas, width, height)
            }

            // 10. Draw Floating Notifications & Popups (zero-blur crisp shadow)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 38f
            for (pop in floatingPopups) {
                val popText = pop.text
                if (popText.isNotEmpty() && pop.x.isFinite() && pop.y.isFinite()) {
                    textPaint.color = Color.BLACK
                    textPaint.alpha = (pop.alpha * 0.65f).toInt().coerceIn(0, 255)
                    safeDrawText(canvas, popText, pop.x + 2f, pop.y + 2f, textPaint)
                    textPaint.color = pop.color
                    textPaint.alpha = pop.alpha.coerceIn(0, 255)
                    safeDrawText(canvas, popText, pop.x, pop.y, textPaint)
                }
            }
            textPaint.alpha = 255

            // 11. Synthwave HUD (Speedometer, Tachometer, Radar, Lap Stopwatch)
            drawSynthwaveHUD(canvas, width, height)

            // 12. State Overlays
            drawStateOverlays(canvas, width, height)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    // --- PARALLAX HORIZON RENDERING ---
    private fun drawSynthwaveParallaxHorizon(canvas: Canvas, width: Int, height: Int) {
        val skyGrad = when (selectedThemeIndex) {
            0 -> skyGradSunset
            1 -> skyGradMidnight
            2 -> skyGradDawn
            else -> skyGradDaylight
        }
        paint.shader = skyGrad
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.5f, paint)
        paint.shader = null

        // Stars
        if (selectedThemeIndex != 3) {
            paint.color = colorStar
            for (i in 0 until 40) {
                val starX = (width * ((sin(i.toDouble()) + 1) / 2)).toFloat()
                val starY = (height * 0.28f * ((sin(i * 1.6) + 1) / 2)).toFloat()
                canvas.drawCircle(starX, starY, 2f, paint)
            }
        }

        val horizonY = height * 0.5f
        val parallaxX = cameraCurvatureOffset * 30f

        // Draw Theme-Specific Horizons
        when (selectedThemeIndex) {
            0 -> { // Neon Sunset: Pulsing retro sun with laser scanlines + glowing neon mountain ridges
                val sunR = width * 0.12f
                val sunX = width / 2f + parallaxX * 0.5f
                val sunY = horizonY - 15f
                paint.shader = sunGrad
                canvas.drawCircle(sunX, sunY, sunR, paint)
                paint.shader = null

                // Laser scanline cuts
                paint.shader = skyGradSunset
                paint.style = Paint.Style.STROKE
                for (lineY in (sunY - sunR).toInt()..(sunY + sunR).toInt() step 12) {
                    val progress = (lineY - (sunY - sunR)) / (sunR * 2)
                    paint.strokeWidth = 2f + progress * 6f
                    canvas.drawLine(sunX - sunR, lineY.toFloat(), sunX + sunR, lineY.toFloat(), paint)
                }
                paint.shader = null
                paint.style = Paint.Style.FILL

                // Neon Mountain Ridge Silhouettes
                drawMountainRidge(canvas, width, horizonY, Color.parseColor("#4A148C"), 70f, parallaxX * 1.2f)
                drawMountainRidge(canvas, width, horizonY, Color.parseColor("#311B92"), 40f, parallaxX * 2.0f)
            }
            1 -> { // Desert Sunset: Sandstone canyon mesas
                val sunR = width * 0.11f
                val sunX = width / 2f + parallaxX * 0.4f
                val sunY = horizonY - 10f
                paint.color = Color.parseColor("#FF6D00")
                canvas.drawCircle(sunX, sunY, sunR, paint)

                drawMesaRidge(canvas, width, horizonY, Color.parseColor("#4E342E"), 60f, parallaxX * 1.2f)
                drawMesaRidge(canvas, width, horizonY, Color.parseColor("#3E2723"), 35f, parallaxX * 2.0f)
            }
            2 -> { // Cyber Night: Cyberpunk skyscraper skyline & Cyber Moon
                val moonX = width * 0.75f + parallaxX * 0.3f
                val moonY = height * 0.2f
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(moonX, moonY, 35f, paint)

                drawCyberSkyline(canvas, width, horizonY, Color.parseColor("#0D0814"), 110f, parallaxX * 1.2f)
                drawCyberSkyline(canvas, width, horizonY, Color.parseColor("#150B24"), 65f, parallaxX * 2.2f)
            }
            3 -> { // Snowy Peak: Northern lights & snowy alpine peaks
                drawAuroraBorealis(canvas, width, height * 0.35f)
                drawAlpineRidge(canvas, width, horizonY, Color.parseColor("#263238"), 90f, parallaxX * 1.2f)
                drawAlpineRidge(canvas, width, horizonY, Color.parseColor("#37474F"), 50f, parallaxX * 2.0f)
            }
        }
    }

    private fun drawMountainRidge(canvas: Canvas, width: Int, horizonY: Float, color: Int, peakHeight: Float, offset: Float) {
        paint.color = color
        paint.style = Paint.Style.FILL
        drawPath.reset()
        drawPath.moveTo(0f, horizonY)
        val step = width / 10f
        for (i in 0..10) {
            val px = i * step + offset
            val py = if (i % 2 == 1) horizonY - peakHeight else horizonY - (peakHeight * 0.3f)
            drawPath.lineTo(px, py)
        }
        drawPath.lineTo(width.toFloat(), horizonY)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
    }

    private fun drawMesaRidge(canvas: Canvas, width: Int, horizonY: Float, color: Int, mesaHeight: Float, offset: Float) {
        paint.color = color
        paint.style = Paint.Style.FILL
        drawPath.reset()
        drawPath.moveTo(0f, horizonY)
        val step = width / 6f
        for (i in 0..6) {
            val px = i * step + offset
            drawPath.lineTo(px, horizonY - mesaHeight)
            drawPath.lineTo(px + step * 0.6f, horizonY - mesaHeight)
            drawPath.lineTo(px + step * 0.8f, horizonY)
        }
        drawPath.lineTo(width.toFloat(), horizonY)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
    }

    private fun drawCyberSkyline(canvas: Canvas, width: Int, horizonY: Float, color: Int, maxBldgH: Float, offset: Float) {
        paint.color = color
        paint.style = Paint.Style.FILL
        val step = 45f
        var cx = -step + (offset % step)
        while (cx < width + step) {
            val bldgH = (abs(sin(cx * 0.05)) * maxBldgH).toFloat() + 20f
            canvas.drawRect(cx, horizonY - bldgH, cx + step * 0.85f, horizonY, paint)

            // Neon glowing windows on foreground buildings
            if (maxBldgH < 80f) {
                paint.color = Color.parseColor("#00E5FF")
                paint.alpha = 140
                canvas.drawRect(cx + 6f, horizonY - bldgH + 8f, cx + step * 0.85f - 6f, horizonY - bldgH + 14f, paint)
                paint.color = color
                paint.alpha = 255
            }
            cx += step
        }
    }

    private fun drawAlpineRidge(canvas: Canvas, width: Int, horizonY: Float, color: Int, peakHeight: Float, offset: Float) {
        paint.color = color
        paint.style = Paint.Style.FILL
        drawPath.reset()
        drawPath.moveTo(0f, horizonY)
        val step = width / 8f
        for (i in 0..8) {
            val px = i * step + offset
            val py = if (i % 2 == 1) horizonY - peakHeight else horizonY - (peakHeight * 0.2f)
            drawPath.lineTo(px, py)
        }
        drawPath.lineTo(width.toFloat(), horizonY)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
    }

    private fun drawAuroraBorealis(canvas: Canvas, width: Int, maxY: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 35f
        paint.color = Color.parseColor("#1DE9B6")
        paint.alpha = 60
        drawPath.reset()
        drawPath.moveTo(0f, maxY * 0.5f)
        for (x in 0..width step 40) {
            val y = maxY * 0.5f + (sin((x + animationTick * 4) * 0.015f) * 35f).toFloat()
            drawPath.lineTo(x.toFloat(), y)
        }
        canvas.drawPath(drawPath, paint)
        paint.style = Paint.Style.FILL
    }

    // --- ROAD & DECORATION RENDERING ---
    private fun drawSegment(
        canvas: Canvas,
        width: Int,
        x1: Float,
        y1: Float,
        w1: Float,
        x2: Float,
        y2: Float,
        w2: Float,
        color: RoadColors
    ) {
        paint.color = color.grass
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, y2, width.toFloat(), y1, paint)

        paint.color = color.rumble
        val r1w = w1 * 1.14f
        val r2w = w2 * 1.14f
        drawPath.reset()
        drawPath.moveTo(x1 - r1w, y1)
        drawPath.lineTo(x2 - r2w, y2)
        drawPath.lineTo(x2 + r2w, y2)
        drawPath.lineTo(x1 + r1w, y1)
        drawPath.close()
        canvas.drawPath(drawPath, paint)

        paint.color = color.road
        drawPath.reset()
        drawPath.moveTo(x1 - w1, y1)
        drawPath.lineTo(x2 - w2, y2)
        drawPath.lineTo(x2 + w2, y2)
        drawPath.lineTo(x1 + w1, y1)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
    }

    private fun drawDecoration(canvas: Canvas, x: Float, y: Float, size: Float, type: Int) {
        if (size <= 0) return
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        when (type) {
            1 -> { // Palm Tree
                paint.color = colorTrunk
                val trunkW = size * 0.1f
                val trunkH = size * 0.9f
                canvas.drawRect(x - trunkW / 2, y - trunkH, x + trunkW / 2, y, paint)
                paint.color = colorFronds
                val leafR = size * 0.28f
                canvas.drawCircle(x, y - trunkH, leafR, paint)
                canvas.drawCircle(x - leafR * 0.6f, y - trunkH + leafR * 0.2f, leafR * 0.8f, paint)
                canvas.drawCircle(x + leafR * 0.6f, y - trunkH + leafR * 0.2f, leafR * 0.8f, paint)
                canvas.drawCircle(x, y - trunkH - leafR * 0.5f, leafR * 0.7f, paint)
            }
            2 -> { // Bush / Pine
                paint.color = colorBush
                val bushR = size * 0.35f
                canvas.drawCircle(x, y - bushR * 0.6f, bushR, paint)
                canvas.drawCircle(x - bushR * 0.5f, y - bushR * 0.4f, bushR * 0.8f, paint)
                canvas.drawCircle(x + bushR * 0.5f, y - bushR * 0.4f, bushR * 0.8f, paint)
            }
            3 -> { // Chevron Warning Sign
                paint.color = colorPost
                val postW = size * 0.05f
                val postH = size * 0.7f
                canvas.drawRect(x - postW / 2, y - postH, x + postW / 2, y, paint)
                paint.color = colorSign
                val signW = size * 0.4f
                val signH = size * 0.3f
                drawRectF.set(x - signW / 2, y - postH - signH * 0.7f, x + signW / 2, y - postH + signH * 0.3f)
                canvas.drawRoundRect(drawRectF, 4f, 4f, paint)
            }
            4 -> { // Cyber Streetlamp
                paint.color = Color.parseColor("#37474F")
                val postW = size * 0.06f
                val postH = size * 1.1f
                canvas.drawRect(x - postW / 2, y - postH, x + postW / 2, y, paint)
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(x, y - postH, size * 0.12f, paint)
            }
        }
    }

    private fun drawTrackObjects(canvas: Canvas, width: Int, height: Int) {
        val roadLength = segments.size * segmentLength
        val startSegIndex = (position / segmentLength).toInt()

        // Boost Pads
        for (pad in boostPads) {
            var padZ = pad.z
            while (padZ < position) padZ += roadLength
            val relZ = padZ - position
            if (relZ > 1f && relZ < (drawDistance - 1) * segmentLength) {
                val segN = (relZ / segmentLength).toInt().coerceIn(0, drawDistance - 2)
                val seg = segments[(startSegIndex + segN) % segments.size]
                val padX = seg.screenX + pad.offset * seg.screenW
                val padY = seg.screenY
                val padW = seg.screenW * 0.35f
                val padH = padW * 0.4f

                paint.color = Color.parseColor("#00E5FF")
                paint.alpha = 190
                drawRectF.set(padX - padW / 2, padY - padH / 2, padX + padW / 2, padY + padH / 2)
                canvas.drawRoundRect(drawRectF, 6f, 6f, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (padW * 0.12f).coerceAtLeast(3f)
                paint.color = Color.WHITE
                drawPath.reset()
                drawPath.moveTo(padX - padW * 0.35f, padY + padH * 0.25f)
                drawPath.lineTo(padX, padY - padH * 0.25f)
                drawPath.lineTo(padX + padW * 0.35f, padY + padH * 0.25f)
                canvas.drawPath(drawPath, paint)
                paint.style = Paint.Style.FILL
            }
        }

        // Oil Slicks
        for (oil in oilSlicks) {
            var oilZ = oil.z
            while (oilZ < position) oilZ += roadLength
            val relZ = oilZ - position
            if (relZ > 1f && relZ < (drawDistance - 1) * segmentLength) {
                val segN = (relZ / segmentLength).toInt().coerceIn(0, drawDistance - 2)
                val seg = segments[(startSegIndex + segN) % segments.size]
                val oilX = seg.screenX + oil.offset * seg.screenW
                val oilY = seg.screenY
                val oilW = seg.screenW * 0.32f
                val oilH = oilW * 0.35f
                paint.color = Color.parseColor("#212121")
                paint.alpha = 210
                drawRectF.set(oilX - oilW / 2, oilY - oilH / 2, oilX + oilW / 2, oilY + oilH / 2)
                canvas.drawOval(drawRectF, paint)
            }
        }

        // Barriers
        for (bar in barriers) {
            var barZ = bar.z
            while (barZ < position) barZ += roadLength
            val relZ = barZ - position
            if (relZ > 1f && relZ < (drawDistance - 1) * segmentLength) {
                val segN = (relZ / segmentLength).toInt().coerceIn(0, drawDistance - 2)
                val seg = segments[(startSegIndex + segN) % segments.size]
                val barX = seg.screenX + bar.offset * seg.screenW
                val barY = seg.screenY
                val barW = seg.screenW * 0.28f
                val barH = barW * 0.6f
                paint.color = Color.parseColor("#FFD600")
                drawRectF.set(barX - barW / 2, barY - barH, barX + barW / 2, barY)
                canvas.drawRoundRect(drawRectF, 4f, 4f, paint)
                paint.color = Color.BLACK
                canvas.drawRect(barX - barW * 0.3f, barY - barH, barX - barW * 0.1f, barY, paint)
                canvas.drawRect(barX + barW * 0.1f, barY - barH, barX + barW * 0.3f, barY, paint)
            }
        }

        // Collectible 3D Rotating Gold Coins
        for (coin in trackCoins) {
            if (!coin.collected) {
                var coinZ = coin.z
                while (coinZ < position) coinZ += roadLength
                val relZ = coinZ - position
                if (relZ > 1f && relZ < (drawDistance - 1) * segmentLength) {
                    val segN = (relZ / segmentLength).toInt().coerceIn(0, drawDistance - 2)
                    val seg = segments[(startSegIndex + segN) % segments.size]
                    val coinX = seg.screenX + coin.offset * seg.screenW
                    val coinY = seg.screenY
                    val coinRadius = seg.screenW * 0.11f

                    val spinScale = abs(cos((animationTick + coin.z) * 0.08f))
                    // Lightweight outer glow ring (zero Skia blur)
                    paint.color = colorCoinGold
                    paint.alpha = 70
                    drawRectF.set(
                        coinX - coinRadius * spinScale * 1.25f,
                        coinY - coinRadius * 2.15f,
                        coinX + coinRadius * spinScale * 1.25f,
                        coinY + coinRadius * 0.15f
                    )
                    canvas.drawOval(drawRectF, paint)
                    paint.alpha = 255
                    drawRectF.set(coinX - coinRadius * spinScale, coinY - coinRadius * 2, coinX + coinRadius * spinScale, coinY)
                    canvas.drawOval(drawRectF, paint)
                }
            }
        }
    }

    private fun drawCompetitorVehicles(canvas: Canvas, width: Int, height: Int) {
        val roadLength = segments.size * segmentLength
        val startSegIndex = (position / segmentLength).toInt()

        visibleCarIndices.clear()
        val trafficCount = traffic.size
        for (i in 0 until trafficCount) {
            var carZ = traffic[i].z
            while (carZ < position) carZ += roadLength
            val relZ = carZ - position
            if (relZ > 1f && relZ < (drawDistance - 1) * segmentLength) {
                if (i < visibleCarRelZ.size) {
                    visibleCarRelZ[i] = relZ
                    visibleCarIndices.add(i)
                }
            }
        }
        // Insertion sort visible cars by relZ descending (farthest first, zero allocation)
        for (i in 1 until visibleCarIndices.size) {
            val keyIdx = visibleCarIndices[i]
            val keyZ = visibleCarRelZ[keyIdx]
            var j = i - 1
            while (j >= 0 && visibleCarRelZ[visibleCarIndices[j]] < keyZ) {
                visibleCarIndices[j + 1] = visibleCarIndices[j]
                j--
            }
            visibleCarIndices[j + 1] = keyIdx
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.YELLOW
        for (idxPos in 0 until visibleCarIndices.size) {
            val carIndex = visibleCarIndices[idxPos]
            val car = traffic[carIndex]
            val relZ = visibleCarRelZ[carIndex]
            val segN = (relZ / segmentLength).toInt().coerceIn(0, drawDistance - 2)
            val seg = segments[(startSegIndex + segN) % segments.size]
            val carX = seg.screenX + car.offset * seg.screenW
            val carY = seg.screenY
            val carW = seg.screenW * 0.44f
            val carH = carW * 0.65f

            if (car.type == 1) {
                drawDetailedTrafficBike(canvas, carX, carY, carW, carH, car.color)
            } else {
                drawDetailedTrafficCar(canvas, carX, carY, carW, carH, car.color)
            }

            // Compute car rank without allocating maps/pairs
            var ahead = if (position > car.z) 1 else 0
            for (k in 0 until trafficCount) {
                if (k != carIndex && traffic[k].z > car.z) ahead++
            }
            val rankStr = rankLabels[ahead.coerceIn(0, rankLabels.lastIndex)]
            textPaint.textSize = (seg.screenW * 0.08f).coerceIn(12f, 24f)
            safeDrawText(canvas, rankStr, carX, carY - carH * 0.7f - 6f, textPaint)
        }
    }

    private fun drawDetailedTrafficCar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        drawRectF.set(x - w / 2, y - h, x + w / 2, y)
        canvas.drawRoundRect(drawRectF, w * 0.12f, w * 0.12f, paint)

        paint.color = colorTrafficCabin
        drawRectF.set(x - w * 0.35f, y - h * 0.85f, x + w * 0.35f, y - h * 0.45f)
        canvas.drawRoundRect(drawRectF, w * 0.08f, w * 0.08f, paint)

        paint.color = colorTrafficSpoiler
        canvas.drawRect(x - w * 0.52f, y - h * 0.92f, x + w * 0.52f, y - h * 0.82f, paint)

        paint.color = Color.BLACK
        canvas.drawRect(x - w * 0.52f - 4f, y - h * 0.32f, x - w * 0.52f, y, paint)
        canvas.drawRect(x + w * 0.52f, y - h * 0.32f, x + w * 0.52f + 4f, y, paint)

        paint.color = colorTrafficBrake
        canvas.drawRect(x - w * 0.44f, y - h * 0.52f, x - w * 0.28f, y - h * 0.38f, paint)
        canvas.drawRect(x + w * 0.28f, y - h * 0.52f, x + w * 0.44f, y - h * 0.38f, paint)
    }

    private fun drawDetailedTrafficBike(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = Color.parseColor("#151515")
        paint.style = Paint.Style.FILL
        drawRectF.set(x - w * 0.12f, y - h * 0.6f, x + w * 0.12f, y)
        canvas.drawRoundRect(drawRectF, w * 0.05f, w * 0.05f, paint)

        paint.color = color
        drawRectF.set(x - w * 0.28f, y - h * 0.8f, x + w * 0.28f, y - h * 0.3f)
        canvas.drawRoundRect(drawRectF, w * 0.1f, w * 0.1f, paint)

        paint.color = Color.RED
        canvas.drawRect(x - w * 0.1f, y - h * 0.85f, x + w * 0.1f, y - h * 0.8f, paint)

        paint.color = Color.parseColor("#37474F")
        drawRectF.set(x - w * 0.22f, y - h * 1.2f, x + w * 0.22f, y - h * 0.7f)
        canvas.drawRoundRect(drawRectF, w * 0.08f, w * 0.08f, paint)

        paint.color = Color.parseColor("#212121")
        canvas.drawCircle(x, y - h * 1.45f, w * 0.16f, paint)

        paint.color = Color.parseColor("#FFD54F")
        drawRectF.set(x - w * 0.1f, y - h * 1.5f, x + w * 0.1f, y - h * 1.42f)
        canvas.drawRoundRect(drawRectF, 2f, 2f, paint)
    }

    private fun drawHeadlightBeam(canvas: Canvas, width: Int, height: Int) {
        val carX = width / 2f
        val carY = height * 0.84f
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = 30
        drawPath.reset()
        drawPath.moveTo(carX - 15f, carY)
        drawPath.lineTo(carX + 15f, carY)
        drawPath.lineTo(carX + width * 0.32f, height * 0.52f)
        drawPath.lineTo(carX - width * 0.32f, height * 0.52f)
        drawPath.close()
        canvas.drawPath(drawPath, paint)
    }

    // --- PLAYER MOTORCYCLE & RIDER ---
    private fun drawPlayerMotorcycle(canvas: Canvas, width: Int, height: Int) {
        val carW = width * 0.22f
        val carH = carW * 0.55f
        val carX = width / 2f
        val carY = height * 0.82f
        val spec = vehicleSpecs[selectedCarIndex]
        val inTurbo = now() < turboUntil

        // Draw Exhaust Particles
        for (p in exhaustParticles) {
            paint.color = p.color
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }

        // Draw Sparks
        for (p in sparkParticles) {
            paint.color = p.color
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }

        // Draw Coin Burst Particles
        for (p in coinParticles) {
            paint.color = colorCoinGold
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }

        // Slipstream Aerodynamic Wind Streaks
        if (isDrafting) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.parseColor("#00E5FF")
            paint.alpha = (sin(animationTick * 0.3) * 60 + 180).toInt().coerceIn(0, 255)
            canvas.drawLine(carX - carW * 0.45f, carY - carH * 0.8f, carX - carW * 0.35f, carY + carH * 0.8f, paint)
            canvas.drawLine(carX + carW * 0.45f, carY - carH * 0.8f, carX + carW * 0.35f, carY + carH * 0.8f, paint)
            paint.style = Paint.Style.FILL
        }

        val specColor = cachedVehicleColors[selectedCarIndex.coerceIn(0, cachedVehicleColors.lastIndex)]
        canvas.withSave {
            // Roll / Lean tilt
            canvas.rotate(steerAngle, carX, carY + carH / 2)

            // Rear Tire with spinning rim spokes
            paint.color = colorDarkTire
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            drawRectF.set(carX - carW * 0.12f, carY + carH * 0.35f, carX + carW * 0.12f, carY + carH * 0.98f)
            canvas.drawRoundRect(drawRectF, 8f, 8f, paint)

            // Neon Wheel Rim Glow
            paint.color = specColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            canvas.drawRoundRect(drawRectF, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            // License Tag
            paint.color = Color.YELLOW
            canvas.drawRect(carX - 18f, carY + carH * 0.32f, carX + 18f, carY + carH * 0.44f, paint)
            textPaint.color = Color.BLACK
            textPaint.textSize = 9f
            textPaint.textAlign = Paint.Align.CENTER
            safeDrawText(canvas, "MOTO", carX, carY + carH * 0.41f, textPaint)

            // Fairing Chassis Body
            paint.color = specColor
            drawRectF.set(carX - carW * 0.28f, carY + carH * 0.15f, carX + carW * 0.28f, carY + carH * 0.65f)
            canvas.drawRoundRect(drawRectF, 12f, 12f, paint)

            // Exhaust Tubes
            paint.color = colorPost
            canvas.drawCircle(carX - carW * 0.22f, carY + carH * 0.72f, 7f, paint)
            canvas.drawCircle(carX + carW * 0.22f, carY + carH * 0.72f, 7f, paint)

            // Exhaust Flames / Nitro Plasma
            if (!gamePaused && !gameOver && speed > 20f) {
                paint.color = if (inTurbo) colorPlasmaOuter else colorFlameOuter
                val flameSize = (Random.nextFloat() * 14f + 5f) * (speed / baseMaxSpeed) * (if (inTurbo) 1.6f else 1.0f)
                canvas.drawCircle(carX - carW * 0.22f, carY + carH * 0.72f + 4f + flameSize / 2, flameSize / 2, paint)
                canvas.drawCircle(carX + carW * 0.22f, carY + carH * 0.72f + 4f + flameSize / 2, flameSize / 2, paint)

                paint.color = if (inTurbo) colorPlasmaCore else colorFlameCore
                canvas.drawCircle(carX - carW * 0.22f, carY + carH * 0.72f + 3f + flameSize / 4, flameSize / 4, paint)
                canvas.drawCircle(carX + carW * 0.22f, carY + carH * 0.72f + 3f + flameSize / 4, flameSize / 4, paint)
            }

            // Rider Torso (Black Leather Jacket)
            paint.color = colorDarkJacket
            drawRectF.set(carX - carW * 0.24f, carY - carH * 0.15f, carX + carW * 0.24f, carY + carH * 0.3f)
            canvas.drawRoundRect(drawRectF, 14f, 14f, paint)

            // Neon Racing Stripes on Jacket
            paint.color = specColor
            canvas.drawRect(carX - carW * 0.20f, carY + carH * 0.05f, carX - carW * 0.14f, carY + carH * 0.25f, paint)
            canvas.drawRect(carX + carW * 0.14f, carY + carH * 0.05f, carX + carW * 0.20f, carY + carH * 0.25f, paint)

            // Helmet
            paint.color = colorDarkHelmet
            canvas.drawCircle(carX, carY - carH * 0.38f, carW * 0.18f, paint)

            // Glowing Neon Visor (soft halo without Skia blur)
            paint.color = colorNeonCyan
            paint.alpha = 80
            drawRectF.set(carX - carW * 0.15f, carY - carH * 0.47f, carX + carW * 0.15f, carY - carH * 0.31f)
            canvas.drawRoundRect(drawRectF, 6f, 6f, paint)
            paint.alpha = 255
            drawRectF.set(carX - carW * 0.12f, carY - carH * 0.44f, carX + carW * 0.12f, carY - carH * 0.34f)
            canvas.drawRoundRect(drawRectF, 4f, 4f, paint)
        }
    }

    private fun drawSpeedLines(canvas: Canvas, width: Int, height: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.WHITE
        paint.alpha = 110
        val cx = width / 2f
        val cy = height * 0.5f
        repeat(12) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val distInner = Random.nextFloat() * width * 0.25f + width * 0.15f
            val distOuter = distInner + Random.nextFloat() * width * 0.25f
            canvas.drawLine(
                cx + (cos(angle) * distInner).toFloat(),
                cy + (sin(angle) * distInner).toFloat(),
                cx + (cos(angle) * distOuter).toFloat(),
                cy + (sin(angle) * distOuter).toFloat(),
                paint
            )
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun appendLapTime(sb: StringBuilder, ms: Long) {
        val totalCs = (ms / 10L).coerceAtLeast(0L)
        val minutes = (totalCs / 6000L).toInt()
        val seconds = ((totalCs / 100L) % 60L).toInt()
        val cs = (totalCs % 100L).toInt()
        if (minutes < 10) sb.append('0')
        sb.append(minutes).append(':')
        if (seconds < 10) sb.append('0')
        sb.append(seconds).append('.')
        if (cs < 10) sb.append('0')
        sb.append(cs)
    }

    private fun isCanvasRenderable(canvas: Canvas?, viewWidth: Int, viewHeight: Int): Boolean {
        if (canvas == null) return false
        if (!isAttachedToWindow || windowVisibility != View.VISIBLE || visibility != View.VISIBLE) return false
        if (viewWidth <= 0 || viewHeight <= 0) return false
        if (canvas.width <= 0 || canvas.height <= 0) return false
        if (!canvas.getClipBounds(clipBoundsRect) || clipBoundsRect.isEmpty) return false
        return true
    }

    private fun safeDrawText(
        canvas: Canvas?,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        y: Float,
        textDrawPaint: Paint?
    ) {
        if (canvas == null || text == null || textDrawPaint == null) return
        val safeStart = start.coerceAtLeast(0)
        val safeEnd = end.coerceAtMost(text.length)
        if (safeStart >= safeEnd) return
        if (!x.isFinite() || !y.isFinite()) return
        if (!textDrawPaint.textSize.isFinite() || textDrawPaint.textSize <= 0f) return
        if (canvas.width <= 0 || canvas.height <= 0) return
        try {
            canvas.drawText(text, safeStart, safeEnd, x, y, textDrawPaint)
        } catch (_: IllegalStateException) {
            // Ignore transient recording canvas invalidation during rapid background transitions
        }
    }

    private fun safeDrawText(
        canvas: Canvas?,
        text: String?,
        x: Float,
        y: Float,
        textDrawPaint: Paint?
    ) {
        if (canvas == null || text.isNullOrBlank() || textDrawPaint == null) return
        if (!x.isFinite() || !y.isFinite()) return
        if (!textDrawPaint.textSize.isFinite() || textDrawPaint.textSize <= 0f) return
        if (canvas.width <= 0 || canvas.height <= 0) return
        try {
            canvas.drawText(text, x, y, textDrawPaint)
        } catch (_: IllegalStateException) {
            // Ignore transient recording canvas invalidation during rapid background transitions
        }
    }

    private fun drawShadowedText(canvas: Canvas?, text: String?, x: Float, y: Float, color: Int) {
        if (canvas == null || text.isNullOrBlank() || !x.isFinite() || !y.isFinite()) return
        textPaint.color = colorShadowDark
        safeDrawText(canvas, text, x + 2f, y + 2f, textPaint)
        textPaint.color = color
        safeDrawText(canvas, text, x, y, textPaint)
    }

    // --- SYNTHWAVE HUD & DASHBOARD (Modular, Cached Strings, Strict Null-Safety & Canvas Validation) ---
    private fun drawSynthwaveHUD(canvas: Canvas?, width: Int, height: Int) {
        if (!isCanvasRenderable(canvas, width, height)) return
        val safeCanvas = canvas ?: return

        val safePosition = if (position.isFinite()) position else 0f
        var passedCount = 0
        for (i in 0 until traffic.size) {
            val carZ = traffic[i].z
            if (carZ.isFinite() && safePosition > carZ) {
                passedCount++
            }
        }
        val currentRank = (21 - passedCount).coerceIn(1, 20)

        val hudY = max(55f, height * 0.065f)
        val leftX = max(45f, width * 0.04f)
        val rightX = min(width - 45f, width * 0.96f)

        drawHudTopBar(safeCanvas, width, height, leftX, rightX, hudY)
        drawHudRadar(safeCanvas, width, height, leftX, safePosition)
        drawHudSpeedAndNitro(safeCanvas, width, height)
        drawHudPositionAndDrafting(safeCanvas, width, height, currentRank)
    }

    private fun drawHudTopBar(canvas: Canvas, width: Int, height: Int, leftX: Float, rightX: Float, hudY: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = colorHudBg
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.11f, paint)

        textPaint.alpha = 255
        textPaint.color = Color.WHITE
        textPaint.textSize = 26f
        textPaint.textAlign = Paint.Align.LEFT

        val safeTotalLaps = totalLaps.coerceAtLeast(1)
        val safeCurrentLap = currentLap.coerceIn(1, safeTotalLaps)
        val safeLapTimeMs = currentLapTimeMs.coerceAtLeast(0L)
        // Throttle header stopwatch rebuild to 10Hz to minimize nDrawText shaping churn on Android TV
        val deciseconds = safeLapTimeMs / 100L
        val leftKey = (selectedMapIndex.toLong() shl 40) xor (safeCurrentLap.toLong() shl 32) xor deciseconds
        if (leftKey != cachedLeftHeaderKey || cachedLeftHeaderText.isEmpty()) {
            val currentMap = if (RetroDriverMapCatalog.maps.isNotEmpty()) {
                RetroDriverMapCatalog.getMap(selectedMapIndex)
            } else {
                null
            }
            val safeMapName = currentMap?.name?.takeIf { it.isNotBlank() } ?: labelDefaultCircuit
            hudStringBuilder.setLength(0)
            hudStringBuilder.append(safeMapName)
                .append("  |  ").append(labelLap).append(' ').append(safeCurrentLap).append('/').append(safeTotalLaps)
                .append("  |  ").append(labelTime).append(": ")
            appendLapTime(hudStringBuilder, deciseconds * 100L)
            cachedLeftHeaderText = hudStringBuilder.toString()
            cachedLeftHeaderKey = leftKey
        }
        safeDrawText(canvas, cachedLeftHeaderText, leftX, hudY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        val safeBestMs = bestLapTimeMs.coerceAtLeast(0L)
        val safeCoins = coinsCollectedSession.coerceAtLeast(0)
        if (safeBestMs != cachedRightHeaderBestMs || safeCoins != cachedRightHeaderCoins || cachedRightHeaderText.isEmpty()) {
            hudStringBuilder.setLength(0)
            hudStringBuilder.append(labelBest).append(": ")
            if (safeBestMs > 0L) {
                appendLapTime(hudStringBuilder, safeBestMs)
            } else {
                hudStringBuilder.append("--:--")
            }
            hudStringBuilder.append("   ").append(labelCoins).append(": ").append(safeCoins)
            cachedRightHeaderText = hudStringBuilder.toString()
            cachedRightHeaderBestMs = safeBestMs
            cachedRightHeaderCoins = safeCoins
        }
        safeDrawText(canvas, cachedRightHeaderText, rightX, hudY, textPaint)
    }

    private fun drawHudRadar(canvas: Canvas, width: Int, height: Int, leftX: Float, safePosition: Float) {
        val radarW = width * 0.28f
        val radarH = 14f
        val radarX = leftX
        val radarY = min(height - 50f, height * 0.92f)
        paint.color = colorHudBarBg
        drawRectF.set(radarX, radarY, radarX + radarW, radarY + radarH)
        canvas.drawRoundRect(drawRectF, 6f, 6f, paint)

        val trackTotalDistance = (trackLengthSegments * segmentLength).coerceAtLeast(1f)
        val playerProg = ((safePosition % trackTotalDistance) / trackTotalDistance).coerceIn(0f, 1f)
        paint.color = colorCoinGold
        canvas.drawCircle(radarX + radarW * playerProg, radarY + radarH / 2, 7f, paint)

        paint.color = colorTaillight
        for (i in 0 until traffic.size) {
            val carZ = traffic[i].z
            if (carZ.isFinite()) {
                val carProg = ((carZ % trackTotalDistance) / trackTotalDistance).coerceIn(0f, 1f)
                canvas.drawCircle(radarX + radarW * carProg, radarY + radarH / 2, 4f, paint)
            }
        }
    }

    private fun drawHudSpeedAndNitro(canvas: Canvas, width: Int, height: Int) {
        val nitroBarW = width * 0.22f
        val nitroBarH = 12f
        val speedX = min(width - nitroBarW - 45f, width * 0.74f)
        val speedY = min(height - 60f, height * 0.88f)
        val safeNitro = if (nitroCharge.isFinite()) nitroCharge.coerceIn(0f, 1f) else 0f

        paint.color = colorNitroBarBg
        drawRectF.set(speedX, speedY + 12f, speedX + nitroBarW, speedY + 12f + nitroBarH)
        canvas.drawRoundRect(drawRectF, 6f, 6f, paint)

        paint.color = if (safeNitro >= 0.99f) colorNeonCyan else colorNeonMagenta
        drawRectF.set(speedX, speedY + 12f, speedX + (nitroBarW * safeNitro), speedY + 12f + nitroBarH)
        canvas.drawRoundRect(drawRectF, 6f, 6f, paint)

        val safeSpeedMph = if (speed.isFinite()) speed.coerceAtLeast(0f).toInt() else 0
        if (safeSpeedMph != cachedSpeedMph || cachedSpeedText.isEmpty()) {
            hudStringBuilder.setLength(0)
            hudStringBuilder.append(safeSpeedMph).append(' ').append(labelMph)
            cachedSpeedText = hudStringBuilder.toString()
            cachedSpeedMph = safeSpeedMph
        }
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 38f
        drawShadowedText(canvas, cachedSpeedText, speedX, speedY, colorNeonCyan)
    }

    private fun drawHudPositionAndDrafting(canvas: Canvas, width: Int, height: Int, currentRank: Int) {
        val rankX = width / 2f
        val rankY = min(height - 45f, height * 0.93f)
        val rankBadgeColor = when (currentRank) {
            1 -> colorCoinGold
            2 -> colorSilver
            3 -> colorBronze
            else -> colorNeonCyan
        }
        if (currentRank != cachedRankValue || cachedRankText.isEmpty()) {
            hudStringBuilder.setLength(0)
            hudStringBuilder.append(labelPos).append(": ").append(currentRank).append(" / 20")
            cachedRankText = hudStringBuilder.toString()
            cachedRankValue = currentRank
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 32f
        drawShadowedText(canvas, cachedRankText, rankX, rankY, rankBadgeColor)

        if (isDrafting) {
            textPaint.textSize = 28f
            drawShadowedText(canvas, labelSlipstream, width / 2f, height * 0.72f, colorNeonGreen)
        }
    }

    // --- STATE OVERLAYS ---
    private fun drawStateOverlays(canvas: Canvas?, width: Int, height: Int) {
        if (!isCanvasRenderable(canvas, width, height)) return
        val safeCanvas = canvas ?: return
        val nowTime = System.currentTimeMillis()

        // Starting Countdown Display
        if (countdown >= 0) {
            textPaint.textAlign = Paint.Align.CENTER
            val elapsed = nowTime - countdownStartTime
            val progress = (elapsed.toFloat() / 650f).coerceIn(0f, 1f)
            val scale = 1.35f - 0.35f * progress
            textPaint.textSize = 120f * scale
            val glowColor = if (countdown == 0) colorNeonGreen else colorNeonCyan
            val countStr = if (countdown == 0) context.getString(R.string.retro_driver_go) else countdown.toString()
            drawShadowedText(safeCanvas, countStr, width / 2f, height / 2f - 30f, glowColor)
        }

        // Lap Banner
        if (nowTime < lapMessageUntil && lapMessage.isNotBlank()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 72f
            drawShadowedText(safeCanvas, lapMessage, width / 2f, height / 2f - 40f, colorNeonGold)
        }

        // Finished Victory Banner
        if (gameWon) {
            celebrationManager.draw(safeCanvas)
            var passedCount = 0
            for (car in traffic) {
                if (position > car.z) passedCount++
            }
            val currentRank = (21 - passedCount).coerceIn(1, 20)
            val currentMap = RetroDriverMapCatalog.getMap(selectedMapIndex)
            val suffix = when (currentRank) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
            val title = context.getString(R.string.retro_driver_finish)
            val subtitle = "You completed ${currentMap.name} in ${currentRank}$suffix place!\nFinal Score: $score  |  Best Lap: ${String.format(Locale.US, "%.2fs", bestLapTimeMs / 1000f)}\n\nPress [ENTER] to Race Again"
            drawOverlay(safeCanvas, title, subtitle)
        } else if (gameOver) {
            celebrationManager.draw(safeCanvas)
            drawOverlay(safeCanvas, context.getString(R.string.game_over), "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (gamePaused && countdown < 0) {
            val currentMap = RetroDriverMapCatalog.getMap(selectedMapIndex)
            val spec = vehicleSpecs[selectedCarIndex.coerceIn(0, vehicleSpecs.lastIndex)]
            drawOverlay(
                safeCanvas,
                context.getString(R.string.game_retrodriver),
                "${context.getString(R.string.resume_hint)}\n\nCircuit: < ${currentMap.name} >\n(${currentMap.description})\nVehicle: < ${spec.name} > (Top Speed: ${spec.topSpeed.toInt()} MPH)\nLaps: $totalLaps Laps\n\n[DPAD ↑ / ↓] Change Map   [DPAD ← / →] Change Vehicle\n[SPACE / ENTER] Start Race / Nitro"
            )
        }
    }

    private fun drawOverlay(canvas: Canvas?, title: String?, subtitle: String?) {
        if (!isCanvasRenderable(canvas, width, height)) return
        val safeCanvas = canvas ?: return

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = colorOverlayBg
        safeCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        textPaint.textSize = 68f
        textPaint.textAlign = Paint.Align.CENTER
        drawShadowedText(safeCanvas, title, width / 2f, height / 2f - 70f, Color.WHITE)

        textPaint.textSize = 28f
        textPaint.color = Color.WHITE
        val lines = subtitle.orEmpty().split("\n")
        var yOffset = height / 2f + 10f
        for (line in lines) {
            if (line.isNotEmpty()) {
                safeDrawText(safeCanvas, line, width / 2f, yOffset, textPaint)
            }
            yOffset += 38f
        }
    }

    // --- CONTROLS: D-PAD, KEYBOARD & GAMEPAD ANALOG INPUT ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gamePaused) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                resume()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                selectedCarIndex = (selectedCarIndex - 1 + vehicleSpecs.size) % vehicleSpecs.size
                baseMaxSpeed = vehicleSpecs[selectedCarIndex].topSpeed
                context.getSharedPreferences(RetroDriverOptionsDialog.PREFS_NAME, Context.MODE_PRIVATE).edit {
                    putInt(RetroDriverOptionsDialog.KEY_CAR_INDEX, selectedCarIndex)
                }
                SoundManager.playClick()
                HapticManager.vibrateClick(context)
                invalidate()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                selectedCarIndex = (selectedCarIndex + 1) % vehicleSpecs.size
                baseMaxSpeed = vehicleSpecs[selectedCarIndex].topSpeed
                context.getSharedPreferences(RetroDriverOptionsDialog.PREFS_NAME, Context.MODE_PRIVATE).edit {
                    putInt(RetroDriverOptionsDialog.KEY_CAR_INDEX, selectedCarIndex)
                }
                SoundManager.playClick()
                HapticManager.vibrateClick(context)
                invalidate()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                selectedMapIndex = (selectedMapIndex - 1 + RetroDriverMapCatalog.maps.size) % RetroDriverMapCatalog.maps.size
                context.getSharedPreferences(RetroDriverOptionsDialog.PREFS_NAME, Context.MODE_PRIVATE).edit {
                    putInt(RetroDriverOptionsDialog.KEY_MAP_INDEX, selectedMapIndex)
                }
                buildRoad()
                SoundManager.playClick()
                HapticManager.vibrateClick(context)
                invalidate()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                selectedMapIndex = (selectedMapIndex + 1) % RetroDriverMapCatalog.maps.size
                context.getSharedPreferences(RetroDriverOptionsDialog.PREFS_NAME, Context.MODE_PRIVATE).edit {
                    putInt(RetroDriverOptionsDialog.KEY_MAP_INDEX, selectedMapIndex)
                }
                buildRoad()
                SoundManager.playClick()
                HapticManager.vibrateClick(context)
                invalidate()
                return true
            }
            return false
        }

        if (countdown >= 0) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D -> true
                else -> super.onKeyDown(keyCode, event)
            }
        }

        if (gameWon || gameOver) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                resetGame()
                startGame()
                return true
            }
            return false
        }

        // Action Trigger for Nitro Boost (Center, Space, Button A/X)
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_X || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (nitroCharge >= 0.25f && now() >= turboUntil) {
                turboUntil = now() + (2500L * nitroCharge).toLong()
                nitroCharge = 0f
                screenShake.trigger(14, 18f)
                SoundManager.playPowerUp()
                HapticManager.vibrateSuccess(context)
                floatingPopups.add(FloatingPopup(labelNitroBoost, width / 2f, height * 0.45f, Color.parseColor("#00E5FF")))
                return true
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_R1 -> {
                keyUpPressed = true
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_L1 -> {
                keyDownPressed = true
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
                keyLeftPressed = true
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                keyRightPressed = true
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_R1 -> {
                keyUpPressed = false
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_L1 -> {
                keyDownPressed = false
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
                keyLeftPressed = false
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                keyRightPressed = false
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override val supportsRevive: Boolean get() = true
    override val canRevive: Boolean get() = gameOver && !gameWon

    override fun reviveGame(): Boolean {
        if (!gameOver || gameWon) return false
        gameOver = false
        gameWon = false
        gamePaused = false
        invincibleUntil = System.currentTimeMillis() + 3500L
        speed = 80f
        celebrationManager.clear()
        SoundManager.playSuccess()
        startFrameLoop()
        invalidate()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {

            val rawX = event.getAxisValue(MotionEvent.AXIS_X).let { if (it != 0f) it else event.getAxisValue(MotionEvent.AXIS_HAT_X) }
            val rawY = event.getAxisValue(MotionEvent.AXIS_Y).let { if (it != 0f) it else event.getAxisValue(MotionEvent.AXIS_HAT_Y) }

            val (filteredX, filteredY) = SettingsManager.applyDeadzone(context, rawX, rawY)
            analogStickX = filteredX
            analogStickY = filteredY

            // Trigger Gas / Brake analog axes (with clean release)
            val gas = event.getAxisValue(MotionEvent.AXIS_GAS).let { if (it != 0f) it else event.getAxisValue(MotionEvent.AXIS_RTRIGGER) }
            val brake = event.getAxisValue(MotionEvent.AXIS_BRAKE).let { if (it != 0f) it else event.getAxisValue(MotionEvent.AXIS_LTRIGGER) }
            if (gas > 0.2f) {
                analogGasActive = true
                keyUpPressed = true
            } else if (analogGasActive) {
                analogGasActive = false
                keyUpPressed = false
            }
            if (brake > 0.2f) {
                analogBrakeActive = true
                keyDownPressed = true
            } else if (analogBrakeActive) {
                analogBrakeActive = false
                keyDownPressed = false
            }

            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (gamePaused) {
                    resume()
                    return true
                }
                if (gameWon || gameOver) {
                    resetGame()
                    startGame()
                    return true
                }

                // Steer relative to screen center
                val normalizedX = (event.x / width) * 2.4f - 1.2f
                playerX = normalizedX.coerceIn(-1.8f, 1.8f)
                keyUpPressed = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                keyUpPressed = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

