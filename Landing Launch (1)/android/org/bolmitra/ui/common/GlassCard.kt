package org.bolmitra.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.bolmitra.ui.theme.BolmitraColors

val GlassShape: Shape = RoundedCornerShape(26.dp)
val InnerShape: Shape = RoundedCornerShape(20.dp)
val PillShape: Shape = RoundedCornerShape(percent = 50)

/** Frosted white panel: translucent fill, hairline highlight, soft shadow. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = BolmitraColors.Glass),
        border = BorderStroke(1.dp, BolmitraColors.GlassStroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        0f to BolmitraColors.Glass,
                        1f to BolmitraColors.GlassSoft,
                    )
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Inverted emphasis panel: paper text on ink. */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = BolmitraColors.Ink),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Small wide-tracked caption used for section chrome. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = BolmitraColors.InkMuted,
        modifier = modifier,
    )
}
