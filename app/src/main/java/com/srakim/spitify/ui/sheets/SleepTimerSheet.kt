package com.srakim.spitify.ui.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.playback.PlaybackState
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.MenuIcons
import com.srakim.spitify.ui.components.formatDuration
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.delay

/** Spotify's sleep timer list. Opens from the song menu and the Timer button in the queue. */
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val state by player.state.collectAsStateWithLifecycle()

    SpitifySheet(onDismiss, scrollable = true) {
        Text(
            "Sleep timer",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        sleepTimerLabel(state)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = SpitifyColors.Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        listOf(5, 10, 15, 30, 45, 60).forEach { minutes ->
            SheetOption(MenuIcons.Timer, if (minutes == 60) "1 hour" else "$minutes minutes", onClick = {
                player.setSleepTimer(minutes)
                actions.toast("Your sleep timer is set")
                onDismiss()
            })
        }
        if (state.current?.isRadio != true) {
            SheetOption(
                MenuIcons.Timer,
                "End of track",
                tint = if (state.sleepAtTrackEnd) SpitifyColors.Accent else SpitifyColors.TextSecondary,
                onClick = {
                    player.setSleepAtTrackEnd()
                    actions.toast("Pausing when this song ends")
                    onDismiss()
                },
            )
        }
        if (state.sleepTimerEndsAt != null || state.sleepAtTrackEnd) {
            SheetOption(Icons.Rounded.Close, "Turn off timer", onClick = {
                player.setSleepTimer(null)
                actions.toast("Sleep timer off")
                onDismiss()
            })
        }
    }
}

/** "12:34 left" or "End of track", ticking every second, or null when no timer is set. */
@Composable
fun sleepTimerLabel(state: PlaybackState): String? {
    val ends = state.sleepTimerEndsAt
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ends) {
        while (ends != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return when {
        state.sleepAtTrackEnd -> "Ends with this song"
        ends != null -> "${formatDuration(((ends - now) / 1000).coerceAtLeast(0))} left"
        else -> null
    }
}
