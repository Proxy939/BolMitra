package org.bolmitra.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

private val BolmitraScheme = lightColorScheme(
    primary = BolmitraColors.Ink,
    onPrimary = BolmitraColors.OnInk,
    secondary = BolmitraColors.InkSoft,
    onSecondary = BolmitraColors.OnInk,
    background = BolmitraColors.Paper,
    onBackground = BolmitraColors.Ink,
    surface = BolmitraColors.PaperWarm,
    onSurface = BolmitraColors.Ink,
    surfaceVariant = BolmitraColors.Paper,
    onSurfaceVariant = BolmitraColors.InkMuted,
    outline = BolmitraColors.InkMuted,
    outlineVariant = BolmitraColors.HairlineOnPaper,
)

@Composable
fun BolmitraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BolmitraScheme,
        typography = BolmitraTypography,
        content = content,
    )
}

/**
 * The soft silk-like wash the reference screens sit on. Drawn as gradients
 * rather than a bitmap so it costs nothing in APK size.
 */
@Composable
fun SilkBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(BolmitraColors.Paper)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to BolmitraColors.PaperWarm,
                        0.45f to BolmitraColors.Paper,
                        1f to BolmitraColors.HairlineOnPaper,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0f to BolmitraColors.PaperWarm,
                        1f to BolmitraColors.Paper.copy(alpha = 0f),
                        center = Offset(220f, 160f),
                        radius = 1400f,
                    )
                )
        )
        content()
    }
}
