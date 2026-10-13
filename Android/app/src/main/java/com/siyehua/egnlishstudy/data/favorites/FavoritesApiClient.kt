package com.siyehua.egnlishstudy.data.favorites

import com.siyehua.egnlishstudy.data.wordform.WordFormApiClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 收藏同步的 HTTP 客户端。 */
class FavoritesApiClient(
    baseUrl: String = WordFormApiClient.DEFAULT_BASE_URL
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

    suspend fun sync(bucket: String, items: List<FavoriteSyncItem>): FavoritesSyncResponse =
        client.post("$normalizedBaseUrl/favorites/sync") {
            setBody(FavoritesSyncRequest(bucket = bucket, items = items))
        }.body()

    companion object {
        private const val REQUEST_TIMEOUT_MS = 30_000L
    }
}
