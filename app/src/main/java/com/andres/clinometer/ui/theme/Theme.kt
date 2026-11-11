package com.andres.clinometer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 custom colours: roles the dynamic scheme has no equivalent for. They're defined for
 * light and dark themes like the scheme's own roles.
 *
 * @property success fills the reading panel when the bench is at the target angle, which has to be
 *   seen from across the room. It's a fixed green rather than a wallpaper-derived role, which could
 *   come out red or grey. These are the tones M3 uses for a primary colour, from the palette of
 *   seed `#1E8E3E`: 40 in light theme, 80 in dark.
 * @property onSuccess text and drawing on [success]: tone 100 in light theme, 20 in dark.
 */
data class CustomColors(val success: Color, val onSuccess: Color)

private val LightCustomColors = CustomColors(success = Color(0xFF006E2A), onSuccess = Color(0xFFFFFFFF))
private val DarkCustomColors = CustomColors(success = Color(0xFF73DC82), onSuccess = Color(0xFF003912))

private val LocalCustomColors = staticCompositionLocalOf { LightCustomColors }

/** The [CustomColors] of the current theme, alongside [MaterialTheme.colorScheme]. */
val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColors.current

@Composable
fun ClinometerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val customColors = if (darkTheme) DarkCustomColors else LightCustomColors
    CompositionLocalProvider(LocalCustomColors provides customColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
