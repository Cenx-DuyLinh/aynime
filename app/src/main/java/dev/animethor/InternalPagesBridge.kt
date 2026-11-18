package dev.animethor

import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface InternalPageActions {
    fun openUrl(url: String)
    fun resumeWatching()
    fun openSavedSite(domain: String)
    fun editSavedSite(domain: String)
    fun resumeWatchingAt(url: String, seconds: Double)
    fun showAnimeOptions(animeName: String, shownName: String)
    fun refreshHomePage()
}

class InternalPagesBridge(
    private val store: AppStore,
    private val kitsunekko: KitsunekkoClient,
    private val animeListLookup: AnimeListLookup,
    private val scope: LifecycleCoroutineScope,
    private val actions: InternalPageActions,
) : WebViewCompat.WebMessageListener {

    fun install(webView: WebView) {
        WebViewCompat.addWebMessageListener(webView, OBJECT_NAME, setOf(Addresses.INTERNAL_ORIGIN), this)
    }

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        // Act only for an internal page itself: not for frames embedded in it, and not once the tab has left it.
        if (!isMainFrame || !Addresses.isInternal(view.url)) {
            return
        }
        val request = runCatching { JSONObject(message.data ?: "") }.getOrNull() ?: return
        when (request.optString("name")) {
            "getHomeData" -> {
                reply(replyProxy, request, homeData())
                lookUpUnnamedAnimeThenRefresh()
            }
            "openSite" -> actions.openSavedSite(request.getString("domain"))
            "editSite" -> actions.editSavedSite(request.getString("domain"))
            "searchGoogle" -> actions.openUrl(Addresses.googleSearchUrl(request.getString("query")))
            "openAnimeOnSite" -> openAnimeOnSite(request)
            "resumeWatching" -> actions.resumeWatching()
            "resumeAnime" -> resumeAnime(request)
            "animeOptions" -> actions.showAnimeOptions(request.getString("anime"), request.optString("shownName"))
            "subtitleStatus" -> replySubtitleStatus(replyProxy, request)
        }
    }

    private fun homeData(): JSONObject {
        val sites = JSONArray()
        store.savedSites().forEach { sites.put(JSONObject().put("domain", it.domain).put("name", it.name)) }
        val data = JSONObject().put("savedSites", sites)
        store.continueWatching()?.let { watching ->
            data.put(
                "continueWatching",
                JSONObject()
                    .put("title", watching.title)
                    .put("url", watching.url)
                    .put("seconds", watching.seconds)
                    .put("subtitleLabel", watching.subtitleLabel),
            )
        }
        data.put("animeList", animeListJson())
        return data
    }

    private fun animeListJson(): JSONArray {
        val animeArray = JSONArray()
        store.animeList().forEach { anime ->
            val sites = JSONArray()
            anime.sites.sortedByDescending { it.watchedAtMs }.forEach { site ->
                sites.put(
                    JSONObject()
                        .put("domain", site.domain)
                        .put("episode", site.episode ?: JSONObject.NULL)
                        .put("seconds", site.seconds)
                        .put("watchedAtMs", site.watchedAtMs),
                )
            }
            val shownName = if (anime.isLookedUp) anime.name else anime.bestNameBeforeLookup
            animeArray.put(
                JSONObject()
                    .put("name", anime.name)
                    .put("shownName", shownName)
                    .put("coverUrl", anime.coverUrl ?: JSONObject.NULL)
                    .put("isLookedUp", anime.isLookedUp)
                    .put("sites", sites),
            )
        }
        return animeArray
    }

    private fun lookUpUnnamedAnimeThenRefresh() {
        lookupJob?.cancel()
        lookupJob = scope.launch {
            var round = animeListLookup.lookUpUnnamedAnime()
            var retriesLeft = MAXIMUM_LOOKUP_RETRIES
            while (!round.listChanged && round.hadConnectionProblems && retriesLeft > 0) {
                retriesLeft -= 1
                delay(AnimeListLookup.RETRY_AFTER_CONNECTION_PROBLEM_MS)
                round = animeListLookup.lookUpUnnamedAnime()
            }
            if (round.listChanged) {
                actions.refreshHomePage()
            }
        }
    }

    private var lookupJob: Job? = null

    private fun resumeAnime(request: JSONObject) {
        val animeName = request.getString("anime")
        val domain = request.getString("domain")
        val anime = store.animeList().firstOrNull { it.name == animeName } ?: return
        val site = anime.sites.firstOrNull { it.domain == domain } ?: return
        actions.resumeWatchingAt(site.url, site.seconds)
    }

    private fun openAnimeOnSite(request: JSONObject) {
        val titles = request.optJSONArray("titles") ?: JSONArray()
        store.savePreferredAnimeTitles((0 until titles.length()).map { titles.getString(it) })
        actions.openUrl(Addresses.googleSiteSearchUrl(request.getString("domain"), request.getString("query")))
    }

    private fun replySubtitleStatus(replyProxy: JavaScriptReplyProxy, request: JSONObject) {
        val titlesArray = request.optJSONArray("titles") ?: JSONArray()
        val titles = (0 until titlesArray.length()).map { titlesArray.getString(it) }
        scope.launch {
            val folder = withContext(Dispatchers.IO) {
                runCatching { FolderMatching.bestFolder(kitsunekko.animeFolders(), titles) }.getOrNull()
            }
            val answer = JSONObject().put("folderName", folder?.name ?: JSONObject.NULL)
            if (folder != null) {
                val episodes = withContext(Dispatchers.IO) {
                    runCatching { kitsunekko.subtitleFiles(folder).mapNotNull { it.episode }.distinct().size }.getOrDefault(0)
                }
                answer.put("episodeFileCount", episodes)
            }
            reply(replyProxy, request, answer)
        }
    }

    private fun reply(replyProxy: JavaScriptReplyProxy, request: JSONObject, answer: JSONObject) {
        answer.put("requestId", request.optInt("requestId"))
        replyProxy.postMessage(answer.toString())
    }

    private companion object {
        const val OBJECT_NAME = "animeThorApp"
        const val MAXIMUM_LOOKUP_RETRIES = 5
    }
}
