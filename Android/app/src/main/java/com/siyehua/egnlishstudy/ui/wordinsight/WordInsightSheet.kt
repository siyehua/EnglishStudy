package com.siyehua.egnlishstudy.ui.wordinsight

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siyehua.egnlishstudy.data.ContentCacheDatabase
import com.siyehua.egnlishstudy.data.FavoriteRecord
import com.siyehua.egnlishstudy.data.wordform.WordFormRelation
import com.siyehua.egnlishstudy.data.wordform.WordFormResponse
import com.siyehua.egnlishstudy.data.wordform.WordFormVariant
import com.siyehua.egnlishstudy.data.wordform.WordFormVariantGroup
import com.siyehua.egnlishstudy.data.wordform.WordMeaningEntry
import com.siyehua.egnlishstudy.data.wordform.WordPhonicsChunk
import com.siyehua.egnlishstudy.data.wordform.WordPhonicsResponse
import com.siyehua.egnlishstudy.data.wordform.WordPhonicsSpan

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordInsightSheet(
    clickedWord: ClickedWord,
    uiState: WordInsightUiState,
    audioState: WordPronunciationUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
    onSpeakWord: (String, String?) -> Unit = { _, _ -> },
    onSpeakText: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { CompactDragHandle() }
    ) {
        WordInsightBody(
            clickedWord = clickedWord,
            uiState = uiState,
            audioState = audioState,
            onRetry = onRetry,
            onSpeakWord = onSpeakWord,
            onSpeakText = onSpeakText
        )
    }
}

@Composable
fun WordInsightBody(
    clickedWord: ClickedWord,
    uiState: WordInsightUiState,
    audioState: WordPronunciationUiState,
    onRetry: () -> Unit = {},
    onSpeakWord: (String, String?) -> Unit = { _, _ -> },
    onSpeakText: (String) -> Unit = {},
    bottomPadding: Dp = 28.dp
) {
    val successState = uiState as? WordInsightUiState.Success
    val wordForm = successState?.wordForm
    val meaningState = successState?.meaningState ?: WordMeaningUiState.Idle
    val phonicsState = successState?.phonicsState ?: WordPhonicsUiState.Idle
    val dictionaryAudioUrl = successState?.pronunciation?.audioUrl?.takeIf { it.isNotBlank() }
    val pronunciationTarget = wordForm?.pronunciationTarget?.takeIf { it.isNotBlank() }
        ?: clickedWord.normalized
    val activeWord = wordForm?.surface ?: clickedWord.word
    val headerPhonics = (phonicsState as? WordPhonicsUiState.Success)
        ?.phonics
        ?.takeIf { it.found }
    val headerChunks = headerPhonics?.displayChunks().orEmpty()
    val headerIpa = headerPhonics
        ?.ipa
        ?.takeIf { it.isNotBlank() }
        ?: successState
            ?.pronunciation
            ?.phonetic
            ?.takeIf { it.isNotBlank() }
    val headerMeaning = currentMeaningSummary(
        wordForm = wordForm,
        meaningState = meaningState
    )

    val context = LocalContext.current
    val favoriteDatabase = remember { ContentCacheDatabase(context) }
    var isWordFavorited by remember(activeWord) {
        mutableStateOf(
            runCatching { favoriteDatabase.isFavorited("word", activeWord, clickedWord.sentence, 0.0) }
                .getOrDefault(false)
        )
    }
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HeaderWordTitle(
                        word = activeWord,
                        chunks = headerChunks
                    )
                    HeaderPhonicsLine(
                        ipa = headerIpa,
                        chunks = headerChunks,
                        meaning = headerMeaning,
                        word = pronunciationTarget,
                        audioUrl = dictionaryAudioUrl,
                        isBusy = audioState.isBusyFor(pronunciationTarget),
                        onSpeakWord = onSpeakWord
                    )
                }

                IconButton(
                    onClick = {
                        if (isWordFavorited) {
                            favoriteDatabase.deleteFavoriteByKey(
                                "word",
                                activeWord,
                                clickedWord.sentence,
                                0.0
                            )
                            isWordFavorited = false
                        } else {
                            favoriteDatabase.addFavorite(
                                FavoriteRecord(
                                    kind = "word",
                                    text = activeWord,
                                    audioUrl = dictionaryAudioUrl,
                                    lessonTitle = clickedWord.sentence,
                                    startTime = 0.0,
                                    endTime = 0.0
                                )
                            )
                            isWordFavorited = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isWordFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isWordFavorited) "取消收藏" else "收藏单词",
                        tint = if (isWordFavorited) Color(red = 0.9f, green = 0.32f, blue = 0.32f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            }

            when (uiState) {
                is WordInsightUiState.Loading -> LoadingSection()
                is WordInsightUiState.Error -> ErrorSection(
                    message = uiState.message,
                    onRetry = onRetry
                )
                is WordInsightUiState.Success,
                WordInsightUiState.Idle -> Unit
            }

            WordPhonicsSection(
                phonicsState = phonicsState
            )

            ChineseMeaningSection(
                meaningState = meaningState,
                audioState = audioState,
                onSpeakText = onSpeakText
            )

            if (uiState is WordInsightUiState.Success) {
                WordFormSection(uiState.wordForm)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            SourceSentenceSection(
                sentence = clickedWord.sentence,
                sentenceChinese = (meaningState as? WordMeaningUiState.Success)
                    ?.meaning
                    ?.sentenceChinese
                    .orEmpty(),
                audioState = audioState,
                onSpeakText = onSpeakText
            )
        }
}

@Composable
private fun CompactDragHandle() {
    Surface(
        modifier = Modifier
            .padding(top = 6.dp, bottom = 2.dp)
            .size(height = 4.dp, width = 34.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    ) {}
}

@Composable
private fun ChineseMeaningSection(
    meaningState: WordMeaningUiState,
    audioState: WordPronunciationUiState,
    onSpeakText: (String) -> Unit
) {
    InsightSection(title = "中文释义") {
        when (meaningState) {
            WordMeaningUiState.Idle -> Text(
                text = "识别单词后显示中文释义。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WordMeaningUiState.Loading -> MeaningLoadingRow()

            is WordMeaningUiState.Success -> {
                val entries = meaningState.meaning.meanings.filter { it.meaning.isNotBlank() }
                if (entries.isEmpty()) {
                    Text(
                        text = "暂时没有找到可靠的中文释义。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.forEach { entry ->
                            MeaningRow(
                                entry = entry,
                                audioState = audioState,
                                onSpeakText = onSpeakText
                            )
                        }
                    }
                }
            }

            is WordMeaningUiState.Error -> Text(
                text = "中文释义暂时不可用：${meaningState.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WordPhonicsSection(phonicsState: WordPhonicsUiState) {
    var showColorGuide by remember { mutableStateOf(false) }
    InsightSection(
        title = "读音拆分",
        titleAction = {
            IconButton(
                onClick = { showColorGuide = !showColorGuide },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "颜色规则",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    ) {
        if (showColorGuide) {
            PhonicsColorGuide()
        }
        when (phonicsState) {
            WordPhonicsUiState.Idle -> Text(
                text = "识别单词后显示读音拆分。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WordPhonicsUiState.Loading -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "正在拆分读音...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            is WordPhonicsUiState.Success -> {
                val phonics = phonicsState.phonics
                val chunks = phonics.displayChunks()
                if (!phonics.found || chunks.isEmpty()) {
                    Text(
                        text = phonics.message.ifBlank { "暂时没有找到可靠的读音拆分。" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    PhonicsStressNotes(chunks = chunks)
                }
            }

            is WordPhonicsUiState.Error -> Text(
                text = "读音拆分暂时不可用：${phonicsState.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PhonicsColorGuide() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "颜色用于把上面的拼写和下面的音标一一对应。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorGuideRow(color = chunkColor(0, false).content, text = "绿色：第 1 个读音块")
            ColorGuideRow(color = chunkColor(1, false).content, text = "橙色：第 2 个读音块")
            ColorGuideRow(color = chunkColor(2, false).content, text = "蓝色：第 3 个读音块")
            ColorGuideRow(color = chunkColor(3, false).content, text = "红色：后续读音块循环使用")
            ColorGuideRow(color = chunkColor(0, true).content, text = "灰色：不发音或几乎听不出的字母")
        }
    }
}

@Composable
private fun ColorGuideRow(
    color: Color,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(9.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = color
        ) {}
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhonicsStressNotes(chunks: List<WordPhonicsChunk>) {
    val noteChunks = chunks.filter { it.hasStressNote() }
    if (noteChunks.isEmpty()) {
        Text(
            text = "暂无明显重音变化。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        chunks.forEachIndexed { index, chunk ->
            if (chunk.hasStressNote()) {
                PhonicsRuleRow(
                    chunk = chunk,
                    color = chunkColor(index, chunk.silent)
                )
            }
        }
    }
}

@Composable
private fun PhonicsRuleRow(
    chunk: WordPhonicsChunk,
    color: ChunkColor
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = chunk.toColoredChunkText(color),
            modifier = Modifier.padding(top = 1.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = buildRuleText(chunk),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MeaningLoadingRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "正在整理中文释义...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MeaningRow(
    entry: WordMeaningEntry,
    audioState: WordPronunciationUiState,
    onSpeakText: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            ) {
                Text(
                    text = entry.label,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            Text(
                text = entry.meaning,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ExampleBlock(
            english = entry.example,
            chinese = entry.exampleChinese,
            audioState = audioState,
            onSpeakText = onSpeakText
        )
    }
}

@Composable
private fun ExampleBlock(
    english: String,
    chinese: String,
    audioState: WordPronunciationUiState,
    onSpeakText: (String) -> Unit
) {
    if (english.isBlank() && chinese.isBlank()) return

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (english.isNotBlank()) {
            SpeakableTextRow(
                text = english,
                audioState = audioState,
                onSpeakText = onSpeakText
            )
        }
        if (chinese.isNotBlank()) {
            Text(
                text = chinese,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceSentenceSection(
    sentence: String,
    sentenceChinese: String,
    audioState: WordPronunciationUiState,
    onSpeakText: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "原文句子",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        SpeakableTextRow(
            text = sentence,
            audioState = audioState,
            onSpeakText = onSpeakText
        )
        if (sentenceChinese.isNotBlank()) {
            Text(
                text = sentenceChinese,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpeakableTextRow(
    text: String,
    audioState: WordPronunciationUiState,
    onSpeakText: (String) -> Unit
) {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return

    Row(
        modifier = Modifier.clickable { onSpeakText(trimmed) },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (audioState.isBusyFor(trimmed)) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(13.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(13.dp)
            )
        }
        Text(
            text = trimmed,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 14.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HeaderWordTitle(
    word: String,
    chunks: List<WordPhonicsChunk>
) {
    val chunkColors = chunks.chunkColors()
    Text(
        text = if (chunks.isNotEmpty()) {
            chunks.toColoredSpellingText(chunkColors, word)
        } else {
            buildAnnotatedString { append(word) }
        },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HeaderPhonicsLine(
    ipa: String?,
    chunks: List<WordPhonicsChunk>,
    meaning: String?,
    word: String,
    audioUrl: String?,
    isBusy: Boolean,
    onSpeakWord: (String, String?) -> Unit
) {
    val canSpeak = ipa?.isNotBlank() == true || chunks.hasChunkIpa()
    val chunkColors = chunks.chunkColors()
    val label = when {
        chunks.hasChunkIpa() -> chunks.toColoredIpaText(chunkColors)
        ipa?.isNotBlank() == true -> buildAnnotatedString { append(ipa) }
        else -> buildAnnotatedString { append("正在识别读音...") }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (canSpeak) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            modifier = if (canSpeak) {
                Modifier.clickable { onSpeakWord(word, audioUrl) }
            } else {
                Modifier
            },
            style = MaterialTheme.typography.labelLarge.copy(lineHeight = 18.sp),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        meaning?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = "·",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LoadingSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "正在识别单词形态...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordFormSection(wordForm: WordFormResponse) {
    InsightSection(title = "单词形态") {
        val groups = wordForm.displayVariantGroups()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            wordForm.relation?.let { relation ->
                InfoPill(label = "词形", value = relation.toChineseLabel())
            }
            groups.forEach { group ->
                WordFormVariantGroupSection(group)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordFormVariantGroupSection(group: WordFormVariantGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (group.isCurrent) {
                Text(
                    text = "当前",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            group.variants.forEach { variant ->
                InfoPill(label = variant.label, value = variant.value)
            }
        }
    }
}

@Composable
private fun ErrorSection(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.36f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "单词形态服务暂时不可用",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun InsightSection(
    title: String,
    titleAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                titleAction?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun InfoPill(
    label: String,
    value: String
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun WordInsightUiState.subtitleText(wordForm: WordFormResponse?): String =
    when (this) {
        is WordInsightUiState.Loading -> "正在判断原形和实际读音"
        is WordInsightUiState.Success -> wordForm?.contextualSummary()
            ?: wordForm?.relation?.toChineseLabel()
            ?: "这是单词本身的常见形式"
        is WordInsightUiState.Error -> "暂时先使用你点中的这个词"
        WordInsightUiState.Idle -> "点击拆分片段来练习发音"
    }

private data class ChunkColor(
    val container: Color,
    val content: Color,
    val silentContent: Color
)

@Composable
private fun chunkColor(index: Int, silent: Boolean): ChunkColor {
    val colors = MaterialTheme.colorScheme
    if (silent) {
        return ChunkColor(
            container = colors.surfaceVariant,
            content = colors.onSurfaceVariant,
            silentContent = colors.onSurfaceVariant
        )
    }
    return when (index % 6) {
        0 -> ChunkColor(colors.primaryContainer, colors.primary, colors.onSurfaceVariant)
        1 -> ChunkColor(colors.tertiaryContainer, colors.tertiary, colors.onSurfaceVariant)
        2 -> ChunkColor(colors.secondaryContainer, colors.secondary, colors.onSurfaceVariant)
        3 -> ChunkColor(colors.errorContainer, colors.error, colors.onSurfaceVariant)
        4 -> ChunkColor(colors.surfaceVariant, colors.error, colors.onSurfaceVariant)
        else -> ChunkColor(colors.surfaceVariant, colors.primary, colors.onSurfaceVariant)
    }
}

@Composable
private fun List<WordPhonicsChunk>.chunkColors(): List<ChunkColor> =
    mapIndexed { index, chunk -> chunkColor(index, chunk.silent) }

private fun WordPhonicsResponse.displayChunks(): List<WordPhonicsChunk> =
    chunks.ifEmpty {
        segments.map { segment ->
            WordPhonicsChunk(
                text = segment.text,
                ipa = segment.ipa,
                phonemes = segment.phonemes,
                spans = emptyList(),
                silent = false,
                stress = "",
                rule = ""
            )
        }
    }

private fun List<WordPhonicsChunk>.toColoredSpellingText(
    chunkColors: List<ChunkColor>,
    displayWord: String? = null
): AnnotatedString {
    val original = displayWord?.takeIf { word -> word.length == sumOf { chunk -> chunk.text.length } }
    var offset = 0
    return buildAnnotatedString {
        this@toColoredSpellingText.forEachIndexed { index, chunk ->
            val spans = chunk.displaySpans()
            spans.forEach { span ->
                val text = original?.substring(offset, offset + span.text.length) ?: span.text
                val color = if (chunk.silent || span.silent) {
                    chunkColors.getOrNull(index)?.silentContent ?: Color.Gray
                } else {
                    chunkColors.getOrNull(index)?.content ?: Color.Unspecified
                }
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
                    append(text)
                }
                offset += span.text.length
            }
        }
    }
}

private fun List<WordPhonicsChunk>.toColoredIpaText(chunkColors: List<ChunkColor>) = buildAnnotatedString {
    append("/")
    forEachIndexed { index, chunk ->
        val body = chunk.ipa.ipaBody()
        if (body.isNotBlank()) {
            withStyle(
                SpanStyle(
                    color = chunkColors.getOrNull(index)?.content ?: Color.Unspecified,
                    fontWeight = FontWeight.Medium
                )
            ) {
                append(body)
            }
        }
    }
    append("/")
}

private fun List<WordPhonicsChunk>.hasChunkIpa(): Boolean =
    any { it.ipa.ipaBody().isNotBlank() }

private fun WordPhonicsChunk.displaySpans() =
    spans.takeIf { parts -> parts.isNotEmpty() && parts.joinToString("") { it.text } == text }
        ?: listOf(WordPhonicsSpan(text = text, silent = silent))

private fun WordPhonicsChunk.toColoredChunkText(color: ChunkColor): AnnotatedString =
    buildAnnotatedString {
        displaySpans().forEach { span ->
            val spanColor = if (silent || span.silent) {
                color.silentContent
            } else {
                color.content
            }
            withStyle(SpanStyle(color = spanColor, fontWeight = FontWeight.SemiBold)) {
                append(span.text)
            }
        }
    }

private fun String.ipaBody(): String =
    trim()
        .removePrefix("/")
        .removeSuffix("/")

private fun WordPhonicsChunk.hasStressNote(): Boolean =
    stress.toChineseStressLabel().isNotBlank() || rule.isNotBlank() || silent || spans.any { it.silent }

private fun buildRuleText(chunk: WordPhonicsChunk): String {
    val parts = buildList {
        if (chunk.stress.isNotBlank()) add(chunk.stress.toChineseStressLabel())
        if (chunk.rule.isNotBlank()) add(chunk.rule)
        if ((chunk.silent || chunk.spans.any { it.silent }) && !chunk.rule.contains("不发音")) {
            add("灰色字母不发音")
        }
    }
    return parts.joinToString(" · ")
}

private fun String.toChineseStressLabel(): String =
    when (lowercase()) {
        "primary" -> "重读"
        "secondary" -> "次重读"
        "unstressed" -> "非重读"
        "silent" -> "不发音"
        else -> ""
    }

private fun WordPronunciationUiState.isBusyFor(word: String): Boolean =
    when (this) {
        is WordPronunciationUiState.Preparing -> this.word.equals(word, ignoreCase = true)
        is WordPronunciationUiState.Playing -> this.word.equals(word, ignoreCase = true)
        else -> false
    }

private fun WordFormRelation.toChineseLabel(): String =
    when (type) {
        "plural_of" -> "$target 的复数"
        "past_tense_of" -> "$target 的过去式"
        "past_participle_of" -> "$target 的过去分词"
        "present_participle_of" -> "$target 的现在分词"
        "third_person_singular_of" -> "$target 的第三人称单数"
        "first_person_singular_of" -> "$target 的第一人称单数"
        "present_plural_or_second_person_of" -> "$target 的现在时复数/第二人称"
        "past_tense_singular_of" -> "$target 的过去式单数"
        "past_tense_plural_of" -> "$target 的过去式复数"
        "contraction_of" -> "$target 的缩写"
        "past_or_base_form_of" -> "$target 的过去式或原形"
        else -> label
    }

private fun WordFormResponse.displayVariantGroups(): List<WordFormVariantGroup> {
    return variantGroups
        .mapNotNull { group ->
            val variants = group.variants
                .filter { it.label.isNotBlank() && it.value.isNotBlank() }
                .dedupeVariants()
            if (variants.isEmpty()) {
                null
            } else {
                group.copy(
                    label = group.label.ifBlank { group.partOfSpeech.toChinesePartOfSpeech() },
                    variants = variants
                )
            }
        }
}

private fun MutableList<WordFormVariant>.addUniqueVariant(variant: WordFormVariant) {
    val normalizedValue = variant.value.trim().lowercase()
    if (normalizedValue.isBlank()) return
    if (any { it.type == variant.type && it.value.trim().lowercase() == normalizedValue }) return
    add(variant)
}

private fun List<WordFormVariant>.dedupeVariants(): List<WordFormVariant> =
    buildList {
        this@dedupeVariants.forEach { addUniqueVariant(it) }
    }

private fun WordFormResponse.contextualSummary(): String? {
    val label = currentPartOfSpeechLabel.ifBlank {
        currentPartOfSpeech?.toChinesePartOfSpeech().orEmpty()
    }
    if (label.isBlank()) return null
    return relation?.let { "$label，${it.toChineseLabel()}" } ?: "当前作$label"
}

private fun currentMeaningSummary(
    wordForm: WordFormResponse?,
    meaningState: WordMeaningUiState
): String? {
    val meanings = (meaningState as? WordMeaningUiState.Success)
        ?.meaning
        ?.meanings
        ?.filter { it.meaning.isNotBlank() }
        .orEmpty()
    if (meanings.isEmpty()) return null

    val currentPart = wordForm?.currentPartOfSpeech?.trim()?.lowercase().orEmpty()
    val entry = meanings.firstOrNull { it.partOfSpeech.trim().lowercase() == currentPart }
        ?: meanings.firstOrNull()
        ?: return null
    return entry.meaning.trim()
}

private fun String.toChinesePartOfSpeech(): String =
    when (this) {
        "noun" -> "名词"
        "verb" -> "动词"
        "adjective" -> "形容词"
        "adverb" -> "副词"
        "pronoun" -> "代词"
        "preposition" -> "介词"
        "conjunction" -> "连词"
        "determiner" -> "限定词"
        "interjection" -> "感叹词"
        else -> "词形"
    }
