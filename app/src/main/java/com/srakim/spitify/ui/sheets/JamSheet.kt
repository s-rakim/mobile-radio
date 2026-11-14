package com.srakim.spitify.ui.sheets

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.srakim.spitify.data.Jam
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.MenuIcons
import com.srakim.spitify.ui.components.OutlineButton
import com.srakim.spitify.ui.theme.SpitifyColors

/**
 * Start a Jam: plays the song if it isn't already on, then shows a code friends scan with their
 * camera (or a link they tap) to drop into the same queue at the same spot on their own phone.
 */
@Composable
fun JamSheet(track: Track, onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val context = LocalContext.current
    val state by player.state.collectAsStateWithLifecycle()

    LaunchedEffect(track.id) {
        if (state.current?.id != track.id) {
            player.play(listOf(track), 0, PlayContext("jam", "Your Jam", "JAM"))
        }
    }

    // Rebuilt as the queue changes, so the code always matches what's playing.
    val playing = state.current?.id == track.id
    val queue = if (playing) listOfNotNull(state.current) + state.upNext.map { it.track } else listOf(track)
    val link = Jam.link(queue, if (playing) state.positionMs else 0L)

    SpitifySheet(onDismiss, scrollable = true) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(SpitifyColors.Highlight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MenuIcons.Jam, contentDescription = null, tint = SpitifyColors.Accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Start a Jam", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Friends scan this with their phone camera and Spitify picks up your queue right where you are.",
                style = MaterialTheme.typography.bodyMedium,
                color = SpitifyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            if (link == null) {
                Text(
                    "Jams work with songs from Audius. Songs from your phone or your own server can't be sent to someone else.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(232.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SpitifyColors.TextPrimary)
                        .padding(16.dp),
                ) {
                    QrCode(link, SpitifyColors.Base, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${queue.count { it.isAudius }.coerceAtMost(40)} songs in this Jam",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlineButton("Done", onClick = onDismiss)
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(SpitifyColors.Accent)
                            .clickable {
                                // Rebuild at the moment of sharing so the playhead is current.
                                val fresh = Jam.link(queue, player.state.value.positionMs.takeIf { playing } ?: 0L) ?: link
                                val text = "Join my Jam on Spitify: $fresh\nNow playing: ${track.title} by ${track.artist}" +
                                    track.link?.let { "\n$it" }.orEmpty()
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(send, "Invite to Jam"))
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Text("Share invite", style = MaterialTheme.typography.labelLarge, color = SpitifyColors.OnAccent)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Draws [text] as a QR code in [color] on whatever background sits behind it. */
@Composable
private fun QrCode(text: String, color: Color, modifier: Modifier) {
    // The playhead changes every second; only the song list really needs a new code.
    val stable = text.substringBefore("&p=")
    val matrix = remember(stable) { encode(text) }
    Canvas(modifier) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m[x, y]) drawRect(color, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

private fun encode(text: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L),
    )
}.getOrNull()
