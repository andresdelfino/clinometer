package com.andres.clinometer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A 0°–90° protractor seen from the side of the bench: the floor runs to the right from the pivot,
 * and the needle is the bench's back pad.
 */
@Composable
fun AngleGauge(
    angleDeg: Double?,
    needleColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val labels = remember(textMeasurer, labelStyle) {
        (0..90 step 15).associateWith { textMeasurer.measure("$it°", labelStyle) }
    }

    Canvas(modifier) {
        val pad = 12.dp.toPx()
        val r = min(size.width, size.height) - 2 * pad
        if (r <= 0f) return@Canvas
        val origin = Offset((size.width - r) / 2f, (size.height + r) / 2f)
        val arcTopLeft = origin - Offset(r, r)
        val arcSize = Size(2 * r, 2 * r)

        fun pointAt(deg: Double, radius: Float): Offset {
            val rad = Math.toRadians(deg)
            return Offset(origin.x + radius * cos(rad).toFloat(), origin.y - radius * sin(rad).toFloat())
        }

        // Canvas arcs run clockwise from 3 o'clock, so an angle above the floor is negative.
        fun sector(fromDeg: Double, toDeg: Double, color: Color) = drawArc(
            color = color,
            startAngle = -toDeg.toFloat(),
            sweepAngle = (toDeg - fromDeg).toFloat(),
            useCenter = true,
            topLeft = arcTopLeft,
            size = arcSize,
        )

        val needleDeg = angleDeg?.coerceIn(0.0, 90.0)

        if (needleDeg != null) sector(0.0, needleDeg, needleColor.copy(alpha = 0.12f))

        // Outer arc, floor and vertical.
        drawArc(
            color = contentColor.copy(alpha = 0.3f),
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawLine(contentColor.copy(alpha = 0.55f), origin, pointAt(0.0, r), 3.dp.toPx(), StrokeCap.Round)
        drawLine(contentColor.copy(alpha = 0.2f), origin, pointAt(90.0, r), 1.dp.toPx())

        drawTicks(r, contentColor, ::pointAt)

        // Degree labels just inside the major ticks, nudged so 0° and 90° stay clear of the axes.
        val labelRadius = r - 30.dp.toPx()
        val gap = 2.dp.toPx()
        labels.forEach { (deg, layout) ->
            // Hide labels the needle runs through rather than draw them half-covered.
            if (needleDeg != null && abs(deg - needleDeg) < 4.0) return@forEach
            val w = layout.size.width
            val h = layout.size.height
            val p = pointAt(deg.toDouble(), labelRadius)
            val cx = p.x.coerceAtLeast(origin.x + w / 2f + gap)
            val cy = p.y.coerceAtMost(origin.y - h / 2f - gap)
            drawText(layout, color = contentColor.copy(alpha = 0.7f), topLeft = Offset(cx - w / 2f, cy - h / 2f))
        }

        // Needle (the back pad) and its pivot.
        if (needleDeg != null) {
            drawLine(needleColor, origin, pointAt(needleDeg, r), 9.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(needleColor, radius = 9.dp.toPx(), center = origin)
    }
}

private fun DrawScope.drawTicks(r: Float, color: Color, pointAt: (Double, Float) -> Offset) {
    val major = 14.dp.toPx()
    val minor = 7.dp.toPx()
    for (deg in 0..90 step 5) {
        val isMajor = deg % 15 == 0
        val d = deg.toDouble()
        drawLine(
            color = color.copy(alpha = if (isMajor) 0.6f else 0.35f),
            start = pointAt(d, r),
            end = pointAt(d, r - if (isMajor) major else minor),
            strokeWidth = (if (isMajor) 2 else 1).dp.toPx(),
        )
    }
}
