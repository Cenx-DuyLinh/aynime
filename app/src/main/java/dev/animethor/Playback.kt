package dev.animethor

import android.net.Uri
import android.os.SystemClock
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

data class VideoReport(val seconds: Double, val paused: Boolean, val rate: Double, val area: Double)

class PlaybackClock {
    private var reportedSeconds = 0.0
    private var reportedAtMs = 0L
    private var paused = true
    private var rate = 1.0
    private var jumpTargetSeconds = 0.0
    private var ignoreOldPositionUntilMs = 0L

    fun update(report: VideoReport) {
        val nowMs = SystemClock.elapsedRealtime()
        val isStillCatchingUp = nowMs < ignoreOldPositionUntilMs
        val isOldPosition = kotlin.math.abs(report.seconds - jumpTargetSeconds) > JUMP_TOLERANCE_SECONDS
        if (isStillCatchingUp && isOldPosition) {
            return
        }
        ignoreOldPositionUntilMs = 0L
        reportedSeconds = report.seconds
        paused = report.paused
        rate = report.rate
        reportedAtMs = nowMs
    }

    fun jumpTo(seconds: Double) {
        val nowMs = SystemClock.elapsedRealtime()
        reportedSeconds = seconds
        reportedAtMs = nowMs
        jumpTargetSeconds = seconds
        ignoreOldPositionUntilMs = nowMs + CATCH_UP_WINDOW_MS
    }

    fun forget() {
        reportedAtMs = 0L
    }

    fun hasVideo(): Boolean = hasVideoWithin(VIDEO_GONE_AFTER_MS)

    fun hasVideoWithin(windowMs: Long): Boolean =
        reportedAtMs != 0L && SystemClock.elapsedRealtime() - reportedAtMs < windowMs

    fun isPlaying(): Boolean = hasVideo() && !paused

    fun isPaused(): Boolean = hasVideo() && paused

    fun positionMs(): Long {
        val sinceReportMs = SystemClock.elapsedRealtime() - reportedAtMs
        val playedSinceReportMs = if (paused) 0.0 else sinceReportMs * rate
        return (reportedSeconds * 1000 + playedSinceReportMs).toLong()
    }

    private companion object {
        const val VIDEO_GONE_AFTER_MS = 5_000L
        const val CATCH_UP_WINDOW_MS = 2_500L
        const val JUMP_TOLERANCE_SECONDS = 1.0
    }
}

class VideoBridge(
    private val onReport: (VideoReport) -> Unit,
    private val onEpisodeHint: (Int) -> Unit,
    private val onNameHints: (List<String>) -> Unit,
) : WebViewCompat.WebMessageListener {
    private var commandTarget: JavaScriptReplyProxy? = null
    private var commandTargetArea = 0.0
    private var commandTargetReportedAtMs = 0L

    fun install(webView: WebView, script: String) {
        WebViewCompat.addWebMessageListener(webView, OBJECT_NAME, setOf("*"), this)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }
    }

    fun forgetVideo() {
        commandTarget = null
        commandTargetArea = 0.0
        commandTargetReportedAtMs = 0L
    }

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val nameHints = parseNameHints(message.data)
        if (nameHints != null) {
            if (isMainFrame) {
                onNameHints(nameHints)
            }
            return
        }
        val episodeHint = parseEpisodeHint(message.data)
        if (episodeHint != null) {
            if (isMainFrame) {
                onEpisodeHint(episodeHint)
            }
            return
        }
        val report = parseReport(message.data) ?: return
        val nowMs = SystemClock.elapsedRealtime()
        val targetIsStale = nowMs - commandTargetReportedAtMs > TARGET_STALE_AFTER_MS
        val isSameTarget = replyProxy == commandTarget
        if (!isSameTarget && !targetIsStale && report.area < commandTargetArea) {
            return
        }
        commandTarget = replyProxy
        commandTargetArea = report.area
        commandTargetReportedAtMs = nowMs
        onReport(report)
    }

    fun seekTo(seconds: Double) {
        sendCommand(JSONObject().put("name", "seek").put("seconds", seconds.coerceAtLeast(0.0)))
    }

    fun togglePlay() {
        sendCommand(JSONObject().put("name", "togglePlay"))
    }

    fun pause() {
        sendCommand(JSONObject().put("name", "pause"))
    }

    private fun sendCommand(command: JSONObject) {
        commandTarget?.postMessage(command.toString())
    }

    private fun parseNameHints(data: String?): List<String>? {
        if (data == null || !data.contains("nameHints")) {
            return null
        }
        return runCatching {
            val hints = JSONObject(data).getJSONArray("nameHints")
            (0 until hints.length()).map { hints.getString(it) }
        }.getOrNull()
    }

    private fun parseEpisodeHint(data: String?): Int? {
        if (data == null || !data.contains("episodeHint")) {
            return null
        }
        return runCatching { JSONObject(data).getInt("episodeHint") }.getOrNull()
    }

    private fun parseReport(data: String?): VideoReport? {
        if (data == null) {
            return null
        }
        return runCatching {
            val item = JSONObject(data)
            VideoReport(
                seconds = item.getDouble("seconds"),
                paused = item.getBoolean("paused"),
                rate = item.optDouble("rate", 1.0),
                area = item.optDouble("area", 0.0),
            )
        }.getOrNull()
    }

    companion object {
        const val OBJECT_NAME = "animeThorVideo"
        private const val TARGET_STALE_AFTER_MS = 1_500L
    }
}

class SubtitleSession {
    var track: SubtitleTrack? = null
        private set
    var offsetMs: Long = 0
        private set
    var overlayVisible = true
    var loadedKey: String? = null
        private set
    var seriesKey: String? = null
        private set

    fun load(newTrack: SubtitleTrack, key: String, newSeriesKey: String, rememberedOffsetMs: Long) {
        track = newTrack
        loadedKey = key
        seriesKey = newSeriesKey
        offsetMs = rememberedOffsetMs
    }

    fun clear() {
        track = null
        loadedKey = null
        seriesKey = null
        offsetMs = 0
    }

    fun shiftBy(deltaMs: Long) {
        offsetMs += deltaMs
    }

    fun syncNearestLineTo(videoPositionMs: Long): Boolean {
        val cues = track?.cues ?: return false
        val heardStartMs = videoPositionMs - REACTION_TIME_MS
        val nearestCue = cues
            .filter { kotlin.math.abs(it.startMs + offsetMs - heardStartMs) <= SYNC_SEARCH_WINDOW_MS }
            .minByOrNull { kotlin.math.abs(it.startMs + offsetMs - heardStartMs) }
            ?: return false
        offsetMs = heardStartMs - nearestCue.startMs
        return true
    }

    fun textAt(videoPositionMs: Long): String = track?.textAt(videoPositionMs - offsetMs) ?: ""

    fun currentCueIndex(videoPositionMs: Long): Int = track?.lastStartedCueIndex(videoPositionMs - offsetMs) ?: -1

    fun videoSecondsForCue(cueIndex: Int): Double? {
        val cues = track?.cues ?: return null
        if (cues.isEmpty()) {
            return null
        }
        val safeIndex = cueIndex.coerceIn(0, cues.size - 1)
        return (cues[safeIndex].startMs + offsetMs) / 1000.0 + 0.01
    }

    fun shortOffsetText(): String {
        val sign = if (offsetMs >= 0) "+" else "−"
        return "$sign%.1f s".format(kotlin.math.abs(offsetMs) / 1000.0)
    }

    fun offsetText(): String {
        val sign = if (offsetMs >= 0) "+" else "−"
        return "offset $sign%.1f s".format(kotlin.math.abs(offsetMs) / 1000.0)
    }

    private companion object {
        const val REACTION_TIME_MS = 250L
        const val SYNC_SEARCH_WINDOW_MS = 15_000L
    }
}
