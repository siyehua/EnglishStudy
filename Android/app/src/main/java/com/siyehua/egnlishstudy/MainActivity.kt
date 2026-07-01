package com.siyehua.egnlishstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.siyehua.egnlishstudy.model.*
import com.siyehua.egnlishstudy.ui.screens.ContentDetailScreen
import com.siyehua.egnlishstudy.ui.screens.ContentListScreen
import com.siyehua.egnlishstudy.ui.theme.EgnlishStudyTheme

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
    var selectedContent by remember { mutableStateOf<Content?>(null) }

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            ContentListScreen(
                onContentClick = { content ->
                    selectedContent = content
                    navController.navigate("detail")
                }
            )
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
