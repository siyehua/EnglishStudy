package com.siyehua.egnlishstudy.data.wordform

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException

class WordFormApiClient(
    baseUrl: String = DEFAULT_BASE_URL
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun resolveWordForm(surface: String, sentence: String): WordFormResponse =
        try {
            client.post("$normalizedBaseUrl/word-form") {
                setBody(
                    WordFormRequest(
                        surface = surface,
                        sentence = sentence
                    )
                )
            }.body()
        } catch (exception: HttpRequestTimeoutException) {
            throw WordFormNetworkException("Word form service timed out.", exception)
        } catch (exception: IOException) {
            throw WordFormNetworkException("Cannot reach word form service.", exception)
        }

    suspend fun resolveWordPronunciation(word: String): WordPronunciationResponse =
        try {
            client.post("$normalizedBaseUrl/word-pronunciation") {
                setBody(WordPronunciationRequest(word = word))
            }.body()
        } catch (exception: HttpRequestTimeoutException) {
            throw WordFormNetworkException("Word pronunciation service timed out.", exception)
        } catch (exception: IOException) {
            throw WordFormNetworkException("Cannot reach word pronunciation service.", exception)
        }

    suspend fun resolveWordPhonics(word: String, sentence: String, ipa: String? = null): WordPhonicsResponse =
        try {
            client.post("$normalizedBaseUrl/word-phonics") {
                setBody(
                    WordPhonicsRequest(
                        word = word,
                        sentence = sentence,
                        ipa = ipa
                    )
                )
            }.body()
        } catch (exception: HttpRequestTimeoutException) {
            throw WordFormNetworkException("Word phonics service timed out.", exception)
        } catch (exception: IOException) {
            throw WordFormNetworkException("Cannot reach word phonics service.", exception)
        }

    suspend fun resolveWordMeaning(word: String, sentence: String): WordMeaningResponse =
        try {
            client.post("$normalizedBaseUrl/word-meaning") {
                setBody(
                    WordMeaningRequest(
                        word = word,
                        sentence = sentence
                    )
                )
            }.body()
        } catch (exception: HttpRequestTimeoutException) {
            throw WordFormNetworkException("Word meaning service timed out.", exception)
        } catch (exception: IOException) {
            throw WordFormNetworkException("Cannot reach word meaning service.", exception)
        }

    suspend fun fetchContent(
        fetchMore: Boolean,
        types: List<String>,
        levels: List<String>,
        sources: List<String>
    ): ContentFetchResponse =
        try {
            client.post("$normalizedBaseUrl/contents") {
                timeout {
                    requestTimeoutMillis = CONTENT_REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = CONTENT_REQUEST_TIMEOUT_MS
                }
                setBody(
                    ContentFetchRequest(
                        fetchMore = fetchMore,
                        types = types,
                        levels = levels,
                        sources = sources
                    )
                )
            }.body()
        } catch (exception: HttpRequestTimeoutException) {
            throw WordFormNetworkException("Content service timed out.", exception)
        } catch (exception: IOException) {
            throw WordFormNetworkException("Cannot reach content service.", exception)
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://cafe-font-happiness-salad.trycloudflare.com"
        private const val REQUEST_TIMEOUT_MS = 60_000L
        private const val CONTENT_REQUEST_TIMEOUT_MS = 120_000L
    }
}

class WordFormNetworkException(
    message: String,
    cause: Throwable
) : Exception(message, cause)
