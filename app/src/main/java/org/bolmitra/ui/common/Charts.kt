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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.bolmitra.ui.theme.BolmitraColors

/**
 * Canvas-drawn chart primitives from the supplied design. No charting dependency is introduced,
 * which is the right call — a chart library for two shapes would be a dependency to carry forever.
 *
 * ### One correction on the way in: stroke widths were in raw pixels
 *
 * The original passed `Stroke(width = 2f)` and `strokeWidth = 1f`, which `DrawScope` interprets as
 * **pixels, not dp**. On the reference tablet's 2.5× density that renders a 0.8 dp line — hairline
 * to invisible, and it gets worse on denser screens rather than better. Every dimension here now
 * goes through `.dp.toPx()`, so the lines look the same on every device. `DrawScope` implements
 * `Density`, so this is free.
 *
 * ### Accessibility
 *
 * A `Canvas` is opaque to screen readers, so each chart takes a `description` and publishes it via
 * `semantics`. A chart nobody can read is decoration, and these carry measurements.
 */

/** Two smoothed line series over dotted guides. */
@Composable
fun DualLineChart(
    primary: List<Float>,
    secondary: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "chart-reveal",
    )

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
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

        // Dotted horizontal guides.
        val dash = 2.dp.toPx()
        val gap = 6.dp.toPx()
        val guides = 3
        for (g in 1..guides) {
            val y = size.height * g / (guides + 1f)
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = BolmitraColors.HairlineOnPaper,
                    start = Offset(x, y),
                    end = Offset(x + dash, y),
                    strokeWidth = 1.dp.toPx(),
                )
                x += dash + gap
            }
        }

        clipRect(right = size.width * progress) {
            drawPath(
                path = buildPath(secondary),
                color = BolmitraColors.InkMuted,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                path = buildPath(primary),
                color = BolmitraColors.Ink,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Thick progress ring with a centred figure.
 *
 * [value] is a fraction. Values above 1f render as a full ring while the label still reports the
 * true number, which is the behaviour the supplied design used for its 120% case.
 */
@Composable
fun RingProgress(
    value: Float,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 128.dp,
    trackColor: Color = BolmitraColors.GlassStroke,
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
            // Skip the arc entirely at zero: a round cap on a 0° sweep still paints a dot, which
            // would read as "a little bit of progress" when there is none. This app has a real 0%
            // to show — no phrase is verified yet — so the difference matters.
            if (swept > 0f) {
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

/** Legend dot plus text. */
@Composable
fun LegendItem(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Spacer(Modifier.size(7.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = BolmitraColors.InkMuted)
    }
}
