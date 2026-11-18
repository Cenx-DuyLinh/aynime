package dev.animethor

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

class SubtitleTrack(val label: String, val cues: List<SubtitleCue>) {

    fun textAt(positionMs: Long): String =
        cues.filter { positionMs >= it.startMs && positionMs < it.endMs }
            .joinToString("\n") { it.text }

    fun lastStartedCueIndex(positionMs: Long): Int = cues.indexOfLast { it.startMs <= positionMs + 50 }
}

object SubtitleParser {
    private val supportedExtensions = listOf(".srt", ".vtt", ".ass", ".ssa")

    private val timeRangePattern = Regex(
        """(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})\s*-->\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})"""
    )
    private val assTimePattern = Regex("""(\d+):(\d{2}):(\d{2})[.:](\d{1,3})""")
    private val htmlTagPattern = Regex("""<[^>]+>""")
    private val assOverridePattern = Regex("""\{[^}]*\}""")

    fun isSupported(fileName: String): Boolean = supportedExtensions.any { fileName.endsWith(it, ignoreCase = true) }

    fun parse(fileName: String, bytes: ByteArray): List<SubtitleCue> {
        val text = decodeText(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val isAss = fileName.endsWith(".ass", ignoreCase = true) || fileName.endsWith(".ssa", ignoreCase = true)
        val cues = if (isAss) parseAss(text) else parseSrtOrVtt(text)
        val japaneseCues = MixedLanguageCleaner.keepJapanese(cues)
        return japaneseCues.filter { it.text.isNotBlank() }.sortedBy { it.startMs }
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.startsWith(0xEF, 0xBB, 0xBF)) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.startsWith(0xFF, 0xFE)) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.startsWith(0xFE, 0xFF)) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return decodeStrictly(bytes, Charsets.UTF_8) ?: String(bytes, Charset.forName("Shift_JIS"))
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }

    private fun decodeStrictly(bytes: ByteArray, charset: Charset): String? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: CharacterCodingException) {
            null
        }
    }

    private fun parseSrtOrVtt(text: String): List<SubtitleCue> {
        val blocks = text.split(Regex("""\n\s*\n"""))
        return blocks.mapNotNull { block -> parseSrtBlock(block) }
    }

    private fun parseSrtBlock(block: String): SubtitleCue? {
        val lines = block.trim().lines()
        val timeLineIndex = lines.indexOfFirst { timeRangePattern.containsMatchIn(it) }
        if (timeLineIndex < 0) {
            return null
        }
        val match = timeRangePattern.find(lines[timeLineIndex]) ?: return null
        val groups = match.groupValues
        val startMs = toMilliseconds(groups[1], groups[2], groups[3], groups[4])
        val endMs = toMilliseconds(groups[5], groups[6], groups[7], groups[8])
        val cueText = lines.drop(timeLineIndex + 1).joinToString("\n") { cleanSrtLine(it) }.trim()
        return SubtitleCue(startMs, endMs, cueText)
    }

    private fun cleanSrtLine(line: String): String =
        line.replace(htmlTagPattern, "").replace(assOverridePattern, "").trim()

    private fun toMilliseconds(hours: String, minutes: String, seconds: String, fraction: String): Long {
        val hourValue = hours.toLongOrNull() ?: 0L
        val fractionMs = fraction.padEnd(3, '0').take(3).toLong()
        return ((hourValue * 60 + minutes.toLong()) * 60 + seconds.toLong()) * 1000 + fractionMs
    }

    private fun parseAss(text: String): List<SubtitleCue> {
        var fieldNames = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        val cues = mutableListOf<SubtitleCue>()
        var insideEvents = false
        for (line in text.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("[")) {
                insideEvents = trimmedLine.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!insideEvents) {
                continue
            }
            if (trimmedLine.startsWith("Format:", ignoreCase = true)) {
                fieldNames = trimmedLine.substringAfter(":").split(",").map { it.trim().lowercase() }
            } else if (trimmedLine.startsWith("Dialogue:", ignoreCase = true)) {
                parseAssDialogue(trimmedLine.substringAfter(":"), fieldNames)?.let { cues.add(it) }
            }
        }
        return cues
    }

    private fun parseAssDialogue(values: String, fieldNames: List<String>): SubtitleCue? {
        val fields = values.split(",", limit = fieldNames.size).map { it.trim() }
        if (fields.size < fieldNames.size) {
            return null
        }
        val startMs = parseAssTime(fields[fieldNames.indexOf("start")]) ?: return null
        val endMs = parseAssTime(fields[fieldNames.indexOf("end")]) ?: return null
        val rawText = fields[fieldNames.indexOf("text")]
        return SubtitleCue(startMs, endMs, cleanAssText(rawText))
    }

    private fun parseAssTime(value: String): Long? {
        val groups = assTimePattern.find(value)?.groupValues ?: return null
        val fraction = groups[4].padEnd(2, '0')
        return toMilliseconds(groups[1], groups[2], groups[3], fraction.padEnd(3, '0'))
    }

    private fun cleanAssText(rawText: String): String =
        rawText.replace(assOverridePattern, "")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .trim()
}

object MixedLanguageCleaner {
    private val kanaPattern = Regex("""[぀-ヿ]""")
    private val hanPattern = Regex("""[一-鿿]""")
    private const val MIXED_FILE_SHARE = 0.25
    private const val MINIMUM_HAN_FOR_CHINESE = 4

    fun keepJapanese(cues: List<SubtitleCue>): List<SubtitleCue> {
        val allLines = cues.flatMap { it.text.lines() }.filter { it.isNotBlank() }
        val chineseLineCount = allLines.count { looksChinese(it) }
        val isMixedFile = allLines.isNotEmpty() && chineseLineCount >= allLines.size * MIXED_FILE_SHARE
        if (!isMixedFile) {
            return cues
        }
        return cues.mapNotNull { cue ->
            val japaneseText = cue.text.lines().filterNot { looksChinese(it) }.joinToString("\n").trim()
            if (japaneseText.isEmpty()) null else cue.copy(text = japaneseText)
        }
    }

    private fun looksChinese(line: String): Boolean =
        !kanaPattern.containsMatchIn(line) && hanPattern.findAll(line).count() >= MINIMUM_HAN_FOR_CHINESE
}
