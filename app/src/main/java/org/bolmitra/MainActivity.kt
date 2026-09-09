package org.bolmitra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.bolmitra.device.assignTier
import org.bolmitra.device.probe
import org.bolmitra.speech.ModelStore
import org.bolmitra.ui.Destination
import org.bolmitra.ui.HomeScreen
import org.bolmitra.ui.LandingScreen
import org.bolmitra.ui.common.SoundEffects
import org.bolmitra.ui.theme.BolMitraTheme
import org.bolmitra.ui.theme.SilkBackdrop

/**
 * Single activity: landing screen, then the app shell.
 *
 * The landing → shell swap is a `rememberSaveable` flag, as in the supplied `BolmitraApp`, with the
 * crossfade kept. No navigation library: the landing screen is seen once per launch and has nowhere
 * to go but forward, so there is no back stack to model.
 *
 * `ponytail:` Ceiling noted — the moment a destination needs arguments, a deep link, or restoration
 * across process death, this should become a `NavHost` rather than growing more flags.
 *
 * ### One thing that must not regress here (O17)
 *
 * Model handles are **process-scoped, never Activity-scoped**. Rotation destroys the Activity, and
 * §6.13 mandates both orientations, so an Activity-scoped ASR handle would reload ~220 MB inside a
 * teacher's turn and break §6.10's own invariant. Today the models live behind `remember` inside
 * the diagnostics screen, which is safe only because that screen is a harness. Wiring Live Class
 * means hoisting them out of composition entirely.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35+ enforces edge-to-edge; opt in explicitly and pad with insets below.
        enableEdgeToEdge()

        // Startup chime. Guarded on savedInstanceState so it plays when the app starts and not
        // again on every rotation — §6.13 mandates both orientations, and a jingle on each turn of
        // the tablet would be a bug the teacher hears rather than sees.
        if (savedInstanceState == null) {
            SoundEffects.playStartup(this)
        }

        setContent { BolMitraTheme { App() } }
    }
}

/**
 * Holds whether the mic may be used, and the one-shot request for it.
 *
 * `RECORD_AUDIO` is dangerous-level, so the manifest entry alone grants nothing on API 23+ — this
 * is the other half. Asked for once on first composition rather than at the moment the teacher
 * presses record: a system dialog appearing the instant they try to speak is the worst possible
 * time, because they have already started talking.
 *
 * A denial is not an error state to recover from here. The turn UI reads [granted] and says what is
 * unavailable and why, which is the same honesty the rest of the app applies to unbuilt stages.
 */
@Composable
private fun rememberMicPermission(): State<Boolean> {
    val context = LocalContext.current
    val granted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok -> granted.value = ok }

    LaunchedEffect(Unit) {
        if (!granted.value) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    return granted
}

@Composable
private fun App() {
    val context = LocalContext.current
    val micGranted by rememberMicPermission()

    // Probed once. `probe` reads /proc/cpuinfo and StatFs — cheap but not free, and the answer
    // cannot change while the process is alive.
    val spec = remember { probe(context) }
    val tier = remember { assignTier(spec) }
    val store = remember { ModelStore(context) }

    var started by rememberSaveable { mutableStateOf(false) }
    // Which rail item the shell opens on. "Show me the numbers" is the first thing asked in a
    // demo, so the landing screen gets a one-tap route to Diagnostics instead of two.
    var shellStart by remember { mutableStateOf(Destination.HOME) }

    // Backdrop sits outside AnimatedContent so the paper wash does not crossfade with itself.
    SilkBackdrop(Modifier.fillMaxSize()) {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { insets ->
            AnimatedContent(
                targetState = started,
                transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(220)) },
                label = "landing-shell",
            ) { inShell ->
                if (inShell) {
                    HomeScreen(
                        spec = spec,
                        tier = tier,
                        store = store,
                        start = shellStart,
                        micGranted = micGranted,
                        modifier = Modifier.padding(insets),
                    )
                } else {
                    LandingScreen(
                        spec = spec,
                        tier = tier,
                        onStart = {
                            shellStart = Destination.HOME
                            started = true
                        },
                        onDiagnostics = {
                            shellStart = Destination.DIAGNOSTICS
                            started = true
                        },
                        modifier = Modifier.padding(insets),
                    )
                }
            }
        }
    }
}
