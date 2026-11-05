package dev.pvps.terminal

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle

data class TerminalPalette(
    val id: String,
    val name: String,
    val background: Int,
    val foreground: Int,
    val cursor: Int,
    val ansi: IntArray
)

object TerminalPalettes {
    val tokyoNight = TerminalPalette(
        id = "tokyo-night",
        name = "Tokyo Night",
        background = 0xFF1A1B26.toInt(),
        foreground = 0xFFC0CAF5.toInt(),
        cursor = 0xFF7AA2F7.toInt(),
        ansi = intArrayOf(
            0xFF15161E.toInt(), 0xFFF7768E.toInt(), 0xFF9ECE6A.toInt(), 0xFFE0AF68.toInt(),
            0xFF7AA2F7.toInt(), 0xFFBB9AF7.toInt(), 0xFF7DCFFF.toInt(), 0xFFA9B1D6.toInt(),
            0xFF414868.toInt(), 0xFFFF7A93.toInt(), 0xFFB9F27C.toInt(), 0xFFFF9E64.toInt(),
            0xFF7DA6FF.toInt(), 0xFFBB9AF7.toInt(), 0xFF0DB9D7.toInt(), 0xFFC0CAF5.toInt()
        )
    )

    val dracula = TerminalPalette(
        id = "dracula",
        name = "Dracula",
        background = 0xFF282A36.toInt(),
        foreground = 0xFFF8F8F2.toInt(),
        cursor = 0xFFBD93F9.toInt(),
        ansi = intArrayOf(
            0xFF21222C.toInt(), 0xFFFF5555.toInt(), 0xFF50FA7B.toInt(), 0xFFF1FA8C.toInt(),
            0xFFBD93F9.toInt(), 0xFFFF79C6.toInt(), 0xFF8BE9FD.toInt(), 0xFFF8F8F2.toInt(),
            0xFF6272A4.toInt(), 0xFFFF6E6E.toInt(), 0xFF69FF94.toInt(), 0xFFFFFFA5.toInt(),
            0xFFD6ACFF.toInt(), 0xFFFF92DF.toInt(), 0xFFA4FFFF.toInt(), 0xFFFFFFFF.toInt()
        )
    )

    val solarizedDark = TerminalPalette(
        id = "solarized-dark",
        name = "Solarized Dark",
        background = 0xFF002B36.toInt(),
        foreground = 0xFF839496.toInt(),
        cursor = 0xFF93A1A1.toInt(),
        ansi = intArrayOf(
            0xFF073642.toInt(), 0xFFDC322F.toInt(), 0xFF859900.toInt(), 0xFFB58900.toInt(),
            0xFF268BD2.toInt(), 0xFFD33682.toInt(), 0xFF2AA198.toInt(), 0xFFEEE8D5.toInt(),
            0xFF002B36.toInt(), 0xFFCB4B16.toInt(), 0xFF586E75.toInt(), 0xFF657B83.toInt(),
            0xFF839496.toInt(), 0xFF6C71C4.toInt(), 0xFF93A1A1.toInt(), 0xFFFDF6E3.toInt()
        )
    )

    val all = listOf(tokyoNight, dracula, solarizedDark)
    fun byId(id: String): TerminalPalette = all.firstOrNull { it.id == id } ?: tokyoNight
}

fun applyPalette(emulator: TerminalEmulator, palette: TerminalPalette) {
    val colors = emulator.mColors.mCurrentColors
    for (i in 0 until minOf(16, colors.size)) colors[i] = palette.ansi[i]
    var index = 16
    val levels = intArrayOf(0, 95, 135, 175, 215, 255)
    for (r in levels) {
        for (g in levels) {
            for (b in levels) {
                if (index >= colors.size) break
                colors[index++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
    for (i in 0 until 24) {
        if (index >= colors.size) break
        val v = 8 + i * 10
        colors[index++] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    if (colors.size > TextStyle.COLOR_INDEX_FOREGROUND) colors[TextStyle.COLOR_INDEX_FOREGROUND] = palette.foreground
    if (colors.size > TextStyle.COLOR_INDEX_BACKGROUND) colors[TextStyle.COLOR_INDEX_BACKGROUND] = palette.background
    if (colors.size > TextStyle.COLOR_INDEX_CURSOR) colors[TextStyle.COLOR_INDEX_CURSOR] = palette.cursor
}
