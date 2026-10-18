package com.untis4wear

/**
 * The nine selectable colors of the Garmin app (same indices, same RGB values as Garmin's
 * Graphics.COLOR_* constants), as ARGB ints.
 */
object Palette {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    private val colors = intArrayOf(
        BLACK,                  // 0 black
        WHITE,                  // 1 white
        0xFFFF0000.toInt(),     // 2 red
        0xFF00FF00.toInt(),     // 3 green
        0xFF0000FF.toInt(),     // 4 blue
        0xFFFFFF00.toInt(),     // 5 yellow
        0xFFFF5500.toInt(),     // 6 orange
        0xFFAA00FF.toInt(),     // 7 purple
        0xFFAAAAAA.toInt()      // 8 light gray
    )

    val size: Int get() = colors.size

    fun color(index: Int): Int = colors[index.coerceIn(0, colors.size - 1)]

    /** Black or white, whichever has the better contrast on [background] (WCAG contrast ratio). */
    fun textColorOn(background: Int): Int {
        val l = luminance(background)
        val contrastWhite = 1.05 / (l + 0.05)
        val contrastBlack = (l + 0.05) / 0.05
        return if (contrastBlack >= contrastWhite) BLACK else WHITE
    }

    private fun luminance(argb: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
