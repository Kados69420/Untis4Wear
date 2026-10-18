package com.untis4wear

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Executor

/** What the screen should currently show. */
enum class Status { NOT_CONFIGURED, LOADING, ERROR, NO_LESSONS, NO_MORE_LESSONS, LESSON }

/** Small dot next to the time: data comes from the offline cache (yellow) / refresh failed (red). */
enum class Indicator { NONE, CACHED, CACHED_ERROR }

data class UiModel(
    val date: LocalDate,
    val now: LocalDateTime,
    val status: Status,
    /** Set when [status] is [Status.LESSON]. */
    val slot: LessonSlot?,
    val indicator: Indicator
)

fun interface TimetableSource {
    /** Blocking, called on a background thread. Throws on any failure. */
    fun fetch(settings: Settings, date: LocalDate): List<LessonSlot>
}

/** Stores the timetable of *today* so it can be shown without a network connection. */
interface TimetableCache {
    fun load(date: LocalDate): List<LessonSlot>?
    fun save(date: LocalDate, slots: List<LessonSlot>)
}

/**
 * All navigation/loading logic of the original app (Untis4GarminActions + the state handling inside
 * Untis4GarminView.onUpdate), free of any Android UI classes.
 *
 * Threading: every public method must be called on the thread that [main] executes on.
 * [onChanged] is invoked on that thread, too.
 */
class TimetableController(
    private val settingsProvider: () -> Settings,
    private val source: TimetableSource,
    private val cache: TimetableCache,
    private val clock: () -> LocalDateTime,
    private val background: Executor,
    private val main: Executor
) {
    var onChanged: (() -> Unit)? = null

    private var now: LocalDateTime = clock()
    private var date: LocalDate = now.toLocalDate()

    /** Selected lesson slot; only used while [autoSelect] is false. */
    private var lessonIndex = 0

    /** True: show the next upcoming lesson for [now] (original: updateLessonNumber). */
    private var autoSelect = true

    private var configured = settingsProvider().isConfigured
    private var fresh: List<LessonSlot>? = null
    private var fetchFailed = false

    @Volatile
    private var requestId = 0L

    // ---- Actions ----------------------------------------------------------------------------

    fun today() {
        now = clock()
        date = now.toLocalDate()
        lessonIndex = 0
        autoSelect = true
        fetch()
    }

    fun refresh() {
        lessonIndex = 0
        autoSelect = false
        fetch()
    }

    fun nextDay() {
        date = date.plusDays(1)
        lessonIndex = 0
        autoSelect = false
        fetch()
    }

    fun previousDay() {
        date = date.minusDays(1)
        lessonIndex = 0
        autoSelect = false
        fetch()
    }

    fun nextLesson() {
        val data = availableData()
        if (data != null) {
            val index = effectiveIndex(data)
            if (index < data.size) {
                lessonIndex = index + 1
                autoSelect = false
                notifyChanged()
                return
            }
        }
        nextDay()
    }

    fun previousLesson() {
        val data = availableData()
        val index = if (data != null) effectiveIndex(data) else lessonIndex
        if (index > 0) {
            lessonIndex = index - 1
            autoSelect = false
            notifyChanged()
        } else {
            previousDay()
        }
    }

    /** Call regularly (e.g. every 30 s) so the clock and the "next lesson" selection stay current. */
    fun tick() {
        val current = clock()
        if (current.hour != now.hour || current.minute != now.minute || current.toLocalDate() != now.toLocalDate()) {
            now = current
            notifyChanged()
        }
    }

    // ---- State ------------------------------------------------------------------------------

    fun model(): UiModel {
        if (!configured) return UiModel(date, now, Status.NOT_CONFIGURED, null, Indicator.NONE)

        val freshData = fresh
        val cached = if (freshData == null && isToday(date)) cache.load(date) else null
        val data = freshData ?: cached
        val fromCache = cached != null

        if (data == null) {
            val status = if (fetchFailed) Status.ERROR else Status.LOADING
            return UiModel(date, now, status, null, Indicator.NONE)
        }

        val indicator = when {
            !fromCache -> Indicator.NONE
            fetchFailed -> Indicator.CACHED_ERROR
            else -> Indicator.CACHED
        }
        if (data.isEmpty()) return UiModel(date, now, Status.NO_LESSONS, null, indicator)

        val index = effectiveIndex(data)
        if (index >= data.size) return UiModel(date, now, Status.NO_MORE_LESSONS, null, indicator)
        return UiModel(date, now, Status.LESSON, data[index], indicator)
    }

    // ---- Internals --------------------------------------------------------------------------

    /** Fresh data if there is some, otherwise today's cached timetable if we are on today. */
    private fun availableData(): List<LessonSlot>? {
        fresh?.let { return it }
        return if (isToday(date)) cache.load(date) else null
    }

    private fun isToday(d: LocalDate): Boolean = d == clock().toLocalDate()

    private fun effectiveIndex(data: List<LessonSlot>): Int {
        if (autoSelect) return nextLessonIndex(data, now.hour * 100 + now.minute)
        return minOf(lessonIndex, data.size)
    }

    private fun fetch() {
        val id = ++requestId
        fresh = null
        fetchFailed = false

        val settings = settingsProvider()
        configured = settings.isConfigured
        if (!configured) {
            notifyChanged()
            return
        }

        val requestedDate = date
        notifyChanged()

        background.execute {
            // Skip requests that were already superseded while waiting in the queue.
            if (id != requestId) return@execute
            val result: List<LessonSlot>? = try {
                source.fetch(settings, requestedDate)
            } catch (e: Exception) {
                null // reported through fetchFailed in onFetched
            }
            main.execute { onFetched(id, requestedDate, result) }
        }
    }

    private fun onFetched(id: Long, requestedDate: LocalDate, result: List<LessonSlot>?) {
        if (id != requestId) return // user already moved on
        if (result == null) {
            fetchFailed = true
        } else {
            fresh = result
            if (isToday(requestedDate)) {
                try {
                    cache.save(requestedDate, result)
                } catch (e: Exception) {
                    // the cache is optional
                }
            }
        }
        notifyChanged()
    }

    private fun notifyChanged() {
        onChanged?.invoke()
    }

    companion object {
        /**
         * Index of the first slot that starts after [hhmm]; data.size if there is none
         * ("no more lessons"). Same rule as the original app.
         */
        fun nextLessonIndex(data: List<LessonSlot>, hhmm: Int): Int {
            val index = data.indexOfFirst { it.time > hhmm }
            return if (index >= 0) index else data.size
        }
    }
}
