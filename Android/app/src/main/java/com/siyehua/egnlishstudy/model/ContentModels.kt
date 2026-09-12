package com.siyehua.egnlishstudy.model

import java.util.UUID

sealed class Content(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: ContentType,
    val level: ContentLevel,
    val sourceName: String = "",
    open val audioUrl: String? = null
)

enum class ContentType {
    ARTICLE, BLOG, NEWS, DIALOGUE
}

enum class ContentLevel {
    A1, A2, B1, B2, C1, C2
}

data class Article(
    val articleTitle: String,
    val content: String,
    val author: String = "Unknown",
    val date: String = "",
    val source: String = author,
    val contentLevel: ContentLevel = ContentLevel.B1,
    val contentId: String = UUID.randomUUID().toString()
) : Content(id = contentId, title = articleTitle, type = ContentType.ARTICLE, level = contentLevel, sourceName = source)

data class Blog(
    val blogTitle: String,
    val content: String,
    val author: String,
    val date: String,
    val source: String = author,
    val contentLevel: ContentLevel = ContentLevel.B1,
    val contentId: String = UUID.randomUUID().toString()
) : Content(id = contentId, title = blogTitle, type = ContentType.BLOG, level = contentLevel, sourceName = source)

data class News(
    val newsTitle: String,
    val content: String,
    val source: String,
    val date: String,
    val contentLevel: ContentLevel = ContentLevel.B1,
    val contentId: String = UUID.randomUUID().toString()
) : Content(id = contentId, title = newsTitle, type = ContentType.NEWS, level = contentLevel, sourceName = source)

data class Dialogue(
    val dialogueTitle: String,
    val lines: List<DialogueLine>,
    val date: String = "",
    val source: String = "",
    val contentLevel: ContentLevel = ContentLevel.A2,
    val contentId: String = UUID.randomUUID().toString(),
    override val audioUrl: String? = null
) : Content(id = contentId, title = dialogueTitle, type = ContentType.DIALOGUE, level = contentLevel, sourceName = source, audioUrl = audioUrl)

data class DialogueLine(
    val speaker: String,
    val text: String,
    val trans: String = ""
)
