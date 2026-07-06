package com.siyehua.egnlishstudy.data

object SentenceSplitter {
    fun split(text: String, minLength: Int = 2): List<String> =
        text.trim()
            .lines()
            .flatMap { splitLine(it) }
            .mapNotNull { sentence ->
                sentence.trim().takeIf { it.length >= minLength }
            }

    private fun splitLine(line: String): List<String> {
        val normalized = line.trim().replace(Regex("[ \\t]+"), " ")
        if (normalized.isBlank()) return emptyList()

        val sentences = mutableListOf<String>()
        var start = 0
        for (index in normalized.indices) {
            val char = normalized[index]
            if (char !in SENTENCE_ENDINGS) continue
            if (char == '.' && isAbbreviationPeriod(normalized, index)) continue

            val nextIndex = index + 1
            if (nextIndex < normalized.length && !normalized[nextIndex].isWhitespace()) continue

            sentences += normalized.substring(start, nextIndex)
            start = nextIndex
            while (start < normalized.length && normalized[start].isWhitespace()) {
                start += 1
            }
        }
        if (start < normalized.length) {
            sentences += normalized.substring(start)
        }
        return sentences
    }

    private fun isAbbreviationPeriod(text: String, index: Int): Boolean {
        val previous = text.getOrNull(index - 1)
        val nextChar = text.getOrNull(index + 1)
        if (previous?.isDigit() == true && nextChar?.isDigit() == true) {
            return true
        }
        if (previous?.isLetterOrDigit() == true && nextChar?.isLetterOrDigit() == true) {
            return true
        }
        if (previous?.isLetter() != true) return false

        val nextNonSpaceIndex = nextNonWhitespaceIndex(text, index + 1)
        val next = nextNonSpaceIndex?.let(text::get)
        val wordBeforePeriod = wordBefore(text, index)
        val dottedTail = dottedTailBefore(text, index)
        if (wordBeforePeriod in ALWAYS_NON_ENDING_ABBREVIATIONS) {
            return true
        }
        if (dottedTail in ALWAYS_NON_ENDING_ABBREVIATIONS) {
            return true
        }
        if (wordBeforePeriod == "no" && next?.isDigit() == true) {
            return true
        }
        if (wordBeforePeriod in CONTEXTUAL_NON_ENDING_ABBREVIATIONS && nextContinuesSentence(next)) {
            return true
        }

        val afterNext = nextNonSpaceIndex?.plus(1)?.let(text::getOrNull)
        if (previous.isUpperCase() && next?.isUpperCase() == true && afterNext == '.') {
            return true
        }

        val beforePrevious = text.getOrNull(index - 2)
        if (previous.isUpperCase() && beforePrevious == '.') {
            return next?.isLowerCase() == true
        }
        if (previous.isUpperCase() && isSpacedInitialismPeriod(text, index)) {
            return next?.isLowerCase() == true
        }

        return false
    }

    private fun nextNonWhitespaceIndex(text: String, start: Int): Int? {
        for (index in start until text.length) {
            if (!text[index].isWhitespace()) return index
        }
        return null
    }

    private val SENTENCE_ENDINGS = setOf('.', '!', '?')
    private val ALWAYS_NON_ENDING_ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "mx", "dr", "prof", "sr", "jr", "st", "mt",
        "vs", "etc", "e.g", "i.e",
        "inc", "ltd", "co", "corp", "dept", "univ", "assn", "bros",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec"
    )
    private val CONTEXTUAL_NON_ENDING_ABBREVIATIONS = setOf(
        "no", "fig", "eq", "ch", "vol", "pp", "p", "sec", "min", "max",
        "ft", "in", "oz", "lb"
    )

    private fun wordBefore(text: String, periodIndex: Int): String {
        var start = periodIndex - 1
        while (start >= 0 && text[start].isLetter()) {
            start -= 1
        }
        return text.substring(start + 1, periodIndex).lowercase()
    }

    private fun dottedTailBefore(text: String, periodIndex: Int): String {
        var start = periodIndex - 1
        while (start >= 0 && (text[start].isLetter() || text[start] == '.')) {
            start -= 1
        }
        return text.substring(start + 1, periodIndex).lowercase()
    }

    private fun nextContinuesSentence(next: Char?): Boolean =
        next?.isLowerCase() == true || next?.isDigit() == true || next in setOf('(', '[', '"', '\'')

    private fun isSpacedInitialismPeriod(text: String, periodIndex: Int): Boolean {
        val spaceBeforeCurrentLetter = periodIndex - 2
        val previousPeriod = periodIndex - 3
        val previousLetter = periodIndex - 4
        return previousLetter >= 0 &&
            text[spaceBeforeCurrentLetter].isWhitespace() &&
            text[previousPeriod] == '.' &&
            text[previousLetter].isUpperCase()
    }
}
