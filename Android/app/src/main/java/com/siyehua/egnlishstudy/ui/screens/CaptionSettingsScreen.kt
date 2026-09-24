package com.siyehua.egnlishstudy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siyehua.egnlishstudy.data.CaptionStyle
import com.siyehua.egnlishstudy.data.CaptionStyleStore
import com.siyehua.egnlishstudy.ui.theme.StudyBlue
import com.siyehua.egnlishstudy.ui.theme.StudyGreen
import com.siyehua.egnlishstudy.ui.theme.StudyYellow

/** 文字颜色可选项 */
private val TEXT_COLORS = listOf(
    0xFFFFFFFF.toInt() to "白色",
    0xFFFFF176.toInt() to "浅黄",
    0xFF80DEEA.toInt() to "浅青",
    0xFFFFAB91.toInt() to "浅橙",
    0xFFA5D6A7.toInt() to "浅绿",
    0xFFF8BBD0.toInt() to "浅粉"
)

/** 背景颜色可选项 */
private val BG_COLORS = listOf(
    0xB3000000.toInt() to "半透明黑",
    0xE6FFFFFF.toInt() to "半透明白",
    0xCC1B5E20.toInt() to "深绿",
    0xCC0D47A1.toInt() to "深蓝",
    0xCCBF360C.toInt() to "深橙",
    0x00000000.toInt() to "无背景"
)

@Composable
fun CaptionSettingsScreen(
    onBack: () -> Unit,
    audioState: com.siyehua.egnlishstudy.ui.AudioUiState = com.siyehua.egnlishstudy.ui.AudioUiState.Idle,
    playerContent: com.siyehua.egnlishstudy.model.Content? = null,
    playerSubtitle: String = "",
    isLoopingLesson: Boolean = false,
    isCaptionOn: Boolean = false,
    onPlayPrevLesson: () -> Unit = {},
    onPlayNextLesson: () -> Unit = {},
    onTogglePlayback: () -> Unit = {},
    onToggleLessonLoop: () -> Unit = {},
    onToggleCaption: () -> Unit = {}
) {
    val context = LocalContext.current
    val store = remember { CaptionStyleStore(context) }
    var style by remember { mutableStateOf(store.load()) }

    fun update(newStyle: CaptionStyle) {
        style = newStyle
        store.save(newStyle)
        // 通知服务立即应用新样式
        runCatching {
            context.sendBroadcast(
                android.content.Intent(
                    com.siyehua.egnlishstudy.playback.PlaybackNotificationService.ACTION_UPDATE_CAPTION_STYLE
                ).setPackage(context.packageName)
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal
        ),
        bottomBar = {
            com.siyehua.egnlishstudy.ui.components.GlobalPlayerBar(
                audioState = audioState,
                content = playerContent,
                subtitle = playerSubtitle,
                isLoopingLesson = isLoopingLesson,
                isCaptionOn = isCaptionOn,
                onToggleLessonLoop = onToggleLessonLoop,
                onPlayPrevLesson = onPlayPrevLesson,
                onPlayNextLesson = onPlayNextLesson,
                onTogglePlayback = onTogglePlayback,
                onToggleCaption = onToggleCaption
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 顶部绿色头部（与收藏夹/课程页一致）
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
                        .padding(start = 10.dp, end = 18.dp, top = 6.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        text = "字幕设置",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(100.dp))

                // 预览
                SettingCard(title = "预览") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "字幕预览 Caption",
                            color = Color(style.textColor),
                            fontSize = style.textSizeSp.sp,
                            modifier = Modifier
                                .background(Color(style.bgColor), MaterialTheme.shapes.small)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 位置
                SettingCard(title = "位置") {
                    Slider(
                        value = style.yPercent.toFloat(),
                        onValueChange = { update(style.copy(yPercent = it.toInt())) },
                        valueRange = 5f..85f
                    )
                    Text(
                        text = "距屏幕顶部 ${style.yPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 字号
                SettingCard(title = "字号") {
                    Slider(
                        value = style.textSizeSp.toFloat(),
                        onValueChange = { update(style.copy(textSizeSp = it.toInt())) },
                        valueRange = 12f..32f
                    )
                    Text(
                        text = "${style.textSizeSp} sp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 文字颜色
                SettingCard(title = "文字颜色") {
                    ColorRow(
                        colors = TEXT_COLORS,
                        selected = style.textColor,
                        onSelect = { update(style.copy(textColor = it)) }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 背景颜色
                SettingCard(title = "背景颜色") {
                    ColorRow(
                        colors = BG_COLORS,
                        selected = style.bgColor,
                        onSelect = { update(style.copy(bgColor = it)) }
                    )
                }

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

/** 统一样式的设置卡片 */
@Composable
private fun SettingCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ColorRow(
    colors: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        colors.forEach { (color, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color(color).let {
                                if (it.alpha == 0f) MaterialTheme.colorScheme.surfaceVariant else it
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = if (selected == color) 3.dp else 1.dp,
                            color = if (selected == color) {
                                StudyBlue
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .clickable { onSelect(color) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
