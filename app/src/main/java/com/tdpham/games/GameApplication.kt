package com.tdpham.games

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class GameApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            Log.d("GameApplication", "Firebase initialized successfully.")
        } catch (e: Exception) {
            Log.e("GameApplication", "Failed to initialize Firebase: ${e.message}", e)
        }
    }
}
