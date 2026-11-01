package com.scanfromcam.data

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Single process-wide worker for camera analysis and capture callbacks.
 * Living for the whole process avoids shutting down an executor while a
 * capture callback is still in flight when the screen goes away.
 */
object CameraWork {
    val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "camera-work").apply { isDaemon = true }
    }
}
