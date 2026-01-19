package com.lomo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.lomo.app.navigation.LomoNavHost
import com.lomo.app.util.ProvideHapticFeedback
import com.lomo.ui.theme.LomoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import com.lomo.app.feature.main.MainViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.appcompat.app.AppCompatActivity

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var dataStore: com.lomo.data.local.datastore.LomoDataStore
    private val viewModel: MainViewModel by viewModels()

    companion object {
        const val ACTION_NEW_MEMO = "com.lomo.app.ACTION_NEW_MEMO"
        const val ACTION_OPEN_MEMO = "com.lomo.app.ACTION_OPEN_MEMO"
        const val EXTRA_MEMO_ID = "memo_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Keep the splash screen on-screen until the UI state is loaded
        splashScreen.setKeepOnScreenCondition {
            viewModel.uiState.value is MainViewModel.MainScreenState.Loading
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            LomoTheme(themeMode = themeMode) {
                ProvideHapticFeedback(dataStore) { hapticEnabled ->
                    com.lomo.ui.util.ProvideAppHapticFeedback(enabled = hapticEnabled) {
                        LomoApp(
                            initialAction = intent?.action
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LomoApp(
    initialAction: String? = null
) { 
    // Handle widget actions
    LaunchedEffect(initialAction) {
        when (initialAction) {
            MainActivity.ACTION_NEW_MEMO -> {
                // TODO: Trigger new memo creation via navigation or event
            }
            MainActivity.ACTION_OPEN_MEMO -> {
                // TODO: Navigate to memo detail/edit
            }
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val navController = rememberNavController()
        LomoNavHost(navController = navController)
    }
}

