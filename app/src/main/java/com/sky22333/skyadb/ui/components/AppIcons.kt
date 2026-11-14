package com.sky22333.skyadb.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Action icons that are not available in Miuix. */
object AppIcons {
    val Wifi = icon("Wifi", "M2 9 Q12 0 22 9 M5 12 Q12 6 19 12 M8 15 Q12 11.5 16 15 M12 19 L12.01 19")
    val Device = icon("Device", "M7 2 H17 Q19 2 19 4 V20 Q19 22 17 22 H7 Q5 22 5 20 V4 Q5 2 7 2 M10 19 H14")
    val DeviceApps = icon("DeviceApps", "M7 2 H17 Q19 2 19 4 V20 Q19 22 17 22 H7 Q5 22 5 20 V4 Q5 2 7 2 M9 7 H10 M14 7 H15 M9 11 H10 M14 11 H15 M10 18 H14")
    val Install = icon("Install", "M3 9 L9 6 L15 9 V17 L9 20 L3 17 Z M3 9 L9 12 L15 9 M9 12 V20 M19 4 V14 M16 11 L19 14 L22 11")
    val Terminal = icon("Terminal", "M4 4 H20 Q21 4 21 5 V19 Q21 20 20 20 H4 Q3 20 3 19 V5 Q3 4 4 4 M7 9 L10 12 L7 15 M12 15 H17")
    val Remote = icon("Remote", "M8 2 H16 Q18 2 18 4 V20 Q18 22 16 22 H8 Q6 22 6 20 V4 Q6 2 8 2 M12 5 V6 M9 10 H15 M12 7 V13 M9 17 H9.1 M15 17 H15.1")
    val PlayPause = icon("PlayPause", "M4 5 L13 12 L4 19 Z M16 6 V18 M20 6 V18")
    val RecentTasks = icon("RecentTasks", "M6 4 H18 Q20 4 20 6 V18 Q20 20 18 20 H6 Q4 20 4 18 V6 Q4 4 6 4 Z")
    val Power = icon("Power", "M12 2 L12 12 M7.1 5.1 A8 8 0 1 0 16.9 5.1")
    val Wake = icon("Wake", "M12 7 A5 5 0 1 0 12 17 A5 5 0 1 0 12 7 M12 2 L12 4 M12 20 L12 22 M2 12 L4 12 M20 12 L22 12 M5 5 L6.5 6.5 M17.5 17.5 L19 19 M19 5 L17.5 6.5 M6.5 17.5 L5 19")
    val Sleep = icon("Sleep", "M19 15 A8 8 0 0 1 9 5 A8 8 0 1 0 19 15 Z")
    val VolumeDown = icon("VolumeDown", "M3 9 L7 9 L12 5 L12 19 L7 15 L3 15 Z M16 9 Q19 12 16 15")
    val SkipNext = icon("SkipNext", "M5 5 L15 12 L5 19 Z M19 5 L19 19")
    val SkipPrevious = icon("SkipPrevious", "M19 5 L9 12 L19 19 Z M5 5 L5 19")
    val Keyboard = icon("Keyboard", "M3 6 L21 6 L21 18 L3 18 Z M6 9 L7 9 M10 9 L11 9 M14 9 L15 9 M18 9 L19 9 M6 12 L7 12 M10 12 L11 12 M14 12 L15 12 M18 12 L19 12 M7 15 L17 15")
    val ArrowUp = icon("ArrowUp", "M12 20 V4 M5 11 L12 4 L19 11")

    private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()
}
