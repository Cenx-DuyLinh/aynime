package dev.animethor

import android.os.SystemClock
import android.view.View

class AutoHidingTopBar(
    private val bar: View,
    private val addressBar: View,
) {
    private var isShown = true
    private var revealedAtMs = 0L

    val isHidden: Boolean
        get() = !isShown

    fun revealBriefly() {
        revealedAtMs = SystemClock.elapsedRealtime()
        if (!isShown) {
            slide(show = true)
        }
    }

    fun update(isVideoPlaying: Boolean) {
        val recentlyRevealed = SystemClock.elapsedRealtime() - revealedAtMs < KEEP_REVEALED_MS
        val shouldShow = !isVideoPlaying || addressBar.hasFocus() || recentlyRevealed
        if (shouldShow != isShown) {
            slide(shouldShow)
        }
    }

    private fun slide(show: Boolean) {
        isShown = show
        bar.animate().cancel()
        if (show) {
            bar.visibility = View.VISIBLE
            bar.animate().alpha(1f).translationY(0f).setDuration(SLIDE_MS).start()
        } else {
            val slideDistance = -bar.height / 2f
            bar.animate()
                .alpha(0f)
                .translationY(slideDistance)
                .setDuration(SLIDE_MS)
                .withEndAction { bar.visibility = View.GONE }
                .start()
        }
    }

    private companion object {
        const val KEEP_REVEALED_MS = 3_000L
        const val SLIDE_MS = 280L
    }
}
