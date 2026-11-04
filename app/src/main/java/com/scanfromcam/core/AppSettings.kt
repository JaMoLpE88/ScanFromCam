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

    /**
     * Whether the app may look for updates on GitHub: null until the user is
     * asked, then their answer. False means not a single network byte, ever.
     */
    var updateConsent: Boolean?
        get() = consent.value
        set(value) {
            consent.value = value
            prefs.edit().apply {
                if (value == null) remove(KEY_CONSENT) else putBoolean(KEY_CONSENT, value)
            }.apply()
        }

    private val consent = mutableStateOf(
        if (prefs.contains(KEY_CONSENT)) prefs.getBoolean(KEY_CONSENT, false) else null
    )

    /** Download a new version without asking; the install prompt still appears. */
    var autoDownload by flag("autoDownload", false)

    /** Last successful check, so a phone opened ten times does not ask ten times. */
    var lastCheckAt: Long
        get() = lastCheck.value
        set(value) {
            lastCheck.value = value
            prefs.edit().putLong(KEY_LAST_CHECK, value).apply()
        }

    private val lastCheck = mutableStateOf(prefs.getLong(KEY_LAST_CHECK, 0L))

    /** ETag of the last release payload: a 304 costs no rate limit. */
    var updateEtag: String?
        get() = prefs.getString(KEY_ETAG, null)
        set(value) = prefs.edit().putString(KEY_ETAG, value).apply()

    /** Last release JSON, kept so a 304 can still resolve to a real answer. */
    var lastReleaseJson: String?
        get() = prefs.getString(KEY_RELEASE_JSON, null)
        set(value) = prefs.edit().putString(KEY_RELEASE_JSON, value).apply()

    /** Version the user said "not now" to: no automatic prompt for it again. */
    var skippedVersion: String?
        get() = prefs.getString(KEY_SKIPPED, null)
        set(value) = prefs.edit().putString(KEY_SKIPPED, value).apply()

    /** versionCode of the APK waiting to be installed, for the cleanup after it. */
    var pendingVersionCode: Long?
        get() = if (prefs.contains(KEY_PENDING)) prefs.getLong(KEY_PENDING, 0L) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_PENDING) else putLong(KEY_PENDING, value)
        }.apply()

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

        private const val KEY_CONSENT = "updateConsent"
        private const val KEY_LAST_CHECK = "updateLastCheck"
        private const val KEY_ETAG = "updateEtag"
        private const val KEY_RELEASE_JSON = "updateReleaseJson"
        private const val KEY_SKIPPED = "updateSkipped"
        private const val KEY_PENDING = "updatePendingVersionCode"
    }
}