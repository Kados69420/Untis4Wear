package com.untis4wear

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/**
 * Replaces the Garmin Connect IQ settings page (which lives on the phone) with a settings screen
 * on the watch itself. Everything is built in code to avoid any layout/AndroidX dependency.
 */
class SettingsActivity : Activity() {

    private lateinit var store: SettingsStore
    private lateinit var scroll: ScrollView
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var url: EditText
    private lateinit var school: EditText
    private lateinit var delimiter: EditText
    private lateinit var normalColor: Spinner
    private lateinit var cancelledColor: Spinner
    private lateinit var changedColor: Spinner
    private lateinit var noSubjectColor: Spinner
    private lateinit var dateSystem: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = (application as UntisApp).settingsStore
        val s = store.load()

        val round = resources.configuration.isScreenRound
        val sidePad = dp(if (round) 30 else 14)
        val topPad = dp(if (round) 40 else 14)
        val bottomPad = dp(if (round) 52 else 14)

        val colorNames = resources.getStringArray(R.array.color_names)
        val dateNames = resources.getStringArray(R.array.date_system_names)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sidePad, topPad, sidePad, bottomPad)
        }

        column.addView(TextView(this).apply {
            setText(R.string.settings_title)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, rowParams())

        username = field(
            column, R.string.setting_username, s.username,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            EditorInfo.IME_ACTION_NEXT
        )
        password = field(
            column, R.string.setting_password, s.password,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            EditorInfo.IME_ACTION_NEXT
        )
        url = field(
            column, R.string.setting_url, s.untisUrl,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            EditorInfo.IME_ACTION_NEXT
        )
        school = field(
            column, R.string.setting_school, s.schoolName,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            EditorInfo.IME_ACTION_DONE
        )
        normalColor = spinner(column, R.string.setting_color_normal, colorNames, s.normalColor)
        cancelledColor = spinner(column, R.string.setting_color_cancelled, colorNames, s.cancelledColor)
        changedColor = spinner(column, R.string.setting_color_changed, colorNames, s.changedColor)
        noSubjectColor = spinner(column, R.string.setting_color_none, colorNames, s.noSubjectColor)
        dateSystem = spinner(column, R.string.setting_date_system, dateNames, s.dateSystem)
        delimiter = field(
            column, R.string.setting_date_delimiter, s.dateDelimiter,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            EditorInfo.IME_ACTION_DONE
        )

        val saveButton = Button(this).apply {
            setText(R.string.settings_save)
            setOnClickListener { save() }
        }
        column.addView(saveButton, rowParams().apply { topMargin = dp(14) })

        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(column)
            // Targeting API 35+ means edge-to-edge: the window is no longer resized for the keyboard,
            // so keep the content above the keyboard / system bars ourselves.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                insets
            }
        }
        setContentView(scroll)
    }

    private fun save() {
        store.save(
            Settings(
                username = username.text.toString().trim(),
                password = password.text.toString(),
                untisUrl = url.text.toString().trim(),
                schoolName = school.text.toString().trim(),
                normalColor = normalColor.selectedItemPosition,
                cancelledColor = cancelledColor.selectedItemPosition,
                changedColor = changedColor.selectedItemPosition,
                noSubjectColor = noSubjectColor.selectedItemPosition,
                dateSystem = dateSystem.selectedItemPosition,
                dateDelimiter = delimiter.text.toString().ifEmpty { "." }
            )
        )
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun label(parent: LinearLayout, textRes: Int) {
        parent.addView(TextView(this).apply {
            setText(textRes)
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, dp(12), 0, dp(2))
        }, rowParams())
    }

    private fun field(
        parent: LinearLayout,
        labelRes: Int,
        value: String,
        type: Int,
        ime: Int
    ): EditText {
        label(parent, labelRes)
        val edit = EditText(this).apply {
            setText(value)
            inputType = type
            imeOptions = ime
            textSize = 14f
        }
        parent.addView(edit, rowParams())
        return edit
    }

    private fun spinner(parent: LinearLayout, labelRes: Int, items: Array<String>, selected: Int): Spinner {
        label(parent, labelRes)
        // A dialog list is easier to use on a small round screen than a dropdown popup.
        val spinner = Spinner(this, Spinner.MODE_DIALOG)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(selected.coerceIn(0, items.size - 1))
        parent.addView(spinner, rowParams())
        return spinner
    }

    private fun rowParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    /** Rotating crown / bezel scrolls the form (ScrollView only does it while it has focus). */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val factor = ViewConfiguration.get(this).scaledVerticalScrollFactor
            val dy = Math.round(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * factor)
            scroll.scrollBy(0, dy)
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
