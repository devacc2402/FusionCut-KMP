package com.example

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.di.AppModule
import com.example.ui.editor.EditorScreen
import com.example.ui.main.MainMenuScreen
import com.example.ui.theme.FusionCutTheme
import com.example.ui.theme.BackgroundDark

@Composable
fun App(module: AppModule) {
    FusionCutTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark),
            color = BackgroundDark
        ) {
            AppNavigation(module)
        }
    }
}

@Composable
fun AppNavigation(module: AppModule) {
    val navController = rememberNavController()
    // ...

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable(
            route = "main",
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            MainMenuScreen(
                module = module,
                onOpenProject = { projectId ->
                    navController.navigate("editor/$projectId")
                }
            )
        }

        composable(
            route = "editor/{projectId}",
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType }
            ),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
            EditorScreen(
                projectId = projectId,
                module = module,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
