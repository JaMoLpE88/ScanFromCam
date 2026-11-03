package com.scanfromcam.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Session state of the app lock. There is no PIN of our own: unlocking is the
 * device credential the user already has (biometrics, PIN, pattern or password),
 * asked by [com.scanfromcam.ui.AppLockGate] through the system prompt.
 */
class AppLock(private val settings: AppSettings) {

    var authenticated by mutableStateOf(false)
        private set

    val enabled: Boolean
        get() = settings.appLockEnabled

    /** True while the app is gated: enabled and not yet authenticated this session. */
    val locked: Boolean
        get() = enabled && !authenticated

    fun onUnlocked() {
        authenticated = true
    }

    fun lock() {
        authenticated = false
    }
}
