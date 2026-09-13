package com.siyehua.egnlishstudy.ui.wordinsight

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

@Composable
fun ClickableReadingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onWordClick: (ClickedWord) -> Unit,
    onSentenceTap: (String) -> Unit = {}
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentOnWordClick by rememberUpdatedState(onWordClick)
    val currentOnSentenceTap by rememberUpdatedState(onSentenceTap)

    DisableSelection {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = modifier.pointerInput(text) {
                detectTapGestures(
                    // Single tap anywhere on the line: play this sentence's audio.
                    onTap = {
                        val sentence = text.trim()
                        if (sentence.isNotBlank()) {
                            currentOnSentenceTap(sentence)
                        }
                    },
                    // Double tap on a word: show its dictionary meaning.
                    onDoubleTap = { offset ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val charOffset = layout.getOffsetForPosition(offset)
                        val clickedWord = text.clickedWordAt(charOffset)
                            ?: return@detectTapGestures
                        currentOnWordClick(clickedWord)
                    }
                )
            },
            onTextLayout = { layoutResult = it }
        )
    }
}
