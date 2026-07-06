package com.siyehua.egnlishstudy.ui.wordinsight

import com.siyehua.egnlishstudy.data.SentenceSplitter

data class ClickedWord(
    val word: String,
    val normalized: String,
    val range: IntRange,
    val sentence: String
)

internal fun String.clickedWordAt(offset: Int): ClickedWord? {
    if (isEmpty()) return null
    val safeOffset = offset.coerceIn(0, lastIndex)
    val index = when {
        this[safeOffset].isWordCoreChar() -> safeOffset
        safeOffset > 0 && this[safeOffset - 1].isWordCoreChar() -> safeOffset - 1
        safeOffset < lastIndex && this[safeOffset + 1].isWordCoreChar() -> safeOffset + 1
        else -> return null
    }

    var start = index
    while (start > 0 && isWordBodyCharAt(start - 1)) {
        start--
    }

    var endExclusive = index + 1
    while (endExclusive < length && isWordBodyCharAt(endExclusive)) {
        endExclusive++
    }

    while (start < endExclusive && !this[start].isWordCoreChar()) {
        start++
    }
    while (endExclusive > start && !this[endExclusive - 1].isWordCoreChar()) {
        endExclusive--
    }
    if (start >= endExclusive) return null

    val word = substring(start, endExclusive)
    val normalized = word
        .trim('\'', '-', '\u2019')
        .lowercase()
    if (normalized.isBlank() || normalized.none { it.isLetter() }) return null

    return ClickedWord(
        word = word,
        normalized = normalized,
        range = start until endExclusive,
        sentence = sentenceAround(start, endExclusive)
    )
}

private fun String.isWordBodyCharAt(index: Int): Boolean {
    val char = this[index]
    if (char.isWordCoreChar()) return true
    if (char != '\'' && char != '\u2019' && char != '-') return false
    val previous = getOrNull(index - 1)
    val next = getOrNull(index + 1)
    return previous?.isWordCoreChar() == true && next?.isWordCoreChar() == true
}

private fun Char.isWordCoreChar(): Boolean =
    isLetter()

private fun String.sentenceAround(start: Int, endExclusive: Int): String {
    val normalized = replace(Regex("[ \\t]+"), " ")
    var searchStart = 0
    SentenceSplitter.split(normalized).forEach { sentence ->
        val sentenceStart = normalized.indexOf(sentence, startIndex = searchStart)
        if (sentenceStart >= 0) {
            val sentenceEnd = sentenceStart + sentence.length
            if (start in sentenceStart until sentenceEnd || endExclusive in (sentenceStart + 1)..sentenceEnd) {
                return sentence
            }
            searchStart = sentenceEnd
        }
    }
    return trim()
}
