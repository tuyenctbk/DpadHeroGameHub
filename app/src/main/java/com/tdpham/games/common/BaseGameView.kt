package com.tdpham.games.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * High-performance abstract SurfaceView game engine providing:
 * - Deterministic fixed-timestep physics updates (e.g. 60Hz)
 * - Smooth sub-frame render interpolation (alpha 0.0 .. 1.0)
 * - Multi-threaded rendering with thread lifecycle safety
 * - Standardized D-Pad, Gamepad, Keyboard, and Analog Stick input with Deadzone filtering
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

    // Surface bounds
    protected var viewWidth: Int = 0
    protected var viewHeight: Int = 0

    // Analog stick last state tracking
    private var lastAnalogDirection = DpadDirection.NONE

    init {
        surfaceHolder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
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

    override fun resume() {
        if (gameThread == null || !gameThread!!.isAlive) {
            isRunning = true
            isPaused = false
            gameThread = Thread(this, "BaseGameEngineThread_${gameKey}").apply {
                priority = Thread.MAX_PRIORITY
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
                    onGameUpdate(fixedDeltaSec)
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
                            onRender(canvas, interpolation)
                        }
                    }
                } catch (e: Exception) {
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
        val dir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> DpadDirection.UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> DpadDirection.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> DpadDirection.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> DpadDirection.RIGHT
            else -> DpadDirection.NONE
        }

        if (dir != DpadDirection.NONE) {
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
        val dir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> DpadDirection.UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> DpadDirection.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> DpadDirection.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> DpadDirection.RIGHT
            else -> DpadDirection.NONE
        }

        if (dir != DpadDirection.NONE) {
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

            // Check primary analog stick axes with deadzone
            val rawX = event.getAxisValue(MotionEvent.AXIS_X).let { if (it != 0f) it else event.getAxisValue(MotionEvent.AXIS_HAT_X) }
            val rawY = event.getAxisValue(MotionEvent.AXIS_Y).let { if (it != 0f) it else event.getAxisValue(MotionEvent.AXIS_HAT_Y) }

            val (filteredX, filteredY) = SettingsManager.applyDeadzone(context, rawX, rawY)
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
}
