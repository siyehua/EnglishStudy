package com.siyehua.egnlishstudy.data.favorites

import android.content.Context
import com.siyehua.egnlishstudy.data.AppLog
import com.siyehua.egnlishstudy.data.ContentCacheDatabase
import com.siyehua.egnlishstudy.data.FavoriteRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 收藏云端同步（单用户：服务端只存一份，不分账号）。
 *
 * - 本地先写、立刻生效，联网后再推；
 * - 服务端按 `updatedAt` 做「最后写入者优先」，返回全量（含墓碑）；
 * - 卸载重装后本地为空，一拉就把收藏恢复回来。
 */
object FavoritesSync {
    private const val BUCKET = "default"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var scheduled: Job? = null

    /** 本地增删改后触发（去抖 1.5s，避免连续操作打多次网络）。 */
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            scheduled?.cancel()
            scheduled = scope.launch {
                delay(1500)
                syncNow(appContext)
            }
        }
    }

    /** 立即同步（进入收藏页 / App 启动时调用）。 */
    suspend fun syncNow(context: Context) {
        val appContext = context.applicationContext
        mutex.withLock {
            try {
                val database = ContentCacheDatabase(appContext)
                val local = database.loadFavoritesForSync()
                val response = FavoritesApiClient().sync(
                    BUCKET,
                    local.map { it.toSyncItem() }
                )
                database.applySyncItems(response.items.map { it.toRecord() })
                AppLog.log(
                    "favorites",
                    "synced local=${local.size} server=${response.items.size}"
                )
            } catch (error: Exception) {
                AppLog.log("favorites", "sync failed: ${error.message}")
            }
        }
    }
}

private fun FavoriteRecord.toSyncItem(): FavoriteSyncItem = FavoriteSyncItem(
    syncId = syncId,
    kind = kind,
    text = text,
    audioUrl = audioUrl,
    contentId = contentId,
    translation = translation,
    lessonTitle = lessonTitle,
    startTime = startTime,
    endTime = endTime,
    updatedAt = if (updatedAt > 0) updatedAt else createdAt,
    deleted = deleted
)

private fun FavoriteSyncItem.toRecord(): FavoriteRecord = FavoriteRecord(
    kind = kind,
    text = text,
    audioUrl = audioUrl,
    contentId = contentId,
    translation = translation,
    lessonTitle = lessonTitle,
    startTime = startTime,
    endTime = endTime,
    createdAt = updatedAt,
    syncId = syncId,
    updatedAt = updatedAt,
    deleted = deleted
)
