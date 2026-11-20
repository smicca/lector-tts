package io.github.smicca.lector.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import io.github.smicca.lector.feature.main.presentation.MainScreen
import io.github.smicca.lector.feature.main.presentation.MainViewModel
import io.github.smicca.lector.feature.voices.presentation.VoicesScreen
import io.github.smicca.lector.feature.voices.presentation.VoicesViewModel

enum class AppDestination {
    MAIN,
    VOICES
}

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val voicesViewModel: VoicesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var currentDestination by remember { mutableStateOf(AppDestination.MAIN) }

                    BackHandler(enabled = currentDestination == AppDestination.VOICES) {
                        currentDestination = AppDestination.MAIN
                        mainViewModel.refresh()
                    }

                    when (currentDestination) {
                        AppDestination.MAIN -> {
                            MainScreen(
                                viewModel = mainViewModel,
                                onNavigateToVoices = {
                                    voicesViewModel.refresh()
                                    currentDestination = AppDestination.VOICES
                                }
                            )
                        }
                        AppDestination.VOICES -> {
                            VoicesScreen(
                                viewModel = voicesViewModel,
                                onBack = {
                                    currentDestination = AppDestination.MAIN
                                    mainViewModel.refresh()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refresh()
        voicesViewModel.refresh()
    }
}
