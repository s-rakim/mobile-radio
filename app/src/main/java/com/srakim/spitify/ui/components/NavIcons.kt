package com.srakim.spitify.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Tab bar icons. Each tab has a resting outline and a heavier selected version, the way the
 * big streaming apps do it: the shape stays put and only the weight changes, so switching tabs
 * reads as "this one lit up" rather than a different picture popping in.
 *
 * Drawn by hand on a 24 grid. Tint them like any other icon.
 */
object NavIcons {
    private const val HOUSE = "M3.75,10.1L12,3.4L20.25,10.1V20.5H14.6V14.4H9.4V20.5H3.75Z"
    private const val HOUSE_DOOR = "M3.75,10.1L12,3.4L20.25,10.1V20.5H3.75Z M10.2,15.2V20.5H13.8V15.2Z"

    val Home = stroke("Home", 1.7f, HOUSE)
    val HomeSelected = filled("HomeSelected", HOUSE_DOOR)

    private const val LENS = "M10.5,3.25a7.25,7.25 0 1,0 0,14.5a7.25,7.25 0 1,0 0,-14.5z"
    private const val HANDLE = "M15.8,15.8L20.75,20.75"

    val Search = stroke("Search", 1.7f, LENS, HANDLE)
    val SearchSelected = stroke("SearchSelected", 2.7f, LENS, HANDLE)

    // Two upright spines and one leaning against them: a shelf of records.
    private const val SPINE_A = "M4.5,3.5V20.5"
    private const val SPINE_B = "M9.5,3.5V20.5"
    private const val LEANER = "M14,4.6L20,19.9"

    val Library = stroke("Library", 1.7f, SPINE_A, SPINE_B, LEANER)
    val LibrarySelected = stroke("LibrarySelected", 2.9f, SPINE_A, SPINE_B, LEANER)

    val Create = stroke("Create", 1.7f, "M12,4.25V19.75", "M4.25,12H19.75")

    /** The speaker-with-screen glyph used for "pick where this plays". */
    val Devices = stroke(
        "Devices",
        1.6f,
        "M5.5,3.5H18.5a1.5,1.5 0 0,1 1.5,1.5V19a1.5,1.5 0 0,1 -1.5,1.5H5.5a1.5,1.5 0 0,1 -1.5,-1.5V5a1.5,1.5 0 0,1 1.5,-1.5z",
        "M12,10.5a3.5,3.5 0 1,0 0,7a3.5,3.5 0 1,0 0,-7z",
        "M12,6.3V6.7",
    )

    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun filled(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = addPathNodes(d),
                fill = SolidColor(Color.Black),
                pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}
