package com.siyehua.egnlishstudy

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.siyehua.egnlishstudy.data.ContentCacheDatabase
import com.siyehua.egnlishstudy.data.LessonQueueHolder
import com.siyehua.egnlishstudy.data.FavoriteRecord
import com.siyehua.egnlishstudy.model.*
import com.siyehua.egnlishstudy.playback.AudioUiState
import com.siyehua.egnlishstudy.playback.PlaybackCore
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siyehua.egnlishstudy.ui.ContentAudioViewModel
import com.siyehua.egnlishstudy.ui.screens.CaptionSettingsScreen
import com.siyehua.egnlishstudy.ui.screens.ContentDetailScreen
import com.siyehua.egnlishstudy.ui.screens.ContentListScreen
import com.siyehua.egnlishstudy.ui.screens.FavoritesScreen
import com.siyehua.egnlishstudy.ui.screens.SentenceDetailScreen
import com.siyehua.egnlishstudy.ui.theme.EgnlishStudyTheme
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightScreen

class MainActivity : ComponentActivity() {
    private var pendingOpenCaptionSettings = false

    private val overlayPermReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (intent.action == com.siyehua.egnlishstudy.playback.PlaybackNotificationService.ACTION_NEED_OVERLAY_PERMISSION) {
                pendingOpenCaptionSettings = true
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val filter = android.content.IntentFilter(
            com.siyehua.egnlishstudy.playback.PlaybackNotificationService.ACTION_NEED_OVERLAY_PERMISSION
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(overlayPermReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(overlayPermReceiver, filter)
        }
        setContent {
            EgnlishStudyTheme {
                MainNavigation()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val database = remember { ContentCacheDatabase(context) }

    val activity = context as? androidx.activity.ComponentActivity
    val audioViewModel: ContentAudioViewModel = if (activity != null) {
        viewModel(viewModelStoreOwner = activity)
    } else {
        viewModel()
    }
    var selectedContent by remember { mutableStateOf<Content?>(null) }
    var selectedWord by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedFavorite by remember { mutableStateOf<FavoriteRecord?>(null) }

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            val listAudioState by PlaybackCore.uiState.collectAsState()
            val listPlayerContent by PlaybackCore.currentLesson.collectAsState()
            val listLooping by PlaybackCore.loopingLessonFlow.collectAsState()
            val listCaptionOn by PlaybackCore.captionOnFlow.collectAsState()
            val listEvent by PlaybackCore.currentSentenceEvent.collectAsState()
            ContentListScreen(
                onContentClick = { content ->
                    selectedContent = content
                    navController.navigate("detail")
                },
                audioState = listAudioState,
                playerContent = listPlayerContent,
                playerSubtitle = listEvent?.text.orEmpty(),
                isLoopingLesson = listLooping,
                isCaptionOn = listCaptionOn,
                onToggleLessonLoop = { audioViewModel.toggleLessonLoop() },
                onPlayPrevLesson = { audioViewModel.playPrevLesson() },
                onPlayNextLesson = { audioViewModel.playNextLesson() },
                onTogglePlayback = {
                    val c = listPlayerContent ?: LessonQueueHolder.items.firstOrNull()
                    if (listAudioState is com.siyehua.egnlishstudy.playback.AudioUiState.Idle && c != null) {
                        audioViewModel.playAll(c)
                    } else if (c != null) {
                        audioViewModel.togglePlayback(c)
                    }
                },
                onToggleCaption = { audioViewModel.toggleCaptionOverlay() },
                onSyncLessonQueue = { items, currentId ->
                    audioViewModel.setLessonQueue(items, currentId ?: items.firstOrNull()?.id.orEmpty())
                },
                onOpenFavorites = { navController.navigate("favorites") },
                onOpenCaptionSettings = {
                    val activity = context as? android.app.Activity
                    if (android.provider.Settings.canDrawOverlays(context)) {
                        navController.navigate("captionSettings")
                    } else if (activity != null) {
                        activity.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${activity.packageName}")
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
        }
        composable("favorites") {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onOpenSentence = { favorite ->
                    selectedFavorite = favorite
                    navController.navigate("sentence")
                },
                onOpenWord = { word, sentence ->
                    selectedWord = word to sentence
                    navController.navigate("word")
                }
            )
        }
        composable("sentence") {
            selectedFavorite?.let { favorite ->
                SentenceDetailScreen(
                    favorite = favorite,
                    onBack = { navController.popBackStack() },
                    onOpenOriginal = { contentId ->
                        val content = runCatching {
                            database.loadAll().firstOrNull { it.id == contentId }
                        }.getOrNull()
                        if (content != null) {
                            selectedContent = content
                            navController.navigate("detail")
                        }
                    }
                )
            }
        }
        composable("word") {
            selectedWord?.let { (word, sentence) ->
                WordInsightScreen(
                    word = word,
                    sentence = sentence,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("captionSettings") {
            val settingsAudioState by PlaybackCore.uiState.collectAsState()
            val settingsContent by PlaybackCore.currentLesson.collectAsState()
            val settingsLooping by PlaybackCore.loopingLessonFlow.collectAsState()
            val settingsCaptionOn by PlaybackCore.captionOnFlow.collectAsState()
            val settingsEvent by PlaybackCore.currentSentenceEvent.collectAsState()
            CaptionSettingsScreen(
                onBack = { navController.popBackStack() },
                audioState = settingsAudioState,
                playerContent = settingsContent,
                playerSubtitle = settingsEvent?.text.orEmpty(),
                isLoopingLesson = settingsLooping,
                isCaptionOn = settingsCaptionOn,
                onPlayPrevLesson = { audioViewModel.playPrevLesson() },
                onPlayNextLesson = { audioViewModel.playNextLesson() },
                onTogglePlayback = {
                    val c = settingsContent
                    if (settingsAudioState is com.siyehua.egnlishstudy.playback.AudioUiState.Idle && c != null) {
                        audioViewModel.playAll(c)
                    } else if (c != null) {
                        audioViewModel.togglePlayback(c)
                    }
                },
                onToggleLessonLoop = { audioViewModel.toggleLessonLoop() },
                onToggleCaption = { audioViewModel.toggleCaptionOverlay() }
            )
        }
        composable("detail") {
            selectedContent?.let { content ->
                ContentDetailScreen(
                    content = content,
                    onBack = { navController.popBackStack() },
                    audioViewModel = audioViewModel,
                    onOpenCaptionSettings = {
                        val activity = context as? android.app.Activity
                        if (android.provider.Settings.canDrawOverlays(context)) {
                            navController.navigate("captionSettings")
                        } else if (activity != null) {
                            activity.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${activity.packageName}")
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
        }
    }
}
