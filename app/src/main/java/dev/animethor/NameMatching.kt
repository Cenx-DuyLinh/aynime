package dev.animethor

import kotlin.math.abs

object EpisodeNumbers {
    private val pagePatterns = listOf(
        Regex("""(?i)episode[\s\-_:]*(\d{1,4})"""),
        Regex("""(?i)(?:^|[^a-z])ep[\s\-_.:]*(\d{1,4})(?!\d)"""),
        Regex("""第\s*(\d{1,4})\s*[話话集]"""),
        Regex("""(?i)tập[\s\-_:]*(\d{1,4})"""),
        Regex("""(?i)(?:^|[/\-_])tap[\-_]?0*(\d{1,4})(?!\d)"""),
    )

    private val loosePagePatterns = listOf(
        Regex("""(?<![\d.])0(\d)(?![\d.])"""),
        Regex("""\s[-–]\s0*(\d{1,3})(?=\s|$)"""),
        Regex("""(?i)[?&](?:ep|episode|e)=0*(\d{1,3})(?:&|$)"""),
    )

    private val fileNamePatterns = listOf(
        Regex("""(?i)(?:^|[^a-z])(?:e|ep|episode)\s*(\d{1,4})(?!\d)"""),
        Regex("""第\s*(\d{1,4})\s*[話话集]"""),
        Regex("""\s-\s(\d{1,4})(?:v\d)?(?!\d)"""),
        Regex("""\[(\d{1,4})(?:v\d)?]"""),
        Regex("""(?:^|[\s_.])(\d{2,3})(?:v\d)?(?:[\s_.]|$)"""),
    )

    fun fromPage(pageTitle: String, pageUrl: String): Int? =
        firstNumber(pagePatterns, pageTitle)
            ?: firstNumber(pagePatterns, pageUrl)
            ?: firstNumber(loosePagePatterns, pageTitle)
            ?: firstNumber(loosePagePatterns, pageUrl)

    fun fromFileName(fileName: String): Int? = firstNumber(fileNamePatterns, fileName.substringBeforeLast("."))

    private fun firstNumber(patterns: List<Regex>, text: String): Int? {
        for (pattern in patterns) {
            val number = pattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (number != null) {
                return number
            }
        }
        return null
    }
}

object PageTitles {
    private val leadingWatchPattern = Regex("""(?i)^\s*(?:watch|xem\s+phim|xem\s+anime|xem)\s+""")
    private val episodePartPattern = Regex("""(?i)\s*(episode|ep\.?|tập)\s*\d+.*$""")
    private val separatorPattern = Regex("""\s[|\-–—]\s""")
    private val noiseWordPattern = Regex(
        """(?i)\b(english|eng|sub|subbed|dub|dubbed|online|free|hd|full|vietsub|thuyết\s+minh|lồng\s+tiếng|trọn\s+bộ)\b""",
    )

    private val leadingEpisodePattern = Regex("""(?i)^\s*(?:tập|episode|ep\.?)\s*\d+\s*[-:|.]?\s*""")
    private val yearOnlyPattern = Regex("""^(?:19|20)\d{2}$""")
    private val nameListSeparatorPattern = Regex("""\s*[,;/|]\s*""")

    fun animeName(pageTitle: String): String {
        val withoutWatch = SeasonNames.canonical(pageTitle)
            .replace(leadingWatchPattern, "")
            .replace(leadingEpisodePattern, "")
        val withoutEpisode = withoutWatch.replace(episodePartPattern, "")
        val firstPart = withoutEpisode.split(separatorPattern).first()
        val withoutNoise = firstPart.replace(noiseWordPattern, " ")
        return withoutNoise.replace(Regex("""\s+"""), " ").trim(' ', '-', ':', '|', '/', '+')
    }

    private val bracketPartPattern = Regex("""\s*[(\[【]([^)\]】]*)[)\]】]""")
    private val trailingSitePattern = Regex("""(?i)\s+(?:on|at)\s+\S+$""")
    private val trailingEpisodePattern = Regex("""(?i)[\s\-–:#.]*(?:e|ep|episode)?\s*\d{1,4}(?:\s*(?:end|final|v\d))?$""")

    fun cleanAnimeName(pageTitle: String): String {
        val name = removeTagBrackets(animeName(pageTitle)).replace(trailingSitePattern, "")
        val withoutTrailingNumber = removeTrailingEpisodeNumber(name)
        return withoutTrailingNumber.replace(Regex("""\s+"""), " ").trim(' ', '-', ':', '|', '/', '.', '#', '+')
    }

    fun alternativeNames(pageTitle: String): List<String> =
        bracketPartPattern.findAll(animeName(pageTitle))
            .flatMap { bracket -> namesFromList(bracket.groupValues[1]) }
            .distinct()
            .toList()

    fun namesFromList(listText: String): List<String> {
        val season = SeasonNames.number(listText)
        return listText.split(nameListSeparatorPattern)
            .map { piece -> piece.replace(noiseWordPattern, " ").replace(Regex("""\s+"""), " ").trim(' ', '.', '-', ':') }
            .filter { isAlternativeName(it) }
            .map { withSeason(it, season) }
            .distinct()
            .sortedByDescending { it.length }
    }

    private fun isAlternativeName(text: String): Boolean {
        val isSeasonOnly = SeasonNames.withoutSeason(text).isBlank()
        val hasLetters = text.count { it.isLetter() } >= MINIMUM_NAME_LETTERS
        return hasLetters && !isSeasonOnly && !yearOnlyPattern.matches(text)
    }

    fun searchNames(pageTitle: String, namesShownOnPage: List<String> = emptyList()): List<String> {
        val mainName = cleanAnimeName(pageTitle)
        val season = SeasonNames.number(mainName)
        val fromPage = namesShownOnPage.flatMap { namesFromList(it) }
        val fromTitle = alternativeNames(pageTitle).map { name -> withSeason(name, season) }
        val allNames = fromPage + fromTitle + mainName
        val (vietnameseNames, otherNames) = allNames.filter { it.isNotBlank() }.distinct().partition { looksVietnamese(it) }
        return otherNames.ifEmpty { vietnameseNames }
    }

    private val vietnameseLetters = "ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ"

    private fun withSeason(name: String, season: Int?): String {
        val needsSeason = season != null && season > 1 && SeasonNames.number(name) == null
        return if (needsSeason) "$name season $season" else name
    }

    fun looksVietnamese(text: String): Boolean = text.lowercase().any { it in vietnameseLetters }

    private const val MINIMUM_NAME_LETTERS = 3

    private fun removeTagBrackets(name: String): String = name.replace(bracketPartPattern) { bracket ->
        val inside = bracket.groupValues[1]
        if (SeasonNames.number(inside) != null) " $inside" else " "
    }

    private fun removeTrailingEpisodeNumber(name: String): String {
        val withoutNumber = name.replace(trailingEpisodePattern, "")
        val numberWasTheSeason = SeasonNames.number(name) != null && SeasonNames.number(withoutNumber) == null
        val nothingLeft = withoutNumber.isBlank()
        return if (numberWasTheSeason || nothingLeft) name else withoutNumber
    }

    private val addressEpisodePartPattern = Regex("""(?i)-(episode|ep)-?\d+.*$""")
    private val addressNoisePattern = Regex("""(?i)-(english|eng|sub|subbed|dub|dubbed|online|free|hd|full|watch)(?=-|$)""")
    private val addressIdPattern = Regex("""-\d{4,}$""")

    fun nameFromAddress(pageUrl: String): String {
        val path = android.net.Uri.parse(pageUrl).path ?: return ""
        val lastPart = path.split("/").lastOrNull { it.isNotBlank() } ?: return ""
        val withoutEpisode = lastPart.replace(addressEpisodePartPattern, "")
        val withoutNoise = withoutEpisode.replace(addressNoisePattern, "").replace(addressIdPattern, "")
        return withoutNoise.replace('-', ' ').replace('_', ' ').trim()
    }
}

object SeasonNames {
    private val ordinalSeasonPattern = Regex("""(?i)\b(\d+)\s*(?:st|nd|rd|th)\s+season\b""")
    private val shortSeasonPattern = Regex("""(?i)\bs(\d{1,2})\b""")
    private val japaneseSeasonPattern = Regex("""第\s*(\d+)\s*期""")
    private val vietnameseSeasonPattern = Regex("""(?i)\bphần\s*(\d+)\b""")
    private val seasonNumberPattern = Regex("""(?i)\bseason\s*(\d+)\b""")

    fun canonical(text: String): String = text
        .replace(ordinalSeasonPattern, "season $1")
        .replace(japaneseSeasonPattern, "season $1")
        .replace(vietnameseSeasonPattern, "season $1")
        .replace(shortSeasonPattern, "season $1")

    fun number(text: String): Int? = seasonNumberPattern.find(canonical(text))?.groupValues?.get(1)?.toIntOrNull()

    fun withoutSeason(text: String): String = canonical(text).replace(seasonNumberPattern, " ").replace(Regex("""\s+"""), " ").trim()
}

object FolderMatching {
    private val nonWordPattern = Regex("""[^\p{L}\p{N}]+""")
    private const val MINIMUM_OVERLAP = 0.6

    fun normalize(text: String): String = SeasonNames.canonical(text).lowercase().replace(nonWordPattern, " ").trim()

    private fun words(text: String): List<String> = normalize(text).split(" ").filter { it.isNotEmpty() }

    fun search(folders: List<AnimeFolder>, query: String): List<AnimeFolder> {
        val queryWords = words(query)
        if (queryWords.isEmpty()) {
            return folders
        }
        return folders
            .map { folder -> folder to matchedWordCount(folder, queryWords) }
            .filter { (_, matchedWords) -> matchedWords > 0 }
            .sortedWith(compareByDescending<Pair<AnimeFolder, Int>> { it.second }.thenBy { it.first.name.length })
            .map { it.first }
    }

    fun bestFolder(folders: List<AnimeFolder>, titles: List<String>, wantedSeason: Int? = null): AnimeFolder? {
        val usableTitles = titles.filter { words(it).isNotEmpty() }
        val seasonFolders = if (wantedSeason == null) folders else folders.filter { seasonOf(it.name) == wantedSeason }
        for (title in usableTitles) {
            val normalizedTitle = normalize(title)
            seasonFolders.firstOrNull { normalize(it.name) == normalizedTitle }?.let { return it }
        }
        for (title in usableTitles) {
            closestFolderContainingAllWords(seasonFolders, title)?.let { return it }
        }
        for (title in usableTitles) {
            mostOverlappingFolder(seasonFolders, title)?.let { return it }
        }
        return null
    }

    private fun seasonOf(text: String): Int = SeasonNames.number(text) ?: 1

    private fun closestFolderContainingAllWords(folders: List<AnimeFolder>, title: String): AnimeFolder? {
        val titleWords = words(title)
        val normalizedTitleLength = normalize(title).length
        return folders
            .filter { matchedWordCount(it, titleWords) == titleWords.size && seasonOf(it.name) == seasonOf(title) }
            .minByOrNull { abs(normalize(it.name).length - normalizedTitleLength) }
    }

    private fun mostOverlappingFolder(folders: List<AnimeFolder>, title: String): AnimeFolder? {
        val titleWords = words(title)
        return folders
            .filter { seasonOf(it.name) == seasonOf(title) }
            .map { folder -> folder to overlap(folder, titleWords) }
            .filter { (_, score) -> score >= MINIMUM_OVERLAP }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun overlap(folder: AnimeFolder, titleWords: List<String>): Double {
        val folderWords = words(folder.name)
        val sharedWords = titleWords.count { it in folderWords.toSet() }
        return sharedWords.toDouble() / maxOf(titleWords.size, folderWords.size)
    }

    private fun matchedWordCount(folder: AnimeFolder, queryWords: List<String>): Int {
        val folderWords = words(folder.name).toSet()
        return queryWords.count { it in folderWords }
    }
}

object SubtitleFileSources {
    private val webSourcePattern = Regex(
        """(?i)\b(web|web-?dl|webrip|netflix|nf|crunchyroll|cr|amazon|amzn|prime|abema|disney|dsnp|hidive|b-global|bilibili|u-next|unext)\b"""
    )
    private val tvSourcePattern = Regex(
        """(?i)\b(at-x|atx|ntv|tbs|tokyo ?mx|mx|bs11|bs-?11|cx|fuji|mbs|tx|tv tokyo|nhk|abc|ytv|ex|tv asahi|bs4|bs-?nittele)\b"""
    )
    private val discSourcePattern = Regex("""(?i)\b(bd|bdrip|blu-?ray|dvd)\b""")

    private val mixedLanguagePattern = Regex(
        """(?i)(jpsc|jptc|scjp|tcjp|\bchs\b|\bcht\b|\bchi\b|chinese|\bgb\b|big5|\b(?:sc|tc)[&_.\- ]?jpn?\b|\bjpn?[&_.\- ]?(?:sc|tc)\b|简|繁|中日|日中|双语|雙語)"""
    )
    private val japaneseOnlyPattern = Regex("""(?i)(\.ja\.|\.ja\[|\.jpn?\.|\bjpn?\b|japanese|日本語|\[ja])""")

    fun label(fileName: String): String = "${languageLabel(fileName)}, ${sourceLabel(fileName)}"

    fun preference(fileName: String): Int = languagePreference(fileName) * 10 + sourcePreference(fileName)

    private fun languageLabel(fileName: String): String = when {
        mixedLanguagePattern.containsMatchIn(fileName) -> "JP+CN"
        japaneseOnlyPattern.containsMatchIn(fileName) -> "JP"
        else -> "JP?"
    }

    private fun languagePreference(fileName: String): Int = when {
        mixedLanguagePattern.containsMatchIn(fileName) -> 0
        japaneseOnlyPattern.containsMatchIn(fileName) -> 2
        else -> 1
    }

    private fun sourceLabel(fileName: String): String {
        webSourcePattern.find(fileName)?.let { return "web: ${it.value}" }
        tvSourcePattern.find(fileName)?.let { return "TV: ${it.value}" }
        discSourcePattern.find(fileName)?.let { return "disc: ${it.value}" }
        return "source unknown"
    }

    private fun sourcePreference(fileName: String): Int = when {
        webSourcePattern.containsMatchIn(fileName) -> 3
        discSourcePattern.containsMatchIn(fileName) -> 2
        tvSourcePattern.containsMatchIn(fileName) -> 0
        else -> 1
    }
}
