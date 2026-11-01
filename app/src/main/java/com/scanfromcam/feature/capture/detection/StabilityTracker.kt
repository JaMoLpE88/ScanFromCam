package com.scanfromcam.feature.capture.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Tells the capture screen whether the handset is still enough to keep reading
 * frames. While the phone moves, live detection has nothing reliable to say and
 * a photo taken then comes out blurred.
 *
 * The signal is the size of the linear acceleration, smoothed so one spike does
 * not flip the state. Between [CALM_LIMIT] and [MOVE_LIMIT] the current state
 * is kept (a dead band around the threshold, so the flag cannot chatter); the
 * jump between consecutive raw accelerometer readings also counts as movement,
 * and that is the only signal on devices without a linear accelerometer. Going
 * still is announced only after it has stayed calm for [STILL_HOLD_MS]; going
 * moving is immediate, because one blurred frame is one wasted capture.
 */
class StabilityTracker(context: Context) : SensorEventListener {

    var isStable: Boolean = true
        private set

    /** Called on the sensor thread whenever stability changes. */
    var onChange: ((Boolean) -> Unit)? = null

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val linear = sensors?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gravity = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val state = MotionState()
    private var level = 0f
    private var listening = false

    private val lastSample = FloatArray(3)
    private var lastSampleValid = false

    fun start() {
        val manager = sensors ?: return
        if (listening) return
        linear?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravity?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        listening = true
    }

    fun stop() {
        sensors?.unregisterListener(this)
        listening = false
        level = 0f
        lastSampleValid = false
        state.reset()
        publish(state.stable)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        val now = SystemClock.elapsedRealtime()
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // Smoothing keeps a single bump from owning the next frames.
                level += SMOOTHING * (length(values) - level)
                publish(state.onLevel(level, now))
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (!lastSampleValid) {
                    copy(values)
                    return
                }
                val jump = length(values, lastSample)
                copy(values)
                publish(
                    if (linear == null) state.onFallback(jump, SHAKE_LIMIT, now)
                    else state.onJolt(jump, SHAKE_LIMIT)
                )
            }
        }
    }

    private fun publish(changed: Boolean) {
        if (changed) onChange?.invoke(state.stable)
    }

    private fun copy(values: FloatArray) {
        lastSample[0] = values[0]
        lastSample[1] = values[1]
        lastSample[2] = values[2]
        lastSampleValid = true
    }

    private fun length(values: FloatArray): Float =
        sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])

    private fun length(current: FloatArray, previous: FloatArray): Float {
        val dx = current[0] - previous[0]
        val dy = current[1] - previous[1]
        val dz = current[2] - previous[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private companion object {
        /** Weight of each new sample in the smoothed level; about three frames. */
        const val SMOOTHING = 0.35f

        /** Smoothed acceleration above which the phone counts as moving, m/s². */
        const val MOVE_LIMIT = 0.28f

        /** Below this the phone is calm again; the gap is the dead band. */
        const val CALM_LIMIT = 0.18f

        /** Frame-to-frame accelerometer jump that means a shake, m/s². */
        const val SHAKE_LIMIT = 0.6f

        /** Time calm before "still" is announced; about five sensor frames. */
        const val STILL_HOLD_MS = 250L
    }
}

/**
 * The decision itself, with no Android types in it, so the dead band and the
 * hold time can be tested with plain millisecond values.
 */
internal class MotionState(
    private val moveLimit: Float = 0.28f,
    private val calmLimit: Float = 0.18f,
    private val holdMs: Long = 250L,
) {

    var stable: Boolean = true
        private set

    private var calmSince = 0L

    /** Feed the smoothed linear acceleration. Returns true when the state flipped. */
    fun onLevel(level: Float, now: Long): Boolean = when {
        level > moveLimit -> moved()
        level < calmLimit -> calmAt(now)
        else -> false
    }

    /** A frame-to-frame accelerometer jump; any jolt means movement. */
    fun onJolt(jump: Float, limit: Float = 0.6f): Boolean =
        if (jump > limit) moved() else false

    /** Same as [onJolt], but the jump is the only signal there is. */
    fun onFallback(jump: Float, limit: Float = 0.6f, now: Long): Boolean =
        if (jump > limit) moved() else calmAt(now)

    fun reset() {
        calmSince = 0L
        stable = true
    }

    private fun moved(): Boolean {
        calmSince = 0L
        return settle(false)
    }

    private fun calmAt(now: Long): Boolean {
        if (calmSince == 0L) calmSince = now
        if (now - calmSince < holdMs) return false
        return settle(true)
    }

    private fun settle(value: Boolean): Boolean {
        if (value == stable) return false
        stable = value
        return true
    }
}
