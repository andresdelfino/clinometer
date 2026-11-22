package com.andres.clinometer

import kotlin.math.atan2
import kotlin.math.sqrt

/** A 3D vector in the device's sensor coordinate system (x right, y up the screen, z out of the screen). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)

    infix fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z

    infix fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    val length: Double get() = sqrt(this dot this)

    fun normalized(): Vec3 {
        val l = length
        return if (l == 0.0) this else Vec3(x / l, y / l, z / l)
    }

    companion object {
        /** What the accelerometer reads when the phone lies perfectly flat, screen up. */
        val SCREEN_UP = Vec3(0.0, 0.0, 1.0)
    }
}

object TiltMath {
    /**
     * Angle between two vectors, in degrees (0..180).
     *
     * With `a` = the current gravity reading and `b` = the reading taken while the phone lay on a
     * level surface, this is the incline of whatever the phone is resting on, regardless of how
     * the phone is rotated on that surface. Because the reference is stored in the phone's own
     * frame, a fixed offset such as a camera bump cancels out.
     */
    fun angleBetweenDeg(a: Vec3, b: Vec3): Double =
        Math.toDegrees(atan2((a cross b).length, a dot b))

    /** Largest angular distance of any sample from [mean]; used to reject calibrations where the phone moved. */
    fun maxDeviationDeg(samples: List<Vec3>, mean: Vec3): Double =
        samples.maxOfOrNull { angleBetweenDeg(it, mean) } ?: 0.0
}
