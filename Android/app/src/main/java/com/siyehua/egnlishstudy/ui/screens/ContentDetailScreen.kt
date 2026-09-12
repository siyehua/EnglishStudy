package com.siyehua.egnlishstudy.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siyehua.egnlishstudy.data.SentenceSplitter
import com.siyehua.egnlishstudy.model.Article
import com.siyehua.egnlishstudy.model.Blog
import com.siyehua.egnlishstudy.model.Content
import com.siyehua.egnlishstudy.model.ContentLevel
import com.siyehua.egnlishstudy.model.ContentType
import com.siyehua.egnlishstudy.model.Dialogue
import com.siyehua.egnlishstudy.model.DialogueLine
import com.siyehua.egnlishstudy.model.News
import com.siyehua.egnlishstudy.ui.AudioPlayMode
import com.siyehua.egnlishstudy.ui.AudioUiState
import com.siyehua.egnlishstudy.ui.ContentAudioViewModel
import com.siyehua.egnlishstudy.ui.theme.EgnlishStudyTheme
import com.siyehua.egnlishstudy.ui.theme.StudyBlue
import com.siyehua.egnlishstudy.ui.theme.StudyBlueSoft
import com.siyehua.egnlishstudy.ui.theme.StudyCoral
import com.siyehua.egnlishstudy.ui.theme.StudyGreen
import com.siyehua.egnlishstudy.ui.theme.StudyGreenDark
import com.siyehua.egnlishstudy.ui.theme.StudyInk
import com.siyehua.egnlishstudy.ui.theme.StudyMint
import com.siyehua.egnlishstudy.ui.theme.StudyOrange
import com.siyehua.egnlishstudy.ui.theme.StudyOrangeSoft
import com.siyehua.egnlishstudy.ui.theme.StudyYellow
import com.siyehua.egnlishstudy.ui.theme.StudyYellowSoft
import com.siyehua.egnlishstudy.ui.wordinsight.ClickableReadingText
import com.siyehua.egnlishstudy.ui.wordinsight.ClickedWord
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightSheet
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightViewModel
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentDetailScreen(
    content: Content,
    onBack: () -> Unit,
    audioViewModel: ContentAudioViewModel = viewModel(),
    wordInsightViewModel: WordInsightViewModel = viewModel()
) {
    val audioState by audioViewModel.uiState.collectAsState()
    val wordInsightState by wordInsightViewModel.uiState.collectAsState()
    val wordPronunciationState by wordInsightViewModel.audioState.collectAsState()
    val currentSentenceIndex = audioState.currentSentenceIndexOrNull()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val collapseRangePx = with(density) { 160.dp.toPx() }
    var selectedWord by remember { mutableStateOf<ClickedWord?>(null) }

    DisposableEffect(content.id) {
        onDispose {
            audioViewModel.stop()
        }
    }

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
        label = "LessonHeaderCollapse"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            LessonDetailHeader(
                content = content,
                onBack = onBack,
                collapseFraction = collapseFraction
            )

            AudioPracticeBar(
                audioState = audioState,
                playMode = audioViewModel.playMode,
                onPlayModeChange = audioViewModel::changePlayMode,
                collapseFraction = collapseFraction,
                onTogglePlayback = { audioViewModel.togglePlayback(content) },
                onStop = { audioViewModel.stop() }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    val onWordClick: (ClickedWord) -> Unit = { clickedWord ->
                        selectedWord = clickedWord
                        wordInsightViewModel.load(clickedWord)
                    }
                    val onSentenceDoubleClick: (SentencePlaybackRequest) -> Unit = { request ->
                        audioViewModel.playSentence(request.text, request.index)
                    }
                    when (content) {
                        is Article -> ArticleDetail(
                            article = content,
                            currentSentenceIndex = currentSentenceIndex,
                            onWordClick = onWordClick,
                            onSentenceDoubleClick = onSentenceDoubleClick
                        )
                        is Blog -> BlogDetail(
                            blog = content,
                            currentSentenceIndex = currentSentenceIndex,
                            onWordClick = onWordClick,
                            onSentenceDoubleClick = onSentenceDoubleClick
                        )
                        is News -> NewsDetail(
                            news = content,
                            currentSentenceIndex = currentSentenceIndex,
                            onWordClick = onWordClick,
                            onSentenceDoubleClick = onSentenceDoubleClick
                        )
                        is Dialogue -> DialogueDetail(
                            dialogue = content,
                            currentSentenceIndex = currentSentenceIndex,
                            onWordClick = onWordClick,
                            onSentenceDoubleClick = onSentenceDoubleClick
                        )
                    }
                }
            }
        }
    }

    selectedWord?.let { clickedWord ->
        WordInsightSheet(
            clickedWord = clickedWord,
            uiState = wordInsightState,
            audioState = wordPronunciationState,
            onDismiss = { selectedWord = null },
            onRetry = { wordInsightViewModel.load(clickedWord) },
            onSpeakWord = { word, audioUrl -> wordInsightViewModel.speak(word, audioUrl) },
            onSpeakText = { text -> wordInsightViewModel.speakText(text) }
        )
    }
}

@Composable
private fun LessonDetailHeader(
    content: Content,
    onBack: () -> Unit,
    collapseFraction: Float
) {
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val topPadding = lerpDp(12.dp, 6.dp, fraction)
    val bottomPadding = lerpDp(18.dp, 12.dp, fraction)
    val contentPadding = lerpDp(20.dp, 14.dp, fraction)
    val contentSpacing = lerpDp(14.dp, 8.dp, fraction)
    val headlineSize = lerpTextUnit(30.sp, 20.sp, fraction)
    val headlineLineHeight = lerpTextUnit(34.sp, 24.sp, fraction)
    val labelAlpha = (1f - fraction * 1.45f).coerceIn(0f, 1f)
    val metaAlpha = (1f - fraction * 1.2f).coerceIn(0f, 1f)
    val metadata = content.metadataLine()

    Surface(
        modifier = Modifier.fillMaxWidth(),
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
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(contentSpacing)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (labelAlpha > 0.02f) {
                        Text(
                            text = "Lesson",
                            style = MaterialTheme.typography.labelLarge,
                            color = StudyMint.copy(alpha = labelAlpha),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = content.title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = headlineSize,
                            lineHeight = headlineLineHeight
                        ),
                        color = Color.White,
                        maxLines = if (fraction < 0.55f) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (metaAlpha > 0.02f) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroBadge(
                                text = content.level.name,
                                containerColor = StudyYellow,
                                contentColor = StudyInk
                            )
                            HeroBadge(
                                text = content.type.displayName(),
                                containerColor = Color.White.copy(alpha = 0.18f * metaAlpha),
                                contentColor = Color.White.copy(alpha = metaAlpha)
                            )
                        }
                        if (metadata.isNotBlank()) {
                            Text(
                                text = metadata,
                                style = MaterialTheme.typography.labelLarge,
                                color = StudyMint.copy(alpha = metaAlpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioPracticeBar(
    audioState: AudioUiState,
    playMode: AudioPlayMode,
    onPlayModeChange: (AudioPlayMode) -> Unit,
    collapseFraction: Float,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit
) {
    val isAutoCollapsed = collapseFraction > 0.16f
    var isManuallyExpanded by rememberSaveable { mutableStateOf(false) }
    val isExpanded = !isAutoCollapsed || isManuallyExpanded
    val isPlaying = audioState is AudioUiState.Playing
    val isPaused = audioState is AudioUiState.Paused
    val isPreparing = audioState is AudioUiState.Preparing
    val canStop = isPlaying || isPaused || isPreparing
    val progressText = audioState.audioCompactStatusText()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = if (isExpanded) 1.dp else 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (isExpanded) 14.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (isExpanded) 12.dp else 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (isExpanded) 4.dp else 1.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Listening practice" else "Listening",
                        style = if (isExpanded) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isExpanded) audioState.audioStatusText() else progressText,
                        style = if (isExpanded) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isExpanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AudioModeChip(
                                label = "完整",
                                selected = playMode == AudioPlayMode.FULL,
                                onClick = { onPlayModeChange(AudioPlayMode.FULL) }
                            )
                            AudioModeChip(
                                label = "对话",
                                selected = playMode == AudioPlayMode.DIALOGUE,
                                onClick = { onPlayModeChange(AudioPlayMode.DIALOGUE) }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onTogglePlayback,
                    enabled = !isPreparing,
                    modifier = Modifier.size(38.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isPlaying) StudyCoral else StudyGreen,
                        contentColor = Color.White,
                        disabledContainerColor = StudyMint,
                        disabledContentColor = StudyGreenDark
                    )
                ) {
                    when {
                        isPreparing -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = StudyGreenDark
                        )

                        isPlaying -> Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause audio",
                            modifier = Modifier.size(18.dp)
                        )

                        else -> Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isPaused) "Resume audio" else "Play audio",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (canStop) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = StudyCoral
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop audio",
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                if (isAutoCollapsed) {
                    IconButton(
                        onClick = { isManuallyExpanded = !isExpanded },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse audio controls" else "Expand audio controls",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            val progress = audioState.progressOrNull()
            if (isExpanded && progress != null) {
                AudioProgress(currentMillis = progress.first, totalMillis = progress.second)
            }
        }
    }
}

@Composable
private fun AudioModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) StudyGreen else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun AudioProgress(currentMillis: Long, totalMillis: Long) {
    val progress = if (totalMillis <= 0L) {
        0f
    } else {
        currentMillis.toFloat() / totalMillis.toFloat()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small)
                .background(StudyMint)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(min(1f, progress))
                    .height(8.dp)
                    .background(StudyGreen)
            )
        }
        Text(
            text = "${currentMillis.formatAudioTime()} / ${totalMillis.formatAudioTime()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArticleDetail(
    article: Article,
    currentSentenceIndex: Int?,
    onWordClick: (ClickedWord) -> Unit = {},
    onSentenceDoubleClick: (SentencePlaybackRequest) -> Unit = {}
) {
    ReadingPanel(
        title = "Reading",
        meta = article.detailMeta(),
        body = article.content,
        currentSentenceIndex = currentSentenceIndex,
        onWordClick = onWordClick,
        onSentenceDoubleClick = onSentenceDoubleClick
    )
}

@Composable
private fun BlogDetail(
    blog: Blog,
    currentSentenceIndex: Int?,
    onWordClick: (ClickedWord) -> Unit = {},
    onSentenceDoubleClick: (SentencePlaybackRequest) -> Unit = {}
) {
    ReadingPanel(
        title = "Blog reading",
        meta = "By ${blog.author}".withDate(blog.date),
        body = blog.content,
        currentSentenceIndex = currentSentenceIndex,
        onWordClick = onWordClick,
        onSentenceDoubleClick = onSentenceDoubleClick
    )
}

@Composable
private fun NewsDetail(
    news: News,
    currentSentenceIndex: Int?,
    onWordClick: (ClickedWord) -> Unit = {},
    onSentenceDoubleClick: (SentencePlaybackRequest) -> Unit = {}
) {
    ReadingPanel(
        title = "News reading",
        meta = "Source: ${news.source}".withDate(news.date),
        body = news.content,
        currentSentenceIndex = currentSentenceIndex,
        onWordClick = onWordClick,
        onSentenceDoubleClick = onSentenceDoubleClick
    )
}

@Composable
private fun DialogueDetail(
    dialogue: Dialogue,
    currentSentenceIndex: Int?,
    onWordClick: (ClickedWord) -> Unit = {},
    onSentenceDoubleClick: (SentencePlaybackRequest) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Dialogue practice",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${dialogue.lines.size} lines",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            var previousSection: String? = null
            dialogue.lines.forEachIndexed { index, line ->
                val section = line.section.ifBlank { "explanation" }
                if (section != previousSection) {
                    Text(
                        text = sectionTitle(section),
                        style = MaterialTheme.typography.titleMedium,
                        color = StudyGreen,
                        modifier = Modifier.padding(top = if (previousSection == null) 0.dp else 8.dp)
                    )
                    previousSection = section
                }
                DialogueLineCard(
                    line = line,
                    sentenceIndex = index,
                    isPrimary = index % 2 == 0,
                    isPlaying = currentSentenceIndex == index,
                    onWordClick = onWordClick,
                    onSentenceDoubleClick = onSentenceDoubleClick
                )
            }
        }
    }
}

@Composable
private fun ReadingPanel(
    title: String,
    meta: String,
    body: String,
    currentSentenceIndex: Int?,
    onWordClick: (ClickedWord) -> Unit,
    onSentenceDoubleClick: (SentencePlaybackRequest) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${body.estimatedMinutes()} min",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            HighlightedReadingText(
                body = body.ifBlank { "No text is available for this lesson." },
                currentSentenceIndex = currentSentenceIndex,
                onWordClick = onWordClick,
                onSentenceDoubleClick = onSentenceDoubleClick
            )
        }
    }
}

@Composable
private fun HighlightedReadingText(
    body: String,
    currentSentenceIndex: Int?,
    onWordClick: (ClickedWord) -> Unit,
    onSentenceDoubleClick: (SentencePlaybackRequest) -> Unit
) {
    val sentences = remember(body) { body.splitIntoDisplaySentences() }
    if (sentences.isEmpty()) {
        ClickableReadingText(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            onWordClick = onWordClick,
            onSentenceDoubleClick = { sentence ->
                onSentenceDoubleClick(SentencePlaybackRequest(sentence, 0))
            }
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sentences.forEachIndexed { index, sentence ->
            HighlightableSentenceRow(
                text = sentence,
                sentenceIndex = index,
                isPlaying = currentSentenceIndex == index,
                onWordClick = onWordClick,
                onSentenceDoubleClick = onSentenceDoubleClick
            )
        }
    }
}

@Composable
private fun HighlightableSentenceRow(
    text: String,
    sentenceIndex: Int,
    isPlaying: Boolean,
    onWordClick: (ClickedWord) -> Unit,
    onSentenceDoubleClick: (SentencePlaybackRequest) -> Unit
) {
    val background = if (isPlaying) StudyMint.copy(alpha = 0.72f) else Color.Transparent
    val leftColor = if (isPlaying) StudyGreen else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .padding(horizontal = if (isPlaying) 8.dp else 0.dp, vertical = if (isPlaying) 5.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(leftColor)
        )
        ClickableReadingText(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isPlaying) StudyInk else MaterialTheme.colorScheme.onSurface,
            onWordClick = onWordClick,
            onSentenceDoubleClick = { sentence ->
                onSentenceDoubleClick(SentencePlaybackRequest(sentence, sentenceIndex))
            }
        )
    }
}

private fun sectionTitle(section: String): String =
    when (section) {
        "dialogue" -> "对话 Dialogue"
        "explanation" -> "讲解 Explanation"
        "review" -> "回顾 Review"
        else -> section
    }

@Composable
private fun DialogueLineCard(
    line: DialogueLine,
    sentenceIndex: Int,
    isPrimary: Boolean,
    isPlaying: Boolean,
    onWordClick: (ClickedWord) -> Unit,
    onSentenceDoubleClick: (SentencePlaybackRequest) -> Unit
) {
    val containerColor = when {
        isPlaying -> StudyMint
        isPrimary -> StudyBlueSoft
        else -> StudyYellowSoft
    }
    val speakerColor = if (isPrimary) StudyBlue else StudyOrange

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = if (isPlaying) BorderStroke(1.dp, StudyGreen.copy(alpha = 0.45f)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                color = Color.White.copy(alpha = 0.82f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = line.speaker.take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = speakerColor
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = line.speaker,
                    style = MaterialTheme.typography.labelLarge,
                    color = speakerColor
                )
                ClickableReadingText(
                    text = line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudyInk,
                    onWordClick = onWordClick,
                    onSentenceDoubleClick = { sentence ->
                        onSentenceDoubleClick(SentencePlaybackRequest(sentence, sentenceIndex))
                    }
                )
                if (line.trans.isNotBlank()) {
                    Text(
                        text = line.trans,
                        style = MaterialTheme.typography.bodySmall,
                        color = StudyInk.copy(alpha = 0.62f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBadge(
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
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            maxLines = 1
        )
    }
}

private fun AudioUiState.audioStatusText(): String =
    when (this) {
        is AudioUiState.Idle -> "Listen sentence by sentence, then read aloud."
        is AudioUiState.Preparing -> "Preparing audio for this lesson."
        is AudioUiState.Playing -> "Playing ${currentMillis.formatAudioTime()} / ${totalMillis.formatAudioTime()}."
        is AudioUiState.Paused -> "Paused at ${currentMillis.formatAudioTime()} / ${totalMillis.formatAudioTime()}."
        is AudioUiState.Error -> message
    }

private fun AudioUiState.audioCompactStatusText(): String =
    when (this) {
        is AudioUiState.Idle -> "Ready to play"
        is AudioUiState.Preparing -> "Preparing"
        is AudioUiState.Playing -> "${currentMillis.formatAudioTime()} / ${totalMillis.formatAudioTime()}"
        is AudioUiState.Paused -> "Paused ${currentMillis.formatAudioTime()}"
        is AudioUiState.Error -> "Audio issue"
    }

private fun AudioUiState.progressOrNull(): Pair<Long, Long>? =
    when (this) {
        is AudioUiState.Playing -> currentMillis to totalMillis
        is AudioUiState.Paused -> currentMillis to totalMillis
        else -> null
    }

private fun AudioUiState.currentSentenceIndexOrNull(): Int? =
    when (this) {
        is AudioUiState.Playing -> currentSentenceIndex
        is AudioUiState.Paused -> currentSentenceIndex
        else -> null
    }

private fun Long.formatAudioTime(): String {
    val safeMillis = coerceAtLeast(0L)
    val totalSeconds = safeMillis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun Content.metadataLine(): String =
    when (this) {
        is Article -> detailMeta().ifBlank { "${level.name} article" }
        is Blog -> "By ${author}".withDate(date)
        is News -> source.ifBlank { "News" }.withDate(date)
        is Dialogue -> "${lines.size} dialogue lines"
    }

private fun Article.detailMeta(): String {
    val authorText = author.takeIf { it.isNotBlank() && it != "Unknown" }
    return when {
        authorText != null && date.isNotBlank() -> "By $authorText - $date"
        authorText != null -> "By $authorText"
        date.isNotBlank() -> date
        else -> ""
    }
}

private fun String.withDate(date: String): String =
    if (date.isBlank()) this else "$this - $date"

private fun String.estimatedMinutes(): Int {
    val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    return max(1, (words + 159) / 160)
}

private fun String.splitIntoDisplaySentences(): List<String> =
    SentenceSplitter.split(this, minLength = MIN_DISPLAY_SENTENCE_LENGTH)

private fun ContentType.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }

private data class SentencePlaybackRequest(
    val text: String,
    val index: Int
)

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    (start.value + (stop.value - start.value) * fraction.coerceIn(0f, 1f)).dp

private fun lerpTextUnit(start: TextUnit, stop: TextUnit, fraction: Float): TextUnit =
    (start.value + (stop.value - start.value) * fraction.coerceIn(0f, 1f)).sp

private const val MIN_DISPLAY_SENTENCE_LENGTH = 2

@Preview(showBackground = true)
@Composable
fun ArticleDetailPreview() {
    EgnlishStudyTheme {
        ContentDetailScreen(
            content = Article(
                articleTitle = "Learning English Tips",
                content = "Practice every day to improve your skills. Choose a short paragraph, listen to it twice, and repeat it slowly. Then write one new sentence using the same pattern.",
                author = "English Desk",
                date = "Today",
                contentLevel = ContentLevel.B1
            ),
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DialogueDetailPreview() {
    EgnlishStudyTheme {
        ContentDetailScreen(
            content = Dialogue(
                dialogueTitle = "Greeting Friends",
                lines = listOf(
                    DialogueLine("Alice", "Hi Bob! How are you?"),
                    DialogueLine("Bob", "I'm great, Alice. How about you?")
                )
            ),
            onBack = {}
        )
    }
}
