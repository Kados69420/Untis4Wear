package com.untis4wear

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Settings in app-private SharedPreferences (not included in backups, see manifest). */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            username = prefs.getString(KEY_USERNAME, d.username) ?: d.username,
            password = prefs.getString(KEY_PASSWORD, d.password) ?: d.password,
            untisUrl = prefs.getString(KEY_URL, d.untisUrl) ?: d.untisUrl,
            schoolName = prefs.getString(KEY_SCHOOL, d.schoolName) ?: d.schoolName,
            normalColor = prefs.getInt(KEY_NORMAL, d.normalColor),
            cancelledColor = prefs.getInt(KEY_CANCELLED, d.cancelledColor),
            changedColor = prefs.getInt(KEY_CHANGED, d.changedColor),
            noSubjectColor = prefs.getInt(KEY_NONE, d.noSubjectColor),
            dateSystem = prefs.getInt(KEY_DATE_SYSTEM, d.dateSystem),
            dateDelimiter = (prefs.getString(KEY_DELIMITER, d.dateDelimiter) ?: d.dateDelimiter)
                .ifEmpty { d.dateDelimiter }
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putString(KEY_USERNAME, s.username)
            .putString(KEY_PASSWORD, s.password)
            .putString(KEY_URL, s.untisUrl)
            .putString(KEY_SCHOOL, s.schoolName)
            .putInt(KEY_NORMAL, s.normalColor)
            .putInt(KEY_CANCELLED, s.cancelledColor)
            .putInt(KEY_CHANGED, s.changedColor)
            .putInt(KEY_NONE, s.noSubjectColor)
            .putInt(KEY_DATE_SYSTEM, s.dateSystem)
            .putString(KEY_DELIMITER, s.dateDelimiter)
            .apply()
    }

    private companion object {
        const val KEY_USERNAME = "Username"
        const val KEY_PASSWORD = "Password"
        const val KEY_URL = "UntisUrl"
        const val KEY_SCHOOL = "SchoolName"
        const val KEY_NORMAL = "SubjectNormalColor"
        const val KEY_CANCELLED = "SubjectCancelledColor"
        const val KEY_CHANGED = "SubjectChangedColor"
        const val KEY_NONE = "NoSubjectColor"
        const val KEY_DATE_SYSTEM = "DateSystem"
        const val KEY_DELIMITER = "DateDelimiter"
    }
}

/** Offline copy of today's timetable (the original app used Application.Storage for this). */
class PrefsTimetableCache(context: Context) : TimetableCache {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("timetable_cache", Context.MODE_PRIVATE)

    override fun load(date: LocalDate): List<LessonSlot>? {
        val entry = TimetableCodec.decode(prefs.getString(KEY, null)) ?: return null
        return if (entry.date == date) entry.slots else null
    }

    /** Only one day is kept (the original cleared the storage before saving). */
    override fun save(date: LocalDate, slots: List<LessonSlot>) {
        prefs.edit().putString(KEY, TimetableCodec.encode(date, slots)).apply()
    }

    private companion object {
        const val KEY = "today"
    }
}
