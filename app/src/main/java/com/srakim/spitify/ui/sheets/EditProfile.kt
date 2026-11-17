package com.srakim.spitify.ui.sheets

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.ProfilePhoto
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.components.MyAvatar
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Edit profile, laid out like Spotify's: your picture big in the middle with "Change photo"
 * under it, then your name. Nothing changes until Save, and saving carries on in the
 * background so closing this can't cut an upload off halfway.
 */
@Composable
fun EditProfileDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    val acct by app.account.state.collectAsStateWithLifecycle()

    val startName = remember { social.me?.displayName ?: settings.profileName }
    var name by remember { mutableStateOf(startName) }
    var newPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var choosing by remember { mutableStateOf(false) }
    val hasPhoto = !removePhoto && (newPhoto != null || settings.profilePhoto > 0 || social.me?.avatar != null)
    val changed = name.trim() != startName.trim() || newPhoto != null || removePhoto

    // The system photo picker: no storage permission needed, and older phones get a fallback.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = uri
    }

    fun save() {
        val clean = name.trim()
        val photo = newPhoto
        val remove = removePhoto
        val signedIn = acct.signedIn
        val nameChanged = clean != startName.trim()
        onDismiss()
        app.appScope.launch {
            if (nameChanged) app.settings.update { it.copy(profileName = clean) }
            var problem: String? = null
            if (photo != null) {
                val (version, bytes) = withContext(Dispatchers.IO) { ProfilePhoto.save(app, photo) }
                app.settings.update { it.copy(profilePhoto = version) }
                if (signedIn) runCatching { app.social.uploadAvatar(bytes) }.onFailure { problem = it.message }
            } else if (remove) {
                withContext(Dispatchers.IO) { ProfilePhoto.clear(app) }
                app.settings.update { it.copy(profilePhoto = 0) }
                if (signedIn) runCatching { app.social.removeAvatar() }.onFailure { problem = it.message }
            }
            if (signedIn && nameChanged && clean.isNotBlank()) {
                runCatching { app.social.updateProfile(displayName = clean) }.onFailure { problem = problem ?: it.message }
            }
            actions.toast(
                when {
                    problem == null -> "Profile saved"
                    // An older server doesn't know about pictures yet; the phone still keeps it.
                    problem!!.contains("404") -> "Saved on this phone. Update your Spitify server so friends see your picture."
                    else -> "Saved on this phone. Couldn't update your Spitify profile: $problem"
                },
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(SpitifyColors.Background)
                .systemBarsPadding()
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Cancel") }
                Text(
                    "Edit profile",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = ::save, enabled = changed) {
                    Text(
                        "Save",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (changed) SpitifyColors.TextPrimary else SpitifyColors.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(148.dp)
                    .clip(CircleShape)
                    .clickable { choosing = true },
                contentAlignment = Alignment.Center,
            ) {
                val preview = newPhoto
                when {
                    preview != null -> Image(
                        preview.asImageBitmap(),
                        contentDescription = "Your new picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    removePhoto -> Avatar(name.ifBlank { "You" }, social.me?.color, 148.dp)
                    else -> MyAvatar(148.dp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Change photo",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .clickable { choosing = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Name",
                style = MaterialTheme.typography.labelMedium,
                color = SpitifyColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            TextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = SpitifyColors.TextPrimary,
                    unfocusedIndicatorColor = SpitifyColors.Highlight,
                    cursorColor = SpitifyColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
            Text(
                "This is how you appear on Spitify, in your greeting and to friends.",
                style = MaterialTheme.typography.bodySmall,
                color = SpitifyColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }

    if (choosing) {
        SpitifySheet(onDismiss = { choosing = false }) {
            SheetOption(Icons.Rounded.PhotoLibrary, "Choose photo", onClick = {
                choosing = false
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            if (hasPhoto) {
                SheetOption(Icons.Rounded.DeleteOutline, "Remove current photo", onClick = {
                    choosing = false
                    newPhoto = null
                    removePhoto = true
                })
            }
        }
    }

    picked?.let { uri ->
        CropPhotoDialog(
            uri = uri,
            onCancel = { picked = null },
            onDone = {
                newPhoto = it
                removePhoto = false
                picked = null
            },
        )
    }
}

/**
 * Move and scale a photo inside a circle, then keep what's in it. Pinch to zoom, drag to move,
 * double tap to zoom in or back out. The photo always fills the circle.
 */
@Composable
private fun CropPhotoDialog(uri: Uri, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        runCatching { withContext(Dispatchers.IO) { ProfilePhoto.decode(context, uri) } }
            .onSuccess { bitmap = it }
            .onFailure { failed = true }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var circle by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel", color = SpitifyColors.TextPrimary) }
                Text("Move and scale", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(
                    enabled = bitmap != null,
                    onClick = {
                        val bmp = bitmap ?: return@TextButton
                        // Where the circle sits over the photo, in the photo's own pixels.
                        val k = circle / min(bmp.width, bmp.height) * zoom
                        val side = circle / k
                        val left = (bmp.width * k / 2 - circle / 2 - offset.x) / k
                        val top = (bmp.height * k / 2 - circle / 2 - offset.y) / k
                        onDone(ProfilePhoto.crop(bmp, left, top, side))
                    },
                ) { Text("Choose", color = if (bitmap != null) SpitifyColors.Accent else SpitifyColors.TextMuted, fontWeight = FontWeight.Bold) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = bitmap
                val side = with(LocalDensity.current) { (min(maxWidth.value, maxHeight.value).dp - 32.dp).toPx() }
                SideEffect { circle = side }
                when {
                    bmp != null -> {
                        val image = remember(bmp) { bmp.asImageBitmap() }
                        val fit = side / min(bmp.width, bmp.height)
                        fun clamp(o: Offset, z: Float): Offset {
                            val maxX = (bmp.width * fit * z - side) / 2
                            val maxY = (bmp.height * fit * z - side) / 2
                            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                        }
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(bmp, side) {
                                    detectTransformGestures { _, pan, gestureZoom, _ ->
                                        zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                        offset = clamp(offset + pan, zoom)
                                    }
                                }
                                .pointerInput(bmp, side) {
                                    detectTapGestures(onDoubleTap = {
                                        zoom = if (zoom > 1.4f) 1f else 2f
                                        offset = clamp(offset, zoom)
                                    })
                                },
                        ) {
                            val k = fit * zoom
                            val w = bmp.width * k
                            val h = bmp.height * k
                            drawImage(
                                image,
                                dstOffset = IntOffset((center.x - w / 2 + offset.x).roundToInt(), (center.y - h / 2 + offset.y).roundToInt()),
                                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                                filterQuality = FilterQuality.High,
                            )
                            val hole = Path().apply { addOval(Rect(center, side / 2)) }
                            clipPath(hole, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.62f)) }
                            drawCircle(Color.White.copy(alpha = 0.85f), radius = side / 2, center = center, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                    failed -> Text("Couldn't open that photo. Try another one.", color = SpitifyColors.TextSecondary)
                    else -> CircularProgressIndicator(color = SpitifyColors.Accent)
                }
            }
            Text(
                "Pinch to zoom, drag to move",
                style = MaterialTheme.typography.bodySmall,
                color = SpitifyColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            )
        }
    }
}
