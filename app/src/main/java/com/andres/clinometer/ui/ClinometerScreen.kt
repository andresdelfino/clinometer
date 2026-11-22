package com.andres.clinometer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andres.clinometer.ClinometerViewModel
import com.andres.clinometer.R
import com.andres.clinometer.Settings
import com.andres.clinometer.UiState
import com.andres.clinometer.ui.theme.ClinometerTheme
import java.util.Locale

@Composable
fun ClinometerScreen(viewModel: ClinometerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    KeepScreenOn()

    LaunchedEffect(viewModel) {
        viewModel.calibrationResults.collect { result ->
            snackbarHostState.showSnackbar(resources.getString(result.messageRes))
        }
    }

    ClinometerContent(
        state = state,
        sensorAvailable = viewModel.sensorAvailable,
        snackbarHostState = snackbarHostState,
        onCalibrate = viewModel::calibrate,
        onResetCalibration = viewModel::resetCalibration,
    )
}

@Composable
fun ClinometerContent(
    state: UiState,
    sensorAvailable: Boolean,
    snackbarHostState: SnackbarHostState,
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
            OutlinedButton(
                onClick = { showCalibrationDialog = true },
                enabled = !state.calibrating,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.calibrate)) }
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

    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AngleGauge(
                angleDeg = state.tiltDeg,
                needleColor = scheme.primary,
                contentColor = scheme.onSurface,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            BigAngle(state.tiltDeg)
            // Laid out even when empty, so the gauge doesn't resize as the message comes and goes.
            Text(
                text = statusText(state).orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
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
private fun statusText(state: UiState): String? = when {
    state.calibrating -> stringResource(R.string.hold_still)
    state.tiltDeg == null -> stringResource(R.string.waiting_for_sensor)
    else -> null
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

private fun formatAngle(deg: Double?): String =
    deg?.let { String.format(Locale.getDefault(), "%.1f°", it) } ?: "––.–°"

@Preview(name = "Light", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun PreviewLight() = ClinometerTheme {
    ClinometerContent(
        state = UiState(tiltDeg = 27.6),
        sensorAvailable = true,
        snackbarHostState = remember { SnackbarHostState() },
        onCalibrate = {}, onResetCalibration = {},
    )
}

@Preview(name = "Dark", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun PreviewDark() = ClinometerTheme(darkTheme = true) {
    ClinometerContent(
        state = UiState(tiltDeg = 45.3),
        sensorAvailable = true,
        snackbarHostState = remember { SnackbarHostState() },
        onCalibrate = {}, onResetCalibration = {},
    )
}

