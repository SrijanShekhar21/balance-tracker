package com.dbt.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dbt.tracker.data.DayPoint
import com.dbt.tracker.util.Money
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Charts are drawn against a single data hue rather than a categorical palette.
 *
 * There is only ever one measure on screen -- money spent -- so a second hue would encode
 * nothing. Where two things must be told apart (recorded versus projected), they are separated
 * by line pattern, which survives colour blindness and greyscale where a second hue would not.
 *
 * #0D9488 was chosen by running the palette validator rather than by eye: it clears the
 * lightness band, the chroma floor and 3:1 contrast against both the light and the dark
 * surface, which is unusual enough that one value serves both themes.
 */
private val SERIES = Color(0xFF0D9488)

private val dayShort = SimpleDateFormat("d MMM", Locale.ENGLISH)
private val dayLong = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH)

/**
 * Daily spend over the trailing month.
 *
 * Touch anywhere on the plot to read a specific day; dragging scrubs across the series. The
 * readout sits above the chart rather than floating over it, because a tooltip under a fingertip
 * on a phone is covered by the finger reading it.
 */
@Composable
fun SpendTrendChart(
    points: List<DayPoint>,
    average: Double,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        EmptyNote("Not enough history yet to draw a trend.")
        return
    }

    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val surface = MaterialTheme.colorScheme.surface
    val crosshair = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    val top = max(points.maxOf { it.spent }, average) * 1.18
    val shown = selected?.let { points.getOrNull(it) } ?: points.last()

    Column(modifier.fillMaxWidth()) {
        Readout(
            label = if (selected == null) "Latest · ${dayLong.format(shown.dayStart)}"
            else dayLong.format(shown.dayStart),
            value = Money.rupees(shown.spent),
            hint = if (selected == null) "Touch the chart to inspect a day" else null
        )

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = 16.dp, bottom = 4.dp)
                // Two recognisers: a tap to pick one day, a drag to sweep across days.
                .pointerInput(points) {
                    detectTapGestures { off -> selected = indexAt(off.x, size.width, points.size) }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { off -> selected = indexAt(off.x, size.width, points.size) }
                    ) { change, _ ->
                        selected = indexAt(change.position.x, size.width, points.size)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = w * i / (points.size - 1).toFloat()
            fun y(v: Double) = (h - (v / top * h)).toFloat().coerceIn(0f, h)

            drawLine(grid, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

            if (average > 0) {
                val ay = y(average)
                drawLine(
                    grid, Offset(0f, ay), Offset(w, ay), strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                val label = measurer.measure("avg ${Money.short(average)}", axisStyle)
                drawText(label, topLeft = Offset(0f, (ay - label.size.height - 3).coerceAtLeast(0f)))
            }

            val line = Path()
            val area = Path().apply { moveTo(0f, h) }
            points.forEachIndexed { i, p ->
                val px = x(i)
                val py = y(p.spent)
                if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
                area.lineTo(px, py)
            }
            area.lineTo(w, h)
            area.close()

            drawPath(area, Brush.verticalGradient(
                listOf(SERIES.copy(alpha = 0.22f), SERIES.copy(alpha = 0f))
            ))
            drawPath(line, SERIES, style = Stroke(width = 2.dp.toPx()))

            selected?.let { i ->
                val px = x(i)
                drawLine(crosshair, Offset(px, 0f), Offset(px, h), strokeWidth = 1.dp.toPx())
                marker(px, y(points[i].spent), surface, emphasised = true)
            } ?: marker(x(points.size - 1), y(points.last().spent), surface)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisLabel(dayShort.format(points.first().dayStart))
            AxisLabel(dayShort.format(points.last().dayStart))
        }
    }
}

/**
 * Cumulative spend this month, continued to month end at the current daily rate.
 *
 * Cumulative rather than daily because the question is "will I overshoot", which a daily series
 * cannot answer. Scrubbing past the last recorded day reads off the projection, so the forecast
 * is inspectable rather than a single number at the end of a dashed line.
 */
@Composable
fun MonthPaceChart(
    monthSeries: List<DayPoint>,
    daysInMonth: Int,
    projectedTotal: Double,
    budget: Double?,
    modifier: Modifier = Modifier
) {
    if (monthSeries.isEmpty()) {
        EmptyNote("No spending recorded this month yet.")
        return
    }

    var selected by remember(monthSeries) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val surface = MaterialTheme.colorScheme.surface
    val crosshair = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val over = negativeColor()

    val cumulative = remember(monthSeries) {
        buildList { var run = 0.0; monthSeries.forEach { run += it.spent; add(run) } }
    }
    val spentSoFar = cumulative.lastOrNull() ?: 0.0
    val recordedDays = monthSeries.size
    val top = max(max(projectedTotal, budget ?: 0.0), spentSoFar) * 1.18
    val willOvershoot = budget != null && projectedTotal > budget

    // Beyond the recorded days the line is a forecast, so the readout says so.
    val dayRate = if (recordedDays > 0) spentSoFar / recordedDays else 0.0
    val selectedDay = selected?.plus(1)
    val selectedValue = selectedDay?.let {
        if (it <= recordedDays) cumulative[it - 1] else dayRate * it
    }

    Column(modifier.fillMaxWidth()) {
        Readout(
            label = when {
                selectedDay == null -> "Spent so far · day $recordedDays"
                selectedDay <= recordedDays -> "By day $selectedDay"
                else -> "Projected by day $selectedDay"
            },
            value = Money.rupees(selectedValue ?: spentSoFar),
            hint = if (selected == null) "Touch to read any day, including the forecast" else null,
            muted = selectedDay != null && selectedDay > recordedDays
        )

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = 16.dp, bottom = 4.dp)
                .pointerInput(monthSeries) {
                    detectTapGestures { off -> selected = indexAt(off.x, size.width, daysInMonth) }
                }
                .pointerInput(monthSeries) {
                    detectHorizontalDragGestures(
                        onDragStart = { off -> selected = indexAt(off.x, size.width, daysInMonth) }
                    ) { change, _ ->
                        selected = indexAt(change.position.x, size.width, daysInMonth)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            fun x(day: Int) = w * (day - 1).coerceAtLeast(0) / (daysInMonth - 1).toFloat()
            fun y(v: Double) = (h - (v / top * h)).toFloat().coerceIn(0f, h)

            drawLine(grid, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

            budget?.takeIf { it > 0 }?.let { b ->
                val by = y(b)
                drawLine(
                    if (willOvershoot) over.copy(alpha = 0.75f) else grid,
                    Offset(0f, by), Offset(w, by), strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                val label = measurer.measure("budget ${Money.short(b)}", axisStyle)
                drawText(label, topLeft = Offset(0f, (by - label.size.height - 3).coerceAtLeast(0f)))
            }

            val actual = Path()
            cumulative.forEachIndexed { i, v ->
                val px = x(i + 1)
                val py = y(v)
                if (i == 0) actual.moveTo(px, py) else actual.lineTo(px, py)
            }
            drawPath(actual, SERIES, style = Stroke(width = 2.dp.toPx()))

            if (recordedDays < daysInMonth) {
                drawPath(
                    Path().apply {
                        moveTo(x(recordedDays), y(spentSoFar))
                        lineTo(x(daysInMonth), y(projectedTotal))
                    },
                    SERIES.copy(alpha = 0.55f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                )
            }

            selectedDay?.let { d ->
                val px = x(d)
                drawLine(crosshair, Offset(px, 0f), Offset(px, h), strokeWidth = 1.dp.toPx())
                selectedValue?.let { marker(px, y(it), surface, emphasised = true) }
            } ?: marker(x(recordedDays), y(spentSoFar), surface)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisLabel("day 1")
            AxisLabel("day $daysInMonth")
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendKey("Spent", dashed = false)
            LegendKey("Projected", dashed = true)
        }
    }
}

// ---------------------------------------------------------------------- pieces

/** The value being pointed at. Sits above the plot so a fingertip cannot cover it. */
@Composable
private fun Readout(label: String, value: String, hint: String?, muted: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Identity by line pattern, so the two states stay distinguishable without a second hue. */
@Composable
private fun LegendKey(label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
            drawLine(
                if (dashed) SERIES.copy(alpha = 0.55f) else SERIES,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Nearest data index to a touch, so the whole chart width is the hit target. */
private fun indexAt(x: Float, width: Int, count: Int): Int {
    if (count <= 1 || width <= 0) return 0
    return ((x / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/**
 * A data point ringed in the surface colour so it stays readable where the line passes beneath.
 * The ring must be the chart's own background, not white, or it punches a hole through dark mode.
 */
private fun DrawScope.marker(cx: Float, cy: Float, surface: Color, emphasised: Boolean = false) {
    val r = if (emphasised) 4.5.dp.toPx() else 3.dp.toPx()
    drawCircle(surface, radius = r + 2.5f, center = Offset(cx, cy))
    drawCircle(SERIES, radius = r, center = Offset(cx, cy))
}

/** A compact proportional bar, used where a full chart would be too much. */
@Composable
fun MiniBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}
