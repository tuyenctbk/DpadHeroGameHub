package com.tdpham.games.common

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * High-performance abstract SurfaceView game engine providing:
 * - Deterministic fixed-timestep physics updates (e.g. 60Hz)
 * - Smooth sub-frame render interpolation (alpha 0.0 .. 1.0)
 * - Multi-threaded rendering with thread lifecycle safety
 * - Standardized D-Pad, Gamepad, Keyboard, and Analog Stick input with Deadzone filtering
 * - Global GameUncaughtExceptionHandler reporting game state, controller input, and device metadata to Crashlytics
 */
abstract class BaseGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback, Runnable, GameView {

    enum class DpadDirection {
        NONE, UP, DOWN, LEFT, RIGHT
    }

    override var gameKey: String = "arcade"
    override var onGameOver: ((Int) -> Unit)? = null

    @Volatile
    protected var isRunning = false

    @Volatile
    protected var isPaused = false

    private var gameThread: Thread? = null
    private val surfaceHolder: SurfaceHolder = holder

    // Physics & Frame Timing Constants
    protected var targetUps: Int = 60 // Fixed updates per second
    private var fixedDeltaTimeNs: Long = 1_000_000_000L / 60
    private val maxFrameSkips = 5

    // FPS / Diagnostic metrics
    var currentFps: Float = 60f
        private set
    private var frameCount = 0
    private var lastFpsTimestamp = 0L
    private var gameStartTime = System.currentTimeMillis()

    // Surface bounds
    protected var viewWidth: Int = 0
    protected var viewHeight: Int = 0

    // Analog stick and controller input tracking for telemetry and crash reports
    private var lastAnalogDirection = DpadDirection.NONE
    var lastKeyCodeName: String = "NONE"
        private set
    var lastDpadDirectionName: String = "NONE"
        private set
    var lastAnalogX: Float = 0f
        private set
    var lastAnalogY: Float = 0f
        private set
    var lastInputDeviceName: String = "None"
        private set
    var lastInputDeviceId: Int = -1
        private set
    var lastInputTimestamp: Long = 0L
        private set

    private var originalThreadHandler: Thread.UncaughtExceptionHandler? = null
    private val gameExceptionHandler by lazy { GameUncaughtExceptionHandler(this) }

    init {
        surfaceHolder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        originalThreadHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(GameUncaughtExceptionHandler(this, originalThreadHandler))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (originalThreadHandler != null) {
            Thread.setDefaultUncaughtExceptionHandler(originalThreadHandler)
        }
    }

    /**
     * Subclasses can override to provide real-time domain specific game metrics (e.g. score, level, lives, combo).
     */
    open fun getGameStateMetadata(): Map<String, String> = emptyMap()

    /**
     * Global Uncaught Exception Handler that captures:
     * 1. Device hardware and memory diagnostics
     * 2. Live game loop metrics and thread state
     * 3. Controller / D-Pad / Analog stick input history
     * 4. Custom game-specific state
     * and exports all telemetry to Firebase Crashlytics.
     */
    class GameUncaughtExceptionHandler(
        private val gameView: BaseGameView,
        private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
    ) : Thread.UncaughtExceptionHandler {

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                val crashlytics = FirebaseCrashlytics.getInstance()
                val context = gameView.context

                // 1. Device Hardware & RAM Metadata
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager?.getMemoryInfo(memoryInfo)
                val displayMetrics = context.resources.displayMetrics

                crashlytics.setCustomKey("device_manufacturer", Build.MANUFACTURER)
                crashlytics.setCustomKey("device_model", Build.MODEL)
                crashlytics.setCustomKey("device_brand", Build.BRAND)
                crashlytics.setCustomKey("device_sdk", Build.VERSION.SDK_INT)
                crashlytics.setCustomKey("device_low_ram", memoryInfo.lowMemory)
                crashlytics.setCustomKey("device_avail_mem_mb", memoryInfo.availMem / (1024 * 1024))
                crashlytics.setCustomKey("device_total_mem_mb", memoryInfo.totalMem / (1024 * 1024))
                crashlytics.setCustomKey("device_screen_res", "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
                crashlytics.setCustomKey("device_density_dpi", displayMetrics.densityDpi)

                // 2. Controller & Input State
                crashlytics.setCustomKey("input_last_key_code", gameView.lastKeyCodeName)
                crashlytics.setCustomKey("input_last_direction", gameView.lastDpadDirectionName)
                crashlytics.setCustomKey("input_last_analog", "X=${gameView.lastAnalogX}, Y=${gameView.lastAnalogY}")
                crashlytics.setCustomKey("input_device_name", gameView.lastInputDeviceName)
                crashlytics.setCustomKey("input_device_id", gameView.lastInputDeviceId)
                crashlytics.setCustomKey("input_last_action_timestamp", gameView.lastInputTimestamp)

                // 3. Game Engine State
                crashlytics.setCustomKey("game_active_key", gameView.gameKey)
                crashlytics.setCustomKey("game_is_running", gameView.isRunning)
                crashlytics.setCustomKey("game_is_paused", gameView.isPaused)
                crashlytics.setCustomKey("game_fps", gameView.currentFps)
                crashlytics.setCustomKey("game_target_ups", gameView.targetUps)
                crashlytics.setCustomKey("game_view_width", gameView.viewWidth)
                crashlytics.setCustomKey("game_view_height", gameView.viewHeight)
                crashlytics.setCustomKey("game_thread_name", thread.name)
                crashlytics.setCustomKey("game_thread_state", thread.state.name)

                // 4. Custom Game-Specific State
                val customMeta = gameView.getGameStateMetadata()
                for ((key, value) in customMeta) {
                    crashlytics.setCustomKey("game_custom_$key", value)
                }

                val logHeader = buildString {
                    appendLine("================ GAME CRASH REPORT ================")
                    appendLine("Game: ${gameView.gameKey.uppercase()} | Thread: ${thread.name} (state=${thread.state})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Memory: Avail=${memoryInfo.availMem / (1024 * 1024)}MB / Total=${memoryInfo.totalMem / (1024 * 1024)}MB | LowRAM=${memoryInfo.lowMemory}")
                    appendLine("Display: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels} @ ${displayMetrics.densityDpi}dpi")
                    appendLine("Engine: running=${gameView.isRunning}, paused=${gameView.isPaused}, fps=${gameView.currentFps}, targetUps=${gameView.targetUps}, bounds=${gameView.viewWidth}x${gameView.viewHeight}")
                    appendLine("Controller Input: device='${gameView.lastInputDeviceName}' (id=${gameView.lastInputDeviceId}), lastKey=${gameView.lastKeyCodeName}, lastDir=${gameView.lastDpadDirectionName}, analog=(X:${gameView.lastAnalogX}, Y:${gameView.lastAnalogY})")
                    if (customMeta.isNotEmpty()) {
                        appendLine("Custom Game Data: $customMeta")
                    }
                    appendLine("==================================================")
                }

                crashlytics.log(logHeader)
                android.util.Log.e("GameCrashHandler", logHeader, throwable)
                crashlytics.recordException(throwable)
            } catch (e: Throwable) {
                android.util.Log.e("GameCrashHandler", "Error capturing crash telemetry: ${e.message}", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Fixed-timestep physics and logic simulation callback.
     * Guaranteed to step at fixed delta intervals (e.g., 1/60s).
     */
    abstract fun onGameUpdate(deltaSec: Float)

    /**
     * Visual rendering pass.
     * @param canvas Surface canvas to draw onto.
     * @param interpolation Frame blend factor (0.0f..1.0f) between previous and current physics ticks.
     */
    abstract fun onRender(canvas: Canvas, interpolation: Float)

    /**
     * Called whenever Surface dimensions change or are initialized.
     */
    open fun onSurfaceDimensionsChanged(width: Int, height: Int) {}

    /**
     * High-level D-Pad and directional navigation handler.
     * Dispatched from Gamepad D-pad, Keyboard WASD/Arrows, and Analog stick.
     */
    open fun onDpadInput(direction: DpadDirection, isPressed: Boolean): Boolean = false

    /**
     * High-level Action button handler (A/B/X/Y, Space, Enter).
     */
    open fun onActionButton(keyCode: Int, isPressed: Boolean): Boolean = false

    /**
     * Continuous analog stick motion after deadzone filtering.
     */
    open fun onAnalogStickMove(axisX: Float, axisY: Float) {}

    // --- GAME LIFECYCLE ---
    override fun startGame() {
        isPaused = false
        resume()
        requestFocus()
    }

    override fun pause() {
        isPaused = true
        isRunning = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join(500)
                retry = false
            } catch (e: InterruptedException) {
                // Keep trying until thread cleanly shuts down
            }
        }
        gameThread = null
    }

    private fun logCrashToFirebase(throwable: Throwable, contextTag: String) {
        try {
            val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("active_game", gameKey)
            crashlytics.setCustomKey("fps", currentFps)
            crashlytics.setCustomKey("view_width", viewWidth)
            crashlytics.setCustomKey("view_height", viewHeight)
            crashlytics.setCustomKey("is_paused", isPaused)
            crashlytics.setCustomKey("target_ups", targetUps)
            crashlytics.log("BaseGameView exception in [$contextTag] for active game: $gameKey")
            crashlytics.recordException(throwable)
        } catch (e: Exception) {
            android.util.Log.e("BaseGameView", "Failed to log exception to Crashlytics: ${e.message}", e)
        }
    }

    override fun resume() {
        if (gameThread == null || !gameThread!!.isAlive) {
            isRunning = true
            isPaused = false
            gameThread = Thread(this, "BaseGameEngineThread_${gameKey}").apply {
                priority = Thread.MAX_PRIORITY
                uncaughtExceptionHandler = gameExceptionHandler
                start()
            }
        }
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    // --- SURFACE CALLBACKS ---
    override fun surfaceCreated(holder: SurfaceHolder) {
        viewWidth = width
        viewHeight = height
        onSurfaceDimensionsChanged(width, height)
        resume()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        onSurfaceDimensionsChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    // --- HIGH-PRECISION FIXED TIMESTEP GAME LOOP ---
    override fun run() {
        var lastTime = System.nanoTime()
        var accumulator = 0L
        lastFpsTimestamp = System.currentTimeMillis()
        frameCount = 0

        fixedDeltaTimeNs = 1_000_000_000L / targetUps.coerceIn(15, 120)
        val fixedDeltaSec = fixedDeltaTimeNs / 1_000_000_000f

        while (isRunning) {
            val now = System.nanoTime()
            var elapsed = now - lastTime
            lastTime = now

            // Cap elapsed time to avoid death-spiral hitching on lag spikes
            if (elapsed > 250_000_000L) {
                elapsed = 250_000_000L
            }

            if (!isPaused) {
                accumulator += elapsed

                var updateCount = 0
                while (accumulator >= fixedDeltaTimeNs && updateCount < maxFrameSkips) {
                    try {
                        onGameUpdate(fixedDeltaSec)
                    } catch (e: Throwable) {
                        gameExceptionHandler.uncaughtException(Thread.currentThread(), e)
                    }
                    accumulator -= fixedDeltaTimeNs
                    updateCount++
                }

                // If heavily lagging, discard remaining accumulator
                if (updateCount >= maxFrameSkips) {
                    accumulator = 0L
                }

                // Sub-frame interpolation factor [0.0 .. 1.0]
                val interpolation = (accumulator.toFloat() / fixedDeltaTimeNs.toFloat()).coerceIn(0f, 1f)

                // Render frame
                var canvas: Canvas? = null
                try {
                    canvas = surfaceHolder.lockCanvas()
                    if (canvas != null) {
                        synchronized(surfaceHolder) {
                            try {
                                onRender(canvas, interpolation)
                            } catch (e: Throwable) {
                                gameExceptionHandler.uncaughtException(Thread.currentThread(), e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    gameExceptionHandler.uncaughtException(Thread.currentThread(), e)
                    e.printStackTrace()
                } finally {
                    if (canvas != null) {
                        try {
                            surfaceHolder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Update FPS calculation every second
                frameCount++
                val currentMillis = System.currentTimeMillis()
                if (currentMillis - lastFpsTimestamp >= 1000) {
                    currentFps = (frameCount * 1000f) / (currentMillis - lastFpsTimestamp)
                    frameCount = 0
                    lastFpsTimestamp = currentMillis
                }
            } else {
                try {
                    Thread.sleep(16)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
        }
    }

    // --- UNIVERSAL INPUT HANDLING (D-PAD / GAMEPAD / KEYBOARD / JOYSTICK) ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        lastKeyCodeName = KeyEvent.keyCodeToString(keyCode)
        lastInputTimestamp = System.currentTimeMillis()
        if (event != null) {
            lastInputDeviceName = event.device?.name ?: "Keyboard/Gamepad"
            lastInputDeviceId = event.deviceId
        }

        val dir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> DpadDirection.UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> DpadDirection.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> DpadDirection.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> DpadDirection.RIGHT
            else -> DpadDirection.NONE
        }

        if (dir != DpadDirection.NONE) {
            lastDpadDirectionName = dir.name
            if (onDpadInput(dir, true)) return true
        }

        val isAction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y -> true
            else -> false
        }

        if (isAction) {
            if (onActionButton(keyCode, true)) return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        lastKeyCodeName = KeyEvent.keyCodeToString(keyCode)
        lastInputTimestamp = System.currentTimeMillis()
        if (event != null) {
            lastInputDeviceName = event.device?.name ?: "Keyboard/Gamepad"
            lastInputDeviceId = event.deviceId
        }

        val dir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> DpadDirection.UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> DpadDirection.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> DpadDirection.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> DpadDirection.RIGHT
            else -> DpadDirection.NONE
        }

        if (dir != DpadDirection.NONE) {
            lastDpadDirectionName = dir.name
            if (onDpadInput(dir, false)) return true
        }

        val isAction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y -> true
            else -> false
        }

        if (isAction) {
            if (onActionButton(keyCode, false)) return true
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {

            lastInputDeviceName = event.device?.name ?: "Joystick"
            lastInputDeviceId = event.deviceId
            lastInputTimestamp = System.currentTimeMillis()

            // Check primary analog stick axes with deadzone
            val rawX = event.getAxisValue(MotionEvent.AXIS_X).let { if (it != 0f) it else event.getAxisValue(MotionEvent.AXIS_HAT_X) }
            val rawY = event.getAxisValue(MotionEvent.AXIS_Y).let { if (it != 0f) it else event.getAxisValue(MotionEvent.AXIS_HAT_Y) }

            val (filteredX, filteredY) = SettingsManager.applyDeadzone(context, rawX, rawY)
            lastAnalogX = filteredX
            lastAnalogY = filteredY
            onAnalogStickMove(filteredX, filteredY)

            // Convert analog deflection to discrete DpadDirection pulses
            val currentDir = when {
                filteredY < -0.4f -> DpadDirection.UP
                filteredY > 0.4f -> DpadDirection.DOWN
                filteredX < -0.4f -> DpadDirection.LEFT
                filteredX > 0.4f -> DpadDirection.RIGHT
                else -> DpadDirection.NONE
            }

            if (currentDir != lastAnalogDirection) {
                lastDpadDirectionName = currentDir.name
                if (lastAnalogDirection != DpadDirection.NONE) {
                    onDpadInput(lastAnalogDirection, false)
                }
                if (currentDir != DpadDirection.NONE) {
                    onDpadInput(currentDir, true)
                }
                lastAnalogDirection = currentDir
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        lastInputDeviceName = "Touchscreen"
        lastInputDeviceId = event.deviceId
        lastInputTimestamp = System.currentTimeMillis()
        return super.onTouchEvent(event)
    }
}
