package com.untis4wear

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the result of the WebUntis JSON-RPC "getTimetable" call into lesson slots.
 * Port of UntisApiClient.requestTimetableCallback: entries are grouped by start time,
 * slots are sorted by start time.
 */
object TimetableParser {

    fun parse(result: JSONArray): List<LessonSlot> {
        // LinkedHashMap keeps the order in which the entries arrived inside a slot.
        val groups = LinkedHashMap<Int, MutableList<Lesson>>()
        for (i in 0 until result.length()) {
            val entry = result.optJSONObject(i) ?: continue
            if (entry.isNull("startTime")) continue
            val start = entry.optInt("startTime", -1)
            if (start < 0) continue
            groups.getOrPut(start) { ArrayList() }.add(toLesson(entry))
        }
        return groups.entries
            .map { LessonSlot(it.key, it.value) }
            .sortedBy { it.time }
    }

    private fun toLesson(entry: JSONObject): Lesson {
        val subjects = entry.optJSONArray("su")
        val rooms = entry.optJSONArray("ro")
        val classes = entry.optJSONArray("kl")

        val subject = nameAt(subjects, 0)
        val room = nameAt(rooms, 0)

        val klass = StringBuilder()
        if (classes != null) {
            for (i in 0 until classes.length()) {
                if (i > 0) klass.append(", ")
                klass.append(nameAt(classes, i))
            }
        }

        val state = when (text(entry, "code")) {
            "cancelled" -> LessonState.CANCELLED
            "irregular" -> LessonState.CHANGED
            else -> LessonState.NORMAL
        }

        val firstRoom = rooms?.optJSONObject(0)
        val origRoom = if (firstRoom != null && !firstRoom.isNull("orgname")) {
            firstRoom.optString("orgname", "").ifBlank { null }
        } else {
            null
        }

        return Lesson(subject, room, klass.toString(), state, origRoom)
    }

    private fun nameAt(array: JSONArray?, index: Int): String {
        val element = array?.optJSONObject(index) ?: return ""
        return text(element, "name")
    }

    /** Android's JSONObject.optString returns the string "null" for JSON null, avoid that. */
    private fun text(obj: JSONObject, key: String): String =
        if (obj.isNull(key)) "" else obj.optString(key, "")
}
