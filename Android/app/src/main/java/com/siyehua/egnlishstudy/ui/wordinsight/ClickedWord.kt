package com.siyehua.egnlishstudy.ui.wordinsight

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
    val sentenceStart = lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), startIndex = start)
        .let { if (it == -1) 0 else it + 1 }
    val sentenceEnd = indexOfAny(charArrayOf('.', '!', '?', '\n'), startIndex = endExclusive)
        .let { if (it == -1) length else it + 1 }
    return substring(sentenceStart, sentenceEnd).trim()
}
