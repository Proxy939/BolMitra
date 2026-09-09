package org.bolmitra.ui

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier
import org.bolmitra.ui.common.BolMitraEmblem
import org.bolmitra.ui.common.BolMitraIcons
import kotlin.math.cos
import kotlin.math.sin

/**
 * Redesigned Landing Screen — Exact pixel replica of the reference design.
 *
 * Visual hierarchy:
 *  - Deep vibrant azure sky gradient with realistic puffy white cumulus clouds along edges & horizon.
 *  - Top nav: BolMitra emblem + title + Hindi tagline left, "HOME  FEATURES  LANGUAGES  IMPACT  ABOUT" right.
 *  - Hero: "Same Classroom." (White) / "Many Languages." (Cyan) / "A Brighter Tomorrow." (White + Amber)
 *  - Subtitle: 3-line mission statement.
 *  - CTA: Chartreuse "GET STARTED ↗" pill with circular arrow badge.
 *  - 3D Concave Panorama Arc of 7 Feature Cards (Worksheets, Live Class, Children Photo,
 *    Hindi→Mundari, Mother Tongue, 3 Languages, Offline Ready).
 *  - Social proof: "Trusted by 1,000+ schools for a brighter Bharat" + 5 gold stars.
 */
@Composable
fun LandingScreen(
    spec: DeviceSpec,
    tier: DeviceTier,
    onStart: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0978C9), // Deep rich azure blue
                        Color(0xFF158EE0),
                        Color(0xFF34A6F0),
                        Color(0xFF5ABEF7), // Soft horizon sky
                    )
                )
            )
    ) {
        // Photorealistic fluffy cumulus clouds on left, right, and bottom horizon
        CloudsBackdrop(modifier = Modifier.fillMaxSize())

        // Main content column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Navigation Bar
            TopNavBar(
                onNavigate = onStart,
                onAbout = onDiagnostics,
            )

            Spacer(Modifier.height(10.dp))

            // Hero Section: Headlines, Subtitle, CTA Button
            HeroSection(onGetStarted = onStart)

            Spacer(Modifier.height(18.dp))

            // 3D Concave Curved Panorama Arc of 7 Feature Cards
            FloatingCardsArc(onCardClick = onStart)

            Spacer(Modifier.height(16.dp))

            // Trust Proof & 5 Golden Stars
            TrustRatingStrip()

            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Top navigation bar: Logo on left, uppercase nav links on right.
 */
@Composable
private fun TopNavBar(
    onNavigate: () -> Unit,
    onAbout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Logo + Tagline
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onNavigate),
        ) {
            BolMitraEmblem(modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "BolMitra",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )
                Text(
                    text = "बोले · सिखाओ · साथ बढ़ो",
                    color = Color(0xFFE0F2FE),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                )
            }
        }

        // Nav Links: HOME · FEATURES · LANGUAGES · IMPACT · ABOUT
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val navItems = listOf(
                "HOME" to onNavigate,
                "FEATURES" to onNavigate,
                "LANGUAGES" to onNavigate,
                "IMPACT" to onNavigate,
                "ABOUT" to onAbout,
            )
            navItems.forEach { (label, action) ->
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.3.sp,
                    modifier = Modifier
                        .clickable(onClick = action)
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                )
            }
        }
    }
}

/**
 * Centered Hero Section with exact typography, color accents, and chartreuse CTA button.
 */
@Composable
private fun HeroSection(onGetStarted: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        // Line 1: "Same Classroom." (Pure White)
        Text(
            text = "Same Classroom.",
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(2.dp))

        // Line 2: "Many Languages." (Cyan / Sky Blue Accent)
        Text(
            text = "Many Languages.",
            color = Color(0xFF67C3F3), // Bright vivid cyan sky blue
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(2.dp))

        // Line 3: "A Brighter Tomorrow." (White + Amber)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "A Brighter ",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = "Tomorrow.",
                color = Color(0xFFF59E0B), // Warm glowing amber orange
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Subtitle paragraph
        Text(
            text = "BolMitra empowers Hindi-medium teachers to deliver mother-tongue-based\neducation in Ho, Mundari and Santali — with AI-powered translation,\naudio and worksheets, all offline on low-cost tablets.",
            color = Color.White.copy(alpha = 0.93f),
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 660.dp),
        )

        Spacer(Modifier.height(16.dp))

        // Chartreuse "GET STARTED ↗" CTA Button
        Row(
            modifier = Modifier
                .shadow(elevation = 12.dp, shape = CircleShape, spotColor = Color(0x550284C7))
                .clip(CircleShape)
                .background(Color(0xFFD4F648)) // Vibrant chartreuse lime
                .clickable(onClick = onGetStarted)
                .padding(start = 22.dp, top = 6.dp, end = 7.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "GET STARTED",
                color = Color(0xFF18181B),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.width(14.dp))
            // Circular black arrow badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF111827), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Diagonal Arrow ↗
                Canvas(modifier = Modifier.size(15.dp)) {
                    val w = size.width
                    val h = size.height
                    val strokeW = 2.2.dp.toPx()
                    val c = Color.White
                    drawLine(c, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.78f, h * 0.22f), strokeW, StrokeCap.Round)
                    drawLine(c, Offset(w * 0.40f, h * 0.22f), Offset(w * 0.78f, h * 0.22f), strokeW, StrokeCap.Round)
                    drawLine(c, Offset(w * 0.78f, h * 0.60f), Offset(w * 0.78f, h * 0.22f), strokeW, StrokeCap.Round)
                }
            }
        }
    }
}

/**
 * Curved 3D Concave Panorama Arc of 7 Feature Cards.
 * Implements cylindrical rotationY perspective and gentle translationY arc.
 */
@Composable
private fun FloatingCardsArc(onCardClick: () -> Unit) {
    val density = LocalDensity.current.density

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Card 1: Worksheets (rotationY = 22f, rotationZ = -5f)
        CardWorksheets(
            modifier = Modifier
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationY = 20f
                    rotationZ = -5f
                    translationY = 14.dp.toPx()
                }
                .clickable(onClick = onCardClick)
        )

        Spacer(Modifier.width(8.dp))

        // Card 2: Live Class (rotationY = 12f, rotationZ = -2.5f)
        CardLiveClass(
            modifier = Modifier
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationY = 12f
                    rotationZ = -2.5f
                    translationY = 5.dp.toPx()
                }
                .clickable(onClick = onCardClick)
        )

        Spacer(Modifier.width(8.dp))

        // Card 3: Children Photo Card (rotationY = 5f, rotationZ = -0.8f)
        CardChildrenPhoto(
            modifier = Modifier
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationY = 5f
                    rotationZ = -0.8f
                    translationY = 0f
                }
                .clickable(onClick = onCardClick)
        )

        Spacer(Modifier.width(8.dp))

        // Card 4: Hindi -> Mundari (Centerpiece, flat, elevated)
        CardHindiMundari(
            modifier = Modifier
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationY = 0f
                    rotationZ = 0f
                    translationY = -6.dp.toPx()
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
                .clickable(onClick = onCardClick)
        )

        Spacer(Modifier.width(8.dp))

        // Card 5: Mother Tongue (rotationY = -5f, rotationZ = 0.8f)
        CardMotherTongue(
            modifier = Modifier
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationY = -5f
                    rotationZ = 0.8f
                    translationY = 0f
                }
                .clickable(onClick = onCardClick)
        )

        Spacer(Modifier.width(8.dp))

        // Card 6: 3 Languages (rotationY = -12f, rotationZ = 2.5f)
        CardThreeLanguages(
            modifier = Modifier
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationY = -12f
                    rotationZ = 2.5f
                    translationY = 5.dp.toPx()
                }
                .clickable(onClick = onCardClick)
        )

        Spacer(Modifier.width(8.dp))

        // Card 7: Offline Ready (rotationY = -20f, rotationZ = 5f)
        CardOfflineReady(
            modifier = Modifier
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationY = -20f
                    rotationZ = 5f
                    translationY = 14.dp.toPx()
                }
                .clickable(onClick = onCardClick)
        )
    }
}

/**
 * Card 1: Worksheets with pomegranate/apple "अ से अनार", mango "आ से आम", and green grass blades.
 */
@Composable
private fun CardWorksheets(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 136.dp, height = 172.dp)
            .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color(0x400284C7))
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .padding(9.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Worksheets",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                // Left item: अ pomegranate
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "अ",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                    )
                    Spacer(Modifier.height(3.dp))
                    Canvas(modifier = Modifier.size(32.dp)) {
                        val w = size.width
                        val h = size.height
                        // Red fruit
                        drawCircle(Color(0xFFDC2626), radius = 12.dp.toPx(), center = Offset(w * 0.5f, h * 0.55f))
                        drawCircle(Color(0xFFEF4444), radius = 5.dp.toPx(), center = Offset(w * 0.42f, h * 0.46f))
                        // Stem / crown
                        drawCircle(Color(0xFF15803D), radius = 2.5.dp.toPx(), center = Offset(w * 0.5f, h * 0.18f))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "अ से अनार",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                    )
                }

                // Right item: आ mango
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "आ",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                    )
                    Spacer(Modifier.height(3.dp))
                    Canvas(modifier = Modifier.size(32.dp)) {
                        val w = size.width
                        val h = size.height
                        // Mango body
                        drawOval(
                            color = Color(0xFFF59E0B),
                            topLeft = Offset(w * 0.20f, h * 0.22f),
                            size = Size(w * 0.58f, h * 0.68f),
                        )
                        // Green leaf
                        drawOval(
                            color = Color(0xFF16A34A),
                            topLeft = Offset(w * 0.45f, h * 0.10f),
                            size = Size(w * 0.32f, h * 0.18f),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "आ से आम",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Decorative green grass blades along bottom edge
            Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                for (i in 0 until 8) {
                    val x = i * (size.width / 8f) + 4.dp.toPx()
                    drawLine(
                        color = Color(0xFF22C55E),
                        start = Offset(x, size.height),
                        end = Offset(x + (if (i % 2 == 0) 3.dp.toPx() else -3.dp.toPx()), 1.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/**
 * Card 2: Live Class with mic badge, "Speak in Hindi / Hear in Mundari", and golden waveform.
 */
@Composable
private fun CardLiveClass(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 136.dp, height = 172.dp)
            .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color(0x400284C7))
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
            .padding(11.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color(0xFF16A34A), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = BolMitraIcons.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Live Class",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Speak in Hindi\nHear in Mundari",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF16A34A),
                lineHeight = 15.sp,
            )

            Spacer(Modifier.weight(1f))

            // Golden audio waveform
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                val w = size.width
                val h = size.height
                val barCount = 18
                val barW = 2.5.dp.toPx()
                val step = (w - barW) / (barCount - 1)
                val waveRatios = listOf(
                    0.25f, 0.40f, 0.65f, 0.90f, 0.50f,
                    0.75f, 1.00f, 0.85f, 0.45f, 0.70f,
                    0.95f, 0.60f, 0.80f, 0.50f, 0.70f,
                    0.40f, 0.60f, 0.25f
                )

                for (i in 0 until barCount) {
                    val barH = waveRatios[i] * h
                    val x = i * step
                    val y = (h - barH) / 2f
                    drawRoundRect(
                        color = Color(0xFFF59E0B),
                        topLeft = Offset(x, y),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                }
            }
        }
    }
}

/**
 * Card 3: Indian School Children illustrated photo card with "Every child understands 💚".
 */
@Composable
private fun CardChildrenPhoto(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 144.dp, height = 172.dp)
            .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color(0x400284C7))
            .background(Color(0xFFFEF3C7), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        // Detailed classroom children vector art
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Warm classroom background
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1), Color(0xFFFFFBEB))
                )
            )

            // Blackboard in background
            drawRoundRect(
                color = Color(0xFF1E3A2B),
                topLeft = Offset(w * 0.08f, h * 0.06f),
                size = Size(w * 0.84f, h * 0.42f),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            // Chalk lines
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(w * 0.18f, h * 0.18f),
                end = Offset(w * 0.45f, h * 0.18f),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(w * 0.18f, h * 0.26f),
                end = Offset(w * 0.70f, h * 0.26f),
                strokeWidth = 1.5.dp.toPx(),
            )

            // Child 1 (Center Front - smiling child with school uniform)
            val c1X = w * 0.50f
            val c1Y = h * 0.58f
            val headR1 = 17.dp.toPx()
            drawCircle(Color(0xFF1A1A1A), radius = headR1 * 1.12f, center = Offset(c1X, c1Y - 4.dp.toPx()))
            drawCircle(Color(0xFFFFCC99), radius = headR1, center = Offset(c1X, c1Y))
            drawCircle(Color(0xFF1A1A1A), radius = 2.dp.toPx(), center = Offset(c1X - 4.5.dp.toPx(), c1Y - 1.dp.toPx()))
            drawCircle(Color(0xFF1A1A1A), radius = 2.dp.toPx(), center = Offset(c1X + 4.5.dp.toPx(), c1Y - 1.dp.toPx()))
            // Big warm smile
            drawArc(
                color = Color(0xFF9A3412),
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(c1X - 5.dp.toPx(), c1Y + 3.dp.toPx()),
                size = Size(10.dp.toPx(), 7.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            // School uniform shirt
            val shirt1 = Path().apply {
                moveTo(c1X - headR1 * 0.9f, c1Y + headR1 * 0.8f)
                lineTo(c1X - headR1 * 1.5f, h)
                lineTo(c1X + headR1 * 1.5f, h)
                lineTo(c1X + headR1 * 0.9f, c1Y + headR1 * 0.8f)
                close()
            }
            drawPath(shirt1, color = Color(0xFF2563EB))
            drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(c1X, c1Y + headR1 * 0.9f))

            // Child 2 (Left - smiling girl)
            val c2X = w * 0.20f
            val c2Y = h * 0.57f
            val headR2 = 14.dp.toPx()
            drawCircle(Color(0xFF1A1A1A), radius = headR2 * 1.15f, center = Offset(c2X, c2Y - 3.dp.toPx()))
            drawCircle(Color(0xFFFFDBAC), radius = headR2, center = Offset(c2X, c2Y))
            drawCircle(Color(0xFF1A1A1A), radius = 1.8.dp.toPx(), center = Offset(c2X - 3.8.dp.toPx(), c2Y - 1.dp.toPx()))
            drawCircle(Color(0xFF1A1A1A), radius = 1.8.dp.toPx(), center = Offset(c2X + 3.8.dp.toPx(), c2Y - 1.dp.toPx()))
            drawArc(
                color = Color(0xFF9A3412),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(c2X - 4.dp.toPx(), c2Y + 2.dp.toPx()),
                size = Size(8.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
            )
            val shirt2 = Path().apply {
                moveTo(c2X - headR2 * 0.8f, c2Y + headR2 * 0.8f)
                lineTo(0f, h)
                lineTo(c2X + headR2 * 1.3f, h)
                lineTo(c2X + headR2 * 0.8f, c2Y + headR2 * 0.8f)
                close()
            }
            drawPath(shirt2, color = Color(0xFF16A34A))

            // Child 3 (Right - smiling girl)
            val c3X = w * 0.80f
            val c3Y = h * 0.57f
            val headR3 = 14.dp.toPx()
            drawCircle(Color(0xFF1A1A1A), radius = headR3 * 1.15f, center = Offset(c3X, c3Y - 3.dp.toPx()))
            drawCircle(Color(0xFFFFCC99), radius = headR3, center = Offset(c3X, c3Y))
            drawCircle(Color(0xFF1A1A1A), radius = 1.8.dp.toPx(), center = Offset(c3X - 3.8.dp.toPx(), c3Y - 1.dp.toPx()))
            drawCircle(Color(0xFF1A1A1A), radius = 1.8.dp.toPx(), center = Offset(c3X + 3.8.dp.toPx(), c3Y - 1.dp.toPx()))
            drawArc(
                color = Color(0xFF9A3412),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(c3X - 4.dp.toPx(), c3Y + 2.dp.toPx()),
                size = Size(8.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
            )
            val shirt3 = Path().apply {
                moveTo(c3X - headR3 * 0.8f, c3Y + headR3 * 0.8f)
                lineTo(c3X - headR3 * 1.3f, h)
                lineTo(w, h)
                lineTo(c3X + headR3 * 0.8f, c3Y + headR3 * 0.8f)
                close()
            }
            drawPath(shirt3, color = Color(0xFFE11D48))
        }

        // Overlay pill at bottom: "Every child understands 💚"
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.94f))
                .padding(vertical = 6.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Every child understands 💚",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF15803D),
            )
        }
    }
}

/**
 * Card 4: Hindi -> Mundari translation card (Centerpiece).
 */
@Composable
private fun CardHindiMundari(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 160.dp, height = 180.dp)
            .shadow(20.dp, RoundedCornerShape(18.dp), spotColor = Color(0x500284C7))
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
            .padding(13.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Hindi → Mundari",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "नमस्ते बच्चों",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
            )
            Text(
                text = "(Namaste bachchon)",
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.weight(1f))

            // Sub-pill with translated Mundari and speaker icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Down-right arrow ↳
                Canvas(modifier = Modifier.size(13.dp)) {
                    val w = size.width
                    val h = size.height
                    val p = Path().apply {
                        moveTo(w * 0.2f, h * 0.1f)
                        lineTo(w * 0.2f, h * 0.7f)
                        lineTo(w * 0.8f, h * 0.7f)
                    }
                    drawPath(p, color = Color(0xFF94A3B8), style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
                    drawLine(Color(0xFF94A3B8), Offset(w * 0.55f, h * 0.45f), Offset(w * 0.8f, h * 0.7f), 1.8.dp.toPx(), StrokeCap.Round)
                    drawLine(Color(0xFF94A3B8), Offset(w * 0.55f, h * 0.95f), Offset(w * 0.8f, h * 0.7f), 1.8.dp.toPx(), StrokeCap.Round)
                }

                Spacer(Modifier.width(5.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFFDCFCE7), RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Johar chotemko",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534),
                    )
                    Icon(
                        imageVector = BolMitraIcons.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

/**
 * Card 5: Mother Tongue / Stronger Learning / Brighter Futures in dark forest green.
 */
@Composable
private fun CardMotherTongue(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 136.dp, height = 172.dp)
            .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color(0x400284C7))
            .background(Color(0xFF0C3826), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF134E35), RoundedCornerShape(16.dp))
            .padding(13.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Mother Tongue",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Stronger Learning",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFE2E8F0),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Brighter Futures",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD9F99D), // Bright chartreuse lime
            )

            Spacer(Modifier.weight(1f))

            // Sprouting green seedling 🌱
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(32.dp)) {
                    val w = size.width
                    val h = size.height
                    // Stem
                    drawLine(
                        color = Color(0xFF86EFAC),
                        start = Offset(w * 0.5f, h * 0.9f),
                        end = Offset(w * 0.5f, h * 0.35f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Left sprout leaf
                    val leftLeaf = Path().apply {
                        moveTo(w * 0.5f, h * 0.50f)
                        cubicTo(w * 0.20f, h * 0.45f, w * 0.15f, h * 0.20f, w * 0.35f, h * 0.15f)
                        cubicTo(w * 0.45f, h * 0.20f, w * 0.48f, h * 0.35f, w * 0.5f, h * 0.50f)
                        close()
                    }
                    drawPath(leftLeaf, color = Color(0xFF4ADE80))
                    // Right sprout leaf
                    val rightLeaf = Path().apply {
                        moveTo(w * 0.5f, h * 0.40f)
                        cubicTo(w * 0.80f, h * 0.35f, w * 0.85f, h * 0.10f, w * 0.65f, h * 0.05f)
                        cubicTo(w * 0.55f, h * 0.10f, w * 0.52f, h * 0.25f, w * 0.5f, h * 0.40f)
                        close()
                    }
                    drawPath(rightLeaf, color = Color(0xFF86EFAC))
                }
            }
        }
    }
}

/**
 * Card 6: 3 Languages with Mundari, Santali, and Ho.
 */
@Composable
private fun CardThreeLanguages(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 136.dp, height = 172.dp)
            .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color(0x400284C7))
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
            .padding(11.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "3 Languages",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
            )

            Spacer(Modifier.height(10.dp))

            // 1. Mundari
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color(0xFFDCFCE7), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(9.dp)) {
                        drawCircle(Color(0xFF16A34A), radius = 3.5.dp.toPx())
                    }
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Mundari",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                )
            }

            Spacer(Modifier.height(5.dp))

            // 2. Santali
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color(0xFFFFEDD5), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(9.dp)) {
                        drawCircle(Color(0xFFEA580C), radius = 3.5.dp.toPx())
                    }
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Santali",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                )
            }

            Spacer(Modifier.height(5.dp))

            // 3. Ho
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color(0xFFFEF08A), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(9.dp)) {
                        drawCircle(Color(0xFFCA8A04), radius = 3.5.dp.toPx())
                    }
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Ho",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                )
            }
        }
    }
}

/**
 * Card 7: Offline Ready with tablet frame and "Works on low-cost tablets".
 */
@Composable
private fun CardOfflineReady(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 136.dp, height = 172.dp)
            .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color(0x400284C7))
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .padding(11.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Offline Ready",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                modifier = Modifier.align(Alignment.Start),
            )

            Spacer(Modifier.height(12.dp))

            // Green outline tablet frame
            Canvas(modifier = Modifier.size(width = 36.dp, height = 48.dp)) {
                val w = size.width
                val h = size.height
                drawRoundRect(
                    color = Color(0xFF16A34A),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = Stroke(width = 2.4.dp.toPx()),
                )
                drawRoundRect(
                    color = Color(0xFFDCFCE7),
                    topLeft = Offset(3.5.dp.toPx(), 5.5.dp.toPx()),
                    size = Size(w - 7.dp.toPx(), h - 13.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
                drawCircle(
                    color = Color(0xFF16A34A),
                    radius = 1.8.dp.toPx(),
                    center = Offset(w * 0.5f, h - 3.8.dp.toPx()),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Works on\nlow-cost tablets",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
            )
        }
    }
}

/**
 * Trust proof and 5 gold stars strip.
 */
@Composable
private fun TrustRatingStrip() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Trusted by 1,000+ schools for a brighter Bharat",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            letterSpacing = 0.3.sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0x600284C7),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 4f,
                )
            ),
        )

        Spacer(Modifier.height(8.dp))

        // 5 Gold Stars
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val outerR = w * 0.48f
                    val innerR = outerR * 0.42f
                    val path = Path()
                    for (i in 0 until 10) {
                        val angle = (i * 36.0 - 90.0) * Math.PI / 180.0
                        val r = if (i % 2 == 0) outerR else innerR
                        val x = cx + cos(angle).toFloat() * r
                        val y = cy + sin(angle).toFloat() * r
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, color = Color(0xFFFBBF24))
                }
            }
        }
    }
}

/**
 * Canvas drawing soft, realistic cumulus clouds across left, right, and bottom edges.
 * Leaves the center clear azure blue behind the text and cards for maximum contrast.
 */
@Composable
private fun CloudsBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Left cloud bank (curving inward on left flank)
        val leftClouds = listOf(
            Triple(Offset(w * -0.02f, h * 0.40f), 80.dp.toPx(), 0.35f),
            Triple(Offset(w * 0.05f, h * 0.48f), 65.dp.toPx(), 0.45f),
            Triple(Offset(w * 0.08f, h * 0.58f), 70.dp.toPx(), 0.40f),
            Triple(Offset(w * 0.02f, h * 0.70f), 90.dp.toPx(), 0.50f),
            Triple(Offset(w * 0.06f, h * 0.85f), 100.dp.toPx(), 0.60f),
        )
        for ((center, radius, alpha) in leftClouds) {
            drawCircle(Color.White.copy(alpha = alpha), radius = radius, center = center)
        }

        // Right cloud bank (curving inward on right flank)
        val rightClouds = listOf(
            Triple(Offset(w * 1.02f, h * 0.34f), 85.dp.toPx(), 0.35f),
            Triple(Offset(w * 0.94f, h * 0.44f), 70.dp.toPx(), 0.45f),
            Triple(Offset(w * 0.92f, h * 0.56f), 75.dp.toPx(), 0.45f),
            Triple(Offset(w * 0.98f, h * 0.70f), 95.dp.toPx(), 0.55f),
            Triple(Offset(w * 0.94f, h * 0.85f), 100.dp.toPx(), 0.60f),
        )
        for ((center, radius, alpha) in rightClouds) {
            drawCircle(Color.White.copy(alpha = alpha), radius = radius, center = center)
        }

        // Bottom cloud horizon bed (below the stars, along the very bottom horizon)
        val bottomPuffs = listOf(
            Triple(Offset(w * 0.0f, h * 0.98f), 65.dp.toPx(), 0.70f),
            Triple(Offset(w * 0.15f, h * 0.99f), 60.dp.toPx(), 0.65f),
            Triple(Offset(w * 0.30f, h * 1.00f), 55.dp.toPx(), 0.60f),
            Triple(Offset(w * 0.50f, h * 1.01f), 50.dp.toPx(), 0.55f),
            Triple(Offset(w * 0.70f, h * 1.00f), 55.dp.toPx(), 0.60f),
            Triple(Offset(w * 0.85f, h * 0.99f), 60.dp.toPx(), 0.65f),
            Triple(Offset(w * 1.0f, h * 0.98f), 65.dp.toPx(), 0.70f),
        )
        for ((center, radius, alpha) in bottomPuffs) {
            drawCircle(Color.White.copy(alpha = alpha), radius = radius, center = center)
        }

        // Very soft white mist across the bottom edge
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.50f)),
                startY = h * 0.94f,
                endY = h,
            )
        )
    }
}
