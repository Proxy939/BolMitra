package org.bolmitra.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Built-in vector icons for BolMitra without external dependencies.
 */
object BolMitraIcons {
    val Mic: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 14f)
                curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
                lineTo(15f, 5f)
                curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
                curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
                lineTo(9f, 11f)
                curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
                close()
                moveTo(17.3f, 11f)
                curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
                curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
                lineTo(5f, 11f)
                curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
                lineTo(11f, 21f)
                lineTo(13f, 21f)
                lineTo(13f, 17.72f)
                curveTo(16.28f, 17.23f, 19f, 14.41f, 19f, 11f)
                lineTo(17.3f, 11f)
                close()
            }
        }.build()
    }

    /**
     * Filled rounded square — the universal "tap again to stop" affordance.
     *
     * The mic button is a toggle, and §4.5's rule that colour is never the only signal applies to
     * state as much as to provenance: a teacher glancing at the tablet mid-lesson must be able to
     * tell "recording" from "idle" by shape, not by noticing an orange ring pulsing.
     */
    val Stop: ImageVector by lazy {
        ImageVector.Builder(
            name = "Stop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 6f)
                lineTo(16f, 6f)
                curveTo(17.1f, 6f, 18f, 6.9f, 18f, 8f)
                lineTo(18f, 16f)
                curveTo(18f, 17.1f, 17.1f, 18f, 16f, 18f)
                lineTo(8f, 18f)
                curveTo(6.9f, 18f, 6f, 17.1f, 6f, 16f)
                lineTo(6f, 8f)
                curveTo(6f, 6.9f, 6.9f, 6f, 8f, 6f)
                close()
            }
        }.build()
    }

    val VolumeUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "VolumeUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 9f)
                lineTo(3f, 15f)
                lineTo(7f, 15f)
                lineTo(12f, 20f)
                lineTo(12f, 4f)
                lineTo(7f, 9f)
                lineTo(3f, 9f)
                close()
                moveTo(16.5f, 12f)
                curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
                lineTo(14f, 16.02f)
                curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
                close()
            }
        }.build()
    }

    val Phone: ImageVector by lazy {
        ImageVector.Builder(
            name = "Phone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                lineTo(8f, 1f)
                curveTo(6.9f, 1f, 6f, 1.9f, 6f, 3f)
                lineTo(6f, 21f)
                curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
                lineTo(16f, 23f)
                curveTo(17.1f, 23f, 18f, 22.1f, 18f, 21f)
                lineTo(18f, 3f)
                curveTo(18f, 1.9f, 17.1f, 1f, 16f, 1f)
                close()
                moveTo(16f, 19f)
                lineTo(8f, 19f)
                lineTo(8f, 5f)
                lineTo(16f, 5f)
                lineTo(16f, 19f)
                close()
            }
        }.build()
    }

    val Book: ImageVector by lazy {
        ImageVector.Builder(
            name = "Book",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 2f)
                lineTo(6f, 2f)
                curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
                lineTo(4f, 20f)
                curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
                lineTo(18f, 22f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                lineTo(20f, 4f)
                curveTo(20f, 2.9f, 19.1f, 2f, 18f, 2f)
                close()
                moveTo(18f, 20f)
                lineTo(6f, 20f)
                lineTo(6f, 4f)
                lineTo(18f, 4f)
                lineTo(18f, 20f)
                close()
            }
        }.build()
    }
}

/**
 * BolMitra stylized flower emblem:
 * Two rich green leaves clasping a central golden petal.
 */
@Composable
fun BolMitraEmblem(modifier: Modifier = Modifier.size(36.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Left leaf (Green)
        val leftLeaf = Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.15f, h * 0.85f, w * 0.05f, h * 0.55f, w * 0.20f, h * 0.35f)
            cubicTo(w * 0.35f, h * 0.40f, w * 0.48f, h * 0.60f, w * 0.5f, h * 0.88f)
            close()
        }
        drawPath(leftLeaf, color = Color(0xFF2EAF3B))

        // Right leaf (Green)
        val rightLeaf = Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.85f, h * 0.85f, w * 0.95f, h * 0.55f, w * 0.80f, h * 0.35f)
            cubicTo(w * 0.65f, h * 0.40f, w * 0.52f, h * 0.60f, w * 0.5f, h * 0.88f)
            close()
        }
        drawPath(rightLeaf, color = Color(0xFF259B32))

        // Center flower bud / petal (Golden yellow/orange)
        val centerBud = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            cubicTo(w * 0.68f, h * 0.30f, w * 0.65f, h * 0.58f, w * 0.5f, h * 0.70f)
            cubicTo(w * 0.35f, h * 0.58f, w * 0.32f, h * 0.30f, w * 0.5f, h * 0.15f)
            close()
        }
        drawPath(centerBud, color = Color(0xFFF5A623))
    }
}

/**
 * Top illustrated banner illustration showing primary school children, sunshine, and motivational banner text.
 */
@Composable
fun KidsBannerArt(
    badgeTextLines: List<String>,
    modifier: Modifier = Modifier.size(width = 240.dp, height = 90.dp),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        // Kids + Sun Canvas
        Canvas(modifier = Modifier.size(width = 130.dp, height = 80.dp)) {
            val w = size.width
            val h = size.height

            // Sun with warm rays
            val sunCenter = Offset(w * 0.92f, h * 0.38f)
            val sunRadius = 14.dp.toPx()
            // Sun rays
            for (i in 0 until 8) {
                val angle = (i * 45.0 * Math.PI / 180.0).toFloat()
                val startR = sunRadius + 3.dp.toPx()
                val endR = sunRadius + 8.dp.toPx()
                drawLine(
                    color = Color(0xFFF59E0B),
                    start = Offset(sunCenter.x + cos(angle) * startR, sunCenter.y + sin(angle) * startR),
                    end = Offset(sunCenter.x + cos(angle) * endR, sunCenter.y + sin(angle) * endR),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(Color(0xFFFBBF24), radius = sunRadius, center = sunCenter)

            // Boy (Left)
            // Head
            val boyHead = Offset(w * 0.36f, h * 0.42f)
            val boyRadius = 16.dp.toPx()
            // Hair
            drawCircle(Color(0xFF261C14), radius = boyRadius * 1.08f, center = Offset(boyHead.x, boyHead.y - 3.dp.toPx()))
            // Face
            drawCircle(Color(0xFFFFCC99), radius = boyRadius, center = boyHead)
            // Eyes
            drawCircle(Color(0xFF261C14), radius = 1.8.dp.toPx(), center = Offset(boyHead.x - 4.5.dp.toPx(), boyHead.y))
            drawCircle(Color(0xFF261C14), radius = 1.8.dp.toPx(), center = Offset(boyHead.x + 4.5.dp.toPx(), boyHead.y))
            // Smile
            drawArc(
                color = Color(0xFFB45309),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(boyHead.x - 4.dp.toPx(), boyHead.y + 1.dp.toPx()),
                size = Size(8.dp.toPx(), 7.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
            )
            // Boy Body (Orange shirt)
            val boyShirt = Path().apply {
                moveTo(boyHead.x - boyRadius * 0.8f, boyHead.y + boyRadius * 0.8f)
                lineTo(boyHead.x - boyRadius * 1.3f, h)
                lineTo(boyHead.x + boyRadius * 1.3f, h)
                lineTo(boyHead.x + boyRadius * 0.8f, boyHead.y + boyRadius * 0.8f)
                close()
            }
            drawPath(boyShirt, color = Color(0xFFF97316))

            // Girl (Right)
            val girlHead = Offset(w * 0.65f, h * 0.45f)
            val girlRadius = 15.dp.toPx()
            // Hair (pigtails / dark hair)
            drawCircle(Color(0xFF1E1611), radius = girlRadius * 1.15f, center = Offset(girlHead.x, girlHead.y - 2.dp.toPx()))
            drawCircle(Color(0xFF1E1611), radius = 7.dp.toPx(), center = Offset(girlHead.x - girlRadius * 0.9f, girlHead.y - 3.dp.toPx()))
            drawCircle(Color(0xFF1E1611), radius = 7.dp.toPx(), center = Offset(girlHead.x + girlRadius * 0.9f, girlHead.y - 3.dp.toPx()))
            // Face
            drawCircle(Color(0xFFFFDBAC), radius = girlRadius, center = girlHead)
            // Eyes
            drawCircle(Color(0xFF1E1611), radius = 1.8.dp.toPx(), center = Offset(girlHead.x - 4.dp.toPx(), girlHead.y))
            drawCircle(Color(0xFF1E1611), radius = 1.8.dp.toPx(), center = Offset(girlHead.x + 4.dp.toPx(), girlHead.y))
            // Smile
            drawArc(
                color = Color(0xFFB45309),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(girlHead.x - 4.dp.toPx(), girlHead.y + 1.dp.toPx()),
                size = Size(8.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
            )
            // Girl Body (Green / yellow dress)
            val girlDress = Path().apply {
                moveTo(girlHead.x - girlRadius * 0.8f, girlHead.y + girlRadius * 0.8f)
                lineTo(girlHead.x - girlRadius * 1.4f, h)
                lineTo(girlHead.x + girlRadius * 1.4f, h)
                lineTo(girlHead.x + girlRadius * 0.8f, girlHead.y + girlRadius * 0.8f)
                close()
            }
            drawPath(girlDress, color = Color(0xFF10B981))

            // Small book between them
            val book = Path().apply {
                moveTo(w * 0.42f, h * 0.72f)
                lineTo(w * 0.58f, h * 0.72f)
                lineTo(w * 0.62f, h * 0.92f)
                lineTo(w * 0.50f, h * 0.88f)
                lineTo(w * 0.38f, h * 0.92f)
                close()
            }
            drawPath(book, color = Color(0xFF3B82F6))
        }

        Spacer(Modifier.width(6.dp))

        // Badge Text ("Different Languages Brighter Futures" etc.)
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            badgeTextLines.forEachIndexed { idx, line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                        lineHeight = 14.sp,
                    ),
                    color = Color(0xFF2E4032),
                )
            }
        }
    }
}

/**
 * Animated sound equalizer waveform lines.
 */
@Composable
fun SoundwaveVisualizer(
    color: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier.size(width = 64.dp, height = 36.dp),
) {
    val infiniteTransition = rememberInfiniteTransition(label = "soundwave")
    val animFactor by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "animWave",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barCount = 7
        val barWidth = 3.dp.toPx()
        val spacing = (w - (barCount * barWidth)) / (barCount - 1)

        val heights = listOf(0.35f, 0.65f, 0.95f, 0.50f, 0.85f, 0.40f, 0.25f)

        for (i in 0 until barCount) {
            val baseH = heights[i] * h
            val actualH = if (isActive) {
                val cycle = ((i * 0.2f + animFactor) % 1.0f)
                h * (0.25f + 0.75f * cycle)
            } else {
                baseH
            }
            val x = i * (barWidth + spacing)
            val y = (h - actualH) / 2f

            drawRoundRect(
                color = color.copy(alpha = if (isActive) 0.85f else 0.45f),
                topLeft = Offset(x, y),
                size = Size(barWidth, actualH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/**
 * Concentric pulsing ring action button for Teacher Mic (Orange) and Student Speaker (Green).
 */
@Composable
fun ConcentricCircleButton(
    icon: ImageVector,
    primaryColor: Color,
    isPulsing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(100.dp),
    /**
     * Spoken label for the action. Required for the mic because it is a toggle: without it
     * TalkBack announces an unlabelled button and gives no way to know whether tapping starts or
     * stops recording.
     */
    contentDescription: String? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier
            .semantics { role = Role.Button }
            .clickable(
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Outer concentric ring 2
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    primaryColor.copy(alpha = if (isPulsing) pulseAlpha * 0.35f else 0.10f),
                    CircleShape,
                ),
        )
        // Outer concentric ring 1
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    primaryColor.copy(alpha = if (isPulsing) pulseAlpha * 0.65f else 0.20f),
                    CircleShape,
                ),
        )
        // Solid core circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(primaryColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Category thumbnail illustrations for the Worksheets grid.
 */
@Composable
fun WorksheetThumbnail(
    categoryIndex: Int,
    modifier: Modifier = Modifier.size(width = 110.dp, height = 74.dp),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (categoryIndex) {
            // 0: Alphabet (अ अनार, आ आम)
            0 -> {
                // Pomegranate (Red circle + top crown)
                val pomCenter = Offset(w * 0.32f, h * 0.55f)
                val pomR = 16.dp.toPx()
                drawCircle(Color(0xFFDC2626), radius = pomR, center = pomCenter)
                drawCircle(Color(0xFFEF4444), radius = pomR * 0.4f, center = Offset(pomCenter.x - 3.dp.toPx(), pomCenter.y - 3.dp.toPx()))

                // Mango (Yellow / Orange curved shape)
                val mangoCenter = Offset(w * 0.68f, h * 0.55f)
                drawOval(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(mangoCenter.x - 14.dp.toPx(), mangoCenter.y - 18.dp.toPx()),
                    size = Size(28.dp.toPx(), 36.dp.toPx()),
                )
                // Small green leaf
                drawOval(
                    color = Color(0xFF16A34A),
                    topLeft = Offset(mangoCenter.x, mangoCenter.y - 22.dp.toPx()),
                    size = Size(10.dp.toPx(), 6.dp.toPx()),
                )
            }
            // 1: Numbers (1 2 3)
            1 -> {
                // Sun
                drawCircle(Color(0xFFFBBF24), radius = 10.dp.toPx(), center = Offset(w * 0.22f, h * 0.55f))
                // Two leaves
                drawOval(Color(0xFF16A34A), topLeft = Offset(w * 0.48f, h * 0.42f), size = Size(8.dp.toPx(), 16.dp.toPx()))
                drawOval(Color(0xFF22C55E), topLeft = Offset(w * 0.54f, h * 0.46f), size = Size(8.dp.toPx(), 16.dp.toPx()))
                // Three mangoes/dots
                drawCircle(Color(0xFFF59E0B), radius = 6.dp.toPx(), center = Offset(w * 0.76f, h * 0.55f))
                drawCircle(Color(0xFFF59E0B), radius = 6.dp.toPx(), center = Offset(w * 0.84f, h * 0.55f))
                drawCircle(Color(0xFFF59E0B), radius = 6.dp.toPx(), center = Offset(w * 0.92f, h * 0.55f))
            }
            // 2: Animals (गाय, हाथी)
            2 -> {
                // Cow outline / body
                drawRoundRect(
                    color = Color(0xFFE2E8F0),
                    topLeft = Offset(w * 0.12f, h * 0.35f),
                    size = Size(36.dp.toPx(), 24.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawCircle(Color(0xFF475569), radius = 4.dp.toPx(), center = Offset(w * 0.24f, h * 0.45f))
                drawCircle(Color(0xFF475569), radius = 3.dp.toPx(), center = Offset(w * 0.36f, h * 0.48f))

                // Elephant (Grey body + trunk)
                drawRoundRect(
                    color = Color(0xFF94A3B8),
                    topLeft = Offset(w * 0.58f, h * 0.30f),
                    size = Size(38.dp.toPx(), 28.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
                drawCircle(Color(0xFF64748B), radius = 7.dp.toPx(), center = Offset(w * 0.62f, h * 0.42f))
            }
            // 3: Colors (लाल, पीला, हरा)
            3 -> {
                drawCircle(Color(0xFFEF4444), radius = 13.dp.toPx(), center = Offset(w * 0.25f, h * 0.52f))
                drawCircle(Color(0xFFFBBF24), radius = 13.dp.toPx(), center = Offset(w * 0.50f, h * 0.52f))
                drawCircle(Color(0xFF22C55E), radius = 13.dp.toPx(), center = Offset(w * 0.75f, h * 0.52f))
            }
            // 4: Fruits (केला, संतरा, आम)
            4 -> {
                // Banana (yellow curved arc)
                drawArc(
                    color = Color(0xFFFBBF24),
                    startAngle = 45f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.25f),
                    size = Size(28.dp.toPx(), 34.dp.toPx()),
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
                )
                // Orange
                drawCircle(Color(0xFFF97316), radius = 12.dp.toPx(), center = Offset(w * 0.50f, h * 0.55f))
                // Mango
                drawOval(Color(0xFFEAB308), topLeft = Offset(w * 0.72f, h * 0.35f), size = Size(20.dp.toPx(), 28.dp.toPx()))
            }
            // 5: My Body (Cartoon face)
            5 -> {
                val faceCenter = Offset(w * 0.50f, h * 0.50f)
                val faceR = 18.dp.toPx()
                // Hair
                drawCircle(Color(0xFF1E293B), radius = faceR * 1.12f, center = Offset(faceCenter.x, faceCenter.y - 3.dp.toPx()))
                // Face
                drawCircle(Color(0xFFFFDBAC), radius = faceR, center = faceCenter)
                // Cheeks
                drawCircle(Color(0xFFFCA5A5), radius = 3.dp.toPx(), center = Offset(faceCenter.x - 9.dp.toPx(), faceCenter.y + 3.dp.toPx()))
                drawCircle(Color(0xFFFCA5A5), radius = 3.dp.toPx(), center = Offset(faceCenter.x + 9.dp.toPx(), faceCenter.y + 3.dp.toPx()))
                // Eyes
                drawCircle(Color(0xFF0F172A), radius = 1.8.dp.toPx(), center = Offset(faceCenter.x - 5.dp.toPx(), faceCenter.y - 1.dp.toPx()))
                drawCircle(Color(0xFF0F172A), radius = 1.8.dp.toPx(), center = Offset(faceCenter.x + 5.dp.toPx(), faceCenter.y - 1.dp.toPx()))
                // Smile
                drawArc(
                    color = Color(0xFFDC2626),
                    startAngle = 10f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(faceCenter.x - 4.dp.toPx(), faceCenter.y + 4.dp.toPx()),
                    size = Size(8.dp.toPx(), 5.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            // 6: Classroom Objects (Book, pencil, chair)
            6 -> {
                // Red Book
                drawRoundRect(
                    color = Color(0xFFDC2626),
                    topLeft = Offset(w * 0.15f, h * 0.40f),
                    size = Size(26.dp.toPx(), 20.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                // Yellow Pencil
                drawRoundRect(
                    color = Color(0xFFFBBF24),
                    topLeft = Offset(w * 0.50f, h * 0.35f),
                    size = Size(6.dp.toPx(), 26.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
                // Brown Chair
                drawRoundRect(
                    color = Color(0xFFB45309),
                    topLeft = Offset(w * 0.74f, h * 0.38f),
                    size = Size(18.dp.toPx(), 22.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            // 7: Tracing Practice (अ आ dotted)
            7 -> {
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(w * 0.20f, h * 0.35f),
                    end = Offset(w * 0.42f, h * 0.35f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawArc(
                    color = Color(0xFF94A3B8),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.22f, h * 0.38f),
                    size = Size(14.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(w * 0.60f, h * 0.35f),
                    end = Offset(w * 0.88f, h * 0.35f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // 8: Short Stories (Landscape)
            else -> {
                // Sky / sun
                drawCircle(Color(0xFFFBBF24), radius = 10.dp.toPx(), center = Offset(w * 0.25f, h * 0.35f))
                // Rolling hills
                drawOval(
                    color = Color(0xFF10B981),
                    topLeft = Offset(w * 0.05f, h * 0.45f),
                    size = Size(w * 0.6f, h * 0.6f),
                )
                drawOval(
                    color = Color(0xFF059669),
                    topLeft = Offset(w * 0.45f, h * 0.40f),
                    size = Size(w * 0.6f, h * 0.7f),
                )
            }
        }
    }
}

/**
 * Embedded preview canvas for the worksheet drawer.
 */
@Composable
fun WorksheetPreviewSheet(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: BolMitra + Class 1 - Worksheet 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BolMitraEmblem(Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "BolMitra",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        ),
                    )
                }
                Text(
                    "Class 1 · Worksheet 1",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color(0xFF64748B),
                )
            }

            Spacer(Modifier.height(6.dp))

            // Banner "अक्षर पहचान"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFDCFCE7), RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "अक्षर पहचान",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534),
                    ),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Two columns: अ with pomegranate and आ with mango
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Column 1: अ
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "अ",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp,
                            color = Color(0xFF0F172A),
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                    // Pomegranate
                    Canvas(Modifier.size(38.dp)) {
                        drawCircle(Color(0xFFDC2626), radius = 16.dp.toPx())
                        drawCircle(Color(0xFFEF4444), radius = 6.dp.toPx(), center = Offset(size.width * 0.4f, size.height * 0.4f))
                    }
                    Text(
                        "अ से अनार",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        ),
                    )
                    Text("ᱰᱟᱲᱤᱢ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color(0xFF64748B)))
                }

                // Column 2: आ
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "आ",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp,
                            color = Color(0xFF0F172A),
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                    // Mango
                    Canvas(Modifier.size(38.dp)) {
                        drawOval(
                            Color(0xFFF59E0B),
                            topLeft = Offset(size.width * 0.2f, size.height * 0.1f),
                            size = Size(24.dp.toPx(), 30.dp.toPx()),
                        )
                        drawOval(
                            Color(0xFF16A34A),
                            topLeft = Offset(size.width * 0.5f, size.height * 0.05f),
                            size = Size(10.dp.toPx(), 6.dp.toPx()),
                        )
                    }
                    Text(
                        "आ से आम",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        ),
                    )
                    Text("ᱩᱞ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color(0xFF64748B)))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tracing boxes: "लिखो और सीखो"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "लिखो और सीखो",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569),
                    ),
                )
            }

            Spacer(Modifier.height(4.dp))

            // Dotted tracing boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("अ", "अ", "अ", "आ", "आ", "आ").forEach { letter ->
                    Box(
                        modifier = Modifier
                            .size(width = 30.dp, height = 36.dp)
                            .border(0.8.dp, Color(0xFFCBD5E1), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            letter,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Light,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Footer tagline
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "बोलो · सिखाओ · साथ बढ़ो",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = Color(0xFF64748B),
                    ),
                )
                Spacer(Modifier.width(4.dp))
                BolMitraEmblem(Modifier.size(10.dp))
            }
        }
    }
}
