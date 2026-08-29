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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * Each day's spend as a bar, with the trailing seven-day mean drawn over it.
 *
 * The two are told apart by form -- filled bar against stroked line -- rather than by a second
 * hue, so the pairing survives colour blindness and greyscale, and no second colour has to earn
 * its place on the chart.
 *
 * The mean is drawn as a moving line rather than one flat reference, because a flat average
 * answers "how does today compare with the whole month" while a moving one answers "how does
 * today compare with how I have been living lately", which is the question worth asking.
 */
@Composable
fun SpendTrendChart(
    points: List<DayPoint>,
    rolling: List<DayPoint>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        EmptyNote("Not enough history yet to draw a trend.")
        return
    }

    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val surface = MaterialTheme.colorScheme.surface
    val crosshair = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val above = negativeColor()

    val top = max(points.maxOf { it.spent }, rolling.maxOfOrNull { it.spent } ?: 0.0) * 1.15
    val i = selected ?: points.lastIndex
    val day = points[i]
    val mean = rolling.getOrNull(i)?.spent ?: 0.0
    val delta = if (mean > 0) (day.spent - mean) / mean * 100 else null

    Column(modifier.fillMaxWidth()) {
        Readout(
            label = if (selected == null) "Latest · ${dayLong.format(day.dayStart)}"
            else dayLong.format(day.dayStart),
            value = Money.rupees(day.spent),
            hint = when {
                delta == null -> null
                day.spent == 0.0 -> "no spending"
                delta > 5 -> "${delta.roundToInt()}% over your 7-day rate"
                delta < -5 -> "${(-delta).roundToInt()}% under your 7-day rate"
                else -> "on your 7-day rate"
            },
            muted = false
        )

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = 16.dp, bottom = 4.dp)
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
            val slot = w / points.size
            // A 2px gap between bars, so adjacent days stay countable rather than reading as
            // one block of colour.
            val barW = (slot - 2.dp.toPx()).coerceAtLeast(1.5.dp.toPx())
            fun cx(idx: Int) = slot * idx + slot / 2
            fun y(v: Double) = (h - (v / top * h)).toFloat().coerceIn(0f, h)

            drawLine(grid, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

            points.forEachIndexed { idx, p ->
                if (p.spent <= 0) return@forEachIndexed
                val over = (rolling.getOrNull(idx)?.spent ?: 0.0).let { it > 0 && p.spent > it }
                val py = y(p.spent)
                drawRoundRect(
                    color = if (over) above.copy(alpha = 0.45f) else SERIES.copy(alpha = 0.45f),
                    topLeft = Offset(cx(idx) - barW / 2, py),
                    size = Size(barW, h - py),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            if (rolling.size == points.size) {
                val line = Path()
                rolling.forEachIndexed { idx, p ->
                    val px = cx(idx)
                    val py = y(p.spent)
                    if (idx == 0) line.moveTo(px, py) else line.lineTo(px, py)
                }
                drawPath(line, SERIES, style = Stroke(width = 2.dp.toPx()))
            }

            selected?.let { idx ->
                val px = cx(idx)
                drawLine(crosshair, Offset(px, 0f), Offset(px, h), strokeWidth = 1.dp.toPx())
                rolling.getOrNull(idx)?.let { marker(px, y(it.spent), surface, emphasised = true) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisLabel(dayShort.format(points.first().dayStart))
            AxisLabel(dayShort.format(points.last().dayStart))
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendSwatch("Spent that day", bar = true, color = SERIES)
            LegendSwatch("Over your rate", bar = true, color = above)
            LegendKey("7-day average", dashed = false)
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

/** A filled chip for bar series, so form carries identity alongside the line keys. */
@Composable
private fun LegendSwatch(label: String, bar: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 10.dp, height = 10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = if (bar) 0.45f else 1f))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
