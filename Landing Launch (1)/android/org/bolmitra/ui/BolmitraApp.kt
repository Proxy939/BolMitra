package org.bolmitra.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.bolmitra.ui.dashboard.DashboardScreen
import org.bolmitra.ui.dashboard.sampleDashboardState
import org.bolmitra.ui.landing.LandingScreen
import org.bolmitra.ui.theme.BolmitraTheme

/**
 * Single entry point for MainActivity.setContent { BolmitraApp() }.
 *
 * The landing -> dashboard swap is a local flag on purpose: no navigation
 * library is added. Replace [signedIn] with your real session state and swap
 * [sampleDashboardState] for a Room-backed [org.bolmitra.ui.dashboard.DashboardUiState].
 */
@Composable
fun BolmitraApp(modifier: Modifier = Modifier) {
    var signedIn by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf(sampleDashboardState) }

    BolmitraTheme {
        AnimatedContent(
            targetState = signedIn,
            transitionSpec = {
                fadeIn(tween(320)) togetherWith fadeOut(tween(220))
            },
            label = "landing-dashboard",
        ) { isSignedIn ->
            if (isSignedIn) {
                DashboardScreen(
                    state = state,
                    onNavSelect = { id -> state = state.copy(selectedNavId = id) },
                    onToggleGoal = { id ->
                        state = state.copy(
                            goals = state.goals.map {
                                if (it.id == id) it.copy(done = !it.done) else it
                            },
                        )
                    },
                    modifier = modifier,
                )
            } else {
                LandingScreen(onGetStarted = { signedIn = true }, modifier = modifier)
            }
        }
    }
}
