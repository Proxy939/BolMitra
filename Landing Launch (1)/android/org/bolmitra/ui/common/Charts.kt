package org.bolmitra.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.bolmitra.ui.theme.BolmitraColors

/**
 * Two smoothed line series over dotted guides, matching the weekly progress
 * panel. Pure Canvas — no charting dependency is introduced.
 */
@Composable
fun DualLineChart(
    primary: List<Float>,
    secondary: List<Float>,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "chart-reveal",
    )

    Canvas(modifier = modifier) {
        val all = primary + secondary
        val max = all.maxOrNull() ?: 1f
        val min = all.minOrNull() ?: 0f
        val span = (max - min).takeIf { it > 0f } ?: 1f

        fun buildPath(values: List<Float>): Path {
            val p = Path()
            if (values.size < 2) return p
            val stepX = size.width / (values.size - 1)
            val points = values.mapIndexed { i, v ->
                Offset(
                    x = i * stepX,
                    y = size.height * 0.95f - ((v - min) / span) * size.height * 0.9f,
                )
            }
            p.moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val a = points[i]
                val b = points[i + 1]
                val midX = (a.x + b.x) / 2f
                p.cubicTo(midX, a.y, midX, b.y, b.x, b.y)
            }
            return p
        }

        // dotted horizontal guides
        val guides = 3
        for (g in 1..guides) {
            val y = size.height * g / (guides + 1f)
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = BolmitraColors.HairlineOnPaper,
                    start = Offset(x, y),
                    end = Offset(x + 3f, y),
                    strokeWidth = 1f,
                )
                x += 9f
            }
        }

        clipRect(right = size.width * progress) {
            drawPath(
                path = buildPath(secondary),
                color = BolmitraColors.InkMuted,
                style = Stroke(width = 2f, cap = StrokeCap.Round),
            )
            drawPath(
                path = buildPath(primary),
                color = BolmitraColors.Ink,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Month-progress ring: thick arc with a rounded cap and a centred figure.
 * [value] is a fraction; values above 1f render as a full ring (the reference
 * shows 120%), while the caption still reports the true number.
 */
@Composable
fun RingProgress(
    value: Float,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 128.dp,
    trackColor: Color = BolmitraColors.HairlineOnPaper,
    arcColor: Color = BolmitraColors.Ink,
) {
    val swept by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "ring-sweep",
    )

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.11f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * swept,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = BolmitraColors.Ink)
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = BolmitraColors.InkMuted,
            )
        }
    }
}

/** Tiny legend dot + text pair. */
@Composable
fun LegendItem(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(7.dp)) { drawCircle(color) }
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = BolmitraColors.InkMuted)
    }
}
