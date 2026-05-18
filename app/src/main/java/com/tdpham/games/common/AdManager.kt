package com.tdpham.games.common

import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus

object AdManager {
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        
        MobileAds.initialize(context) { status: InitializationStatus ->
            isInitialized = true
        }
    }

    // Methods for loading/showing banners or interstitials can be added here
}
