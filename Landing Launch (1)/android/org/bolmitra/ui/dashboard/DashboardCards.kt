package org.bolmitra.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.bolmitra.ui.common.GlassCard
import org.bolmitra.ui.common.InkCard
import org.bolmitra.ui.common.InnerShape
import org.bolmitra.ui.common.LegendItem
import org.bolmitra.ui.common.RingProgress
import org.bolmitra.ui.common.SectionLabel
import org.bolmitra.ui.common.DualLineChart
import org.bolmitra.ui.theme.BolmitraColors

/* ---------------------------------------------------------------- overall */

@Composable
fun OverallInformationCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    InkCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Overall Information",
                style = MaterialTheme.typography.titleLarge,
                color = BolmitraColors.OnInk,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.Share, null, tint = BolmitraColors.OnInkMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.MoreVert, null, tint = BolmitraColors.OnInkMuted, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            HeadlineMetric(state.tasksDone, state.tasksDoneCaption)
            Spacer(Modifier.width(28.dp))
            HeadlineMetric(state.projectsStopped, state.projectsStoppedCaption)
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.metricTiles.forEach { tile ->
                MetricTileView(tile, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeadlineMetric(value: String, caption: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displayLarge, color = BolmitraColors.OnInk)
        Box(
            Modifier
                .padding(top = 4.dp, bottom = 6.dp)
                .width(44.dp)
                .height(2.dp)
                .background(BolmitraColors.OnInk)
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = BolmitraColors.OnInkMuted)
    }
}

@Composable
private fun MetricTileView(tile: MetricTile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(BolmitraColors.InkSoft, InnerShape)
            .border(1.dp, BolmitraColors.HairlineOnInk, InnerShape)
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .border(2.dp, BolmitraColors.OnInkMuted, CircleShape),
        )
        Spacer(Modifier.height(12.dp))
        Text(tile.value, style = MaterialTheme.typography.headlineMedium, color = BolmitraColors.OnInk)
        Text(tile.caption, style = MaterialTheme.typography.bodySmall, color = BolmitraColors.OnInkMuted)
    }
}

/* ----------------------------------------------------------------- weekly */

@Composable
fun WeeklyProgressCard(weekly: WeeklyProgress, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Weekly progress",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.Refresh, null, tint = BolmitraColors.InkMuted, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem("Sport", BolmitraColors.Ink)
            LegendItem("Study", BolmitraColors.InkMuted)
            Spacer(Modifier.weight(1f))
            Text(
                weekly.delta,
                style = MaterialTheme.typography.labelSmall,
                color = BolmitraColors.OnInk,
                modifier = Modifier
                    .background(BolmitraColors.Ink, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        DualLineChart(
            primary = weekly.primary,
            secondary = weekly.secondary,
            modifier = Modifier.fillMaxWidth().height(110.dp),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            weekly.dayLabels.forEachIndexed { index, day ->
                val selected = index == weekly.selectedDayIndex
                Box(
                    Modifier.size(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(Modifier.size(22.dp).background(BolmitraColors.Ink, CircleShape))
                    }
                    Text(
                        day,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) BolmitraColors.OnInk else BolmitraColors.InkMuted,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ month */

@Composable
fun MonthProgressCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Month progress",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.KeyboardArrowRight, null, tint = BolmitraColors.InkMuted, modifier = Modifier.size(18.dp))
        }
        Text(
            state.monthDelta,
            style = MaterialTheme.typography.bodySmall,
            color = BolmitraColors.InkMuted,
        )

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.monthLegend.forEach { LegendItem(it, BolmitraColors.Ink) }
            }
            RingProgress(
                value = state.monthRingValue,
                label = state.monthRingLabel,
                caption = "of goal",
                diameter = 112.dp,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(BolmitraColors.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Share month progress",
                    tint = BolmitraColors.OnInk,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .background(BolmitraColors.Ink, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Download Report",
                    style = MaterialTheme.typography.labelLarge,
                    color = BolmitraColors.OnInk,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.ArrowDropDown,
                    null,
                    tint = BolmitraColors.OnInk,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ goals */

@Composable
fun MonthGoalsCard(
    goals: List<GoalItem>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Month goals:",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(28.dp).border(1.dp, BolmitraColors.HairlineOnPaper, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Refresh, null, tint = BolmitraColors.InkMuted, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Create, null, tint = BolmitraColors.Ink, modifier = Modifier.size(16.dp))
        }

        Spacer(Modifier.height(14.dp))

        goals.forEach { goal ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(
                            if (goal.done) BolmitraColors.Ink else BolmitraColors.Glass,
                            RoundedCornerShape(5.dp),
                        )
                        .border(1.dp, BolmitraColors.InkMuted, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (goal.done) {
                        Icon(
                            Icons.Filled.Check,
                            null,
                            tint = BolmitraColors.OnInk,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    goal.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (goal.done) BolmitraColors.Ink else BolmitraColors.InkMuted,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ tasks */

@Composable
fun TaskCard(task: TaskItem, modifier: Modifier = Modifier) {
    InkCard(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.glyph, style = MaterialTheme.typography.titleLarge, color = BolmitraColors.OnInk)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.MoreVert, null, tint = BolmitraColors.OnInkMuted, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            task.title,
            style = MaterialTheme.typography.titleMedium,
            color = BolmitraColors.OnInk,
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.meta, style = MaterialTheme.typography.bodySmall, color = BolmitraColors.OnInkMuted)
            Spacer(Modifier.weight(1f))
            if (task.reminder) {
                Box(
                    Modifier.size(30.dp).background(BolmitraColors.Paper, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "Reminder set",
                        tint = BolmitraColors.Ink,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AddTaskCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(
                1.dp,
                BolmitraColors.InkMuted.copy(alpha = 0.4f),
                RoundedCornerShape(26.dp),
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("+", style = MaterialTheme.typography.titleLarge, color = BolmitraColors.InkMuted)
            Spacer(Modifier.width(8.dp))
            Text("Add task", style = MaterialTheme.typography.titleMedium, color = BolmitraColors.InkMuted)
        }
    }
}

/* --------------------------------------------------------------- projects */

@Composable
fun ProjectCard(project: ProjectItem, modifier: Modifier = Modifier) {
    InkCard(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                project.title,
                style = MaterialTheme.typography.titleMedium,
                color = BolmitraColors.OnInk,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.size(22.dp).border(1.dp, BolmitraColors.HairlineOnInk, CircleShape))
        }
        Spacer(Modifier.height(10.dp))
        LegendItem(project.status, BolmitraColors.OnInkMuted)
        if (project.detail.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                project.detail,
                style = MaterialTheme.typography.bodySmall,
                color = BolmitraColors.OnInkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RowSectionHeader(
    title: String,
    trailing: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        SectionLabel(trailing)
        Icon(
            Icons.Filled.ArrowDropDown,
            null,
            tint = BolmitraColors.InkMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}
