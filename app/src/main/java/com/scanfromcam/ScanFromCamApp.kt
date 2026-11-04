package com.scanfromcam

import android.app.Application
import com.scanfromcam.di.AppContainer
import org.opencv.android.OpenCVLoader

class ScanFromCamApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // A downloaded APK only lives while the sheet is on screen.
        runCatching { container.updater.cleanCache() }
        runCatching { OpenCVLoader.initLocal() }
    }
}
