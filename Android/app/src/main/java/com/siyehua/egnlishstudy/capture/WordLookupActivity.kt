package com.siyehua.egnlishstudy.capture

import android.app.SearchManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siyehua.egnlishstudy.data.ManualFavorite
import com.siyehua.egnlishstudy.ui.theme.EgnlishStudyTheme
import com.siyehua.egnlishstudy.ui.wordinsight.ClickedWord
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightSheet
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val lookupWork = CoroutineScope(SupervisorJob() + Dispatchers.Default)

class WordLookupActivity : ComponentActivity() {

    private val query = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.siyehua.egnlishstudy.data.AppLog.init(this)
        query.value = extractQuery(intent)
        if (query.value.isEmpty()) {
            finish()
            return
        }
        setContent {
            EgnlishStudyTheme {
                val current = query.value
                if (current.isNotEmpty()) {
                    LookupSheet(
                        query = current,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = extractQuery(intent)
        if (next.isEmpty()) {
            finish()
            return
        }
        query.value = next
    }

    private fun extractQuery(intent: android.content.Intent?): String =
        intent?.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LookupSheet(
    query: String,
    onDismiss: () -> Unit,
    viewModel: WordInsightViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val isWord = ManualFavorite.looksLikeSingleWord(query) == true

    val clickedWord = remember(query) {
        ClickedWord(
            word = query,
            normalized = query.lowercase(),
            range = 0 until query.length,
            sentence = ""
        )
    }
    LaunchedEffect(clickedWord) {
        if (isWord) viewModel.load(clickedWord)
    }

    if (isWord) {
        WordInsightSheet(
            clickedWord = clickedWord,
            uiState = uiState,
            audioState = audioState,
            onDismiss = onDismiss,
            onRetry = { viewModel.load(clickedWord) },
            onSpeakWord = { word, audioUrl -> viewModel.speak(word, audioUrl) },
            onSpeakText = { text -> viewModel.speakText(text) }
        )
    } else {
        SentenceLookupSheet(
            sentence = query,
            onDismiss = onDismiss
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SentenceLookupSheet(
    sentence: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var translation by remember(sentence) { mutableStateOf<String?>(null) }
    var saved by remember(sentence) { mutableStateOf(false) }

    LaunchedEffect(sentence) {
        translation = runCatching { ManualFavorite.translate(context, sentence) }.getOrDefault("")
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { SentenceDragHandle() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text(
                text = sentence,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = translation?.ifBlank { "没有查询到翻译" } ?: "正在翻译…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = {
                    saved = true
                    val value = sentence
                    val current = translation.orEmpty()
                    lookupWork.launch {
                        runCatching {
                            ManualFavorite.save(context.applicationContext, value, current)
                        }
                    }
                },
                enabled = !saved && translation != null
            ) {
                Text(if (saved) "已加入单词本" else "加入单词本")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SentenceDragHandle() {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .padding(top = 6.dp, bottom = 2.dp)
            .height(4.dp)
            .fillMaxWidth(0.09f),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    ) {}
}
