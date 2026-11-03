package com.scanfromcam.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * User preferences. Each flag is Compose state on top of SharedPreferences, so
 * the switch that shows it and the camera that obeys it share one value without
 * listeners. Defaults keep the behaviour the app had before the switches existed.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * App language. First launch follows the device: Spanish only on Spanish
     * (Spain) devices, English everywhere else.
     */
    var language: Lang
        get() = languageState.value
        set(value) {
            languageState.value = value
            Strings.lang = value
            prefs.edit().putString("language", value.tag).apply()
        }

    private val languageState = mutableStateOf(
        prefs.getString("language", null)?.let { Lang.fromTag(it) } ?: Lang.defaultFor()
    )

    init {
        Strings.lang = languageState.value
    }

    var frameBeep by flag("frameBeep", true)
    var shutterSound by flag("shutterSound", true)
    var autoCapture by flag("autoCapture", true)

    /** Library layout: thumbnails in a grid instead of rows in a list. */
    var gridView by flag("gridView", false)

    /** Bakes a tiled watermark into every saved page. */
    var watermark by flag("watermark", false)

    /** Text repeated across the watermark, empty means nothing to draw. */
    var watermarkText: String
        get() = watermarkTextState.value
        set(value) {
            val text = value.take(MAX_WATERMARK_LENGTH)
            watermarkTextState.value = text
            prefs.edit().putString("watermarkText", text).apply()
        }

    private val watermarkTextState =
        mutableStateOf(prefs.getString("watermarkText", DEFAULT_WATERMARK) ?: DEFAULT_WATERMARK)

    /** Seconds the document must stay framed before the automatic shot, 0..3. */
    var autoCaptureDelay: Int
        get() = delay.value
        set(value) {
            val seconds = value.coerceIn(0, MAX_DELAY)
            delay.value = seconds
            prefs.edit().putInt("autoCaptureDelay", seconds).apply()
        }

    private val delay = mutableStateOf(prefs.getInt("autoCaptureDelay", DEFAULT_DELAY).coerceIn(0, MAX_DELAY))

    /** Gate the app behind the device credential (biometrics, PIN, pattern…). */
    var appLockEnabled by flag("appLockEnabled", false)

    private fun flag(key: String, default: Boolean) = Flag(prefs, key, default)

    private class Flag(
        private val prefs: SharedPreferences,
        private val key: String,
        default: Boolean,
    ) : ReadWriteProperty<AppSettings, Boolean> {
        private val state = mutableStateOf(prefs.getBoolean(key, default))

        override fun getValue(thisRef: AppSettings, property: KProperty<*>): Boolean = state.value

        override fun setValue(thisRef: AppSettings, property: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    companion object {
        const val MAX_DELAY = 3
        const val DEFAULT_DELAY = 2
        const val MAX_WATERMARK_LENGTH = 40
        const val DEFAULT_WATERMARK = "ScanFromCam"
    }
}