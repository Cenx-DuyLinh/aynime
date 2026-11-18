package dev.animethor

import android.content.res.ColorStateList
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat

enum class SubtitleState(val colorResource: Int) {
    SEARCHING(R.color.muted),
    READY(R.color.good),
    MISSING(R.color.accent),
    PROBLEM(R.color.accent),
}

class SubtitleControls(
    private val pill: View,
    private val statusDot: View,
    private val statusText: TextView,
    private val offsetText: TextView,
) {
    private var lastInteractionMs = 0L
    private var isAllowedOnThisPage = false
    private var isShown = false
    private var wasVideoPlaying = false
    private var hasVideoOnPage = false

    fun setStatus(newState: SubtitleState, shortText: String) {
        statusText.text = shortText
        statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(pill.context, newState.colorResource))
        noteInteraction()
    }

    fun setOffsetText(text: String) {
        offsetText.text = text
    }

    fun showOffsetChanged(text: String) {
        setOffsetText(text)
        noteInteraction()
        offsetText.animate().cancel()
        offsetText.scaleX = POP_SCALE
        offsetText.scaleY = POP_SCALE
        offsetText.animate().scaleX(1f).scaleY(1f).setDuration(POP_MS).start()
    }

    fun isHiddenAndContains(screenX: Float, screenY: Float): Boolean {
        if (!isAllowedOnThisPage || !hasVideoOnPage || isShown) {
            return false
        }
        val location = IntArray(2)
        pill.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        return screenX >= left && screenX <= left + pill.width && screenY >= top && screenY <= top + pill.height
    }

    fun setAllowedOnThisPage(allowed: Boolean) {
        isAllowedOnThisPage = allowed
        if (!allowed) {
            pill.visibility = View.GONE
            isShown = false
        }
    }

    fun noteInteraction() {
        lastInteractionMs = SystemClock.elapsedRealtime()
    }

    fun update(hasVideo: Boolean, isVideoPlaying: Boolean, bandTopFromBottomPx: Float) {
        if (!isAllowedOnThisPage) {
            return
        }
        hasVideoOnPage = hasVideo
        pill.translationY = -(bandTopFromBottomPx + GAP_ABOVE_BAND_PX * pill.resources.displayMetrics.density)
        val playbackJustStarted = isVideoPlaying && !wasVideoPlaying
        wasVideoPlaying = isVideoPlaying
        if (playbackJustStarted) {
            lastInteractionMs = 0L
        }
        val recentlyTouched = SystemClock.elapsedRealtime() - lastInteractionMs < SHOW_AFTER_INTERACTION_MS
        val shouldShow = hasVideo && (!isVideoPlaying || recentlyTouched)
        if (shouldShow != isShown) {
            fade(shouldShow)
        }
    }

    private fun fade(show: Boolean) {
        isShown = show
        pill.animate().cancel()
        if (show) {
            pill.visibility = View.VISIBLE
            pill.animate().alpha(1f).setDuration(FADE_MS).start()
        } else {
            pill.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction { pill.visibility = View.INVISIBLE }.start()
        }
    }

    private companion object {
        const val SHOW_AFTER_INTERACTION_MS = 3_000L
        const val FADE_MS = 250L
        const val FADE_OUT_MS = 180L
        const val GAP_ABOVE_BAND_PX = 8
        const val POP_SCALE = 1.25f
        const val POP_MS = 180L
    }
}
