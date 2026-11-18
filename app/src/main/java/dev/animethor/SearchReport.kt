package dev.animethor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SearchReport {
    private val lines = mutableListOf<Pair<String, String>>()

    @Synchronized
    fun add(label: String, value: String) {
        lines.add(label to value)
    }

    @Synchronized
    fun asText(): String = lines.joinToString("\n\n") { (label, value) -> "$label\n$value" }

    @Synchronized
    fun isEmpty(): Boolean = lines.isEmpty()
}

class SearchReportDialog(private val context: Context) {

    fun show(report: SearchReport?, currentStatus: String) {
        val reportText = if (report == null || report.isEmpty()) "No subtitle search has run on this page yet." else report.asText()
        val fullText = "Status\n$currentStatus\n\n$reportText\n\nRecent app events\n${DiagnosticsLog.asText()}"
        MaterialAlertDialogBuilder(context)
            .setTitle("Subtitle search details")
            .setMessage(fullText)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy") { _, _ -> copy(fullText) }
            .show()
    }

    private fun copy(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Aynime subtitle search", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}
