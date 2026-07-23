package com.tdpham.games.common

import android.os.Handler
import android.os.Looper
import android.util.Log

object IdleAdManager {
    private const val TAG = "IdleAdManager"
    
    enum class IdleState {
        ACTIVE,
        IDLE_CORNER,
        IDLE_PRE_FULL, // Warning state
        IDLE_FULL,
        IDLE_LOOP,
        IDLE_YIELD // Stop showing ads after long idle (20 mins)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentState = IdleState.ACTIVE
    
    var isGameMode = false
    var isWaitingMode = false
    
    private var lastInteractionTime = System.currentTimeMillis()
    private var adShownStartTime = 0L
    private var snoozeMultiplier = 1.0f
    
    private var onStateChangeListener: ((IdleState, Int) -> Unit)? = null

    private val idleCheckRunnable = object : Runnable {
        override fun run() {
            updateState()
            handler.postDelayed(this, 1000)
        }
    }

    fun init(listener: (IdleState, Int) -> Unit) {
        onStateChangeListener = listener
        startTracking()
    }

    fun startTracking() {
        handler.removeCallbacks(idleCheckRunnable)
        handler.post(idleCheckRunnable)
    }

    fun stopTracking() {
        handler.removeCallbacks(idleCheckRunnable)
    }

    fun notifyInteraction() {
        val now = System.currentTimeMillis()
        
        // Handle Smart Snooze logic if we were in FULL/LOOP state
        if (currentState == IdleState.IDLE_FULL || currentState == IdleState.IDLE_LOOP) {
            val adDurationSec = (now - adShownStartTime) / 1000
            val threshold = ConfigManager.getAdsSnoozeDismissThresholdSec()
            
            if (adDurationSec < threshold) {
                // User dismissed quickly -> Active Thinker -> Apply Snooze
                snoozeMultiplier = 2.0f
                Log.d(TAG, "Smart Snooze applied. Multiplier = 2x")
            } else {
                // User was likely away -> Reset to normal
                snoozeMultiplier = 1.0f
                Log.d(TAG, "User returned from away. Multiplier reset.")
            }
        }

        lastInteractionTime = now
        if (currentState != IdleState.ACTIVE) {
            currentState = IdleState.ACTIVE
            onStateChangeListener?.invoke(currentState, 0)
        }
    }

    private fun updateState() {
        val now = System.currentTimeMillis()
        val idleSec = ((now - lastInteractionTime) / 1000).toInt()
        
        val cornerThreshold = when {
            isWaitingMode -> ConfigManager.getAdsIdleWaitCornerSec()
            isGameMode -> ConfigManager.getAdsIdlePlayCornerSec()
            else -> ConfigManager.getAdsIdleMenuCornerSec()
        } * snoozeMultiplier

        val fullThreshold = when {
            isWaitingMode -> ConfigManager.getAdsIdleWaitFullSec()
            isGameMode -> ConfigManager.getAdsIdlePlayFullSec()
            else -> ConfigManager.getAdsIdleMenuFullSec()
        } * snoozeMultiplier
        
        val yieldThreshold = 20 * 60 // 20 minutes fixed yield
        
        val newState = when {
            idleSec >= yieldThreshold -> IdleState.IDLE_YIELD
            idleSec >= fullThreshold -> IdleState.IDLE_FULL
            idleSec >= (fullThreshold - 3) -> IdleState.IDLE_PRE_FULL // 3s warning
            idleSec >= cornerThreshold -> IdleState.IDLE_CORNER
            else -> IdleState.ACTIVE
        }

        val remainingToFull = (fullThreshold - idleSec).toInt().coerceAtLeast(0)
        
        if (newState != currentState) {
            currentState = newState
            if (newState == IdleState.IDLE_FULL) {
                adShownStartTime = System.currentTimeMillis()
            }
            onStateChangeListener?.invoke(currentState, remainingToFull)
        } else if (currentState == IdleState.IDLE_PRE_FULL) {
            onStateChangeListener?.invoke(currentState, remainingToFull)
        } else if (currentState == IdleState.IDLE_FULL || currentState == IdleState.IDLE_LOOP) {
            // Handle Looping logic
            val timeSinceAdStart = (now - adShownStartTime) / 1000
            val refreshInterval = ConfigManager.getAdsIdleRefreshSec()
            if (timeSinceAdStart >= refreshInterval) {
                currentState = IdleState.IDLE_LOOP
                adShownStartTime = now // Reset for next loop
                onStateChangeListener?.invoke(currentState, 0)
            }
        }
    }
}
