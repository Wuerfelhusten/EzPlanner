package com.moddingforge.ezplanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moddingforge.ezplanner.domain.Money

data class Slice(val label: String, val cents: Long, val color: Color)

/** Donut chart with the total in the middle and a legend below. */
@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.cents }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                val strokeWidth = size.minDimension * 0.16f
                val inset = strokeWidth / 2
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokeWidth))
                var start = -90f
                val gap = if (slices.size > 1) 1.5f else 0f
                slices.forEach { s ->
                    val sweep = 360f * s.cents / total
                    if (sweep > gap) {
                        drawArc(s.color, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(strokeWidth))
                    }
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ausgaben", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(slices.sumOf { it.cents }), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.padding(top = 12.dp)) {
            slices.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(s.color)
                    HSpace(10)
                    Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${(s.cents * 100 / total)} %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HSpace(12)
                    Text(Money.format(s.cents), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

data class BarGroup(val label: String, val income: Long, val expense: Long, val highlighted: Boolean = false)

/** Paired bars (income / expense) per month. */
@Composable
fun IncomeExpenseBars(
    groups: List<BarGroup>,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
) {
    val max = groups.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1) ?: 1
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            val n = groups.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = slot * 0.28f
            for (i in 0..3) {
                val y = size.height * i / 3
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            groups.forEachIndexed { i, g ->
                val center = slot * i + slot / 2
                val alpha = if (g.highlighted) 1f else 0.55f
                val hi = size.height * g.income / max
                val he = size.height * g.expense / max
                drawRoundRect(
                    incomeColor.copy(alpha = alpha),
                    topLeft = Offset(center - barWidth - 2f, size.height - hi),
                    size = Size(barWidth, hi),
                    cornerRadius = CornerRadius(6f, 6f),
                )
                drawRoundRect(
                    expenseColor.copy(alpha = alpha),
                    topLeft = Offset(center + 2f, size.height - he),
                    size = Size(barWidth, he),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            groups.forEach { g ->
                Text(
                    g.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (g.highlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(incomeColor)
            HSpace(6)
            Text("Einkommen", style = MaterialTheme.typography.bodySmall)
            HSpace(16)
            ColorDot(expenseColor)
            HSpace(6)
            Text("Ausgaben", style = MaterialTheme.typography.bodySmall)
        }
    }
}
