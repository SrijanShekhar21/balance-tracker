package com.dbt.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dbt.tracker.data.DayPoint
import com.dbt.tracker.util.Days
import com.dbt.tracker.util.Money
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

/**
 * Charts are drawn against a single data hue rather than a categorical palette.
 *
 * There is only ever one measure on screen -- money spent -- so a second hue would encode
 * nothing. Where two things must be told apart (recorded spend versus projected), they are
 * separated by line style, which survives colour blindness and greyscale printing in a way
 * a second hue would not.
 *
 * #0D9488 was chosen by running the palette validator rather than by eye: it clears the
 * lightness band, the chroma floor and 3:1 contrast against both the light and the dark
 * surface, which is unusual enough that the same value serves both themes.
 */
private val SERIES = Color(0xFF0D9488)

private val dayLabel = SimpleDateFormat("d MMM", Locale.ENGLISH)

@Composable
private fun axisStyle() = TextStyle(
    fontSize = 10.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun valueStyle() = TextStyle(
    fontSize = 11.sp,
    color = MaterialTheme.colorScheme.onSurface
)

/**
 * Daily spend over the trailing month, with the average as a reference line.
 *
 * A single series, so there is no legend: the panel title names it. Only the peak and the
 * final point are labelled -- a number on every point would be unreadable at thirty days
 * wide and would bury the shape, which is the thing worth seeing.
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

    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val value = valueStyle()
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val surface = MaterialTheme.colorScheme.surface

    val peak = points.maxByOrNull { it.spent }
    val top = max(points.maxOf { it.spent }, average) * 1.18

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 18.dp, bottom = 4.dp)
        ) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = w * i / (points.size - 1).toFloat()
            fun y(v: Double) = (h - (v / top * h)).toFloat().coerceIn(0f, h)

            // Baseline only. A full grid would compete with a line this thin.
            drawLine(grid, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

            if (average > 0) {
                val ay = y(average)
                drawLine(
                    grid,
                    Offset(0f, ay),
                    Offset(w, ay),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                val label = measurer.measure("avg ${Money.short(average)}", axis)
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

            drawPath(
                area,
                Brush.verticalGradient(listOf(SERIES.copy(alpha = 0.22f), SERIES.copy(alpha = 0f)))
            )
            drawPath(line, SERIES, style = Stroke(width = 2.dp.toPx()))

            // Markers carry a surface-coloured ring so they stay legible where the line
            // doubles back under them.
            points.lastOrNull()?.let { last ->
                marker(x(points.size - 1), y(last.spent), surface)
            }
            peak?.takeIf { it.spent > 0 }?.let { pk ->
                val i = points.indexOf(pk)
                marker(x(i), y(pk.spent), surface)
                val label = measurer.measure(Money.short(pk.spent), value)
                drawText(
                    label,
                    topLeft = Offset(
                        (x(i) - label.size.width / 2f).coerceIn(0f, w - label.size.width),
                        (y(pk.spent) - label.size.height - 8).coerceAtLeast(0f)
                    )
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dayLabel.format(points.first().dayStart), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dayLabel.format(points.last().dayStart), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Cumulative spend this month, continued to month end at the current daily rate.
 *
 * Cumulative rather than daily because the question this answers is "will I overshoot", which
 * a daily series cannot show. The projection is the same series in a dashed state, so the
 * legend distinguishes them by pattern rather than by colour.
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

    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val value = valueStyle()
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val surface = MaterialTheme.colorScheme.surface
    val over = negativeColor()

    // Running total, one point per elapsed day.
    val cumulative = buildList {
        var run = 0.0
        monthSeries.forEach { run += it.spent; add(run) }
    }
    val spentSoFar = cumulative.lastOrNull() ?: 0.0
    val top = max(max(projectedTotal, budget ?: 0.0), spentSoFar) * 1.18
    val willOvershoot = budget != null && projectedTotal > budget

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 18.dp, bottom = 4.dp)
        ) {
            val w = size.width
            val h = size.height
            fun x(day: Int) = w * (day - 1).coerceAtLeast(0) / (daysInMonth - 1).toFloat()
            fun y(v: Double) = (h - (v / top * h)).toFloat().coerceIn(0f, h)

            drawLine(grid, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

            budget?.takeIf { it > 0 }?.let { b ->
                val by = y(b)
                drawLine(
                    if (willOvershoot) over.copy(alpha = 0.7f) else grid,
                    Offset(0f, by), Offset(w, by),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                val label = measurer.measure("budget ${Money.short(b)}", axis)
                drawText(label, topLeft = Offset(0f, (by - label.size.height - 3).coerceAtLeast(0f)))
            }

            val actual = Path()
            cumulative.forEachIndexed { i, v ->
                val px = x(i + 1)
                val py = y(v)
                if (i == 0) actual.moveTo(px, py) else actual.lineTo(px, py)
            }
            drawPath(actual, SERIES, style = Stroke(width = 2.dp.toPx()))

            // Projection continues from where the record ends to the end of the month.
            if (monthSeries.size < daysInMonth) {
                val fromX = x(monthSeries.size)
                val fromY = y(spentSoFar)
                drawPath(
                    Path().apply { moveTo(fromX, fromY); lineTo(x(daysInMonth), y(projectedTotal)) },
                    SERIES.copy(alpha = 0.55f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                )
                marker(x(daysInMonth), y(projectedTotal), surface, ring = false)
                val label = measurer.measure(Money.short(projectedTotal), value)
                drawText(
                    label,
                    topLeft = Offset(
                        (x(daysInMonth) - label.size.width).coerceAtLeast(0f),
                        (y(projectedTotal) - label.size.height - 8).coerceAtLeast(0f)
                    )
                )
            }
            marker(x(monthSeries.size), y(spentSoFar), surface)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("day 1", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("day $daysInMonth", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendKey("Spent so far", dashed = false)
            LegendKey("Projected", dashed = true)
        }
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

/**
 * A data point with a surface-coloured ring, so it stays readable where the line passes
 * beneath it. The ring must be the chart's own background rather than white, or it punches
 * a bright hole through the dark theme.
 */
private fun DrawScope.marker(cx: Float, cy: Float, surface: Color, ring: Boolean = true) {
    if (ring) drawCircle(surface, radius = 5.dp.toPx() / 2 + 2f, center = Offset(cx, cy))
    drawCircle(SERIES, radius = 5.dp.toPx() / 2 + 0.5f, center = Offset(cx, cy))
}

/** A compact bar for one category, used where a full chart would be too much. */
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
