package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.editor.EditorScreen
import com.example.ui.main.MainMenuScreen
import com.example.ui.theme.FusionCutTheme
import com.example.ui.theme.BackgroundDark
import com.example.util.AndroidServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidServices.context = applicationContext
        com.example.data.repository.initRepository(this)
        val module = com.example.di.createModule(this)
        enableEdgeToEdge()
        setContent {
            App(module)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.example.engine.BitmapCache.trim(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        com.example.engine.BitmapCache.clear()
    }
}
