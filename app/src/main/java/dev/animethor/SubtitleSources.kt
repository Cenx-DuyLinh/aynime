package dev.animethor

import android.net.Uri
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class AnimeFolder(val category: String, val name: String, val treeSha: String) {
    val key: String get() = "$category/$name"
}

data class SubtitleFile(val folder: AnimeFolder, val fileName: String) {
    val episode: Int? get() = EpisodeNumbers.fromFileName(fileName)
}

class DiskCache(private val directory: File, private val maximumFiles: Int) {

    @Synchronized
    fun read(key: String): ByteArray? {
        val file = fileFor(key)
        if (!file.exists()) {
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return file.readBytes()
    }

    @Synchronized
    fun write(key: String, bytes: ByteArray) {
        directory.mkdirs()
        fileFor(key).writeBytes(bytes)
        removeOldestFiles()
    }

    fun readOrDownload(key: String, download: () -> ByteArray): ByteArray {
        read(key)?.let { return it }
        val bytes = download()
        write(key, bytes)
        return bytes
    }

    private fun fileFor(key: String): File = File(directory, hashOf(key))

    private fun removeOldestFiles() {
        val files = directory.listFiles() ?: return
        if (files.size <= maximumFiles) {
            return
        }
        files.sortedBy { it.lastModified() }.take(files.size - maximumFiles).forEach { it.delete() }
    }

    private fun hashOf(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

class KitsunekkoClient(storageDirectory: File) {
    private val folderCacheFile = File(storageDirectory, "kitsunekko_folders.json")
    private val treeCache = DiskCache(File(storageDirectory, "kitsunekko_trees"), maximumFiles = 600)
    private val subtitleCache = DiskCache(File(storageDirectory, "subtitle_files"), maximumFiles = 300)
    private val filesByFolderKey = mutableMapOf<String, List<SubtitleFile>>()
    private var folders: List<AnimeFolder>? = null

    @Synchronized
    fun animeFolders(forceRefresh: Boolean = false): List<AnimeFolder> {
        if (!forceRefresh) {
            folders?.let { return it }
            readCachedFolders()?.let { cached ->
                folders = cached
                return cached
            }
        }
        val downloaded = downloadFolderList()
        writeCachedFolders(downloaded)
        folders = downloaded
        filesByFolderKey.clear()
        return downloaded
    }

    @Synchronized
    fun subtitleFiles(folder: AnimeFolder): List<SubtitleFile> {
        filesByFolderKey[folder.key]?.let { return it }
        val files = treeEntries(folder.treeSha)
            .filter { it.getString("type") == "blob" && SubtitleParser.isSupported(it.getString("path")) }
            .map { SubtitleFile(folder, it.getString("path")) }
            .sortedWith(compareBy<SubtitleFile> { it.episode ?: Int.MAX_VALUE }.thenBy { it.fileName })
        filesByFolderKey[folder.key] = files
        return files
    }

    fun downloadSubtitle(file: SubtitleFile): ByteArray {
        val path = listOf("subtitles", file.folder.category, file.folder.name, file.fileName)
            .joinToString("/") { Uri.encode(it) }
        return subtitleCache.readOrDownload(path) { Http.downloadBytes("$RAW_ROOT/$path") }
    }

    fun folderWithKey(key: String): AnimeFolder? = animeFolders().firstOrNull { it.key == key }

    private fun downloadFolderList(): List<AnimeFolder> {
        val rootEntries = JSONArray(Http.downloadText("$API_ROOT/contents/"))
        val subtitlesSha = (0 until rootEntries.length())
            .map { rootEntries.getJSONObject(it) }
            .firstOrNull { it.getString("name") == "subtitles" }
            ?.getString("sha")
            ?: throw IOException("The subtitle mirror has no subtitles folder")
        val categories = treeEntries(subtitlesSha).filter { it.getString("type") == "tree" }
        return categories.flatMap { category ->
            treeEntries(category.getString("sha"))
                .filter { it.getString("type") == "tree" }
                .map { AnimeFolder(category.getString("path"), it.getString("path"), it.getString("sha")) }
        }
    }

    private fun treeEntries(treeSha: String): List<JSONObject> {
        val bytes = treeCache.readOrDownload(treeSha) { Http.downloadBytes("$API_ROOT/git/trees/$treeSha") }
        val tree = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("tree")
        return (0 until tree.length()).map { tree.getJSONObject(it) }
    }

    private fun readCachedFolders(): List<AnimeFolder>? {
        if (!folderCacheFile.exists()) {
            return null
        }
        val ageMs = System.currentTimeMillis() - folderCacheFile.lastModified()
        if (ageMs > CACHE_LIFETIME_MS) {
            return null
        }
        val array = JSONArray(folderCacheFile.readText())
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            AnimeFolder(item.getString("category"), item.getString("name"), item.getString("sha"))
        }
    }

    private fun writeCachedFolders(folderList: List<AnimeFolder>) {
        val array = JSONArray()
        folderList.forEach { folder ->
            array.put(JSONObject().put("category", folder.category).put("name", folder.name).put("sha", folder.treeSha))
        }
        folderCacheFile.writeText(array.toString())
    }

    private companion object {
        const val API_ROOT = "https://api.github.com/repos/Ajatt-Tools/kitsunekko-mirror"
        const val RAW_ROOT = "https://raw.githubusercontent.com/Ajatt-Tools/kitsunekko-mirror/main"
        const val CACHE_LIFETIME_MS = 2L * 24 * 60 * 60 * 1000
    }
}

data class AniListAnime(
    val id: Int,
    val titles: List<String>,
    val displayTitle: String,
    val coverUrl: String?,
    val popularity: Int,
    val format: String?,
    val year: Int?,
)

object AniListClient {
    private const val ADDRESS = "https://graphql.anilist.co"
    private const val QUERY =
        "query (\$search: String) { Page(perPage: 5) { media(search: \$search, type: ANIME) " +
            "{ id popularity format seasonYear title { romaji english native } coverImage { large } } } }"
    private const val REMEMBER_ANSWERS_MS = 30 * 60_000L
    private val recentAnswers = mutableMapOf<String, Pair<Long, List<AniListAnime>>>()

    fun search(animeName: String): List<AniListAnime> {
        val question = animeName.trim().lowercase()
        rememberedAnswer(question)?.let { return it }
        val answer = askAniList(animeName)
        synchronized(recentAnswers) { recentAnswers[question] = SystemClock.elapsedRealtime() to answer }
        return answer
    }

    private fun rememberedAnswer(question: String): List<AniListAnime>? = synchronized(recentAnswers) {
        val (answeredAtMs, answer) = recentAnswers[question] ?: return null
        val isFresh = SystemClock.elapsedRealtime() - answeredAtMs < REMEMBER_ANSWERS_MS
        if (isFresh) answer else null
    }

    private fun askAniList(animeName: String): List<AniListAnime> {
        val body = JSONObject()
            .put("query", QUERY)
            .put("variables", JSONObject().put("search", animeName))
        val response = JSONObject(Http.postJson(ADDRESS, body.toString()))
        val media = response.getJSONObject("data").getJSONObject("Page").getJSONArray("media")
        return (0 until media.length()).map { index ->
            val item = media.getJSONObject(index)
            val title = item.getJSONObject("title")
            val titles = listOf("romaji", "english", "native").mapNotNull { key -> textOrNull(title, key) }
            AniListAnime(
                id = item.getInt("id"),
                titles = titles,
                displayTitle = textOrNull(title, "english") ?: titles.firstOrNull() ?: animeName,
                coverUrl = item.optJSONObject("coverImage")?.let { textOrNull(it, "large") },
                popularity = item.optInt("popularity"),
                format = textOrNull(item, "format"),
                year = if (item.isNull("seasonYear")) null else item.optInt("seasonYear"),
            )
        }
    }

    private fun textOrNull(item: JSONObject, key: String): String? =
        item.optString(key).takeIf { it.isNotBlank() && it != "null" }

    fun titlesFor(animeName: String): List<String> = search(animeName).flatMap { it.titles }
}

data class JimakuFile(val name: String, val address: String) {
    val episode: Int? get() = EpisodeNumbers.fromFileName(name)
}

class JimakuClient(private val apiKey: () -> String, storageDirectory: File) {
    private val subtitleCache = DiskCache(File(storageDirectory, "jimaku_files"), maximumFiles = 200)

    fun isConfigured(): Boolean = apiKey().isNotBlank()

    fun entryIdFor(aniListId: Int): Int? {
        val entries = JSONArray(Http.downloadText("$API_ROOT/entries/search?anime=true&anilist_id=$aniListId", authorization()))
        return if (entries.length() == 0) null else entries.getJSONObject(0).getInt("id")
    }

    fun files(entryId: Int, episode: Int): List<JimakuFile> {
        val items = JSONArray(Http.downloadText("$API_ROOT/entries/$entryId/files?episode=$episode", authorization()))
        return (0 until items.length())
            .map { items.getJSONObject(it) }
            .map { JimakuFile(it.getString("name"), it.getString("url")) }
            .filter { SubtitleParser.isSupported(it.name) }
    }

    fun download(file: JimakuFile): ByteArray =
        subtitleCache.readOrDownload(file.address) { Http.downloadBytes(file.address, authorization()) }

    private fun authorization(): Map<String, String> = mapOf("Authorization" to apiKey())

    private companion object {
        const val API_ROOT = "https://jimaku.cc/api"
    }
}

data class SubtitleMatch(
    val fileName: String,
    val episode: Int?,
    val label: String,
    val seriesName: String,
    val folderKeyToRemember: String?,
    val download: () -> ByteArray,
    val downloadNextEpisode: (() -> Unit)?,
)

class SubtitleFinder(
    private val kitsunekko: KitsunekkoClient,
    private val jimaku: JimakuClient,
    private val store: AppStore,
) {
    sealed class Outcome {
        data class Found(val match: SubtitleMatch) : Outcome()
        data class NotFound(val shortReason: String) : Outcome()
    }

    fun findForPage(pageTitle: String, pageUrl: String, namesShownOnPage: List<String>, episode: Int, report: SearchReport): Outcome {
        val nameCandidates = PageTitles.searchNames(pageTitle, namesShownOnPage) +
            PageTitles.animeName(pageTitle) +
            PageTitles.nameFromAddress(pageUrl)
        val names = nameCandidates
            .filter { it.isNotBlank() }
            .distinct()
        report.add("Page", "$pageTitle\n${pageUrl.substringBefore('?')}")
        report.add("Episode detected", "$episode")
        report.add("Names from the page", names.joinToString("\n").ifBlank { "(none)" })
        if (names.isEmpty()) {
            return Outcome.NotFound("No anime name on this page")
        }
        val namesForAniList = names.filterNot { PageTitles.looksVietnamese(it) }.ifEmpty { names }
        val aniListMatches = aniListMatches(namesForAniList, report)
        val fromKitsunekko = findOnKitsunekko(names, aniListMatches, episode, report)
        if (fromKitsunekko != null) {
            return Outcome.Found(fromKitsunekko)
        }
        val fromJimaku = findOnJimaku(names, aniListMatches, episode, report)
        if (fromJimaku != null) {
            return Outcome.Found(fromJimaku)
        }
        return Outcome.NotFound("No Japanese subtitles for episode $episode")
    }

    fun matchFor(file: SubtitleFile, seriesName: String): SubtitleMatch = SubtitleMatch(
        fileName = file.fileName,
        episode = file.episode,
        label = "${file.folder.name} · ep ${file.episode ?: "?"}",
        seriesName = seriesName,
        folderKeyToRemember = file.folder.key,
        download = { kitsunekko.downloadSubtitle(file) },
        downloadNextEpisode = { downloadNextKitsunekkoEpisode(file) },
    )

    private fun aniListMatches(names: List<String>, report: SearchReport): List<AniListAnime> {
        val matches = names.flatMap { name -> aniListWithSeasonFallback(name) }.distinctBy { it.id }
        val answer = matches.take(4).joinToString("\n") { "#${it.id}  ${it.titles.joinToString(" / ")}" }
        report.add("AniList answer", answer.ifBlank { "(nothing found)" })
        return matches
    }

    private fun aniListWithSeasonFallback(name: String): List<AniListAnime> {
        val direct = runCatching { AniListClient.search(name) }.getOrDefault(emptyList())
        val season = SeasonNames.number(name) ?: return direct
        val baseMatches = runCatching { AniListClient.search(SeasonNames.withoutSeason(name)) }.getOrDefault(emptyList())
        val withSeasonAdded = baseMatches.map { anime -> anime.copy(titles = anime.titles.map { "$it Season $season" }) }
        return direct + withSeasonAdded
    }

    private fun findOnKitsunekko(
        names: List<String>,
        aniListMatches: List<AniListAnime>,
        episode: Int,
        report: SearchReport,
    ): SubtitleMatch? {
        val folder = kitsunekkoFolder(names, aniListMatches, report) ?: return null
        val files = kitsunekko.subtitleFiles(folder).filter { it.episode == episode }
        val best = files.maxByOrNull { SubtitleFileSources.preference(it.fileName) }
        val fileList = files.joinToString("\n") { "[${SubtitleFileSources.label(it.fileName)}] ${it.fileName}" }
        report.add("Kitsunekko files for episode $episode", fileList.ifBlank { "(none in this folder)" })
        if (best == null) {
            return null
        }
        report.add("Chosen", "Kitsunekko: ${best.fileName}")
        return matchFor(best, names.first())
    }

    private fun kitsunekkoFolder(names: List<String>, aniListMatches: List<AniListAnime>, report: SearchReport): AnimeFolder? {
        for (name in names) {
            val remembered = store.subtitleFolderKeyForAnime(name)?.let { kitsunekko.folderWithKey(it) }
            if (remembered != null) {
                report.add("Kitsunekko folder", "${remembered.name}\n(remembered from your earlier choice)")
                return remembered
            }
        }
        val folders = kitsunekko.animeFolders()
        val wantedSeason = names.firstNotNullOfOrNull { SeasonNames.number(it) }
        val candidateTitles = aniListMatches.flatMap { it.titles } + store.preferredAnimeTitles() + names
        val byName = FolderMatching.bestFolder(folders, candidateTitles, wantedSeason)
        if (byName != null) {
            report.add("Kitsunekko folder", "${byName.name}\n(name match${wantedSeason?.let { ", season $it" } ?: ""})")
            return byName
        }
        val pickerSearch = aniListMatches.firstOrNull()?.titles?.firstOrNull() ?: names.first()
        val suggested = FolderMatching.search(folders, pickerSearch).firstOrNull()
        report.add("Kitsunekko folder", suggested?.let { "${it.name}\n(closest to \"$pickerSearch\")" } ?: "(no folder matches)")
        return suggested
    }

    private fun findOnJimaku(
        names: List<String>,
        aniListMatches: List<AniListAnime>,
        episode: Int,
        report: SearchReport,
    ): SubtitleMatch? {
        if (!jimaku.isConfigured()) {
            report.add("Jimaku", "Not set up (menu > Jimaku API key)")
            return null
        }
        val wantedSeason = names.firstNotNullOfOrNull { SeasonNames.number(it) } ?: 1
        val ordered = aniListMatches.sortedByDescending { anime -> anime.titles.any { (SeasonNames.number(it) ?: 1) == wantedSeason } }
        for (anime in ordered.take(3)) {
            val result = runCatching { jimakuMatch(anime, names.first(), episode) }
            result.onFailure { report.add("Jimaku", "Error for AniList #${anime.id}: ${it.message}") }
            val match = result.getOrNull()
            if (match != null) {
                report.add("Chosen", "Jimaku: ${match.fileName}")
                return match
            }
        }
        report.add("Jimaku", "No file for episode $episode")
        return null
    }

    private fun jimakuMatch(anime: AniListAnime, seriesName: String, episode: Int): SubtitleMatch? {
        val entryId = jimaku.entryIdFor(anime.id) ?: return null
        val best = jimaku.files(entryId, episode).maxByOrNull { SubtitleFileSources.preference(it.name) } ?: return null
        return SubtitleMatch(
            fileName = best.name,
            episode = episode,
            label = "${anime.titles.firstOrNull() ?: seriesName} · ep $episode (Jimaku)",
            seriesName = "jimaku-${anime.id}",
            folderKeyToRemember = null,
            download = { jimaku.download(best) },
            downloadNextEpisode = { downloadNextJimakuEpisode(entryId, episode) },
        )
    }

    private fun downloadNextKitsunekkoEpisode(file: SubtitleFile) {
        val nextEpisode = (file.episode ?: return) + 1
        val next = kitsunekko.subtitleFiles(file.folder)
            .filter { it.episode == nextEpisode }
            .maxByOrNull { SubtitleFileSources.preference(it.fileName) }
            ?: return
        kitsunekko.downloadSubtitle(next)
    }

    private fun downloadNextJimakuEpisode(entryId: Int, episode: Int) {
        val next = jimaku.files(entryId, episode + 1).maxByOrNull { SubtitleFileSources.preference(it.name) } ?: return
        jimaku.download(next)
    }
}
