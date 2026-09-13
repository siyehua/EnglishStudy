package com.siyehua.egnlishstudy

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
import com.siyehua.egnlishstudy.model.*
import com.siyehua.egnlishstudy.ui.screens.ContentDetailScreen
import com.siyehua.egnlishstudy.ui.screens.ContentListScreen
import com.siyehua.egnlishstudy.ui.screens.FavoritesScreen
import com.siyehua.egnlishstudy.ui.theme.EgnlishStudyTheme
import com.siyehua.egnlishstudy.ui.wordinsight.WordInsightScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EgnlishStudyTheme {
                MainNavigation()
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val database = remember { ContentCacheDatabase(context) }
    var selectedContent by remember { mutableStateOf<Content?>(null) }
    var selectedWord by remember { mutableStateOf<Pair<String, String>?>(null) }

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            ContentListScreen(
                onContentClick = { content ->
                    selectedContent = content
                    navController.navigate("detail")
                },
                onOpenFavorites = { navController.navigate("favorites") }
            )
        }
        composable("favorites") {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onOpenSentence = { contentId ->
                    val content = runCatching {
                        database.loadAll().firstOrNull { it.id == contentId }
                    }.getOrNull()
                    if (content != null) {
                        selectedContent = content
                        navController.navigate("detail")
                    }
                },
                onOpenWord = { word, sentence ->
                    selectedWord = word to sentence
                    navController.navigate("word")
                }
            )
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
        composable("detail") {
            selectedContent?.let { content ->
                ContentDetailScreen(
                    content = content,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
