package dev.animethor

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

data class SavedSite(val domain: String, val name: String)

data class ContinueWatching(val url: String, val title: String, val seconds: Double, val subtitleLabel: String)

data class WatchedOnSite(val domain: String, val url: String, val episode: Int?, val seconds: Double, val watchedAtMs: Long)

data class WatchedAnime(
    val name: String,
    val pageNames: List<String>,
    val searchNames: List<String>,
    val aniListId: Int?,
    val coverUrl: String?,
    val sites: List<WatchedOnSite>,
) {
    val lastWatchedAtMs: Long get() = sites.maxOfOrNull { it.watchedAtMs } ?: 0L

    val isLookedUp: Boolean get() = aniListId != null

    val bestNameBeforeLookup: String get() = searchNames.firstOrNull() ?: PageTitles.cleanAnimeName(name)

    fun isKnownAs(pageName: String): Boolean {
        val knownNames = (listOf(name) + pageNames).flatMap { listOf(it, PageTitles.cleanAnimeName(it)) }
        return knownNames.any { it.equals(pageName, ignoreCase = true) }
    }

    fun withSite(watched: WatchedOnSite): WatchedAnime =
        copy(sites = listOf(watched) + sites.filter { it.domain != watched.domain })

    fun mergedWith(other: WatchedAnime): WatchedAnime {
        val newestSitePerDomain = (sites + other.sites)
            .sortedByDescending { it.watchedAtMs }
            .distinctBy { it.domain }
        val allPageNames = (pageNames + other.pageNames).distinctBy { it.lowercase() }
        val allSearchNames = (searchNames + other.searchNames).distinctBy { it.lowercase() }
        return copy(pageNames = allPageNames, searchNames = allSearchNames, sites = newestSitePerDomain)
    }
}

data class OverlayStyle(
    val backgroundOpacityPercent: Int,
    val textSizeSp: Int,
    val bottomMarginPercent: Int,
    val widthPercent: Int,
    val bandHeightPercent: Int,
    val keepBoxBetweenLines: Boolean,
    val softEdges: Boolean,
    val pausedLiftPercent: Int,
    val bandColor: Int,
    val textColor: Int,
) {
    companion object {
        val DEFAULT = OverlayStyle(
            backgroundOpacityPercent = 80,
            textSizeSp = 30,
            bottomMarginPercent = 0,
            widthPercent = 86,
            bandHeightPercent = 15,
            keepBoxBetweenLines = false,
            softEdges = true,
            pausedLiftPercent = 12,
            bandColor = 0x141210,
            textColor = 0xFFFDF8,
        )
    }
}

class AppStore(context: Context) {
    private val preferences = context.getSharedPreferences("anime_thor", Context.MODE_PRIVATE)

    fun savedSites(): List<SavedSite> {
        val array = JSONArray(preferences.getString(KEY_SAVED_SITES, "[]"))
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SavedSite(item.getString("domain"), item.getString("name"))
        }
    }

    fun isSiteSaved(domain: String): Boolean = savedSites().any { it.domain == domain }

    fun setSiteSaved(domain: String, saved: Boolean) {
        val otherSites = savedSites().filter { it.domain != domain }
        val newSites = if (saved) otherSites + SavedSite(domain, siteNameFromDomain(domain)) else otherSites
        writeSavedSites(newSites)
    }

    fun changeSavedSiteAddress(oldDomain: String, newDomain: String) {
        val updatedSites = savedSites().map { site ->
            if (site.domain == oldDomain) SavedSite(newDomain, siteNameFromDomain(newDomain)) else site
        }
        writeSavedSites(updatedSites.distinctBy { it.domain })
    }

    private fun siteNameFromDomain(domain: String): String = domain.substringBefore(".")

    @Synchronized
    fun moveSiteInWatchedLists(oldDomain: String, newDomain: String) {
        val movedAnimeList = animeList().map { anime ->
            val movedSites = anime.sites
                .map { site -> if (site.domain == oldDomain) movedSite(site, newDomain) else site }
                .sortedByDescending { it.watchedAtMs }
                .distinctBy { it.domain }
            anime.copy(sites = movedSites)
        }
        writeAnimeList(movedAnimeList)
        val watching = continueWatching() ?: return
        if (Addresses.siteDomain(watching.url) == oldDomain) {
            saveContinueWatching(watching.copy(url = Addresses.withDomain(watching.url, newDomain)))
        }
    }

    private fun movedSite(site: WatchedOnSite, newDomain: String): WatchedOnSite =
        site.copy(domain = newDomain, url = Addresses.withDomain(site.url, newDomain))

    private fun writeSavedSites(sites: List<SavedSite>) {
        val array = JSONArray()
        sites.forEach { site -> array.put(JSONObject().put("domain", site.domain).put("name", site.name)) }
        preferences.edit().putString(KEY_SAVED_SITES, array.toString()).apply()
    }

    fun continueWatching(): ContinueWatching? {
        val text = preferences.getString(KEY_CONTINUE_WATCHING, null) ?: return null
        val item = JSONObject(text)
        return ContinueWatching(
            url = item.getString("url"),
            title = item.getString("title"),
            seconds = item.getDouble("seconds"),
            subtitleLabel = item.optString("subtitleLabel"),
        )
    }

    fun saveContinueWatching(watching: ContinueWatching) {
        val item = JSONObject()
            .put("url", watching.url)
            .put("title", watching.title)
            .put("seconds", watching.seconds)
            .put("subtitleLabel", watching.subtitleLabel)
        preferences.edit().putString(KEY_CONTINUE_WATCHING, item.toString()).apply()
    }

    fun animeList(): List<WatchedAnime> {
        val array = JSONArray(preferences.getString(KEY_ANIME_LIST, "[]"))
        return (0 until array.length()).map { index -> watchedAnimeFromJson(array.getJSONObject(index)) }
    }

    @Synchronized
    fun recordWatching(pageName: String, searchNames: List<String>, watched: WatchedOnSite): WatchedAnime {
        val currentList = animeList()
        val matching = currentList.filter { it.isKnownAs(pageName) }
        val existing = matching.sortedByDescending { it.isLookedUp }.reduceOrNull { combined, other -> combined.mergedWith(other) }
        val updated = existing?.withSite(watched)?.copy(searchNames = searchNames, pageNames = (existing.pageNames + pageName).distinct())
            ?: WatchedAnime(pageName, listOf(pageName), searchNames, aniListId = null, coverUrl = null, sites = listOf(watched))
        val otherAnime = currentList.filter { it !in matching }
        writeAnimeList((listOf(updated) + otherAnime).take(MAXIMUM_ANIME_IN_LIST))
        return updated
    }

    @Synchronized
    fun saveLookedUpAnime(pageName: String, lookedUp: AniListAnime) {
        val currentList = animeList()
        val entry = currentList.firstOrNull { it.isKnownAs(pageName) } ?: return
        val sameAnime = currentList.filter { it !== entry && it.aniListId == lookedUp.id }
        val named = entry.copy(name = lookedUp.displayTitle, aniListId = lookedUp.id, coverUrl = lookedUp.coverUrl)
        val merged = sameAnime.fold(named) { combined, other -> combined.mergedWith(other) }
        val untouched = currentList.filter { it !== entry && it !in sameAnime }
        writeAnimeList(listOf(merged) + untouched)
    }

    @Synchronized
    fun removeAnime(animeName: String) {
        writeAnimeList(animeList().filter { it.name != animeName })
    }

    private fun writeAnimeList(animeList: List<WatchedAnime>) {
        val array = JSONArray()
        animeList.sortedByDescending { it.lastWatchedAtMs }.forEach { array.put(watchedAnimeToJson(it)) }
        preferences.edit().putString(KEY_ANIME_LIST, array.toString()).apply()
    }

    private fun watchedAnimeToJson(anime: WatchedAnime): JSONObject {
        val sites = JSONArray()
        anime.sites.forEach { site ->
            sites.put(
                JSONObject()
                    .put("domain", site.domain)
                    .put("url", site.url)
                    .put("episode", site.episode ?: JSONObject.NULL)
                    .put("seconds", site.seconds)
                    .put("watchedAtMs", site.watchedAtMs),
            )
        }
        return JSONObject()
            .put("name", anime.name)
            .put("pageNames", JSONArray(anime.pageNames))
            .put("searchNames", JSONArray(anime.searchNames))
            .put("aniListId", anime.aniListId ?: JSONObject.NULL)
            .put("coverUrl", anime.coverUrl ?: JSONObject.NULL)
            .put("sites", sites)
    }

    private fun watchedAnimeFromJson(item: JSONObject): WatchedAnime {
        val sitesArray = item.getJSONArray("sites")
        val sites = (0 until sitesArray.length()).map { index ->
            val site = sitesArray.getJSONObject(index)
            WatchedOnSite(
                domain = site.getString("domain"),
                url = site.getString("url"),
                episode = if (site.isNull("episode")) null else site.getInt("episode"),
                seconds = site.getDouble("seconds"),
                watchedAtMs = site.getLong("watchedAtMs"),
            )
        }
        val name = item.getString("name")
        val pageNamesArray = item.optJSONArray("pageNames")
        val pageNames = if (pageNamesArray == null) listOf(name) else stringsOf(pageNamesArray)
        val searchNamesArray = item.optJSONArray("searchNames")
        val searchNames = if (searchNamesArray == null) PageTitles.searchNames(name) else stringsOf(searchNamesArray)
        return WatchedAnime(
            name = name,
            pageNames = pageNames,
            searchNames = searchNames,
            aniListId = if (item.isNull("aniListId")) null else item.optInt("aniListId"),
            coverUrl = if (item.isNull("coverUrl")) null else item.optString("coverUrl"),
            sites = sites,
        )
    }

    private fun stringsOf(array: JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }

    fun overlayStyle(): OverlayStyle {
        val default = OverlayStyle.DEFAULT
        return OverlayStyle(
            backgroundOpacityPercent = preferences.getInt(KEY_OVERLAY_OPACITY, default.backgroundOpacityPercent),
            textSizeSp = preferences.getInt(KEY_OVERLAY_TEXT_SIZE, default.textSizeSp),
            bottomMarginPercent = preferences.getInt(KEY_OVERLAY_BOTTOM, default.bottomMarginPercent),
            widthPercent = preferences.getInt(KEY_OVERLAY_WIDTH, default.widthPercent),
            bandHeightPercent = preferences.getInt(KEY_OVERLAY_BAND_HEIGHT, default.bandHeightPercent),
            keepBoxBetweenLines = preferences.getBoolean(KEY_OVERLAY_KEEP_BOX, default.keepBoxBetweenLines),
            softEdges = preferences.getBoolean(KEY_OVERLAY_SOFT_EDGES, default.softEdges),
            pausedLiftPercent = preferences.getInt(KEY_OVERLAY_PAUSED_LIFT, default.pausedLiftPercent),
            bandColor = preferences.getInt(KEY_OVERLAY_BAND_COLOR, default.bandColor),
            textColor = preferences.getInt(KEY_OVERLAY_TEXT_COLOR, default.textColor),
        )
    }

    fun saveOverlayStyle(style: OverlayStyle) {
        preferences.edit()
            .putInt(KEY_OVERLAY_OPACITY, style.backgroundOpacityPercent)
            .putInt(KEY_OVERLAY_TEXT_SIZE, style.textSizeSp)
            .putInt(KEY_OVERLAY_BOTTOM, style.bottomMarginPercent)
            .putInt(KEY_OVERLAY_WIDTH, style.widthPercent)
            .putInt(KEY_OVERLAY_BAND_HEIGHT, style.bandHeightPercent)
            .putBoolean(KEY_OVERLAY_KEEP_BOX, style.keepBoxBetweenLines)
            .putBoolean(KEY_OVERLAY_SOFT_EDGES, style.softEdges)
            .putInt(KEY_OVERLAY_PAUSED_LIFT, style.pausedLiftPercent)
            .putInt(KEY_OVERLAY_BAND_COLOR, style.bandColor)
            .putInt(KEY_OVERLAY_TEXT_COLOR, style.textColor)
            .apply()
    }

    fun controllerButton(action: ControllerAction): Int =
        preferences.getInt(KEY_CONTROLLER_PREFIX + action.name, action.defaultKeyCode)

    fun setControllerButton(action: ControllerAction, keyCode: Int) {
        val editor = preferences.edit()
        ControllerAction.entries
            .filter { it != action && controllerButton(it) == keyCode && keyCode != ControllerAction.NO_BUTTON }
            .forEach { editor.putInt(KEY_CONTROLLER_PREFIX + it.name, ControllerAction.NO_BUTTON) }
        editor.putInt(KEY_CONTROLLER_PREFIX + action.name, keyCode).apply()
    }

    fun actionForButton(keyCode: Int): ControllerAction? =
        ControllerAction.entries.firstOrNull { controllerButton(it) == keyCode }

    fun captureButton(): Int = preferences.getInt(KEY_CAPTURE_BUTTON, KeyEvent.KEYCODE_BUTTON_X)

    fun saveCaptureButton(keyCode: Int) {
        preferences.edit().putInt(KEY_CAPTURE_BUTTON, keyCode).apply()
    }

    fun returnFocusAfterOtherApps(): Boolean = preferences.getBoolean(KEY_RETURN_FOCUS, true)

    fun saveReturnFocusAfterOtherApps(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_RETURN_FOCUS, enabled).apply()
    }

    fun updateRepository(): String = preferences.getString(KEY_UPDATE_REPOSITORY, DEFAULT_UPDATE_REPOSITORY) ?: DEFAULT_UPDATE_REPOSITORY

    fun saveUpdateRepository(repository: String) {
        preferences.edit().putString(KEY_UPDATE_REPOSITORY, repository.trim()).apply()
    }

    fun jimakuApiKey(): String = preferences.getString(KEY_JIMAKU_API_KEY, "") ?: ""

    fun saveJimakuApiKey(apiKey: String) {
        preferences.edit().putString(KEY_JIMAKU_API_KEY, apiKey.trim()).apply()
    }

    fun subtitleOffsetMs(seriesKey: String): Long = preferences.getLong(KEY_OFFSET_PREFIX + seriesKey, 0L)

    fun saveSubtitleOffsetMs(seriesKey: String, offsetMs: Long) {
        preferences.edit().putLong(KEY_OFFSET_PREFIX + seriesKey, offsetMs).apply()
    }

    fun subtitleFolderKeyForAnime(animeName: String): String? =
        preferences.getString(KEY_FOLDER_FOR_ANIME_PREFIX + animeName.lowercase(), null)

    fun rememberSubtitleFolderForAnime(animeName: String, folderKey: String) {
        preferences.edit().putString(KEY_FOLDER_FOR_ANIME_PREFIX + animeName.lowercase(), folderKey).apply()
    }

    fun preferredAnimeTitles(): List<String> {
        val array = JSONArray(preferences.getString(KEY_PREFERRED_TITLES, "[]"))
        return (0 until array.length()).map { array.getString(it) }
    }

    fun savePreferredAnimeTitles(titles: List<String>) {
        preferences.edit().putString(KEY_PREFERRED_TITLES, JSONArray(titles).toString()).apply()
    }

    private companion object {
        const val KEY_SAVED_SITES = "saved_sites"
        const val KEY_CONTINUE_WATCHING = "continue_watching"
        const val KEY_ANIME_LIST = "anime_list"
        const val MAXIMUM_ANIME_IN_LIST = 80
        const val KEY_OVERLAY_OPACITY = "overlay_opacity_v5"
        const val KEY_OVERLAY_TEXT_SIZE = "overlay_text_size_v2"
        const val KEY_OVERLAY_BOTTOM = "overlay_bottom_v5"
        const val KEY_OVERLAY_WIDTH = "overlay_width_v3"
        const val KEY_OVERLAY_BAND_HEIGHT = "overlay_band_height"
        const val KEY_OVERLAY_KEEP_BOX = "overlay_keep_box_v3"
        const val KEY_OVERLAY_SOFT_EDGES = "overlay_soft_edges"
        const val KEY_OVERLAY_PAUSED_LIFT = "overlay_paused_lift"
        const val KEY_OVERLAY_BAND_COLOR = "overlay_band_color"
        const val KEY_OVERLAY_TEXT_COLOR = "overlay_text_color"
        const val KEY_OFFSET_PREFIX = "subtitle_offset_"
        const val KEY_JIMAKU_API_KEY = "jimaku_api_key"
        const val KEY_CONTROLLER_PREFIX = "controller_"
        const val KEY_RETURN_FOCUS = "return_focus_after_other_apps"
        const val KEY_CAPTURE_BUTTON = "playtranslate_capture_button"
        const val KEY_UPDATE_REPOSITORY = "update_repository"
        const val DEFAULT_UPDATE_REPOSITORY = "Cenx-DuyLinh/aynime"
        const val KEY_FOLDER_FOR_ANIME_PREFIX = "folder_for_anime_"
        const val KEY_PREFERRED_TITLES = "preferred_titles"
    }
}
