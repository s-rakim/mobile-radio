package com.srakim.spitify.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.ProfilePhoto
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.sheets.Avatar

/**
 * Your own picture for an image loader: the one saved on this phone, else the one on your
 * Spitify server (set from another phone), else null.
 */
@Composable
fun rememberMyPhoto(): Any? {
    val app = LocalApp.current
    val context = LocalContext.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    val local = remember(settings.profilePhoto) { ProfilePhoto.file(context, settings.profilePhoto) }
    val remote = social.me?.avatar
    return remember(local, remote) { local ?: app.social.avatarUrl(remote) }
}

/** Your picture in a circle, or your initial when you haven't picked one. */
@Composable
fun MyAvatar(size: Dp, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    val acct by app.account.state.collectAsStateWithLifecycle()
    val name = social.me?.displayName ?: settings.profileName.ifBlank { acct.username ?: "You" }
    Avatar(name, social.me?.color, size, modifier, image = rememberMyPhoto())
}
