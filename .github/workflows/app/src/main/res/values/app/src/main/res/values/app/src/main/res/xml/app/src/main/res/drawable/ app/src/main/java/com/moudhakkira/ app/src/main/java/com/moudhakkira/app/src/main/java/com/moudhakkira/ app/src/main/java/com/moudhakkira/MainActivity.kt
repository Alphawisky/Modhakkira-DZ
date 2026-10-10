package com.moudhakkira

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController

class MoudhakkiraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val auth = AuthService(this)
        val startDest = if (auth.isLoggedIn) Screen.Home.route else Screen.Login.route

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1565C0),
                    secondary = Color(0xFF2E7D32),
                    background = Color(0xFFF8F9FA)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController, startDestination = startDest)
                }
            }
        }
    }
}
