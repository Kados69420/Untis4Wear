package com.untis4wear

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** JSON (de)serialization of a day's timetable for the offline cache. */
object TimetableCodec {

    class Entry(val date: LocalDate, val slots: List<LessonSlot>)

    fun encode(date: LocalDate, slots: List<LessonSlot>): String {
        val slotArray = JSONArray()
        for (slot in slots) {
            val lessonArray = JSONArray()
            for (lesson in slot.lessons) {
                val o = JSONObject()
                    .put("subject", lesson.subject)
                    .put("room", lesson.room)
                    .put("class", lesson.klass)
                    .put("state", lesson.state.name)
                if (lesson.origRoom != null) o.put("origRoom", lesson.origRoom)
                lessonArray.put(o)
            }
            slotArray.put(JSONObject().put("time", slot.time).put("lessons", lessonArray))
        }
        return JSONObject()
            .put("date", date.toString())
            .put("slots", slotArray)
            .toString()
    }

    /** Returns null if the text is missing or unreadable. */
    fun decode(text: String?): Entry? {
        if (text.isNullOrEmpty()) return null
        return try {
            val root = JSONObject(text)
            val date = LocalDate.parse(root.getString("date"))
            val slotArray = root.getJSONArray("slots")
            val slots = ArrayList<LessonSlot>()
            for (i in 0 until slotArray.length()) {
                val slotObj = slotArray.getJSONObject(i)
                val lessonArray = slotObj.getJSONArray("lessons")
                val lessons = ArrayList<Lesson>()
                for (j in 0 until lessonArray.length()) {
                    val o = lessonArray.getJSONObject(j)
                    val state = try {
                        LessonState.valueOf(o.getString("state"))
                    } catch (e: IllegalArgumentException) {
                        LessonState.NORMAL
                    }
                    lessons.add(
                        Lesson(
                            subject = o.getString("subject"),
                            room = o.getString("room"),
                            klass = o.getString("class"),
                            state = state,
                            origRoom = if (o.isNull("origRoom")) null else o.getString("origRoom")
                        )
                    )
                }
                slots.add(LessonSlot(slotObj.getInt("time"), lessons))
            }
            Entry(date, slots)
        } catch (e: Exception) {
            null
        }
    }
}
