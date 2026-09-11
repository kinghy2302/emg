package com.kinghy2302.emg.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SignalPlot(
    title: String,
    x: DoubleArray,
    y: DoubleArray,
    xMin: Double,
    xMax: Double,
    yMin: Double,
    yMax: Double,
    xLabel: String,
    yLabel: String,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val axisColor = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    val tickPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(
            (tickColor.alpha * 255).toInt(),
            (tickColor.red * 255).toInt(),
            (tickColor.green * 255).toInt(),
            (tickColor.blue * 255).toInt(),
        )
        textSize = with(density) { 10.sp.toPx() }
        isAntiAlias = true
    }

    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val left = 52f
                val right = size.width - 12f
                val top = 10f
                val bottom = size.height - 28f
                val plotW = (right - left).coerceAtLeast(1f)
                val plotH = (bottom - top).coerceAtLeast(1f)
                val xSpan = (xMax - xMin).let { if (it <= 0.0) 1.0 else it }
                val ySpan = (yMax - yMin).let { if (it <= 0.0) 1.0 else it }

                fun xToPx(xv: Double): Float =
                    left + ((xv - xMin) / xSpan).toFloat() * plotW

                fun yToPx(yv: Double): Float =
                    bottom - ((yv - yMin) / ySpan).toFloat() * plotH

                val xTicks = 5
                val yTicks = 4
                for (i in 0..xTicks) {
                    val xv = xMin + xSpan * i / xTicks
                    val px = xToPx(xv)
                    drawLine(gridColor, Offset(px, top), Offset(px, bottom), strokeWidth = 1f)
                    val label = formatTick(xv)
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        px - 10f,
                        bottom + 18f,
                        tickPaint,
                    )
                }
                for (i in 0..yTicks) {
                    val yv = yMin + ySpan * i / yTicks
                    val py = yToPx(yv)
                    drawLine(gridColor, Offset(left, py), Offset(right, py), strokeWidth = 1f)
                    val label = formatTick(yv)
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        2f,
                        py + 4f,
                        tickPaint,
                    )
                }
                drawLine(
                    axisColor,
                    Offset(left, top),
                    Offset(left, bottom),
                    strokeWidth = 2f,
                )
                drawLine(
                    axisColor,
                    Offset(left, bottom),
                    Offset(right, bottom),
                    strokeWidth = 2f,
                )

                if (x.isNotEmpty() && y.isNotEmpty() && x.size == y.size) {
                    val path = Path()
                    path.moveTo(xToPx(x[0]), yToPx(y[0]))
                    for (i in 1 until x.size) {
                        path.lineTo(xToPx(x[i]), yToPx(y[i]))
                    }
                    drawPath(
                        path,
                        color = lineColor,
                        style = Stroke(width = 2.2f, cap = StrokeCap.Round),
                    )
                }

                drawContext.canvas.nativeCanvas.drawText(xLabel, size.width / 2f - 24f, size.height - 2f, tickPaint)
                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.rotate(-90f, 10f, size.height / 2f)
                drawContext.canvas.nativeCanvas.drawText(yLabel, 10f, size.height / 2f, tickPaint)
                drawContext.canvas.nativeCanvas.restore()
            }
        }
    }
}

private fun formatTick(v: Double): String {
    val abs = kotlin.math.abs(v)
    return when {
        abs >= 100 -> "%.0f".format(v)
        abs >= 10 -> "%.1f".format(v)
        abs >= 1 -> "%.2f".format(v)
        else -> "%.2f".format(v)
    }
}
