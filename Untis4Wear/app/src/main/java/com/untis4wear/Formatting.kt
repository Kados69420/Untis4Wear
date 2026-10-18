package com.untis4wear

import java.time.LocalDate

object Formatting {
    /** "H:mm", e.g. 7:05 (hour not padded, like the original app). */
    fun timeOfDay(hour: Int, minute: Int): String = "$hour:${minute.toString().padStart(2, '0')}"

    /** WebUntis HHmm integer (e.g. 745) to "7:45". */
    fun lessonTime(hhmm: Int): String = timeOfDay(hhmm / 100, hhmm % 100)

    /**
     * Date in the user's chosen order and delimiter. Day/month are not zero padded
     * (same as the original app).
     */
    fun date(date: LocalDate, dateSystem: Int, delimiter: String): String {
        val d = date.dayOfMonth
        val m = date.monthValue
        val y = date.year
        return when (dateSystem) {
            0 -> "$d$delimiter$m$delimiter$y"
            1 -> "$m$delimiter$d$delimiter$y"
            else -> "$y$delimiter$m$delimiter$d"
        }
    }
}
