package com.example.vplayer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vplayer.ui.theme.VPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VPlayerTheme {
                VPlayerApp()
            }
        }
    }
}

@Composable
fun VPlayerApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "video_list") {
        composable("video_list") {
            VideoListScreen(onVideoClick = { video ->
                // Encode URI to pass safely in navigation route
                val encodedUri = Uri.encode(video.uri.toString())
                navController.navigate("video_player/${video.id}/$encodedUri")
            })
        }
        composable(
            route = "video_player/{videoId}/{videoUri}",
            arguments = listOf(
                navArgument("videoId") { type = NavType.LongType },
                navArgument("videoUri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getLong("videoId") ?: 0L
            val videoUriString = backStackEntry.arguments?.getString("videoUri")
            val videoUri = Uri.parse(Uri.decode(videoUriString))
            
            VideoPlayerScreen(videoId = videoId, videoUri = videoUri)
        }
    }
}
