package com.andres.clinometer.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andres.clinometer.ClinometerViewModel
import com.andres.clinometer.ClinometerViewModel.Companion.MAX_TARGET
import com.andres.clinometer.ClinometerViewModel.Companion.MIN_TARGET
import com.andres.clinometer.ClinometerViewModel.Companion.PRESETS
import com.andres.clinometer.R
import com.andres.clinometer.Settings
import com.andres.clinometer.UiState
import com.andres.clinometer.ui.theme.ClinometerTheme
import com.andres.clinometer.ui.theme.customColors
import java.util.Locale
import kotlin.math.abs

@Composable
fun ClinometerScreen(viewModel: ClinometerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    KeepScreenOn()
    TargetBeeper(onTarget = state.onTarget, enabled = state.settings.beep)

    LaunchedEffect(viewModel) {
        viewModel.calibrationResults.collect { result ->
            snackbarHostState.showSnackbar(resources.getString(result.messageRes))
        }
    }

    ClinometerContent(
        state = state,
        sensorAvailable = viewModel.sensorAvailable,
        snackbarHostState = snackbarHostState,
        onTargetChange = viewModel::setTarget,
        onToggleBeep = viewModel::toggleBeep,
        onCycleTolerance = viewModel::cycleTolerance,
        onCalibrate = viewModel::calibrate,
        onResetCalibration = viewModel::resetCalibration,
    )
}

@Composable
fun ClinometerContent(
    state: UiState,
    sensorAvailable: Boolean,
    snackbarHostState: SnackbarHostState,
    onTargetChange: (Int) -> Unit,
    onToggleBeep: () -> Unit,
    onCycleTolerance: () -> Unit,
    onCalibrate: () -> Unit,
    onResetCalibration: () -> Unit,
) {
    var showCalibrationDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (!sensorAvailable) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_sensor), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadingPanel(state, Modifier.fillMaxWidth().weight(1f))
            TargetPanel(state.settings.targetDeg, onTargetChange, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showCalibrationDialog = true },
                    enabled = !state.calibrating,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.calibrate)) }
                FilledTonalButton(onClick = onToggleBeep, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (state.settings.beep) R.string.sound_on else R.string.sound_off))
                }
                FilledTonalButton(onClick = onCycleTolerance, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tolerance_button, formatTolerance(state.settings.toleranceDeg)))
                }
            }
        }
    }

    if (showCalibrationDialog) {
        CalibrationDialog(
            settings = state.settings,
            onCalibrate = onCalibrate,
            onReset = onResetCalibration,
            onDismiss = { showCalibrationDialog = false },
        )
    }
}

@Composable
private fun ReadingPanel(state: UiState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val onTarget = state.onTarget
    val container by animateColorAsState(if (onTarget) custom.success else scheme.surfaceContainerHigh, label = "container")
    val content by animateColorAsState(if (onTarget) custom.onSuccess else scheme.onSurface, label = "content")
    val contentVariant by animateColorAsState(if (onTarget) custom.onSuccess else scheme.onSurfaceVariant, label = "contentVariant")
    val needle by animateColorAsState(if (onTarget) custom.onSuccess else scheme.primary, label = "needle")
    val target by animateColorAsState(if (onTarget) custom.onSuccess else scheme.tertiary, label = "target")

    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = container, contentColor = content) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AngleGauge(
                angleDeg = state.tiltDeg,
                targetDeg = state.settings.targetDeg,
                toleranceDeg = state.settings.toleranceDeg,
                needleColor = needle,
                targetColor = target,
                contentColor = content,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            BigAngle(state.tiltDeg)
            Text(
                text = statusText(state),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    R.string.target_summary,
                    state.settings.targetDeg,
                    formatTolerance(state.settings.toleranceDeg),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = contentVariant,
            )
        }
    }
}

/** The live reading, sized to the panel width so "90.0°" always fits on one line. */
@Composable
private fun BigAngle(angleDeg: Double?) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) { min(maxWidth / 2.6f, 112.dp).toSp() }
        Text(
            text = formatAngle(angleDeg),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.1f,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun statusText(state: UiState): String {
    val tilt = state.tiltDeg
    return when {
        state.calibrating -> stringResource(R.string.hold_still)
        tilt == null -> stringResource(R.string.waiting_for_sensor)
        state.onTarget -> stringResource(R.string.on_target)
        else -> {
            val delta = tilt - state.settings.targetDeg
            stringResource(if (delta < 0) R.string.too_flat else R.string.too_steep, abs(delta))
        }
    }
}

@Composable
private fun TargetPanel(targetDeg: Int, onTargetChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.target),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { onTargetChange(targetDeg - 1) },
                    enabled = targetDeg > MIN_TARGET,
                ) { Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.decrease_target)) }
                Text(
                    text = "$targetDeg°",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 80.dp),
                )
                FilledTonalIconButton(
                    onClick = { onTargetChange(targetDeg + 1) },
                    enabled = targetDeg < MAX_TARGET,
                ) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.increase_target)) }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PRESETS.forEachIndexed { index, preset ->
                    SegmentedButton(
                        selected = preset == targetDeg,
                        onClick = { onTargetChange(preset) },
                        shape = SegmentedButtonDefaults.itemShape(index, PRESETS.size),
                        // Seven segments leave no room for the checkmark or the default padding.
                        icon = {},
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) { Text("$preset°", maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

@Composable
private fun CalibrationDialog(
    settings: Settings,
    onCalibrate: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calibrate_title)) },
        text = {
            Column {
                Text(stringResource(R.string.calibrate_body))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (settings.isCalibrated) {
                        stringResource(R.string.calibrate_current_offset, settings.zeroOffsetDeg)
                    } else {
                        stringResource(R.string.calibrate_not_calibrated)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCalibrate(); onDismiss() }) { Text(stringResource(R.string.calibrate)) }
        },
        dismissButton = {
            Row {
                if (settings.isCalibrated) {
                    TextButton(onClick = { onReset(); onDismiss() }) { Text(stringResource(R.string.reset)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Plays a short double beep each time the reading enters the target band. */
@Composable
private fun TargetBeeper(onTarget: Boolean, enabled: Boolean) {
    val currentEnabled by rememberUpdatedState(enabled)
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    LaunchedEffect(onTarget) {
        if (onTarget && currentEnabled) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
    }
}

private fun formatAngle(deg: Double?): String =
    deg?.let { String.format(Locale.getDefault(), "%.1f°", it) } ?: "––.–°"

private fun formatTolerance(deg: Double): String =
    if (deg % 1.0 == 0.0) deg.toInt().toString() else String.format(Locale.getDefault(), "%.1f", deg)

@Preview(name = "Adjusting", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun PreviewAdjusting() = ClinometerTheme {
    ClinometerContent(
        state = UiState(tiltDeg = 27.6, settings = Settings(targetDeg = 30)),
        sensorAvailable = true,
        snackbarHostState = remember { SnackbarHostState() },
        onTargetChange = {}, onToggleBeep = {}, onCycleTolerance = {}, onCalibrate = {}, onResetCalibration = {},
    )
}

@Preview(name = "On target", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun PreviewOnTarget() = ClinometerTheme(darkTheme = true) {
    ClinometerContent(
        state = UiState(tiltDeg = 45.3, settings = Settings(targetDeg = 45), onTarget = true),
        sensorAvailable = true,
        snackbarHostState = remember { SnackbarHostState() },
        onTargetChange = {}, onToggleBeep = {}, onCycleTolerance = {}, onCalibrate = {}, onResetCalibration = {},
    )
}

