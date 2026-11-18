package dev.animethor

import android.content.Context
import java.io.File

class AdBlocker(private val context: Context) {
    private val downloadedListFile = File(context.filesDir, "ad_hosts.txt")

    @Volatile
    private var blockedHosts: Set<String> = emptySet()

    val blockedHostCount: Int get() = blockedHosts.size

    fun load() {
        val bundledHosts = context.assets.open(BUNDLED_LIST).bufferedReader().use { reader ->
            reader.readLines().mapNotNull { hostFromLine(it) }
        }
        val downloadedHosts = if (downloadedListFile.exists()) {
            downloadedListFile.readLines().mapNotNull { hostFromLine(it) }
        } else {
            emptyList()
        }
        blockedHosts = HashSet<String>(bundledHosts.size + downloadedHosts.size).apply {
            addAll(bundledHosts)
            addAll(downloadedHosts)
        }
    }

    fun updateFromInternet(): Int {
        val hostsText = Http.downloadText(DOWNLOAD_ADDRESS)
        val hosts = hostsText.lines().mapNotNull { hostFromLine(it) }
        downloadedListFile.writeText(hosts.joinToString("\n"))
        load()
        return blockedHosts.size
    }

    fun isBlocked(host: String): Boolean {
        var candidate = host.lowercase()
        while (candidate.contains('.')) {
            if (candidate in blockedHosts) {
                return true
            }
            candidate = candidate.substringAfter('.')
        }
        return false
    }

    private fun hostFromLine(line: String): String? {
        val withoutComment = line.substringBefore('#').trim()
        if (withoutComment.isEmpty()) {
            return null
        }
        val parts = withoutComment.split(Regex("""\s+"""))
        val host = if (parts.size >= 2) parts[1] else parts[0]
        val isPlaceholder = host == "localhost" || host == "0.0.0.0" || host.endsWith(".localdomain")
        return if (isPlaceholder || !host.contains('.')) null else host.lowercase()
    }

    private companion object {
        const val BUNDLED_LIST = "ad_hosts.txt"
        const val DOWNLOAD_ADDRESS = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    }
}
