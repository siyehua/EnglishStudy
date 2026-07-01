package com.siyehua.egnlishstudy.ui.wordinsight

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siyehua.egnlishstudy.data.TtsAudioManager
import com.siyehua.egnlishstudy.data.WordAudioResult
import com.siyehua.egnlishstudy.data.wordform.WordFormRepository
import com.siyehua.egnlishstudy.data.wordform.WordFormResponse
import com.siyehua.egnlishstudy.data.wordform.WordMeaningRepository
import com.siyehua.egnlishstudy.data.wordform.WordMeaningResponse
import com.siyehua.egnlishstudy.data.wordform.WordPhonicsRepository
import com.siyehua.egnlishstudy.data.wordform.WordPhonicsResponse
import com.siyehua.egnlishstudy.data.wordform.WordPronunciationRepository
import com.siyehua.egnlishstudy.data.wordform.WordPronunciationResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WordInsightViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository = WordFormRepository(application)
    private val pronunciationRepository = WordPronunciationRepository(application)
    private val meaningRepository = WordMeaningRepository(application)
    private val phonicsRepository = WordPhonicsRepository(application)
    private val ttsAudioManager = TtsAudioManager(application)

    private val _uiState = MutableStateFlow<WordInsightUiState>(WordInsightUiState.Idle)
    val uiState: StateFlow<WordInsightUiState> = _uiState.asStateFlow()
    private val _audioState = MutableStateFlow<WordPronunciationUiState>(WordPronunciationUiState.Idle)
    val audioState: StateFlow<WordPronunciationUiState> = _audioState.asStateFlow()

    private var resolveJob: Job? = null
    private var audioJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    fun load(clickedWord: ClickedWord) {
        resolveJob?.cancel()
        _audioState.value = WordPronunciationUiState.Idle
        _uiState.value = WordInsightUiState.Loading(clickedWord)
        resolveJob = viewModelScope.launch {
            val wordFormLookup = async {
                runCatching {
                    repository.resolve(
                        surface = clickedWord.word,
                        sentence = clickedWord.sentence
                    )
                }
            }
            val pronunciationLookup = async {
                runCatching {
                    pronunciationRepository.resolve(clickedWord.normalized)
                }
            }
            val meaningLookup = async {
                runCatching {
                    meaningRepository.resolve(
                        word = clickedWord.normalized,
                        sentence = clickedWord.sentence
                    )
                }
            }
            wordFormLookup.await().fold(
                onSuccess = { wordForm ->
                    var successState = WordInsightUiState.Success(
                        clickedWord = clickedWord,
                        wordForm = wordForm,
                        pronunciation = null,
                        meaningState = WordMeaningUiState.Loading,
                        phonicsState = WordPhonicsUiState.Loading
                    )
                    _uiState.value = successState

                    val pronunciation = pronunciationLookup.await().getOrNull()
                    if (pronunciation != null) {
                        successState = successState.copy(pronunciation = pronunciation)
                        _uiState.value = successState
                    }

                    val phonicsLookup = async {
                        runCatching {
                            phonicsRepository.resolve(
                                word = clickedWord.normalized,
                                sentence = clickedWord.sentence,
                                ipa = pronunciation?.phonetic
                            )
                        }
                    }

                    launch {
                        val meaningState = meaningLookup.await().fold(
                            onSuccess = { WordMeaningUiState.Success(it) },
                            onFailure = {
                                WordMeaningUiState.Error(
                                    it.message ?: "Word meaning lookup failed."
                                )
                            }
                        )
                        updateCurrentSuccess { current ->
                            current.copy(meaningState = meaningState)
                        }
                    }

                    launch {
                        val phonicsState = phonicsLookup.await().fold(
                            onSuccess = { WordPhonicsUiState.Success(it) },
                            onFailure = {
                                WordPhonicsUiState.Error(
                                    it.message ?: "Word phonics lookup failed."
                                )
                            }
                        )
                        updateCurrentSuccess { current ->
                            current.copy(phonicsState = phonicsState)
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.value = WordInsightUiState.Error(
                        clickedWord = clickedWord,
                        message = error.message ?: "Word form lookup failed."
                    )
                }
            )
        }
    }

    private fun updateCurrentSuccess(
        transform: (WordInsightUiState.Success) -> WordInsightUiState.Success
    ) {
        val current = _uiState.value as? WordInsightUiState.Success ?: return
        _uiState.value = transform(current)
    }

    fun speak(word: String, audioUrl: String? = null) {
        val target = word.trim()
        if (target.isBlank()) return

        audioJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        _audioState.value = WordPronunciationUiState.Preparing(target)
        audioJob = viewModelScope.launch {
            when (val result = ttsAudioManager.ensureAudioForWord(target, audioUrl)) {
                is WordAudioResult.Success -> {
                    _audioState.value = WordPronunciationUiState.Playing(target)
                    mediaPlayer = ttsAudioManager.playUri(
                        uri = result.uri,
                        onCompletion = {
                            mediaPlayer = null
                            _audioState.value = WordPronunciationUiState.Idle
                        },
                        onError = { message ->
                            mediaPlayer = null
                            _audioState.value = WordPronunciationUiState.Error(message)
                        }
                    )
                    if (mediaPlayer == null) {
                        _audioState.value = WordPronunciationUiState.Error("Audio playback failed.")
                    }
                }
                is WordAudioResult.Failure -> {
                    _audioState.value = WordPronunciationUiState.Error(result.message)
                }
            }
        }
    }

    fun speakText(text: String) {
        val target = text.trim()
        if (target.isBlank()) return

        audioJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        _audioState.value = WordPronunciationUiState.Preparing(target)
        audioJob = viewModelScope.launch {
            when (val result = ttsAudioManager.ensureAudioForText(target)) {
                is WordAudioResult.Success -> {
                    _audioState.value = WordPronunciationUiState.Playing(target)
                    mediaPlayer = ttsAudioManager.playUri(
                        uri = result.uri,
                        onCompletion = {
                            mediaPlayer = null
                            _audioState.value = WordPronunciationUiState.Idle
                        },
                        onError = { message ->
                            mediaPlayer = null
                            _audioState.value = WordPronunciationUiState.Error(message)
                        }
                    )
                    if (mediaPlayer == null) {
                        _audioState.value = WordPronunciationUiState.Error("Audio playback failed.")
                    }
                }
                is WordAudioResult.Failure -> {
                    _audioState.value = WordPronunciationUiState.Error(result.message)
                }
            }
        }
    }

    override fun onCleared() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onCleared()
    }
}

sealed class WordInsightUiState {
    object Idle : WordInsightUiState()
    data class Loading(val clickedWord: ClickedWord) : WordInsightUiState()
    data class Success(
        val clickedWord: ClickedWord,
        val wordForm: WordFormResponse,
        val pronunciation: WordPronunciationResponse? = null,
        val meaningState: WordMeaningUiState = WordMeaningUiState.Idle,
        val phonicsState: WordPhonicsUiState = WordPhonicsUiState.Idle
    ) : WordInsightUiState()
    data class Error(
        val clickedWord: ClickedWord,
        val message: String
    ) : WordInsightUiState()
}

sealed class WordMeaningUiState {
    object Idle : WordMeaningUiState()
    object Loading : WordMeaningUiState()
    data class Success(val meaning: WordMeaningResponse) : WordMeaningUiState()
    data class Error(val message: String) : WordMeaningUiState()
}

sealed class WordPhonicsUiState {
    object Idle : WordPhonicsUiState()
    object Loading : WordPhonicsUiState()
    data class Success(val phonics: WordPhonicsResponse) : WordPhonicsUiState()
    data class Error(val message: String) : WordPhonicsUiState()
}

sealed class WordPronunciationUiState {
    object Idle : WordPronunciationUiState()
    data class Preparing(val word: String) : WordPronunciationUiState()
    data class Playing(val word: String) : WordPronunciationUiState()
    data class Error(val message: String) : WordPronunciationUiState()
}
