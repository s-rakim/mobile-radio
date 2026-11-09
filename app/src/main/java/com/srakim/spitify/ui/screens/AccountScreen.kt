package com.srakim.spitify.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.SpitifyServerApi
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalSettings
import com.srakim.spitify.ui.components.OutlineButton
import com.srakim.spitify.ui.components.WhiteButton
import com.srakim.spitify.ui.theme.SpitifyColors

/** Log in or create an account on your Spitify server, so playlists and likes are saved there. */
@Composable
fun AccountScreen() {
    val account = LocalApp.current.account
    val actions = LocalActions.current
    val state by account.state.collectAsStateWithLifecycle()
    val settings by LocalSettings.current.state.collectAsStateWithLifecycle()
    val lib by LocalLibrary.current.state.collectAsStateWithLifecycle()
    val hasServer = SpitifyServerApi.base(settings.webServerUrl) != null

    Column(Modifier.fillMaxSize().background(SpitifyColors.Background)) {
        Row(Modifier.statusBarsPadding().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Account", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            when {
                state.signedIn -> SignedIn(
                    username = state.username.orEmpty(),
                    summary = "${lib.playlists.size} playlists • ${lib.liked.size} liked songs • ${lib.followedArtists.size} artists",
                    status = when {
                        state.syncing -> "Syncing…"
                        state.error != null -> state.error!!
                        state.lastSynced > 0 -> "Saved " + DateUtils.getRelativeTimeSpanString(state.lastSynced)
                        else -> "Not synced yet"
                    },
                    isError = state.error != null && !state.syncing,
                    onSync = { account.syncNow() },
                    onLogOut = {
                        account.signOut()
                        actions.toast("Logged out. Your library stays on this phone.")
                    },
                )
                !hasServer -> NeedsServer(onSetUp = actions::openSettings)
                else -> LoginForm(notice = state.error)
            }
        }
    }
}

@Composable
private fun LoginForm(notice: String?) {
    val account = LocalApp.current.account
    val actions = LocalActions.current
    val focus = LocalFocusManager.current
    var create by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(notice) }

    fun submit() {
        if (busy) return
        if (username.isBlank() || password.isBlank()) {
            error = "Enter a username and password"
            return
        }
        focus.clearFocus()
        busy = true
        error = null
        account.signIn(username, password, create) { result ->
            busy = false
            result.fold(
                { actions.toast(if (create) "Account created. Your library is saved." else "Logged in. Your library is synced.") },
                { error = it.message ?: "Couldn't sign in" },
            )
        }
    }

    Text(
        if (create) "Create your account" else "Log in to Spitify",
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Your playlists, liked songs and followed artists are saved on your own Spitify server, so they come back on any phone you log in on.",
        style = MaterialTheme.typography.bodyMedium,
        color = SpitifyColors.TextSecondary,
    )
    Spacer(Modifier.height(28.dp))

    Field(username, { username = it.trim().lowercase() }, "Username", ImeAction.Next, KeyboardType.Text) { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
    Spacer(Modifier.height(12.dp))
    Field(password, { password = it }, "Password", ImeAction.Done, KeyboardType.Password, secret = true) { submit() }

    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFF15E6C))
    }

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = ::submit,
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = SpitifyColors.Accent, contentColor = SpitifyColors.OnAccent),
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(color = SpitifyColors.OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(if (create) "Create account" else "Log in", style = MaterialTheme.typography.titleSmall)
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        if (create) "Already have an account? Log in" else "New here? Create an account",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                create = !create
                error = null
            }
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun SignedIn(
    username: String,
    summary: String,
    status: String,
    isError: Boolean,
    onSync: () -> Unit,
    onLogOut: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(88.dp).clip(CircleShape).background(SpitifyColors.Accent), contentAlignment = Alignment.Center) {
            Text(
                username.firstOrNull()?.uppercase() ?: "S",
                style = MaterialTheme.typography.displaySmall,
                color = SpitifyColors.OnAccent,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(username, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) Color(0xFFF15E6C) else SpitifyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WhiteButton("Sync now", onClick = onSync)
            OutlineButton("Log out", onClick = onLogOut)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Changes save a couple of seconds after you make them. Play history stays on each phone.",
            style = MaterialTheme.typography.bodySmall,
            color = SpitifyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NeedsServer(onSetUp: () -> Unit) {
    Text("Log in to Spitify", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "Accounts live on your Spitify server. Add its address first under Settings → Music sources → Spitify server, then come back here.",
        style = MaterialTheme.typography.bodyMedium,
        color = SpitifyColors.TextSecondary,
    )
    Spacer(Modifier.height(24.dp))
    WhiteButton("Set up server", onClick = onSetUp)
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    ime: ImeAction,
    type: KeyboardType,
    secret: Boolean = false,
    onIme: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ime, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SpitifyColors.Accent,
            cursorColor = SpitifyColors.Accent,
            focusedLabelColor = SpitifyColors.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
