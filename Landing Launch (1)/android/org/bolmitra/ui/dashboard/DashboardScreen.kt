package org.bolmitra.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.bolmitra.ui.common.GlassCard
import org.bolmitra.ui.common.GlassShape
import org.bolmitra.ui.common.InnerShape
import org.bolmitra.ui.common.SectionLabel
import org.bolmitra.ui.theme.BolmitraColors
import org.bolmitra.ui.theme.BolmitraTheme
import org.bolmitra.ui.theme.SilkBackdrop

/**
 * Post-login home. Stateless: hand it a [DashboardUiState] built from Room and
 * it renders. Wide layouts (>= 840dp) get the permanent left rail; narrower
 * ones get a bottom bar instead.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onNavSelect: (String) -> Unit = {},
    onToggleGoal: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SilkBackdrop(modifier = modifier) {
        BoxWithWidth { wide ->
            Row(Modifier.fillMaxSize().padding(14.dp)) {
                if (wide) {
                    SidebarRail(state, onNavSelect, Modifier.width(210.dp).fillMaxHeight())
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    TopBar(state.greetingName)
                    Spacer(Modifier.height(14.dp))
                    DashboardBody(state, wide, onToggleGoal, Modifier.weight(1f))
                    if (!wide) {
                        Spacer(Modifier.height(10.dp))
                        BottomBar(state, onNavSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWithWidth(content: @Composable (wide: Boolean) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        content(maxWidth >= 840.dp)
    }
}

/* ---------------------------------------------------------------- sidebar */

private fun navIcon(id: String): ImageVector = when (id) {
    "dashboard" -> Icons.Filled.Home
    "calendar" -> Icons.Filled.DateRange
    "tasks" -> Icons.Filled.List
    "stats" -> Icons.Filled.Star
    "docs" -> Icons.Filled.List
    "slack" -> Icons.Filled.Notifications
    "notion" -> Icons.Filled.List
    "add" -> Icons.Filled.Add
    else -> Icons.Filled.List
}

@Composable
private fun SidebarRail(
    state: DashboardUiState,
    onNavSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).background(BolmitraColors.Ink, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("//", style = MaterialTheme.typography.labelSmall, color = BolmitraColors.OnInk)
            }
            Spacer(Modifier.width(8.dp))
            Text("iDraft", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(22.dp))

        state.navItems.forEach { item ->
            NavRow(
                title = item.title,
                icon = navIcon(item.id),
                selected = item.id == state.selectedNavId,
                onClick = { onNavSelect(item.id) },
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Integrations")
        Spacer(Modifier.height(8.dp))
        state.integrations.forEach { item ->
            NavRow(item.title, navIcon(item.id), selected = false, onClick = { onNavSelect(item.id) })
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Teams")
        Spacer(Modifier.height(8.dp))
        state.teams.forEach { team ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            if (team.dark) BolmitraColors.Ink else BolmitraColors.InkMuted,
                            CircleShape,
                        )
                )
                Spacer(Modifier.width(12.dp))
                Text(team.name, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.weight(1f))
        NavRow("Settings", Icons.Filled.Settings, selected = false, onClick = { onNavSelect("settings") })
    }
}

@Composable
private fun NavRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) BolmitraColors.Ink else androidx.compose.ui.graphics.Color.Transparent,
                InnerShape,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) BolmitraColors.OnInk else BolmitraColors.InkMuted,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) BolmitraColors.OnInk else BolmitraColors.Ink,
        )
    }
}

@Composable
private fun BottomBar(state: DashboardUiState, onNavSelect: (String) -> Unit) {
    GlassCard(contentPadding = 8.dp, shape = RoundedCornerShape(50)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.navItems.forEach { item ->
                val selected = item.id == state.selectedNavId
                Box(
                    Modifier
                        .size(42.dp)
                        .clickable { onNavSelect(item.id) }
                        .background(
                            if (selected) BolmitraColors.Ink else androidx.compose.ui.graphics.Color.Transparent,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        navIcon(item.id),
                        contentDescription = item.title,
                        tint = if (selected) BolmitraColors.OnInk else BolmitraColors.InkMuted,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- top bar */

@Composable
private fun TopBar(name: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Hi, $name!",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier
                .background(BolmitraColors.Ink, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = BolmitraColors.OnInk, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create", style = MaterialTheme.typography.labelLarge, color = BolmitraColors.OnInk)
        }
        Spacer(Modifier.width(10.dp))
        CircleAction(Icons.Filled.Search, "Search")
        Spacer(Modifier.width(8.dp))
        CircleAction(Icons.Filled.Notifications, "Notifications")
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(38.dp)
                .background(BolmitraColors.InkSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = "Your profile",
                tint = BolmitraColors.OnInk,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CircleAction(icon: ImageVector, label: String) {
    Box(
        Modifier
            .size(38.dp)
            .background(BolmitraColors.Glass, CircleShape)
            .border(1.dp, BolmitraColors.HairlineOnPaper, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = BolmitraColors.Ink, modifier = Modifier.size(17.dp))
    }
}

/* -------------------------------------------------------------------- body */

@Composable
private fun DashboardBody(
    state: DashboardUiState,
    wide: Boolean,
    onToggleGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OverallInformationCard(state, Modifier.weight(1.15f))
                WeeklyProgressCard(state.weekly, Modifier.weight(1f))
                MonthProgressCard(state, Modifier.weight(1f))
            }
        } else {
            OverallInformationCard(state, Modifier.fillMaxWidth())
            WeeklyProgressCard(state.weekly, Modifier.fillMaxWidth())
            MonthProgressCard(state, Modifier.fillMaxWidth())
        }

        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MonthGoalsCard(state.goals, onToggleGoal, Modifier.weight(1f))
                Column(Modifier.weight(1.6f)) {
                    RowSectionHeader("Task In process (${state.tasks.size})", "Open archive")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.tasks.forEach { TaskCard(it, Modifier.weight(1f).height(150.dp)) }
                        AddTaskCard(Modifier.weight(1f).height(150.dp))
                    }
                }
            }
        } else {
            MonthGoalsCard(state.goals, onToggleGoal, Modifier.fillMaxWidth())
            RowSectionHeader("Task In process (${state.tasks.size})", "Open archive")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.tasks, key = { it.id }) { task ->
                    TaskCard(task, Modifier.width(230.dp).height(150.dp))
                }
                item { AddTaskCard(Modifier.width(180.dp).height(150.dp)) }
            }
        }

        RowSectionHeader("Last Projects", "Sort by")
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                state.projects.forEach { ProjectCard(it, Modifier.weight(1f).height(120.dp)) }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.projects, key = { it.id }) { project ->
                    ProjectCard(project, Modifier.width(250.dp).height(120.dp))
                }
            }
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Preview(widthDp = 1024, heightDp = 768, showBackground = true)
@Composable
private fun DashboardTabletPreview() {
    BolmitraTheme { DashboardScreen(sampleDashboardState) }
}

@Preview(widthDp = 412, heightDp = 900, showBackground = true)
@Composable
private fun DashboardPhonePreview() {
    BolmitraTheme { DashboardScreen(sampleDashboardState) }
}
