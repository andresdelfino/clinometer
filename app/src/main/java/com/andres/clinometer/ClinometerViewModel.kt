package com.andres.clinometer

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class Settings(
    val targetDeg: Int = 30,
    val toleranceDeg: Double = 1.0,
    val beep: Boolean = true,
    /** Gravity direction (unit vector, phone frame) measured on a level surface. */
    val reference: Vec3 = Vec3.SCREEN_UP,
) {
    val isCalibrated: Boolean get() = reference != Vec3.SCREEN_UP
    val zeroOffsetDeg: Double get() = TiltMath.angleBetweenDeg(reference, Vec3.SCREEN_UP)
}

data class UiState(
    /** Current incline in degrees, or null until the first sensor reading arrives. */
    val tiltDeg: Double? = null,
    val settings: Settings = Settings(),
    val onTarget: Boolean = false,
    val calibrating: Boolean = false,
)

enum class CalibrationResult(@StringRes val messageRes: Int) {
    Done(R.string.calibration_done),
    NotFlat(R.string.calibration_not_flat),
    Moved(R.string.calibration_moved),
    NoData(R.string.calibration_no_data),
}

class ClinometerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    // Kept apart from the rest of the settings because it describes this phone (its camera bump,
    // its case), so it's left out of backups (see res/xml/data_extraction_rules.xml).
    private val calibrationPrefs = app.getSharedPreferences(PREFS_CALIBRATION, Context.MODE_PRIVATE)

    private val sensor = TiltSensor(app)

    val sensorAvailable: Boolean = sensor.isAvailable

    // Sensor runs only while the UI (or a calibration) is collecting; the value resets when it stops
    // so a stale reading is never shown after returning to the app.
    private val gravity: StateFlow<GravitySample?> = sensor.gravity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), null)

    private val settings = MutableStateFlow(loadSettings())
    private val calibrating = MutableStateFlow(false)

    private val calibrationChannel = Channel<CalibrationResult>(Channel.BUFFERED)
    val calibrationResults: Flow<CalibrationResult> = calibrationChannel.receiveAsFlow()

    val uiState: StateFlow<UiState> =
        combine(gravity, settings, calibrating) { g, s, c -> Triple(g, s, c) }
            .scan(UiState(settings = settings.value)) { previous, (g, s, c) ->
                val tilt = g?.let { TiltMath.angleBetweenDeg(it.vector, s.reference) }
                val onTarget = tilt != null &&
                    TiltMath.isOnTarget(tilt, s.targetDeg.toDouble(), s.toleranceDeg, previous.onTarget)
                UiState(tiltDeg = tilt, settings = s, onTarget = onTarget, calibrating = c)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), UiState(settings = settings.value))

    fun setTarget(deg: Int) = updateSettings { it.copy(targetDeg = deg.coerceIn(MIN_TARGET, MAX_TARGET)) }

    fun toggleBeep() = updateSettings { it.copy(beep = !it.beep) }

    fun cycleTolerance() = updateSettings { s ->
        val next = TOLERANCES.firstOrNull { it > s.toleranceDeg } ?: TOLERANCES.first()
        s.copy(toleranceDeg = next)
    }

    fun resetCalibration() = updateSettings { it.copy(reference = Vec3.SCREEN_UP) }

    /**
     * Averages the gravity direction over a short window and stores it as "level".
     * Waits first so the tap that started it has settled.
     */
    fun calibrate() {
        if (calibrating.value) return
        viewModelScope.launch {
            calibrating.value = true
            try {
                delay(CALIBRATION_SETTLE_MS)
                val samples = mutableListOf<Vec3>()
                withTimeoutOrNull(CALIBRATION_SAMPLE_MS) {
                    gravity.filterNotNull().collect { samples += it.vector.normalized() }
                }
                calibrationChannel.send(applyCalibration(samples))
            } finally {
                calibrating.value = false
            }
        }
    }

    private fun applyCalibration(samples: List<Vec3>): CalibrationResult {
        if (samples.size < MIN_CALIBRATION_SAMPLES) return CalibrationResult.NoData
        val mean = samples.reduce(Vec3::plus).normalized()
        if (TiltMath.maxDeviationDeg(samples, mean) > MAX_CALIBRATION_WOBBLE_DEG) return CalibrationResult.Moved
        if (TiltMath.angleBetweenDeg(mean, Vec3.SCREEN_UP) > MAX_CALIBRATION_OFFSET_DEG) return CalibrationResult.NotFlat
        updateSettings { it.copy(reference = mean) }
        return CalibrationResult.Done
    }

    private fun updateSettings(transform: (Settings) -> Settings) {
        val s = settings.updateAndGet(transform)
        prefs.edit {
            putInt(KEY_TARGET, s.targetDeg)
            putFloat(KEY_TOLERANCE, s.toleranceDeg.toFloat())
            putBoolean(KEY_BEEP, s.beep)
        }
        calibrationPrefs.edit {
            putFloat(KEY_REF_X, s.reference.x.toFloat())
            putFloat(KEY_REF_Y, s.reference.y.toFloat())
            putFloat(KEY_REF_Z, s.reference.z.toFloat())
        }
    }

    private fun loadSettings(): Settings {
        val defaults = Settings()
        val reference = Vec3(
            calibrationPrefs.getFloat(KEY_REF_X, 0f).toDouble(),
            calibrationPrefs.getFloat(KEY_REF_Y, 0f).toDouble(),
            calibrationPrefs.getFloat(KEY_REF_Z, 1f).toDouble(),
        )
        return Settings(
            targetDeg = prefs.getInt(KEY_TARGET, defaults.targetDeg).coerceIn(MIN_TARGET, MAX_TARGET),
            toleranceDeg = prefs.getFloat(KEY_TOLERANCE, defaults.toleranceDeg.toFloat()).toDouble(),
            beep = prefs.getBoolean(KEY_BEEP, defaults.beep),
            reference = if (reference.length > 0.5) reference else Vec3.SCREEN_UP,
        )
    }

    companion object {
        const val MIN_TARGET = 0
        const val MAX_TARGET = 90
        val PRESETS = listOf(0, 15, 30, 45, 60, 75, 90)
        val TOLERANCES = listOf(0.5, 1.0, 2.0)

        private const val CALIBRATION_SETTLE_MS = 1000L
        private const val CALIBRATION_SAMPLE_MS = 1500L
        private const val MIN_CALIBRATION_SAMPLES = 10
        private const val MAX_CALIBRATION_WOBBLE_DEG = 0.5
        private const val MAX_CALIBRATION_OFFSET_DEG = 20.0

        // res/xml/data_extraction_rules.xml backs up the settings by file name: "clinometer.xml".
        private const val PREFS_SETTINGS = "clinometer"
        private const val PREFS_CALIBRATION = "calibration"

        private const val KEY_TARGET = "target"
        private const val KEY_TOLERANCE = "tolerance"
        private const val KEY_BEEP = "beep"
        private const val KEY_REF_X = "ref_x"
        private const val KEY_REF_Y = "ref_y"
        private const val KEY_REF_Z = "ref_z"
    }
}
