package dev.animethor

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Hands PlayTranslate the subtitle line Aynime is showing, so a capture reads the exact text
 * instead of running OCR on the band (OCR can pick up burned-in English through a see-through
 * band, and misreads kanji). PlayTranslate asks with a query on [CURRENT]; only PlayTranslate is
 * answered, and only while a video with a subtitle line is on screen.
 *
 * The answer is one row with one column, [COLUMN_JSON]:
 * {
 *   "version": 1, "displayId": 0, "displayWidth": 1920, "displayHeight": 1080,
 *   "groups": [                          // one per subtitle line as written (hard line break)
 *     { "text": "…", "lines": [          // one per row on screen (a long line can wrap)
 *       { "text": "…", "textStart": 0, "bounds": [l, t, r, b],
 *         "chars": [ { "offset": 0, "text": "あ", "bounds": [l, t, r, b] }, … ] } ] } ]
 * }
 * All bounds are display pixels on [displayId]; a char's offset is its index in its line's text.
 */
class SubtitleShareProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (callingPackage?.startsWith(PLAYTRANSLATE_PACKAGE) != true) {
            return null
        }
        val cursor = MatrixCursor(arrayOf(COLUMN_JSON))
        SubtitleShare.snapshotFromMainThread()?.let { cursor.addRow(arrayOf(it)) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val COLUMN_JSON = "json"
        private const val PLAYTRANSLATE_PACKAGE = "com.playtranslate"
    }
}

/** The subtitle view the provider reads, set while the player screen is started. */
object SubtitleShare {
    private var overlayView: WeakReference<TextView>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Set on start, cleared on stop: a stopped Aynime is not on screen and shares nothing. */
    fun attach(textView: TextView?) {
        overlayView = textView?.let { WeakReference(it) }
    }

    /** Builds the snapshot on the main thread (views are only safe there); null when nothing is shown. */
    fun snapshotFromMainThread(): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return snapshot()
        }
        val result = AtomicReference<String?>(null)
        val done = CountDownLatch(1)
        mainHandler.post {
            result.set(runCatching { snapshot() }.getOrNull())
            done.countDown()
        }
        return if (done.await(MAIN_THREAD_WAIT_MS, TimeUnit.MILLISECONDS)) result.get() else null
    }

    private fun snapshot(): String? {
        val view = overlayView?.get() ?: return null
        val text = view.text?.toString().orEmpty()
        val layout = view.layout
        val display = view.display
        if (!view.isAttachedToWindow || view.visibility != View.VISIBLE || text.isBlank() || layout == null || display == null) {
            return null
        }
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val originX = location[0] + view.totalPaddingLeft - view.scrollX
        // totalPaddingTop includes the gravity offset (the text sits at the bottom of the band).
        val originY = location[1] + view.totalPaddingTop - view.scrollY

        val groups = JSONArray()
        var groupStart = 0
        for (hardLine in text.split('\n')) {
            val groupEnd = groupStart + hardLine.length
            val lines = JSONArray()
            if (hardLine.isNotBlank()) {
                for (row in 0 until layout.lineCount) {
                    val rowStart = maxOf(layout.getLineStart(row), groupStart)
                    val rowEnd = minOf(layout.getLineEnd(row), groupEnd)
                    if (rowStart >= rowEnd) continue
                    val rowText = text.substring(rowStart, rowEnd)
                    if (rowText.isBlank()) continue
                    lines.put(rowJson(view, row, rowStart, rowEnd, rowText, rowStart - groupStart, originX, originY))
                }
            }
            if (lines.length() > 0) {
                groups.put(JSONObject().put("text", hardLine).put("lines", lines))
            }
            groupStart = groupEnd + 1
        }
        if (groups.length() == 0) {
            return null
        }
        @Suppress("DEPRECATION")
        val size = Point().also { display.getRealSize(it) }
        return JSONObject()
            .put("version", 1)
            .put("displayId", display.displayId)
            .put("displayWidth", size.x)
            .put("displayHeight", size.y)
            .put("groups", groups)
            .toString()
    }

    private fun rowJson(
        view: TextView,
        row: Int,
        rowStart: Int,
        rowEnd: Int,
        rowText: String,
        textStart: Int,
        originX: Int,
        originY: Int,
    ): JSONObject {
        val layout = view.layout
        val top = originY + layout.getLineTop(row)
        val bottom = originY + layout.getLineBaseline(row) + layout.paint.fontMetricsInt.descent
        val chars = JSONArray()
        var rowLeft = Int.MAX_VALUE
        var rowRight = Int.MIN_VALUE
        for (index in rowStart until rowEnd) {
            val character = view.text[index]
            if (character.isWhitespace() || character.isLowSurrogate()) continue
            val left = layout.getPrimaryHorizontal(index)
            val next = if (character.isHighSurrogate()) index + 2 else index + 1
            val right = if (next < rowEnd) layout.getPrimaryHorizontal(next) else layout.getLineRight(row)
            val charLeft = originX + minOf(left, right).toInt()
            val charRight = originX + maxOf(left, right).toInt()
            rowLeft = minOf(rowLeft, charLeft)
            rowRight = maxOf(rowRight, charRight)
            chars.put(
                JSONObject()
                    .put("offset", index - rowStart)
                    .put("text", view.text.substring(index, minOf(next, rowEnd)))
                    .put("bounds", JSONArray(listOf(charLeft, top, charRight, bottom))),
            )
        }
        return JSONObject()
            .put("text", rowText)
            .put("textStart", textStart)
            .put("bounds", JSONArray(listOf(rowLeft, top, rowRight, bottom)))
            .put("chars", chars)
    }

    private const val MAIN_THREAD_WAIT_MS = 300L
}
