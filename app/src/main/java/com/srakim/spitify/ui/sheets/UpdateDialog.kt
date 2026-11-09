package com.srakim.spitify.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.AppUpdater.Status
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.theme.SpitifyColors

/** Shows over any screen when a newer build is out, while it downloads, and if install needs permission. */
@Composable
fun UpdateDialog() {
    val updater = LocalApp.current.updater
    val actions = LocalActions.current
    val status by updater.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { updater.checkIfDue() }

    when (val s = status) {
        is Status.Available -> AlertDialog(
            onDismissRequest = { updater.later(s.release) },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Update available") },
            text = {
                Text(
                    "Spitify ${s.release.versionName} is out. Your library, playlists and downloads stay as they are.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { updater.install(s.release) }) { Text("Update", color = SpitifyColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { updater.later(s.release) }) { Text("Later", color = SpitifyColors.TextSecondary) }
            },
        )

        is Status.Downloading -> AlertDialog(
            onDismissRequest = {},
            containerColor = SpitifyColors.Elevated,
            title = { Text("Downloading update") },
            text = {
                Column {
                    Text("Spitify ${s.release.versionName}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        color = SpitifyColors.Accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )

        is Status.NeedsPermission -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            containerColor = SpitifyColors.Elevated,
            title = { Text("One more step") },
            text = {
                Text(
                    "Android needs your OK before Spitify can install updates. Turn on \"Allow from this source\", come back, then tap Install.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { updater.install(s.release) }) { Text("Install", color = SpitifyColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { updater.dismiss() }) { Text("Not now", color = SpitifyColors.TextSecondary) }
            },
        )

        is Status.Failed -> LaunchedEffect(s) {
            actions.toast(s.message)
            updater.dismiss()
        }

        else -> Unit
    }
}
