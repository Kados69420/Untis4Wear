package com.untis4wear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Draws the same screen as the Garmin app: time on top, colored lesson panel(s) in the middle,
 * date at the bottom. All positions are fractions of the view height so it fits any watch.
 *
 * Input: swipe up = next lesson, swipe down = previous lesson, tap = menu (click listener).
 */
class TimetableView(context: Context) : View(context) {

    var model: UiModel? = null
        set(value) {
            field = value
            invalidate()
        }

    var settings: Settings = Settings()
        set(value) {
            field = value
            invalidate()
        }

    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val density = resources.displayMetrics.density
    private val swipeDistance = 40f * density
    private val tapSlop = 12f * density
    private var downX = 0f
    private var downY = 0f

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val m = model ?: return
        val s = settings
        val w = width.toFloat()
        val h = height.toFloat()
        val band = h * 0.2f

        // Top: start time of the shown lesson, otherwise the current time.
        val slot = m.slot
        val headerText = if (m.status == Status.LESSON && slot != null) {
            Formatting.lessonTime(slot.time)
        } else {
            Formatting.timeOfDay(m.now.hour, m.now.minute)
        }
        drawText(canvas, headerText, w / 2f, band / 2f, Color.WHITE, h * 0.095f, w * 0.45f, false)

        // Bottom: date.
        val dateText = Formatting.date(m.date, s.dateSystem, s.dateDelimiter.ifEmpty { "." })
        drawText(canvas, dateText, w / 2f, h - band / 2f, Color.WHITE, h * 0.078f, w * 0.6f, false)

        // Middle.
        when (m.status) {
            Status.NOT_CONFIGURED ->
                drawMessage(canvas, w, h, Palette.color(s.noSubjectColor), context.getString(R.string.status_setup))
            Status.LOADING ->
                drawMessage(canvas, w, h, null, context.getString(R.string.status_loading))
            Status.ERROR ->
                drawMessage(canvas, w, h, Color.RED, context.getString(R.string.status_error))
            Status.NO_LESSONS ->
                drawMessage(canvas, w, h, Palette.color(s.noSubjectColor), context.getString(R.string.status_no_lessons))
            Status.NO_MORE_LESSONS ->
                drawMessage(canvas, w, h, Palette.color(s.noSubjectColor), context.getString(R.string.status_no_more_lessons))
            Status.LESSON -> if (slot != null) drawLessons(canvas, w, h, slot, s)
        }

        // Dot: yellow = showing cached data, red = cached data and the refresh failed.
        if (m.indicator != Indicator.NONE) {
            fillPaint.color = if (m.indicator == Indicator.CACHED) Color.YELLOW else Color.RED
            canvas.drawCircle(w / 3f, band / 2f, h * 0.022f, fillPaint)
        }
    }

    private fun drawMessage(canvas: Canvas, w: Float, h: Float, background: Int?, text: String) {
        if (background != null) {
            fillPaint.color = background
            canvas.drawRect(0f, h * 0.2f, w, h * 0.8f, fillPaint)
        }
        val color = if (background != null) Palette.textColorOn(background) else Color.WHITE
        val lines = text.split("\n")
        val size = h * 0.1f
        val lineHeight = size * 1.3f
        val firstY = h / 2f - lineHeight * (lines.size - 1) / 2f
        for ((i, line) in lines.withIndex()) {
            drawText(canvas, line, w / 2f, firstY + i * lineHeight, color, size, w * 0.85f, false)
        }
    }

    private fun drawLessons(canvas: Canvas, w: Float, h: Float, slot: LessonSlot, s: Settings) {
        val count = slot.lessons.size
        if (count == 0) return
        val columnWidth = w / count
        for ((i, lesson) in slot.lessons.withIndex()) {
            val background = Palette.color(
                when (lesson.state) {
                    LessonState.CANCELLED -> s.cancelledColor
                    LessonState.NORMAL -> s.normalColor
                    LessonState.CHANGED -> s.changedColor
                }
            )
            val left = columnWidth * i
            fillPaint.color = background
            canvas.drawRect(left, h * 0.2f, left + columnWidth, h * 0.8f, fillPaint)

            val foreground = Palette.textColorOn(background)
            val cx = left + columnWidth / 2f
            val maxWidth = columnWidth * 0.88f

            drawText(canvas, lesson.subject, cx, h * 0.35f, foreground, h * 0.115f, maxWidth, true)

            val orig = lesson.origRoom
            if (orig != null) {
                val changedBg = Palette.color(s.changedColor)
                drawText(
                    canvas, "${lesson.room}($orig)", cx, h * 0.50f,
                    Palette.textColorOn(changedBg), h * 0.085f, maxWidth, false, changedBg
                )
            } else {
                drawText(canvas, lesson.room, cx, h * 0.50f, foreground, h * 0.085f, maxWidth, false)
            }

            drawText(canvas, lesson.klass, cx, h * 0.65f, foreground, h * 0.085f, maxWidth, false)
        }
    }

    /**
     * Draws [text] centered on ([cx], [cy]). Text wider than [maxWidth] is first shrunk (down to 60%)
     * and then cut with an ellipsis. [highlight] paints a rounded background behind the text.
     */
    private fun drawText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        color: Int,
        size: Float,
        maxWidth: Float,
        bold: Boolean,
        highlight: Int? = null
    ) {
        if (text.isEmpty()) return
        textPaint.isFakeBoldText = bold
        textPaint.textSize = size

        var shown = text
        val fullWidth = textPaint.measureText(shown)
        if (fullWidth > maxWidth) {
            textPaint.textSize = max(size * maxWidth / fullWidth, size * 0.6f)
            if (textPaint.measureText(shown) > maxWidth) {
                while (shown.length > 1 && textPaint.measureText("$shown…") > maxWidth) {
                    shown = shown.dropLast(1)
                }
                shown += "…"
            }
        }

        val metrics = textPaint.fontMetrics
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f

        if (highlight != null) {
            val halfWidth = textPaint.measureText(shown) / 2f
            val pad = textPaint.textSize * 0.3f
            fillPaint.color = highlight
            rect.set(cx - halfWidth - pad, baseline + metrics.ascent, cx + halfWidth + pad, baseline + metrics.descent)
            canvas.drawRoundRect(rect, pad, pad, fillPaint)
        }

        textPaint.color = color
        canvas.drawText(shown, cx, baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dy) >= swipeDistance && abs(dy) > abs(dx) * 1.5f) {
                    // finger moved up -> like scrolling down -> next
                    if (dy < 0f) onNext?.invoke() else onPrevious?.invoke()
                } else if (abs(dx) < tapSlop && abs(dy) < tapSlop) {
                    performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
