package com.untis4wear

/** Mirrors the "state" field of the original app: 0 = cancelled, 1 = normal, 2 = changed. */
enum class LessonState { CANCELLED, NORMAL, CHANGED }

data class Lesson(
    val subject: String,
    val room: String,
    /** Class(es), comma separated. */
    val klass: String,
    val state: LessonState,
    /** Original room if the room was changed, otherwise null (the original app used -1). */
    val origRoom: String?
)

/** All lessons that start at the same time. [time] is WebUntis' HHmm, e.g. 745 for 07:45. */
data class LessonSlot(
    val time: Int,
    val lessons: List<Lesson>
)

/** User configuration. Same options as the Garmin app's settings page. */
data class Settings(
    val username: String = "",
    val password: String = "",
    /** WebUntis host, e.g. "mese.webuntis.com". */
    val untisUrl: String = "",
    val schoolName: String = "",
    /** Color indices, see [Palette]. */
    val normalColor: Int = 5,
    val cancelledColor: Int = 8,
    val changedColor: Int = 7,
    val noSubjectColor: Int = 4,
    /** 0 = DD.MM.YYYY, 1 = MM.DD.YYYY, 2 = YYYY.MM.DD */
    val dateSystem: Int = 0,
    val dateDelimiter: String = "."
) {
    val isConfigured: Boolean
        get() = username.isNotBlank() && password.isNotEmpty() &&
            untisUrl.isNotBlank() && schoolName.isNotBlank()
}
