package com.untis4wear

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

class MainActivity : Activity() {

    private lateinit var app: UntisApp
    private lateinit var view: TimetableView
    private val handler = Handler(Looper.getMainLooper())
    private var menuDialog: AlertDialog? = null
    private var lastRotaryAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            app.controller.tick()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as UntisApp

        view = TimetableView(this)
        view.onNext = { app.controller.nextLesson() }
        view.onPrevious = { app.controller.previousLesson() }
        view.setOnClickListener { showMenu() }
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        val controller = app.controller
        val settings = app.settingsStore.load()
        val settingsChanged = settings != app.lastSettings
        val awayLongEnough = SystemClock.elapsedRealtime() - app.lastStopAt > RESET_AFTER_MS

        view.settings = settings
        controller.onChanged = { view.model = controller.model() }

        if (settingsChanged || awayLongEnough) {
            // Like starting the Garmin app: jump to today / the next lesson and reload.
            controller.today()
        } else {
            controller.tick()
        }
        app.lastSettings = settings
        view.model = controller.model()

        handler.postDelayed(ticker, TICK_INTERVAL_MS)

        if (!settings.isConfigured && !app.promptedSetup) {
            app.promptedSetup = true
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
        app.controller.onChanged = null
        menuDialog?.dismiss()
        menuDialog = null
        app.lastStopAt = SystemClock.elapsedRealtime()
    }

    private fun showMenu() {
        if (menuDialog?.isShowing == true) return
        val controller = app.controller
        val labels = arrayOf(
            getString(R.string.menu_next_lesson),
            getString(R.string.menu_previous_lesson),
            getString(R.string.menu_next_day),
            getString(R.string.menu_previous_day),
            getString(R.string.menu_today),
            getString(R.string.menu_refresh),
            getString(R.string.menu_settings)
        )
        menuDialog = AlertDialog.Builder(this)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> controller.nextLesson()
                    1 -> controller.previousLesson()
                    2 -> controller.nextDay()
                    3 -> controller.previousDay()
                    4 -> controller.today()
                    5 -> controller.refresh()
                    6 -> startActivity(Intent(this, SettingsActivity::class.java))
                    else -> Unit
                }
            }
            .show()
    }

    /** Rotating crown / bezel: clockwise = next lesson, counter-clockwise = previous lesson. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (delta != 0f && event.eventTime - lastRotaryAt >= ROTARY_STEP_MS) {
                lastRotaryAt = event.eventTime
                // Same sign convention as ScrollView: negative = clockwise = move down.
                if (delta < 0f) app.controller.nextLesson() else app.controller.previousLesson()
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private companion object {
        const val TICK_INTERVAL_MS = 20_000L
        const val RESET_AFTER_MS = 60_000L
        const val ROTARY_STEP_MS = 120L
    }
}
