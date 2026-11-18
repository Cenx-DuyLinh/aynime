package dev.animethor

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LookupRound(val listChanged: Boolean, val hadConnectionProblems: Boolean)

class AnimeListLookup(private val store: AppStore) {
    private val oneLookupAtATime = Mutex()
    private val nextAttemptMsByNames = mutableMapOf<List<String>, Long>()

    suspend fun lookUpUnnamedAnime(): LookupRound = oneLookupAtATime.withLock {
        withContext(Dispatchers.IO) {
            var listChanged = false
            var hadConnectionProblems = false
            store.animeList()
                .filter { !it.isLookedUp && isDueForAttempt(namesForAniList(it)) }
                .forEach { anime ->
                    val names = namesForAniList(anime)
                    if (names.isEmpty()) {
                        waitBeforeNextAttempt(names, RETRY_AFTER_NOT_FOUND_MS)
                        DiagnosticsLog.add("My anime: \"${anime.name}\" has only a Vietnamese name, long-press it to choose")
                        return@forEach
                    }
                    val result = runCatching { findOnAniList(names) }
                    DiagnosticsLog.add(lookupSummary(names.first(), result))
                    val found = result.getOrNull()
                    when {
                        result.isFailure -> {
                            hadConnectionProblems = true
                            waitBeforeNextAttempt(names, RETRY_AFTER_CONNECTION_PROBLEM_MS)
                        }
                        found == null -> waitBeforeNextAttempt(names, RETRY_AFTER_NOT_FOUND_MS)
                        else -> {
                            store.saveLookedUpAnime(anime.name, found)
                            listChanged = true
                        }
                    }
                }
            LookupRound(listChanged, hadConnectionProblems)
        }
    }

    private fun namesForAniList(anime: WatchedAnime): List<String> =
        anime.searchNames.ifEmpty { listOf(anime.name) }.filterNot { PageTitles.looksVietnamese(it) }

    private fun lookupSummary(name: String, result: Result<AniListAnime?>): String {
        val error = result.exceptionOrNull()
        val found = result.getOrNull()
        return when {
            error != null -> "My anime: \"$name\" lookup failed (${error.message}), trying again in 30 s"
            found == null -> "My anime: \"$name\" not found on AniList"
            else -> "My anime: \"$name\" -> #${found.id} ${found.displayTitle}, cover ${if (found.coverUrl == null) "none" else "ok"}"
        }
    }

    private fun waitBeforeNextAttempt(names: List<String>, waitMs: Long) {
        nextAttemptMsByNames[names] = SystemClock.elapsedRealtime() + waitMs
    }

    private fun isDueForAttempt(names: List<String>): Boolean {
        val nextAttemptMs = nextAttemptMsByNames[names] ?: return true
        return SystemClock.elapsedRealtime() >= nextAttemptMs
    }

    private fun findOnAniList(searchNames: List<String>): AniListAnime? =
        searchNames.firstNotNullOfOrNull { name -> findOneNameOnAniList(name) }

    private fun findOneNameOnAniList(name: String): AniListAnime? {
        val searchName = SeasonNames.canonical(PageTitles.cleanAnimeName(name))
        val wantedSeason = SeasonNames.number(searchName) ?: 1
        val matches = AniListClient.search(searchName).ifEmpty { AniListClient.search(SeasonNames.withoutSeason(searchName)) }
        return bestMatch(matches, wantedSeason)
    }

    private fun bestMatch(matches: List<AniListAnime>, wantedSeason: Int): AniListAnime? {
        val sameSeason = matches.filter { seasonOf(it) == wantedSeason }
        return sameSeason.ifEmpty { matches }.maxByOrNull { it.popularity }
    }

    private fun seasonOf(anime: AniListAnime): Int = anime.titles.firstNotNullOfOrNull { SeasonNames.number(it) } ?: 1

    companion object {
        const val RETRY_AFTER_CONNECTION_PROBLEM_MS = 30_000L
        private const val RETRY_AFTER_NOT_FOUND_MS = 10 * 60_000L
    }
}
