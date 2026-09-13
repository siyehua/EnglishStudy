package com.siyehua.egnlishstudy.data.wordform

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WordFormRequest(
    val surface: String,
    val sentence: String = ""
)

@Serializable
data class WordFormResponse(
    val surface: String,
    val normalized: String,
    val headword: String,
    @SerialName("pronunciationTarget")
    val pronunciationTarget: String,
    val relation: WordFormRelation? = null,
    val expansion: String? = null,
    @SerialName("currentPartOfSpeech")
    val currentPartOfSpeech: String? = null,
    @SerialName("currentPartOfSpeechLabel")
    val currentPartOfSpeechLabel: String = "",
    @SerialName("variantGroups")
    val variantGroups: List<WordFormVariantGroup> = emptyList(),
    val confidence: String,
    val source: String
)

@Serializable
data class WordFormRelation(
    val type: String,
    val target: String,
    val label: String
)

@Serializable
data class WordFormVariant(
    val type: String,
    val label: String,
    val value: String
)

@Serializable
data class WordFormVariantGroup(
    @SerialName("partOfSpeech")
    val partOfSpeech: String,
    val label: String,
    @SerialName("isCurrent")
    val isCurrent: Boolean = false,
    val variants: List<WordFormVariant> = emptyList()
)

@Serializable
data class WordPronunciationRequest(
    val word: String
)

@Serializable
data class WordPronunciationResponse(
    val word: String,
    val normalized: String,
    val phonetic: String? = null,
    @SerialName("audioUrl")
    val audioUrl: String? = null,
    val source: String,
    val found: Boolean
)

@Serializable
data class WordPhonicsRequest(
    val word: String,
    val sentence: String = "",
    val ipa: String? = null
)

@Serializable
data class WordPhonicsResponse(
    val word: String,
    val normalized: String,
    val ipa: String? = null,
    val phonemes: List<String> = emptyList(),
    val segments: List<WordPhonicsSegment> = emptyList(),
    val chunks: List<WordPhonicsChunk> = emptyList(),
    val note: String = "",
    val verification: String = "",
    val source: String,
    val found: Boolean,
    val message: String = ""
)

@Serializable
data class WordPhonicsSegment(
    val text: String,
    val phonemes: List<String> = emptyList(),
    val ipa: String = ""
)

@Serializable
data class WordPhonicsChunk(
    val text: String,
    val ipa: String = "",
    val phonemes: List<String> = emptyList(),
    val spans: List<WordPhonicsSpan> = emptyList(),
    val silent: Boolean = false,
    val stress: String = "",
    val rule: String = ""
)

@Serializable
data class WordPhonicsSpan(
    val text: String,
    val silent: Boolean = false
)

@Serializable
data class WordMeaningRequest(
    val word: String,
    val sentence: String = ""
)

@Serializable
data class WordMeaningResponse(
    val word: String,
    val normalized: String,
    val meanings: List<WordMeaningEntry> = emptyList(),
    val source: String,
    val found: Boolean,
    @SerialName("sentenceChinese")
    val sentenceChinese: String = ""
)

@Serializable
data class WordMeaningEntry(
    @SerialName("partOfSpeech")
    val partOfSpeech: String,
    val label: String,
    val meaning: String,
    val example: String = "",
    @SerialName("exampleChinese")
    val exampleChinese: String = ""
)

@Serializable
data class ContentFetchRequest(
    @SerialName("fetchMore")
    val fetchMore: Boolean = false,
    val types: List<String> = emptyList(),
    val levels: List<String> = emptyList(),
    val sources: List<String> = emptyList()
)

@Serializable
data class ContentFetchResponse(
    val items: List<RemoteContentItem> = emptyList(),
    val filters: RemoteContentFilters? = null
)

@Serializable
data class RemoteContentFilters(
    val types: List<RemoteFilterOption> = emptyList(),
    val levels: List<RemoteFilterOption> = emptyList(),
    val sources: List<RemoteFilterOption> = emptyList()
)

@Serializable
data class RemoteFilterOption(
    val id: String,
    val label: String,
    val count: Int
)

@Serializable
data class RemoteContentItem(
    val id: String,
    val title: String,
    val type: String,
    val level: String,
    val body: String,
    val author: String? = null,
    val source: String? = null,
    val date: String = "",
    val lines: List<RemoteDialogueLine> = emptyList(),
    @SerialName("audioUrl")
    val audioUrl: String? = null,
    @SerialName("audioStart")
    val audioStart: Double = 0.0,
    @SerialName("audioEnd")
    val audioEnd: Double = 0.0
)

@Serializable
data class RemoteDialogueLine(
    val speaker: String,
    val text: String,
    val start: Double = 0.0,
    val end: Double = 0.0,
    @SerialName("audioUrl")
    val audioUrl: String? = null
)
