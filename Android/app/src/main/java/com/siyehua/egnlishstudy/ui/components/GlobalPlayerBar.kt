package com.siyehua.egnlishstudy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.siyehua.egnlishstudy.model.Content
import com.siyehua.egnlishstudy.playback.AudioUiState
import com.siyehua.egnlishstudy.ui.theme.StudyGreen
import com.siyehua.egnlishstudy.ui.theme.StudyGreenDark
import com.siyehua.egnlishstudy.ui.theme.StudyMint
import com.siyehua.egnlishstudy.ui.theme.StudyCoral
import kotlin.math.max
import kotlin.math.min

@Composable
fun GlobalPlayerBar(
    audioState: AudioUiState,
    content: Content?,

    subtitle: String = "",
    isLoopingLesson: Boolean,
    isCaptionOn: Boolean,
    onToggleLessonLoop: () -> Unit,
    onPlayPrevLesson: () -> Unit,
    onPlayNextLesson: () -> Unit,
    onTogglePlayback: () -> Unit,
    onToggleCaption: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying = audioState is AudioUiState.Playing
    val isPaused = audioState is AudioUiState.Paused
    val isPreparing = audioState is AudioUiState.Preparing
    val progress = audioState.progressOrNull()
    val statusText = when {
        isPreparing -> "正在准备音频…"
        isPaused -> "已暂停 · ${audioState.compactStatusText()}"
        isLoopingLesson -> "整课循环中 · ${audioState.compactStatusText()}"
        isPlaying -> "正在播放 · ${audioState.compactStatusText()}"
        else -> audioState.statusText()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 6.dp
    ) {
        Column(

            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp)
                .navigationBarsPadding()
                .padding(bottom = 6.dp)
        ) {
            Text(
                text = content?.title ?: "暂无播放内容",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(

                text = subtitle.ifBlank { statusText },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    icon = Icons.Default.Repeat,
                    description = if (isLoopingLesson) "循环已开启" else "整课循环",
                    active = isLoopingLesson,
                    onClick = onToggleLessonLoop
                )
                PlayerIconButton(
                    icon = Icons.Default.SkipPrevious,
                    description = "上一课",
                    enabled = !isPreparing,
                    onClick = onPlayPrevLesson
                )
                IconButton(
                    onClick = onTogglePlayback,
                    enabled = !isPreparing,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = StudyGreen
                    )
                ) {
                    when {
                        isPreparing -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = StudyGreenDark
                        )
                        isPlaying -> Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "暂停",
                            modifier = Modifier.size(24.dp)
                        )
                        else -> Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                PlayerIconButton(
                    icon = Icons.Default.SkipNext,
                    description = "下一课",
                    enabled = !isPreparing,
                    onClick = onPlayNextLesson
                )
                PlayerIconButton(
                    icon = Icons.Default.ClosedCaption,
                    description = if (isCaptionOn) "字幕已开启" else "桌面字幕",
                    active = isCaptionOn,
                    onClick = onToggleCaption
                )
            }

            if (progress != null) {
                PlayerProgress(currentMillis = progress.first, totalMillis = progress.second)
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (active) StudyMint else Color.Transparent,
            contentColor = if (active) StudyGreenDark else StudyGreen
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlayerProgress(currentMillis: Long, totalMillis: Long) {
    val progress = if (totalMillis <= 0L) 0f else currentMillis.toFloat() / totalMillis.toFloat()
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(StudyMint)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(min(1f, progress))
                .height(6.dp)
                .background(StudyGreen)
        )
    }
}

fun AudioUiState.statusText(): String = when (this) {
    is AudioUiState.Idle -> "点播放开始收听"
    is AudioUiState.Preparing -> "正在准备音频…"
    is AudioUiState.Playing -> "正在播放"
    is AudioUiState.Paused -> "已暂停"
    is AudioUiState.Error -> message
}

fun AudioUiState.compactStatusText(): String = when (this) {
    is AudioUiState.Idle -> "Ready"
    is AudioUiState.Preparing -> "Preparing"
    is AudioUiState.Playing -> millisText(currentMillis) + " / " + millisText(totalMillis)
    is AudioUiState.Paused -> millisText(currentMillis)
    is AudioUiState.Error -> "Audio issue"
}

fun AudioUiState.progressOrNull(): Pair<Long, Long>? = when (this) {
    is AudioUiState.Playing -> currentMillis to totalMillis
    is AudioUiState.Paused -> currentMillis to totalMillis
    else -> null
}

private fun millisText(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${if (s < 10) "0$s" else "$s"}"
}
