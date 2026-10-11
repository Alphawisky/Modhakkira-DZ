package com.moudhakkira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation.compose.rememberNavController

private val MoudhakkiraColors = lightColorScheme(
    primary = Color(0xFF176B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EEE5),
    onPrimaryContainer = Color(0xFF103D34),
    secondary = Color(0xFFB8793E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8CF),
    background = Color(0xFFF5F7F4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EFEB),
    onSurface = Color(0xFF19231F),
    onSurfaceVariant = Color(0xFF53615A),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = AuthService(this)
        val startDestination = if (auth.isLoggedIn) Screen.Home.route else Screen.Login.route

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = MoudhakkiraColors) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        AppNavigation(
                            navController = navController,
                            startDestination = startDestination
                        )
                    }
                }
            }
        }
    }
}
