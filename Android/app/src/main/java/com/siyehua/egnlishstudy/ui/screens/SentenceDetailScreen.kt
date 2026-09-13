package com.siyehua.egnlishstudy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siyehua.egnlishstudy.data.FavoriteRecord
import com.siyehua.egnlishstudy.data.wordform.WordFormApiClient
import com.siyehua.egnlishstudy.ui.AudioUiState
import com.siyehua.egnlishstudy.ui.ContentAudioViewModel
import com.siyehua.egnlishstudy.ui.theme.StudyGreen
import com.siyehua.egnlishstudy.ui.theme.StudyInk
import com.siyehua.egnlishstudy.ui.wordinsight.ClickableReadingText
import com.siyehua.egnlishstudy.ui.wordinsight.ClickedWord
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightSheet
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightViewModel

/** Detail page for a favourited sentence: play / loop the clip and tap words. */
@Composable
fun SentenceDetailScreen(
    favorite: FavoriteRecord,
    onBack: () -> Unit,
    audioViewModel: ContentAudioViewModel = viewModel(),
    wordInsightViewModel: WordInsightViewModel = viewModel()
) {
    val audioState by audioViewModel.uiState.collectAsState()
    val wordInsightState by wordInsightViewModel.uiState.collectAsState()
    val wordPronunciationState by wordInsightViewModel.audioState.collectAsState()
    var selectedWord by remember { mutableStateOf<ClickedWord?>(null) }

    val isPlaying = audioState is AudioUiState.Playing ||
        audioState is AudioUiState.Preparing

    val clipUrl = remember(favorite.audioUrl) {
        favorite.audioUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                if (url.startsWith("http")) url else WordFormApiClient.DEFAULT_BASE_URL + url
            }
    }

    fun playSentence() {
        val url = clipUrl ?: return
        audioViewModel.playSegmentUrl(url, favorite.text, 0)
    }

    fun loopSentence() {
        val url = clipUrl ?: return
        audioViewModel.loopSegmentUrl(url, 0)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = StudyGreen,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = favorite.lessonTitle.ifBlank { "句子" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                    IconButton(
                        onClick = { loopSentence() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.Repeat, contentDescription = "循环播放")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    ClickableReadingText(
                        text = favorite.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = StudyInk,
                        modifier = Modifier.padding(16.dp),
                        onWordClick = { clickedWord -> selectedWord = clickedWord },
                        onSentenceTap = { playSentence() }
                    )
                }

                Text(
                    text = "点句子播放整句，双击单词查看释义",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                audioViewModel.stop()
                            } else {
                                playSentence()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = StudyGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放整句"
                        )
                    }
                    if (isPlaying) {
                        IconButton(onClick = { audioViewModel.stop() }) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "停止",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
