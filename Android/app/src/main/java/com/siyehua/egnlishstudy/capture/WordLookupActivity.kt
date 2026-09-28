package com.siyehua.egnlishstudy.capture

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siyehua.egnlishstudy.data.ManualFavorite
import com.siyehua.egnlishstudy.ui.theme.EgnlishStudyTheme
import com.siyehua.egnlishstudy.ui.wordinsight.ClickedWord
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightBody
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val lookupWork = CoroutineScope(SupervisorJob() + Dispatchers.Default)

class WordLookupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = intent?.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
        if (query.isEmpty()) {
            finish()
            return
        }
        setContent {
            EgnlishStudyTheme {
                WordLookupDialog(
                    query = query,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
private fun WordLookupDialog(
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

    var sentenceTranslation by remember(query) { mutableStateOf<String?>(null) }
    var sentenceSaved by remember(query) { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (!isWord) {
            sentenceTranslation =
                runCatching { ManualFavorite.translate(context, query) }.getOrDefault("")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
        ) {
            Column {
                if (isWord) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        WordInsightBody(
                            clickedWord = clickedWord,
                            uiState = uiState,
                            audioState = audioState,
                            onRetry = { viewModel.load(clickedWord) },
                            onSpeakWord = { word, audioUrl -> viewModel.speak(word, audioUrl) },
                            onSpeakText = { text -> viewModel.speakText(text) }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = query,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = sentenceTranslation?.ifBlank { "没有查询到翻译" } ?: "正在翻译…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    sentenceSaved = true
                                    val value = query
                                    val translation = sentenceTranslation.orEmpty()
                                    lookupWork.launch {
                                        runCatching {
                                            ManualFavorite.save(
                                                context.applicationContext,
                                                value,
                                                translation
                                            )
                                        }
                                    }
                                },
                                enabled = !sentenceSaved && sentenceTranslation != null
                            ) {
                                Text(if (sentenceSaved) "已加入单词本" else "加入单词本")
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
