package com.siyehua.egnlishstudy.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siyehua.egnlishstudy.data.FetchDataManager
import com.siyehua.egnlishstudy.data.wordform.RemoteContentFilters
import com.siyehua.egnlishstudy.data.wordform.RemoteFilterOption
import com.siyehua.egnlishstudy.model.Content
import com.siyehua.egnlishstudy.model.ContentLevel
import com.siyehua.egnlishstudy.model.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContentViewModel(application: Application) : AndroidViewModel(application) {
    private val fetchDataManager = FetchDataManager(application)

    private val _uiState = MutableStateFlow<ContentUiState>(ContentUiState.Loading)
    val uiState: StateFlow<ContentUiState> = _uiState.asStateFlow()

    private var displayedContent: List<Content> = emptyList()
    private var selectedTypes: Set<ContentType> = emptySet()
    private var selectedLevels: Set<ContentLevel> = emptySet()
    private var selectedSources: Set<String> = emptySet()
    private var nextOffset = 0
    private var hasMore = true
    private var isLoadingPage = false
    private var isRefreshingRemote = false
    private var typeCounts: Map<ContentType, Int> = emptyMap()
    private var levelCounts: Map<ContentLevel, Int> = emptyMap()
    private var sourceCounts: Map<String, Int> = emptyMap()
    private var typeOptions: List<FilterOption> = emptyList()
    private var levelOptions: List<FilterOption> = emptyList()
    private var sourceOptions: List<FilterOption> = emptyList()
    private var totalCount = 0

    init {
        refreshContent()
    }

    fun refreshContent() {
        viewModelScope.launch {
            val cachedContent = fetchDataManager.queryCachedContent(
                types = selectedTypes,
                levels = selectedLevels,
                sources = selectedSources,
                limit = PAGE_SIZE,
                offset = 0
            )
            if (cachedContent.isNotEmpty()) {
                replaceDisplayedContent(cachedContent)
                nextOffset = cachedContent.size
                hasMore = cachedContent.size == PAGE_SIZE
                refreshFilterCounts()
                emitSuccess()
            } else {
                _uiState.value = ContentUiState.Loading
            }

            try {
                isRefreshingRemote = true
                val remote = fetchDataManager.refreshRemoteContent(
                    fetchMore = false,
                    replaceCache = true
                )
                updateRemoteFilterOptions(remote.filters)
                if (remote.content.isNotEmpty()) {
                    reloadFirstPage()
                } else if (cachedContent.isEmpty()) {
                    displayedContent = emptyList()
                    nextOffset = 0
                    hasMore = false
                    refreshFilterCounts()
                    emitSuccess()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh remote content", e)
                if (cachedContent.isEmpty()) {
                    _uiState.value = ContentUiState.Error("Failed to load content: ${e.message}")
                }
            } finally {
                isRefreshingRemote = false
                if (_uiState.value is ContentUiState.Success) {
                    emitSuccess()
                }
            }
        }
    }

    fun refreshMoreContent() {
        if (isRefreshingRemote) return

        viewModelScope.launch {
            isRefreshingRemote = true
            emitSuccess()
            try {
                val remote = fetchDataManager.refreshRemoteContent(
                    fetchMore = true,
                    types = selectedTypes,
                    levels = selectedLevels,
                    sources = selectedSources,
                    replaceCache = selectedTypes.isEmpty() && selectedLevels.isEmpty() && selectedSources.isEmpty()
                )
                updateRemoteFilterOptions(remote.filters)
                if (remote.content.isNotEmpty()) {
                    reloadFirstPage()
                } else {
                    emitSuccess()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh more content", e)
                if (displayedContent.isEmpty()) {
                    _uiState.value = ContentUiState.Error("Failed to load content: ${e.message}")
                }
            } finally {
                isRefreshingRemote = false
                if (displayedContent.isNotEmpty() || _uiState.value !is ContentUiState.Error) {
                    emitSuccess()
                }
            }
        }
    }

    fun toggleType(type: ContentType) {
        selectedTypes = selectedTypes.toggle(type)
        reloadAfterFilterChanged()
    }

    fun toggleLevel(level: ContentLevel) {
        selectedLevels = selectedLevels.toggle(level)
        reloadAfterFilterChanged()
    }

    fun toggleSource(source: String) {
        selectedSources = selectedSources.toggle(source)
        reloadAfterFilterChanged()
    }

    fun clearFilters() {
        selectedTypes = emptySet()
        selectedLevels = emptySet()
        selectedSources = emptySet()
        reloadAfterFilterChanged()
    }

    fun loadNextPage() {
        if (!hasMore || isLoadingPage) return

        isLoadingPage = true
        emitSuccess(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val nextPage = fetchDataManager.queryCachedContent(
                    types = selectedTypes,
                    levels = selectedLevels,
                    sources = selectedSources,
                    limit = PAGE_SIZE,
                    offset = nextOffset
                )
                mergeDisplayedContent(nextPage)
                nextOffset += nextPage.size
                hasMore = nextPage.size == PAGE_SIZE
            } finally {
                isLoadingPage = false
                emitSuccess()
            }
        }
    }

    private fun reloadAfterFilterChanged() {
        viewModelScope.launch {
            reloadFirstPage()
        }
    }

    private suspend fun reloadFirstPage() {
        isLoadingPage = true
        val firstPage = fetchDataManager.queryCachedContent(
            types = selectedTypes,
            levels = selectedLevels,
            sources = selectedSources,
            limit = PAGE_SIZE,
            offset = 0
        )
        replaceDisplayedContent(firstPage)
        nextOffset = firstPage.size
        hasMore = firstPage.size == PAGE_SIZE
        refreshFilterCounts()
        isLoadingPage = false
        emitSuccess()
    }

    private fun replaceDisplayedContent(content: List<Content>) {
        displayedContent = content.deduplicateById()
    }

    private fun mergeDisplayedContent(content: List<Content>) {
        val byId = LinkedHashMap<String, Content>()
        displayedContent.forEach { item ->
            byId[item.id] = item
        }
        content.forEach { item ->
            byId[item.id] = item
        }
        displayedContent = byId.values.toList()
    }

    private fun List<Content>.deduplicateById(): List<Content> {
        val byId = LinkedHashMap<String, Content>()
        forEach { item ->
            byId[item.id] = item
        }
        return byId.values.toList()
    }

    private suspend fun refreshFilterCounts() {
        val counts = fetchDataManager.loadFilterCounts(
            selectedTypes = selectedTypes,
            selectedLevels = selectedLevels,
            selectedSources = selectedSources
        )
        typeCounts = counts.typeCounts
        levelCounts = counts.levelCounts
        sourceCounts = counts.sourceCounts
        if (typeOptions.isEmpty()) {
            typeOptions = typeCounts.toTypeOptions()
        }
        if (levelOptions.isEmpty()) {
            levelOptions = levelCounts.toLevelOptions()
        }
        if (sourceOptions.isEmpty()) {
            sourceOptions = sourceCounts.toSourceOptions()
        }
        totalCount = fetchDataManager.countCachedContent(
            types = selectedTypes,
            levels = selectedLevels,
            sources = selectedSources
        )
    }

    private fun emitSuccess(isLoadingMore: Boolean = false) {
        _uiState.value = ContentUiState.Success(
            content = displayedContent,
            selectedTypes = selectedTypes,
            selectedLevels = selectedLevels,
            selectedSources = selectedSources,
            typeCounts = typeCounts,
            levelCounts = levelCounts,
            sourceCounts = sourceCounts,
            typeOptions = typeOptions.ifEmpty { typeCounts.toTypeOptions() },
            levelOptions = levelOptions.ifEmpty { levelCounts.toLevelOptions() },
            sourceOptions = sourceOptions.ifEmpty { sourceCounts.toSourceOptions() },
            totalCount = totalCount,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            isRefreshing = isRefreshingRemote
        )
    }

    private fun updateRemoteFilterOptions(filters: RemoteContentFilters?) {
        if (filters == null) return
        val remoteTypes = filters.types.toFilterOptions()
        val remoteLevels = filters.levels.toFilterOptions()
        val remoteSources = filters.sources.toFilterOptions()
        if (remoteTypes.isNotEmpty()) {
            typeOptions = remoteTypes
        }
        if (remoteLevels.isNotEmpty()) {
            levelOptions = remoteLevels
        }
        if (remoteSources.isNotEmpty()) {
            sourceOptions = remoteSources
        }
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> =
        if (item in this) this - item else this + item

    companion object {
        private const val PAGE_SIZE = 20
        private const val TAG = "ContentViewModel"
    }
}

sealed class ContentUiState {
    object Loading : ContentUiState()
    data class Success(
        val content: List<Content>,
        val selectedTypes: Set<ContentType> = emptySet(),
        val selectedLevels: Set<ContentLevel> = emptySet(),
        val selectedSources: Set<String> = emptySet(),
        val typeCounts: Map<ContentType, Int> = emptyMap(),
        val levelCounts: Map<ContentLevel, Int> = emptyMap(),
        val sourceCounts: Map<String, Int> = emptyMap(),
        val typeOptions: List<FilterOption> = emptyList(),
        val levelOptions: List<FilterOption> = emptyList(),
        val sourceOptions: List<FilterOption> = emptyList(),
        val totalCount: Int = content.size,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isRefreshing: Boolean = false
    ) : ContentUiState()
    data class Error(val message: String) : ContentUiState()
}

data class FilterOption(
    val id: String,
    val label: String,
    val count: Int
)

private fun Map<ContentType, Int>.toTypeOptions(): List<FilterOption> =
    entries
        .sortedBy { it.key.ordinal }
        .map { (type, count) ->
            FilterOption(
                id = type.name,
                label = type.name.lowercase().replaceFirstChar { it.uppercase() },
                count = count
            )
        }

private fun Map<ContentLevel, Int>.toLevelOptions(): List<FilterOption> =
    entries
        .sortedBy { it.key.ordinal }
        .map { (level, count) -> FilterOption(id = level.name, label = level.name, count = count) }

private fun Map<String, Int>.toSourceOptions(): List<FilterOption> =
    entries
        .sortedByDescending { it.value }
        .map { (source, count) -> FilterOption(id = source, label = source, count = count) }

private fun List<RemoteFilterOption>.toFilterOptions(): List<FilterOption> =
    map { option ->
        FilterOption(
            id = option.id,
            label = option.label.ifBlank { option.id },
            count = option.count
        )
    }
