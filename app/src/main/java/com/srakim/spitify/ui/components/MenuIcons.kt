package com.srakim.spitify.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Thin outlined icons for the song menu, drawn to match Spotify's line style: 1.6 stroke,
 * round ends, no fills. Material's set is chunkier, so these are hand drawn on a 24 grid.
 * Tint them like any other icon.
 */
object MenuIcons {
    private const val RING = "M12,2.75a9.25,9.25 0 1,0 0,18.5a9.25,9.25 0 1,0 0,-18.5z"

    val Share = icon(
        "Share",
        "M18,2.6a2.6,2.6 0 1,0 0,5.2a2.6,2.6 0 1,0 0,-5.2z",
        "M6,9.4a2.6,2.6 0 1,0 0,5.2a2.6,2.6 0 1,0 0,-5.2z",
        "M18,16.2a2.6,2.6 0 1,0 0,5.2a2.6,2.6 0 1,0 0,-5.2z",
        "M8.3,10.7L15.7,6.3",
        "M8.3,13.3L15.7,17.7",
    )

    val AddToPlaylist = icon("AddToPlaylist", RING, "M12,7.5V16.5", "M7.5,12H16.5")

    val RemoveFromPlaylist = icon("RemoveFromPlaylist", RING, "M7.5,12H16.5")

    val AddToQueue = icon(
        "AddToQueue",
        "M5,2.5V8.5",
        "M2,5.5H8",
        "M11,4H18.5a2.5,2.5 0 0,1 0,5H11",
        "M4,14H20",
        "M4,19.5H20",
    )

    val Queue = icon(
        "Queue",
        "M6.5,3.5H17.5a2.5,2.5 0 0,1 0,5H6.5a2.5,2.5 0 0,1 0,-5z",
        "M4,14H20",
        "M4,19.5H20",
    )

    val Album = icon("Album", RING, "M12,9.5a2.5,2.5 0 1,0 0,5a2.5,2.5 0 1,0 0,-5z")

    val Artist = icon(
        "Artist",
        "M9.5,3.25a4,4 0 1,0 0,8a4,4 0 1,0 0,-8z",
        "M2.75,21V19.5a5,5 0 0,1 5,-5H11.5a5,5 0 0,1 2.4,0.6",
        "M20,12.5V20",
        "M18.5,18.5a1.5,1.5 0 1,0 0,3a1.5,1.5 0 1,0 0,-3z",
        "M20,12.5L22,13.25",
    )

    val Jam = icon(
        "Jam",
        "M9,8.4a1.8,1.8 0 1,0 0,3.6a1.8,1.8 0 1,0 0,-3.6z",
        "M15,8.4a1.8,1.8 0 1,0 0,3.6a1.8,1.8 0 1,0 0,-3.6z",
        "M6,16.6a3,3 0 0,1 6,0",
        "M12,16.6a3,3 0 0,1 6,0",
        "M3.1,13.5A9,9 0 0,0 9.6,20.7",
        "M9.6,20.7L7.4,21.6",
        "M9.6,20.7L8.5,18.6",
        "M20.9,10.5A9,9 0 0,0 14.4,3.3",
        "M14.4,3.3L16.6,2.4",
        "M14.4,3.3L15.5,5.4",
    )

    val Exclude = icon("Exclude", RING, "M8.8,8.8L15.2,15.2", "M15.2,8.8L8.8,15.2")

    val Include = icon("Include", RING, "M7.8,12.3L10.6,15.1L16.2,9.3")

    val Radio = icon(
        "Radio",
        "M12,10.4a1.6,1.6 0 1,0 0,3.2a1.6,1.6 0 1,0 0,-3.2z",
        "M8.6,8.4a5,5 0 0,0 0,7.2",
        "M15.4,8.4a5,5 0 0,1 0,7.2",
        "M5.6,5.4a9.2,9.2 0 0,0 0,13.2",
        "M18.4,5.4a9.2,9.2 0 0,1 0,13.2",
    )

    val Credits = icon(
        "Credits",
        "M3,4H12",
        "M3,9H9",
        "M3,14H9",
        "M3,20.5H21",
        "M19.5,3.5V13",
        "M17.5,11.5a2,2 0 1,0 0,4a2,2 0 1,0 0,-4z",
        "M19.5,3.5L21.5,4.2",
    )

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
