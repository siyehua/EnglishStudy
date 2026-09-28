package com.siyehua.egnlishstudy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.siyehua.egnlishstudy.data.ContentCacheDatabase
import com.siyehua.egnlishstudy.data.FavoriteRecord
import com.siyehua.egnlishstudy.data.ManualFavorite
import com.siyehua.egnlishstudy.data.wordform.WordMeaningRepository
import com.siyehua.egnlishstudy.ui.theme.StudyGreen
import com.siyehua.egnlishstudy.ui.theme.StudyYellow

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenSentence: (FavoriteRecord) -> Unit,
    onOpenWord: (String, String) -> Unit
) {
    val context = LocalContext.current
    val database = remember { ContentCacheDatabase(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var favorites by remember {
        mutableStateOf(runCatching { database.loadFavorites() }.getOrDefault(emptyList()))
    }
    var showAddDialog by remember { mutableStateOf(false) }
    val addState = remember { AddFavoriteState() }
    val meaningRepository = remember { WordMeaningRepository(context) }
    val scope = rememberCoroutineScope()

    fun reloadFavorites() {
        favorites = runCatching { database.loadFavorites() }.getOrDefault(emptyList())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadFavorites()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun submit(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        addState.isLoading = true
        addState.error = null
        scope.launch {
            val translation = runCatching { ManualFavorite.translate(context, value) }
                .getOrElse { error ->
                    addState.error = error.message ?: "翻译失败，请检查网络后重试"
                    addState.isLoading = false
                    return@launch
                }
            if (translation.isBlank()) {
                addState.error = "没有获取到翻译，请检查拼写"
                addState.isLoading = false
                return@launch
            }
            runCatching { ManualFavorite.save(context, value, translation) }
                .onFailure { error ->
                    addState.error = error.message ?: "保存失败"
                    addState.isLoading = false
                    return@launch
                }
            addState.isLoading = false
            showAddDialog = false
            addState.reset()
            reloadFavorites()
        }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "收藏夹",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            addState.reset()
                            showAddDialog = true
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加收藏")
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = StudyYellow
                    ) {
                        Text(
                            text = "${favorites.size} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF1F2A24),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (favorites.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "还没有收藏",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "在课程里点句子或单词即可收藏",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favorites, key = { it.id }) { favorite ->
                    FavoriteCard(
                        favorite = favorite,
                        onOpen = {
                            if (favorite.kind == "sentence") {
                                onOpenSentence(favorite)
                            } else {
                                onOpenWord(favorite.text, favorite.lessonTitle)
                            }
                        },
                        onDelete = {
                            database.deleteFavorite(favorite.id)
                            reloadFavorites()
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddFavoriteDialog(
            state = addState,
            onDismiss = {
                showAddDialog = false
                addState.reset()
            },
            onSubmit = { submit(it) }
        )
    }
}

@Composable
private fun AddFavoriteDialog(
    state: AddFavoriteState,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isLoading) onDismiss() },
        title = { Text("添加收藏") },
        text = {
            Column {
                Text(
                    text = "输入单词或句子，会自动翻译并按类型收藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.input,
                    onValueChange = state::onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    enabled = !state.isLoading,
                    placeholder = { Text("例如 remember 或 Nice to meet you.") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.inputKindLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = StudyGreen
                )
                state.error?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(state.input) },
                enabled = state.canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = StudyGreen)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("翻译中")
                } else {
                    Text("添加")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isLoading) {
                Text("取消")
            }
        }
    )
}

class AddFavoriteState {
    var input by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun reset() {
        input = ""
        isLoading = false
        error = null
    }

    fun onInputChange(value: String) {
        input = value
        error = null
    }

    val canSubmit: Boolean
        get() = !isLoading && input.trim().isNotEmpty()

    fun inputKindLabel(): String = when (ManualFavorite.looksLikeSingleWord(input.trim())) {
        true -> "识别为：单词"
        false -> "识别为：句子"
        null -> "输入内容后自动判断类型"
    }
}

@Composable
private fun FavoriteCard(
    favorite: FavoriteRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when {
                    favorite.lessonTitle.isNotBlank() -> favorite.lessonTitle
                    favorite.kind == "word" -> "单词"
                    else -> ""
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
