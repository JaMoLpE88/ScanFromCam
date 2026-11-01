package com.scanfromcam.core

import android.media.AudioManager
import android.media.MediaActionSound
import android.media.ToneGenerator

/**
 * Short audio feedback for the camera: one beep when the document is framed and
 * the system shutter click when a photo is taken. Both use platform sounds, so
 * the app ships no audio files and respects the device audio settings. Each one
 * can be turned off from Ajustes.
 */
class SoundFeedback(private val settings: AppSettings) {

    private val tone: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
    }.getOrNull()

    private val shutter = MediaActionSound().apply {
        runCatching { load(MediaActionSound.SHUTTER_CLICK) }
    }

    fun documentFramed() {
        if (!settings.frameBeep) return
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
    }

    fun shutter() {
        if (!settings.shutterSound) return
        runCatching { shutter.play(MediaActionSound.SHUTTER_CLICK) }
    }

    fun release() {
        runCatching { tone?.release() }
        runCatching { shutter.release() }
    }
}
