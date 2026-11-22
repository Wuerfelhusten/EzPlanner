package com.moddingforge.ezplanner.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.domain.SankeyData
import com.moddingforge.ezplanner.domain.SankeyRole
import com.moddingforge.ezplanner.ui.theme.Ez
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val TypePalette = listOf(0xFFFFB454, 0xFFB18CFF, 0xFFFF6B6B, 0xFF4DD0E1, 0xFFFF7A9A, 0xFFA5D66F, 0xFFFFD54F)

/**
 * Sankey diagram: columns of nodes (height = amount) connected by bands (width = amount).
 * Labels sit right of each node. Wider than the screen when there are many columns, then it
 * scrolls horizontally.
 */
@Composable
fun SankeyChart(
    data: SankeyData,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    /** Applied to the full-size drawing (inside the scroll), e.g. to record it for sharing. */
    captureModifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val money = Ez.money
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val pill = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    val totalColor = MaterialTheme.colorScheme.primary

    val columns = (data.nodes.maxOfOrNull { it.column } ?: 0) + 1
    val typeKeys = data.nodes.filter { it.role == SankeyRole.EXPENSE_TYPE }.map { it.key }
    fun colorOf(key: String): Color {
        val n = data.nodes.first { it.key == key }
        n.color?.let { return Color(it.toInt()) }
        return when (n.role) {
            SankeyRole.INCOME -> money.income
            SankeyRole.OTHER_INCOME -> money.income.copy(alpha = 0.7f)
            SankeyRole.FROM_POTS, SankeyRole.SAVING -> money.transfer
            SankeyRole.DEFICIT -> money.expense
            SankeyRole.TOTAL -> totalColor
            SankeyRole.REST -> Color(0xFF8FA3B8)
            SankeyRole.EXPENSE_TYPE -> Color(TypePalette[typeKeys.indexOf(key).coerceAtLeast(0) % TypePalette.size].toInt())
            SankeyRole.CATEGORY -> muted
        }
    }

    BoxWithConstraints(modifier) {
        val nodeW = 12.dp
        val labelW = 112.dp
        val minGap = 128.dp
        val fitGap = if (columns > 1) (maxWidth - nodeW - labelW) / (columns - 1) else 0.dp
        val colGap = if (scrollable) maxOf(minGap, fitGap) else fitGap
        val width = colGap * (columns - 1) + nodeW + labelW
        val canvasModifier = Modifier.width(width).height(360.dp)
        val scrollModifier = if (scrollable && width > maxWidth) Modifier.horizontalScroll(rememberScrollState()) else Modifier

        Canvas(scrollModifier.then(captureModifier).then(canvasModifier)) {
            val gap = 10.dp.toPx()
            val nw = nodeW.toPx()
            val cg = colGap.toPx()
            val h = size.height
            val byColumn = (0 until columns).map { c -> data.nodes.filter { it.column == c } }
            val values = data.nodes.associate { it.key to data.value(it.key).toDouble() }
            val scale = byColumn.filter { it.isNotEmpty() }.minOf { col ->
                (h - gap * (col.size - 1)) / col.sumOf { values.getValue(it.key) }.coerceAtLeast(1.0)
            }

            // Node rectangles, each column centered vertically.
            val top = mutableMapOf<String, Float>()
            val height = mutableMapOf<String, Float>()
            byColumn.forEach { col ->
                val used = col.sumOf { values.getValue(it.key) * scale } + gap * (col.size - 1)
                var y = ((h - used) / 2).toFloat()
                col.forEach { n ->
                    val nh = (values.getValue(n.key) * scale).toFloat().coerceAtLeast(2f)
                    top[n.key] = y
                    height[n.key] = nh
                    y += nh + gap
                }
            }
            val column = data.nodes.associate { it.key to it.column }
            fun x(key: String) = column.getValue(key) * cg

            // Bands: ordered by the position of the other end to avoid crossings.
            val outOffset = mutableMapOf<String, Float>()
            val inOffset = mutableMapOf<String, Float>()
            val ordered = data.links.sortedWith(compareBy({ column.getValue(it.from) }, { top.getValue(it.from) }, { top.getValue(it.to) }))
            ordered.forEach { l ->
                val bh = (l.value * scale).toFloat()
                val sx = x(l.from) + nw
                val sy = top.getValue(l.from) + (outOffset[l.from] ?: 0f)
                val tx = x(l.to)
                val ty = top.getValue(l.to) + (inOffset[l.to] ?: 0f)
                outOffset[l.from] = (outOffset[l.from] ?: 0f) + bh
                inOffset[l.to] = (inOffset[l.to] ?: 0f) + bh
                val mx = (sx + tx) / 2
                val path = Path().apply {
                    moveTo(sx, sy)
                    cubicTo(mx, sy, mx, ty, tx, ty)
                    lineTo(tx, ty + bh)
                    cubicTo(mx, ty + bh, mx, sy + bh, sx, sy + bh)
                    close()
                }
                val c = if (column.getValue(l.from) == 0) colorOf(l.from) else colorOf(l.to)
                drawPath(path, c.copy(alpha = 0.38f))
            }

            data.nodes.forEach { n ->
                drawRoundRect(
                    colorOf(n.key),
                    topLeft = Offset(x(n.key), top.getValue(n.key)),
                    size = Size(nw, height.getValue(n.key)),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }

            // Labels with a pill background so they stay readable over the bands.
            val maxLabel = (if (columns > 1) cg - nw - 8.dp.toPx() else labelW.toPx()).coerceAtMost(labelW.toPx() + 40.dp.toPx())
            data.nodes.forEach { n ->
                val nameLayout = measurer.measure(
                    n.label,
                    TextStyle(color = onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxLabel.toInt().coerceAtLeast(1)),
                )
                val amountLayout = measurer.measure(
                    Money.format(values.getValue(n.key).toLong()),
                    TextStyle(color = muted, fontSize = 10.sp),
                    maxLines = 1,
                )
                val w = maxOf(nameLayout.size.width, amountLayout.size.width).toFloat()
                val th = (nameLayout.size.height + amountLayout.size.height).toFloat()
                val lx = x(n.key) + nw + 4.dp.toPx()
                val ly = (top.getValue(n.key) + height.getValue(n.key) / 2 - th / 2).coerceIn(0f, h - th)
                val p = 3.dp.toPx()
                drawRoundRect(pill, Offset(lx - p, ly - p / 2), Size(w + 2 * p, th + p), CornerRadius(6f, 6f))
                drawText(nameLayout, topLeft = Offset(lx, ly))
                drawText(amountLayout, topLeft = Offset(lx, ly + nameLayout.size.height))
            }
        }
    }
}

/** Saves [image] as PNG in the cache and opens the share sheet. */
suspend fun shareImage(context: Context, image: ImageBitmap, fileName: String, title: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, fileName).also { f -> f.outputStream().use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // ClipData carries the read grant to the share sheet itself (preview) and to the target app.
    send.clipData = android.content.ClipData.newRawUri(title, uri)
    context.startActivity(Intent.createChooser(send, title))
}
