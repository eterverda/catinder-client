package com.catinder

import android.app.Application
import com.catinder.images.CatinderImageLoader
import com.catinder.logging.MiniLogger

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        CatinderImageLoader.init(this)
        MiniLogger.d("App created")
    }
}
