package com.siyehua.egnlishstudy.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siyehua.egnlishstudy.model.Article
import com.siyehua.egnlishstudy.model.Blog
import com.siyehua.egnlishstudy.model.Content
import com.siyehua.egnlishstudy.model.ContentLevel
import com.siyehua.egnlishstudy.model.ContentType
import com.siyehua.egnlishstudy.model.Dialogue
import com.siyehua.egnlishstudy.model.News
import com.siyehua.egnlishstudy.ui.ContentUiState
import com.siyehua.egnlishstudy.ui.ContentViewModel
import com.siyehua.egnlishstudy.ui.FilterOption
import com.siyehua.egnlishstudy.ui.theme.EgnlishStudyTheme
import com.siyehua.egnlishstudy.ui.theme.StudyBlue
import com.siyehua.egnlishstudy.ui.theme.StudyBlueSoft
import com.siyehua.egnlishstudy.ui.theme.StudyCoral
import com.siyehua.egnlishstudy.ui.theme.StudyCoralSoft
import com.siyehua.egnlishstudy.ui.theme.StudyGreen
import com.siyehua.egnlishstudy.ui.theme.StudyGreenDark
import com.siyehua.egnlishstudy.ui.theme.StudyInk
import com.siyehua.egnlishstudy.ui.theme.StudyMint
import com.siyehua.egnlishstudy.ui.theme.StudyOrange
import com.siyehua.egnlishstudy.ui.theme.StudyOrangeSoft
import com.siyehua.egnlishstudy.ui.theme.StudyYellow
import com.siyehua.egnlishstudy.ui.theme.StudyYellowSoft
import kotlin.math.max

@Composable
fun ContentListScreen(
    onContentClick: (Content) -> Unit,
    onOpenFavorites: () -> Unit = {},
    viewModel: ContentViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    ContentListContent(
        uiState = uiState,
        onContentClick = onContentClick,
        onOpenFavorites = onOpenFavorites,
        onTypeToggle = viewModel::toggleType,
        onLevelToggle = viewModel::toggleLevel,
        onSourceToggle = viewModel::toggleSource,
        onClearFilters = viewModel::clearFilters,
        onLoadNextPage = viewModel::loadNextPage,
        onRefreshMore = viewModel::refreshMoreContent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentListContent(
    uiState: ContentUiState,
    onContentClick: (Content) -> Unit,
    onOpenFavorites: () -> Unit,
    onTypeToggle: (ContentType) -> Unit,
    onLevelToggle: (ContentLevel) -> Unit,
    onSourceToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRefreshMore: () -> Unit
) {
    val selectedTypes = (uiState as? ContentUiState.Success)?.selectedTypes.orEmpty()
    val selectedLevels = (uiState as? ContentUiState.Success)?.selectedLevels.orEmpty()
    val selectedSources = (uiState as? ContentUiState.Success)?.selectedSources.orEmpty()
    val typeCounts = (uiState as? ContentUiState.Success)?.typeCounts.orEmpty()
    val levelCounts = (uiState as? ContentUiState.Success)?.levelCounts.orEmpty()
    val sourceCounts = (uiState as? ContentUiState.Success)?.sourceCounts.orEmpty()
    val typeOptions = (uiState as? ContentUiState.Success)?.typeOptions.orEmpty()
    val levelOptions = (uiState as? ContentUiState.Success)?.levelOptions.orEmpty()
    val sourceOptions = (uiState as? ContentUiState.Success)?.sourceOptions.orEmpty()
    val isRefreshing = (uiState as? ContentUiState.Success)?.isRefreshing == true
    val activeFilterCount = selectedTypes.size + selectedLevels.size + selectedSources.size
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val collapseRangePx = with(density) { 180.dp.toPx() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) { padding ->
        when (val state = uiState) {
            is ContentUiState.Loading -> {
                LoadingLessons(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            is ContentUiState.Error -> {
                ErrorLessons(
                    message = state.message,
                    onRetry = onRefreshMore,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            is ContentUiState.Success -> {
                val content = remember(state.content) {
                    state.content.deduplicateById()
                }
                val totalCount = state.totalCount.coerceAtLeast(content.size)
                val collapseFractionTarget by remember(listState, collapseRangePx) {
                    derivedStateOf {
                        when {
                            listState.firstVisibleItemIndex > 0 -> 1f
                            else -> (listState.firstVisibleItemScrollOffset / collapseRangePx)
                                .coerceIn(0f, 1f)
                        }
                    }
                }
                val collapseFraction by animateFloatAsState(
                    targetValue = collapseFractionTarget,
                    label = "HomeHeaderCollapse"
                )
                val shouldLoadNextPage by remember(
                    listState,
                    content.size,
                    state.hasMore,
                    state.isLoadingMore
                ) {
                    derivedStateOf {
                        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            ?: return@derivedStateOf false
                        state.hasMore &&
                            !state.isLoadingMore &&
                            lastVisibleIndex >= content.lastIndex - 4
                    }
                }

                LaunchedEffect(shouldLoadNextPage) {
                    if (shouldLoadNextPage) {
                        onLoadNextPage()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(padding)
                ) {
                    LearningHomeHeader(
                        lessonCount = totalCount,
                        activeFilterCount = activeFilterCount,
                        selectedLevels = selectedLevels,
                        selectedTypes = selectedTypes,
                        selectedSources = selectedSources,
                        levelCounts = levelCounts,
                        typeCounts = typeCounts,
                        sourceCounts = sourceCounts,
                        levelOptions = levelOptions,
                        typeOptions = typeOptions,
                        sourceOptions = sourceOptions,
                        isRefreshing = isRefreshing,
                        onRefreshMore = onRefreshMore,
                        onLevelToggle = onLevelToggle,
                        onTypeToggle = onTypeToggle,
                        onSourceToggle = onSourceToggle,
                        onClearFilters = onClearFilters,
                        collapseFraction = collapseFraction,
                        onOpenFavorites = onOpenFavorites
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            SectionHeader(
                                title = "Fresh lessons",
                                subtitle = if (totalCount > content.size) {
                                    "${content.size} shown of $totalCount"
                                } else {
                                    "${content.size} ready"
                                }
                            )
                        }

                        if (content.isEmpty()) {
                            item {
                                EmptyLessons(
                                    onClearFilters = onClearFilters,
                                    activeFilterCount = activeFilterCount
                                )
                            }
                        }

                        items(
                            items = content,
                            key = { it.id }
                        ) { item ->
                            ContentItem(content = item, onClick = { onContentClick(item) })
                        }

                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(22.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningHomeHeader(
    lessonCount: Int,
    activeFilterCount: Int,
    selectedLevels: Set<ContentLevel>,
    selectedTypes: Set<ContentType>,
    selectedSources: Set<String>,
    levelCounts: Map<ContentLevel, Int>,
    typeCounts: Map<ContentType, Int>,
    sourceCounts: Map<String, Int>,
    levelOptions: List<FilterOption>,
    typeOptions: List<FilterOption>,
    sourceOptions: List<FilterOption>,
    isRefreshing: Boolean,
    onRefreshMore: () -> Unit,
    onLevelToggle: (ContentLevel) -> Unit,
    onTypeToggle: (ContentType) -> Unit,
    onSourceToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    collapseFraction: Float,
    onOpenFavorites: () -> Unit
) {
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val topPadding = lerpDp(12.dp, 6.dp, fraction)
    val bottomPadding = lerpDp(18.dp, 12.dp, fraction)
    val contentPadding = lerpDp(20.dp, 14.dp, fraction)
    val contentSpacing = lerpDp(16.dp, 8.dp, fraction)
    val headlineSize = lerpTextUnit(34.sp, 22.sp, fraction)
    val headlineLineHeight = lerpTextUnit(38.sp, 26.sp, fraction)
    val labelAlpha = (1f - fraction * 1.45f).coerceIn(0f, 1f)
    val titleText = if (fraction < 0.72f) "Pick a lesson" else "English Study"
    var isFilterOpen by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = StudyGreen,
        tonalElevation = 0.dp,
        shadowElevation = lerpDp(4.dp, 1.dp, fraction)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = topPadding, bottom = bottomPadding)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(contentSpacing)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (labelAlpha > 0.02f) {
                        Text(
                            text = "English Study",
                            style = MaterialTheme.typography.labelLarge,
                            color = StudyMint.copy(alpha = labelAlpha),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = headlineSize,
                            lineHeight = headlineLineHeight
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LearningStatPill(
                        text = "$lessonCount lessons",
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    )
                    LearningStatPill(
                        text = if (activeFilterCount == 0) "All levels" else "$activeFilterCount filters",
                        containerColor = StudyYellow,
                        contentColor = StudyInk
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onOpenFavorites,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "收藏夹"
                    )
                }
                Box {
                    IconButton(
                        onClick = { isFilterOpen = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (activeFilterCount > 0) StudyYellow else Color.White.copy(alpha = 0.18f),
                            contentColor = if (activeFilterCount > 0) StudyInk else Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.FilterList, contentDescription = "Focus filters")
                    }

                    FocusFilterMenu(
                        expanded = isFilterOpen,
                        selectedLevels = selectedLevels,
                        selectedTypes = selectedTypes,
                        selectedSources = selectedSources,
                        levelCounts = levelCounts,
                        typeCounts = typeCounts,
                        sourceCounts = sourceCounts,
                        levelOptions = levelOptions,
                        typeOptions = typeOptions,
                        sourceOptions = sourceOptions,
                        activeFilterCount = activeFilterCount,
                        onLevelToggle = onLevelToggle,
                        onTypeToggle = onTypeToggle,
                        onSourceToggle = onSourceToggle,
                        onClearFilters = onClearFilters,
                        onDismiss = { isFilterOpen = false }
                    )
                }

                IconButton(
                    onClick = onRefreshMore,
                    enabled = !isRefreshing,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.12f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningStatPill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private data class ActiveFilterTag(
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit
)

private enum class FocusOptionStyle {
    Pills,
    Rows
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusFilterMenu(
    expanded: Boolean,
    selectedLevels: Set<ContentLevel>,
    selectedTypes: Set<ContentType>,
    selectedSources: Set<String>,
    levelCounts: Map<ContentLevel, Int>,
    typeCounts: Map<ContentType, Int>,
    sourceCounts: Map<String, Int>,
    levelOptions: List<FilterOption>,
    typeOptions: List<FilterOption>,
    sourceOptions: List<FilterOption>,
    activeFilterCount: Int,
    onLevelToggle: (ContentLevel) -> Unit,
    onTypeToggle: (ContentType) -> Unit,
    onSourceToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val activeTags = remember(selectedLevels, selectedTypes, selectedSources) {
        buildList {
            selectedLevels
                .sortedBy { it.ordinal }
                .forEach { level ->
                    add(
                        ActiveFilterTag(
                            label = level.name,
                            containerColor = levelColor(level),
                            contentColor = StudyInk,
                            onClick = { onLevelToggle(level) }
                        )
                    )
                }

            selectedTypes
                .sortedBy { it.ordinal }
                .forEach { type ->
                    val visual = type.visual()
                    add(
                        ActiveFilterTag(
                            label = type.displayName(),
                            containerColor = visual.containerColor,
                            contentColor = visual.contentColor,
                            onClick = { onTypeToggle(type) }
                        )
                    )
                }

            selectedSources
                .sorted()
                .forEach { source ->
                    add(
                        ActiveFilterTag(
                            label = source,
                            containerColor = StudyMint,
                            contentColor = StudyGreenDark,
                            onClick = { onSourceToggle(source) }
                        )
                    )
                }
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 312.dp, max = 372.dp)
            .heightIn(max = 620.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 312.dp, max = 372.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Surface(
                color = StudyGreen,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Focus",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = if (activeFilterCount == 0) {
                                "All lessons"
                            } else {
                                "$activeFilterCount filters active"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = StudyMint,
                            maxLines = 1
                        )
                    }

                    if (activeFilterCount > 0) {
                        Surface(
                            onClick = onClearFilters,
                            shape = MaterialTheme.shapes.small,
                            color = Color.White.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (activeTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        activeTags.forEach { tag ->
                            ActiveFilterTagChip(tag = tag)
                        }
                    }
                }

                FilterOptionGroup(
                    title = "Level",
                    options = levelOptions,
                    selectedIds = selectedLevels.map { it.name }.toSet(),
                    fallbackCounts = levelCounts.mapKeys { it.key.name },
                    optionStyle = FocusOptionStyle.Pills,
                    onToggle = { id ->
                        runCatching { ContentLevel.valueOf(id) }.getOrNull()?.let(onLevelToggle)
                    }
                )

                FilterOptionGroup(
                    title = "Type",
                    options = typeOptions,
                    selectedIds = selectedTypes.map { it.name }.toSet(),
                    fallbackCounts = typeCounts.mapKeys { it.key.name },
                    optionStyle = FocusOptionStyle.Pills,
                    onToggle = { id ->
                        runCatching { ContentType.valueOf(id) }.getOrNull()?.let(onTypeToggle)
                    }
                )

                FilterOptionGroup(
                    title = "Source",
                    options = sourceOptions,
                    selectedIds = selectedSources,
                    fallbackCounts = sourceCounts,
                    optionStyle = FocusOptionStyle.Rows,
                    onToggle = onSourceToggle
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterOptionGroup(
    title: String,
    options: List<FilterOption>,
    selectedIds: Set<String>,
    fallbackCounts: Map<String, Int>,
    optionStyle: FocusOptionStyle,
    onToggle: (String) -> Unit
) {
    val visibleOptions = visibleFilterOptions(options, fallbackCounts)
    if (visibleOptions.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${visibleOptions.sumOf { it.count }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        when (optionStyle) {
            FocusOptionStyle.Pills -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    visibleOptions.forEach { option ->
                        FocusFilterPill(
                            label = option.label,
                            count = option.count,
                            selected = option.id in selectedIds,
                            onClick = { onToggle(option.id) }
                        )
                    }
                }
            }

            FocusOptionStyle.Rows -> {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    visibleOptions.forEach { option ->
                        SourceFilterRow(
                            label = option.label,
                            count = option.count,
                            selected = option.id in selectedIds,
                            onClick = { onToggle(option.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusFilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) StudyGreen else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) StudyGreen else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                modifier = Modifier.widthIn(max = 160.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = if (selected) {
                    Color.White.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SourceFilterRow(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) StudyMint else MaterialTheme.colorScheme.background,
        border = BorderStroke(
            1.dp,
            if (selected) StudyGreen.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) StudyGreenDark else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count lessons",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (selected) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = StudyGreen
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterTagChip(tag: ActiveFilterTag) {
    Surface(
        onClick = tag.onClick,
        shape = MaterialTheme.shapes.small,
        color = tag.containerColor,
        border = BorderStroke(1.dp, tag.contentColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tag.label,
                modifier = Modifier.widthIn(max = 220.dp),
                style = MaterialTheme.typography.labelMedium,
                color = tag.contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = tag.contentColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentItem(content: Content, onClick: () -> Unit) {
    val visual = content.type.visual()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = visual.containerColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = visual.label,
                            tint = visual.contentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ContentBadge(
                            text = content.level.name,
                            containerColor = levelColor(content.level),
                            contentColor = StudyInk
                        )
                        ContentBadge(
                            text = content.type.displayName(),
                            containerColor = visual.containerColor,
                            contentColor = visual.contentColor
                        )
                        if (content.sourceName.isNotBlank()) {
                            ContentBadge(
                                text = content.sourceName,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = content.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = content.previewText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.56f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = StudyGreenDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${content.estimatedMinutes()} min lesson",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start",
                        style = MaterialTheme.typography.labelLarge,
                        color = StudyGreenDark
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = StudyGreenDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier
                .widthIn(max = 190.dp)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun visibleFilterOptions(
    options: List<FilterOption>,
    fallbackCounts: Map<String, Int>
): List<FilterOption> =
    options.ifEmpty {
        fallbackCounts
            .toList()
            .sortedByDescending { it.second }
            .map { (id, count) -> FilterOption(id = id, label = id, count = count) }
    }

@Composable
private fun LoadingLessons(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = StudyGreen)
            Text(
                text = "Preparing lessons",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun ErrorLessons(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Lessons did not load",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = StudyGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun EmptyLessons(
    onClearFilters: () -> Unit,
    activeFilterCount: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "No matching lessons",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Try another level or category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (activeFilterCount > 0) {
                TextButton(onClick = onClearFilters, modifier = Modifier.widthIn(min = 0.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear filters")
                }
            }
        }
    }
}

private data class ContentVisual(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val label: String
)

private fun ContentType.visual(): ContentVisual =
    when (this) {
        ContentType.ARTICLE -> ContentVisual(
            icon = Icons.AutoMirrored.Filled.Article,
            containerColor = StudyBlueSoft,
            contentColor = StudyBlue,
            label = "Article"
        )

        ContentType.BLOG -> ContentVisual(
            icon = Icons.Default.EditNote,
            containerColor = StudyOrangeSoft,
            contentColor = StudyOrange,
            label = "Blog"
        )

        ContentType.NEWS -> ContentVisual(
            icon = Icons.Default.Newspaper,
            containerColor = StudyYellowSoft,
            contentColor = StudyInk,
            label = "News"
        )

        ContentType.DIALOGUE -> ContentVisual(
            icon = Icons.Default.ChatBubble,
            containerColor = StudyCoralSoft,
            contentColor = StudyCoral,
            label = "Dialogue"
        )
    }

private fun levelColor(level: ContentLevel): Color =
    when (level) {
        ContentLevel.A1, ContentLevel.A2 -> StudyMint
        ContentLevel.B1, ContentLevel.B2 -> StudyYellowSoft
        ContentLevel.C1, ContentLevel.C2 -> StudyCoralSoft
    }

private fun Content.previewText(): String {
    val text = when (this) {
        is Article -> content
        is Blog -> content
        is News -> content
        is Dialogue -> lines.joinToString(" ") { "${it.speaker}: ${it.text}" }
    }.replace(Regex("\\s+"), " ").trim()

    return if (text.length > 138) {
        text.take(135) + "..."
    } else {
        text.ifBlank { "Tap to start this lesson." }
    }
}

private fun Content.estimatedMinutes(): Int {
    val text = when (this) {
        is Article -> content
        is Blog -> content
        is News -> content
        is Dialogue -> lines.joinToString(" ") { it.text }
    }
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    return max(1, (words + 159) / 160)
}

private fun ContentType.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    (start.value + (stop.value - start.value) * fraction.coerceIn(0f, 1f)).dp

private fun lerpTextUnit(start: TextUnit, stop: TextUnit, fraction: Float): TextUnit =
    (start.value + (stop.value - start.value) * fraction.coerceIn(0f, 1f)).sp

private fun List<Content>.deduplicateById(): List<Content> {
    val byId = LinkedHashMap<String, Content>()
    forEach { item ->
        byId[item.id] = item
    }
    return byId.values.toList()
}

@Preview(showBackground = true)
@Composable
fun ContentListScreenPreview() {
    EgnlishStudyTheme {
        var selectedLevel by remember { mutableStateOf(setOf(ContentLevel.B1)) }

        ContentListContent(
            uiState = ContentUiState.Success(
                content = listOf(
                    Article(
                        articleTitle = "Small habits for speaking better English",
                        content = "Practice every day with short sentences, repeat useful phrases, and listen to natural conversations before you speak.",
                        contentLevel = ContentLevel.B1
                    ),
                    News(
                        newsTitle = "Scientists test new clean energy storage",
                        content = "Researchers announced a new storage system that could help cities use more clean energy during peak hours.",
                        source = "Preview",
                        date = "",
                        contentLevel = ContentLevel.B2
                    ),
                    Dialogue(
                        dialogueTitle = "Ordering coffee before class",
                        lines = listOf(
                            com.siyehua.egnlishstudy.model.DialogueLine("Alice", "Could I get a latte, please?"),
                            com.siyehua.egnlishstudy.model.DialogueLine("Barista", "Sure. Would you like it hot or iced?")
                        )
                    )
                ),
                selectedLevels = selectedLevel,
                levelCounts = ContentLevel.values().associateWith { 8 },
                typeCounts = ContentType.values().associateWith { 5 },
                sourceCounts = mapOf("EnglishPod" to 20)
            ),
            onContentClick = {},
            onOpenFavorites = {},
            onTypeToggle = {},
            onLevelToggle = { selectedLevel = selectedLevel.toggle(it) },
            onSourceToggle = {},
            onClearFilters = { selectedLevel = emptySet() },
            onLoadNextPage = {},
            onRefreshMore = {}
        )
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item
