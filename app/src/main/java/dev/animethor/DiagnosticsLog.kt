package dev.animethor

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsLog {
    private const val MAXIMUM_LINES = 30
    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(text: String) {
        lines.addLast("${timeFormat.format(Date())}  $text")
        while (lines.size > MAXIMUM_LINES) {
            lines.removeFirst()
        }
    }

    @Synchronized
    fun asText(): String = if (lines.isEmpty()) "Nothing yet." else lines.joinToString("\n")
}
