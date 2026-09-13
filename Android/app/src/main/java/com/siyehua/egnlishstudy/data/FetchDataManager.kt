package com.siyehua.egnlishstudy.data

import android.content.Context
import android.util.Log
import com.siyehua.egnlishstudy.data.wordform.RemoteContentItem
import com.siyehua.egnlishstudy.data.wordform.RemoteContentFilters
import com.siyehua.egnlishstudy.data.wordform.WordFormApiClient
import com.siyehua.egnlishstudy.model.Article
import com.siyehua.egnlishstudy.model.Blog
import com.siyehua.egnlishstudy.model.Content
import com.siyehua.egnlishstudy.model.ContentLevel
import com.siyehua.egnlishstudy.model.ContentType
import com.siyehua.egnlishstudy.model.Dialogue
import com.siyehua.egnlishstudy.model.DialogueLine
import com.siyehua.egnlishstudy.model.News
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FetchDataManager(context: Context? = null) {
    private val cacheDatabase = context?.applicationContext?.let(::ContentCacheDatabase)
    private val backendClient = WordFormApiClient()

    suspend fun loadCachedContent(): List<Content> = withContext(Dispatchers.IO) {
        loadCachedContentInternal()
    }

    suspend fun queryCachedContent(
        types: Set<ContentType>,
        levels: Set<ContentLevel>,
        sources: Set<String>,
        limit: Int,
        offset: Int
    ): List<Content> = withContext(Dispatchers.IO) {
        cacheDatabase?.queryContent(
            types = types,
            levels = levels,
            sources = sources,
            limit = limit,
            offset = offset
        ).orEmpty()
    }

    suspend fun countCachedContent(
        types: Set<ContentType>,
        levels: Set<ContentLevel>,
        sources: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        cacheDatabase?.countContent(types = types, levels = levels, sources = sources) ?: 0
    }

    suspend fun loadFilterCounts(
        selectedTypes: Set<ContentType>,
        selectedLevels: Set<ContentLevel>,
        selectedSources: Set<String>
    ): FilterCounts = withContext(Dispatchers.IO) {
        FilterCounts(
            typeCounts = cacheDatabase?.countByType(
                levels = selectedLevels,
                sources = selectedSources
            ).orEmpty(),
            levelCounts = cacheDatabase?.countByLevel(
                types = selectedTypes,
                sources = selectedSources
            ).orEmpty(),
            sourceCounts = cacheDatabase?.countBySource(
                types = selectedTypes,
                levels = selectedLevels
            ).orEmpty()
        )
    }

    suspend fun refreshRemoteContent(
        fetchMore: Boolean = false,
        types: Set<ContentType> = emptySet(),
        levels: Set<ContentLevel> = emptySet(),
        sources: Set<String> = emptySet(),
        replaceCache: Boolean = false
    ): ContentRefreshResult = withContext(Dispatchers.IO) {
        refreshRemoteContentInternal(
            request = FetchRequest(
                fetchMore = fetchMore,
                types = types,
                levels = levels,
                sources = sources
            ),
            replaceCache = replaceCache
        )
    }

    suspend fun fetchAll(): List<Content> = withContext(Dispatchers.IO) {
        refreshRemoteContentInternal(FetchRequest(fetchMore = false), replaceCache = false)
            .content
            .ifEmpty { loadCachedContentInternal() }
    }

    private fun loadCachedContentInternal(): List<Content> =
        cacheDatabase?.loadAll().orEmpty()

    private suspend fun refreshRemoteContentInternal(
        request: FetchRequest,
        replaceCache: Boolean
    ): ContentRefreshResult {
        val remote = fetchRemoteContent(request)
        val remoteContent = remote.content
        if (remoteContent.isEmpty()) {
            return ContentRefreshResult(content = emptyList(), filters = remote.filters)
        }

        val merged = remoteContent
            .distinctBy { it.id }
            .sortedByDescending { it.sortDate() }
        if (replaceCache) {
            cacheDatabase?.replaceAll(merged)
        } else {
            cacheDatabase?.upsertAll(merged)
        }
        return ContentRefreshResult(content = merged, filters = remote.filters)
    }

    private suspend fun fetchRemoteContent(request: FetchRequest): ContentRefreshResult {
        val response = backendClient.fetchContent(
            fetchMore = request.fetchMore,
            types = request.types.map { it.name },
            levels = request.levels.map { it.name },
            sources = request.sources.toList()
        )
        val content = response.items
            .mapNotNull { item ->
                runCatching { item.toContent() }
                    .onFailure { Log.w(TAG, "Failed to map backend content ${item.id}", it) }
                    .getOrNull()
            }
            .distinctBy { it.id }
        return ContentRefreshResult(content = content, filters = response.filters)
    }

    private fun RemoteContentItem.toContent(): Content {
        val contentType = ContentType.valueOf(type)
        val contentLevel = runCatching { ContentLevel.valueOf(level) }
            .getOrDefault(ContentLevel.B1)

        return when (contentType) {
            ContentType.ARTICLE -> Article(
                articleTitle = title,
                content = body,
                author = author ?: "Unknown",
                date = date,
                source = source ?: author ?: "Unknown",
                contentLevel = contentLevel,
                contentId = id
            )

            ContentType.BLOG -> Blog(
                blogTitle = title,
                content = body,
                author = author ?: "Unknown",
                date = date,
                source = source ?: author ?: "Unknown",
                contentLevel = contentLevel,
                contentId = id
            )

            ContentType.NEWS -> News(
                newsTitle = title,
                content = body,
                source = source ?: "Unknown",
                date = date,
                contentLevel = contentLevel,
                contentId = id
            )

            ContentType.DIALOGUE -> Dialogue(
                dialogueTitle = title,
                lines = lines.map { line ->
                    DialogueLine(
                        speaker = line.speaker,
                        text = line.text,
                        start = line.start,
                        end = line.end
                    )
                }.ifEmpty {
                    listOf(DialogueLine(speaker = "Narrator", text = body))
                },
                date = date,
                source = source.orEmpty(),
                contentLevel = contentLevel,
                contentId = id,
                audioUrl = audioUrl,
                audioStart = audioStart,
                audioEnd = audioEnd
            )
        }
    }

    private fun Content.sortDate(): String =
        when (this) {
            is Article -> date
            is Blog -> date
            is News -> date
            is Dialogue -> date
        }

    private data class FetchRequest(
        val fetchMore: Boolean,
        val types: Set<ContentType> = emptySet(),
        val levels: Set<ContentLevel> = emptySet(),
        val sources: Set<String> = emptySet()
    )

    data class FilterCounts(
        val typeCounts: Map<ContentType, Int>,
        val levelCounts: Map<ContentLevel, Int>,
        val sourceCounts: Map<String, Int>
    )

    data class ContentRefreshResult(
        val content: List<Content>,
        val filters: RemoteContentFilters? = null
    )

    companion object {
        private const val TAG = "FetchDataManager"
    }
}
