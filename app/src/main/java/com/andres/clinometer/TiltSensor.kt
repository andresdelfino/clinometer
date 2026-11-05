package com.andres.clinometer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * One filtered gravity reading. The timestamp makes every sample distinct, so a perfectly steady
 * phone still produces a stream of samples rather than being collapsed into one by StateFlow.
 */
data class GravitySample(val vector: Vec3, val timestampNanos: Long)

/**
 * Streams the gravity vector from the raw accelerometer, low-pass filtered.
 *
 * The raw accelerometer is used rather than the fused TYPE_GRAVITY sensor: for a phone that is
 * sitting still it is the most accurate source, and the filter removes its noise.
 */
class TiltSensor(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isAvailable: Boolean get() = accelerometer != null

    /** Emits while collected; the sensor is unregistered as soon as collection stops. */
    fun gravity(timeConstantSeconds: Double = 0.3): Flow<GravitySample> = callbackFlow {
        val sensor = accelerometer
        if (sensorManager == null || sensor == null) {
            close()
            return@callbackFlow
        }

        var fx = 0.0
        var fy = 0.0
        var fz = 0.0
        var lastTimestamp = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0].toDouble()
                val y = event.values[1].toDouble()
                val z = event.values[2].toDouble()
                if (lastTimestamp == 0L) {
                    fx = x; fy = y; fz = z
                } else {
                    // Exponential moving average with a time-based weight, so the smoothing
                    // is the same whatever rate the device delivers samples at.
                    val dt = (event.timestamp - lastTimestamp) / 1e9
                    val alpha = (dt / (timeConstantSeconds + dt)).coerceIn(0.0, 1.0)
                    fx += alpha * (x - fx)
                    fy += alpha * (y - fy)
                    fz += alpha * (z - fz)
                }
                lastTimestamp = event.timestamp
                trySend(GravitySample(Vec3(fx, fy, fz), event.timestamp))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.buffer(Channel.CONFLATED)
}
