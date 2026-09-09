package org.bolmitra.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay

/** How loud a notification is allowed to be about itself. */
enum class NoticeKind { SUCCESS, WARNING }

/**
 * One in-app notification.
 *
 * [messageHi] is separate rather than concatenated because the Hindi line is set smaller and in a
 * muted tone — a teacher scanning the popup mid-lesson reads one line, not a bilingual paragraph.
 */
data class AppNotice(
    val title: String,
    val message: String,
    val messageHi: String? = null,
    val kind: NoticeKind = NoticeKind.SUCCESS,
)

/**
 * Shows in-app notifications and plays [SoundEffects.playNotification] with each one.
 *
 * ### Why in-app rather than a system notification
 *
 * A real `NotificationManager` notification would need `POST_NOTIFICATIONS` on API 33+, which is a
 * runtime permission dialog for a device whose entire selling point is that it asks for nothing and
 * sends nothing. It would also survive the app going to background, which is wrong here: these
 * announce something that just finished on screen, in front of a class, and are meaningless later.
 *
 * ### The sound is tied to showing, not to the caller
 *
 * [show] plays the chime itself so no call site can raise a popup silently or play the chime without
 * a popup. Keeping the two together is the whole reason this is a class and not a `mutableStateOf`.
 */
class Notifier internal constructor(private val playSound: () -> Unit) {

    var current by mutableStateOf<AppNotice?>(null)
        private set

    fun show(notice: AppNotice) {
        current = notice
        playSound()
    }

    fun dismiss() {
        current = null
    }
}

/** Remembers a [Notifier] wired to the notification chime. */
@Composable
fun rememberNotifier(): Notifier {
    val context = LocalContext.current
    return remember { Notifier { SoundEffects.playNotification(context.applicationContext) } }
}

/**
 * Renders [notifier]'s current notice as a floating card.
 *
 * Uses [Popup] rather than a `Box` overlay inside the pane: every screen here lives in a
 * `verticalScroll`, and a notice that scrolls away with the content is not a notification. `Popup`
 * renders in its own window, so it stays put.
 *
 * Auto-dismisses, because a teacher mid-lesson should not have to tidy up after the app. The
 * dismiss button exists anyway for the case where it covers something they need.
 */
@Composable
fun NotificationHost(notifier: Notifier, modifier: Modifier = Modifier) {
    val notice = notifier.current

    // Keyed on the notice so a second notification restarts the clock rather than inheriting the
    // remaining time from the first.
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(AUTO_DISMISS_MS)
            notifier.dismiss()
        }
    }

    if (notice == null) return

    // No AnimatedVisibility. It was wrapping this with a hardcoded `visible = true`, which cannot
    // animate in (the state starts at its target) and cannot animate out (the whole subtree leaves
    // composition the moment `notice` goes null). Two transitions that never run, plus a layer
    // between Popup and its content that can only go wrong.
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(-40, 40)) {
        NoticeCard(notice = notice, onDismiss = notifier::dismiss, modifier = modifier)
    }
}

@Composable
private fun NoticeCard(notice: AppNotice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val accent = when (notice.kind) {
        // Verified green and approximate amber, the two hues the provenance system already
        // established. Not new colours: a teacher has learned what these two mean.
        NoticeKind.SUCCESS -> Color(0xFF1B5E20)
        NoticeKind.WARNING -> Color(0xFF8A5300)
    }
    val wash = when (notice.kind) {
        NoticeKind.SUCCESS -> Color(0xFFDCFCE7)
        NoticeKind.WARNING -> Color(0xFFFEF3C7)
    }

    Row(
        modifier = modifier
            .widthIn(min = 280.dp, max = 460.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(wash, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.widthIn(max = 340.dp)) {
            Text(
                notice.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    // 16 sp floor: this is text a teacher has to read, mid-lesson, at a glance.
                    fontSize = 16.sp,
                    color = Color(0xFF1E293B),
                ),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                notice.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp,
                    color = Color(0xFF475569),
                ),
            )
            notice.messageHi?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                    ),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private const val AUTO_DISMISS_MS = 5_000L
