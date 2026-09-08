package org.bolmitra.ui.dashboard

import androidx.compose.runtime.Immutable

@Immutable
data class NavItem(val id: String, val title: String)

@Immutable
data class TeamItem(val id: String, val name: String, val dark: Boolean)

@Immutable
data class MetricTile(val value: String, val caption: String)

@Immutable
data class GoalItem(val id: String, val title: String, val done: Boolean)

@Immutable
data class TaskItem(
    val id: String,
    val title: String,
    val meta: String,
    val glyph: String,
    val reminder: Boolean,
)

@Immutable
data class ProjectItem(
    val id: String,
    val title: String,
    val status: String,
    val detail: String,
)

@Immutable
data class WeeklyProgress(
    val primary: List<Float>,
    val secondary: List<Float>,
    val dayLabels: List<String>,
    val selectedDayIndex: Int,
    val delta: String,
)

@Immutable
data class DashboardUiState(
    val greetingName: String,
    val navItems: List<NavItem>,
    val selectedNavId: String,
    val integrations: List<NavItem>,
    val teams: List<TeamItem>,
    val tasksDone: String,
    val tasksDoneCaption: String,
    val projectsStopped: String,
    val projectsStoppedCaption: String,
    val metricTiles: List<MetricTile>,
    val weekly: WeeklyProgress,
    val monthDelta: String,
    val monthRingValue: Float,
    val monthRingLabel: String,
    val monthLegend: List<String>,
    val goals: List<GoalItem>,
    val tasks: List<TaskItem>,
    val projects: List<ProjectItem>,
)

/** Static sample state so previews and the first run have something to show. */
val sampleDashboardState = DashboardUiState(
    greetingName = "Dilan",
    navItems = listOf(
        NavItem("dashboard", "Dashboard"),
        NavItem("calendar", "Calendar"),
        NavItem("tasks", "My Tasks"),
        NavItem("stats", "Statistics"),
        NavItem("docs", "Documents"),
    ),
    selectedNavId = "dashboard",
    integrations = listOf(
        NavItem("slack", "Slack"),
        NavItem("notion", "Notion"),
        NavItem("add", "Add new plugin"),
    ),
    teams = listOf(
        TeamItem("seo", "SEO", dark = true),
        TeamItem("marketing", "Marketing", dark = false),
    ),
    tasksDone = "43",
    tasksDoneCaption = "Tasks done\nfor all time",
    projectsStopped = "2",
    projectsStoppedCaption = "projects are\nstopped",
    metricTiles = listOf(
        MetricTile("28", "Projects"),
        MetricTile("14", "In Progress"),
        MetricTile("7", "Completed"),
    ),
    weekly = WeeklyProgress(
        primary = listOf(18f, 26f, 22f, 40f, 62f, 48f, 58f),
        secondary = listOf(12f, 14f, 30f, 24f, 38f, 34f, 44f),
        dayLabels = listOf("M", "T", "W", "T", "F", "S", "S"),
        selectedDayIndex = 5,
        delta = "+34%",
    ),
    monthDelta = "+20% compared to last month",
    monthRingValue = 1.2f,
    monthRingLabel = "120%",
    monthLegend = listOf("Sport", "Study", "Project"),
    goals = listOf(
        GoalItem("g1", "Read 2 books", done = true),
        GoalItem("g2", "Sports every day", done = false),
        GoalItem("g3", "Complete the course", done = false),
        GoalItem("g4", "Bend down with a parachute", done = false),
    ),
    tasks = listOf(
        TaskItem("t1", "Buy Susan a gift for Bitherday", "Today", "\uD83C\uDF81", reminder = true),
        TaskItem("t2", "Doctor's appointment on Tuesday", "02.09.2023", "\u271A", reminder = true),
    ),
    projects = listOf(
        ProjectItem(
            "p1",
            "New Schedule",
            "In progress",
            "Done: Develop a new plan for Alina's education; Print a new timetable; Buy ...",
        ),
        ProjectItem("p2", "Prototype animation", "Completed", ""),
        ProjectItem("p3", "Ai Project 2 part", "In progress", ""),
    ),
)
