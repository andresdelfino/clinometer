package com.andres.clinometer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TiltMathTest {
    private val g = 9.81

    /** Gravity reading for a phone lying screen-up on a surface tilted [deg] about the phone's x axis. */
    private fun tiltedAboutX(deg: Double) = Math.toRadians(deg).let { Vec3(0.0, g * sin(it), g * cos(it)) }

    private fun rotateAboutX(v: Vec3, deg: Double): Vec3 {
        val a = Math.toRadians(deg)
        return Vec3(v.x, v.y * cos(a) - v.z * sin(a), v.y * sin(a) + v.z * cos(a))
    }

    private fun rotateAboutZ(v: Vec3, deg: Double): Vec3 {
        val a = Math.toRadians(deg)
        return Vec3(v.x * cos(a) - v.y * sin(a), v.x * sin(a) + v.y * cos(a), v.z)
    }

    @Test
    fun flatPhoneReadsZero() {
        assertEquals(0.0, TiltMath.angleBetweenDeg(Vec3(0.0, 0.0, g), Vec3.SCREEN_UP), 1e-9)
    }

    @Test
    fun uprightPhoneReadsNinety() {
        assertEquals(90.0, TiltMath.angleBetweenDeg(Vec3(0.0, g, 0.0), Vec3.SCREEN_UP), 1e-9)
    }

    @Test
    fun benchAnglesAreRecovered() {
        for (deg in listOf(0.0, 15.0, 30.0, 45.0, 60.0, 75.0, 90.0)) {
            assertEquals(deg, TiltMath.angleBetweenDeg(tiltedAboutX(deg), Vec3.SCREEN_UP), 1e-9)
        }
    }

    @Test
    fun phoneRotationOnTheBenchDoesNotMatter() {
        // Same 30° incline; phone turned to point anywhere on the pad.
        for (yaw in listOf(0.0, 37.0, 90.0, 180.0, 271.0)) {
            val reading = rotateAboutZ(tiltedAboutX(30.0), yaw)
            assertEquals(30.0, TiltMath.angleBetweenDeg(reading, Vec3.SCREEN_UP), 1e-9)
        }
    }

    @Test
    fun calibrationCancelsCameraBump() {
        // Phone rocks 2.5° on its camera bump, so on a level floor it already reads 2.5°.
        val bump = 2.5
        val levelReading = rotateAboutX(Vec3(0.0, 0.0, g), bump)
        assertEquals(bump, TiltMath.angleBetweenDeg(levelReading, Vec3.SCREEN_UP), 1e-9)

        val reference = levelReading.normalized()
        // Bench tilted 45°, in a direction at an arbitrary angle to the phone; the phone still
        // rests on its bump, so the same fixed rotation applies on top.
        val benchGravity = rotateAboutZ(rotateAboutX(Vec3(0.0, 0.0, g), 45.0), 20.0)
        val reading = rotateAboutX(benchGravity, bump)
        assertEquals(45.0, TiltMath.angleBetweenDeg(reading, reference), 1e-9)
        // Without calibration the bump would add error.
        assertTrue(kotlin.math.abs(TiltMath.angleBetweenDeg(reading, Vec3.SCREEN_UP) - 45.0) > 1.0)
    }

    @Test
    fun maxDeviationOfSteadySamplesIsSmall() {
        val samples = listOf(Vec3(0.0, 0.0, 1.0), rotateAboutX(Vec3(0.0, 0.0, 1.0), 0.2))
        val mean = samples.reduce(Vec3::plus).normalized()
        assertEquals(0.1, TiltMath.maxDeviationDeg(samples, mean), 1e-6)
    }
}
