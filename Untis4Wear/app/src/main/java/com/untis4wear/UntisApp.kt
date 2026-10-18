package com.untis4wear

import android.app.Application
import java.time.LocalDateTime
import java.util.concurrent.Executors

/**
 * Process-wide state holder. The controller lives here so the timetable position survives
 * activity re-creation (font size / theme changes etc.).
 */
class UntisApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var controller: TimetableController
        private set

    /** Settings the controller was last (re)started with, null before the first start. */
    var lastSettings: Settings? = null

    /** Time (SystemClock.elapsedRealtime) at which the main screen was last hidden. */
    var lastStopAt: Long = 0L

    /** The setup screen is opened automatically only once per process. */
    var promptedSetup: Boolean = false

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        val api = UntisApi()
        controller = TimetableController(
            settingsProvider = { settingsStore.load() },
            source = TimetableSource { settings, date -> api.fetchTimetable(settings, date) },
            cache = PrefsTimetableCache(this),
            clock = { LocalDateTime.now() },
            background = Executors.newSingleThreadExecutor(),
            main = mainExecutor
        )
    }
}
