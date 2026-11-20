package io.github.smicca.lector.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.smicca.lector.feature.main.presentation.MainScreen
import io.github.smicca.lector.feature.main.presentation.MainViewModel
import io.github.smicca.lector.feature.voices.presentation.VoicesScreen
import io.github.smicca.lector.feature.voices.presentation.VoicesViewModel
import io.github.smicca.lector.ui.components.DrawerSection
import io.github.smicca.lector.ui.components.LectorDrawerSheet
import io.github.smicca.lector.ui.theme.LectorTheme
import kotlinx.coroutines.launch

enum class AppDestination {
    LISTEN,
    VOICES
}

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val voicesViewModel: VoicesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LectorTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var currentDestination by remember { mutableStateOf(AppDestination.LISTEN) }
                var activeDrawerSection by remember { mutableStateOf(DrawerSection.ROOT) }

                val mainUiState by mainViewModel.uiState.collectAsState()

                // Parent-first Back navigation contract:
                // 1. If drawer is open: navigate nested section back to root, or close drawer.
                // 2. If on dedicated screen (Voices): return to Listen home.
                // 3. Otherwise: let system handle Back (exits app).
                BackHandler(enabled = drawerState.isOpen) {
                    if (activeDrawerSection != DrawerSection.ROOT) {
                        activeDrawerSection = DrawerSection.ROOT
                    } else {
                        scope.launch { drawerState.close() }
                    }
                }

                BackHandler(enabled = !drawerState.isOpen && currentDestination == AppDestination.VOICES) {
                    currentDestination = AppDestination.LISTEN
                    mainViewModel.refresh()
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentDestination == AppDestination.LISTEN,
                    drawerContent = {
                        LectorDrawerSheet(
                            activeSection = activeDrawerSection,
                            onSectionChange = { section -> activeDrawerSection = section },
                            onClose = {
                                scope.launch {
                                    drawerState.close()
                                    activeDrawerSection = DrawerSection.ROOT
                                }
                            },
                            onNavigateToVoices = {
                                voicesViewModel.refresh()
                                currentDestination = AppDestination.VOICES
                            },
                            selectedVoice = mainUiState.selectedVoice,
                            availableVoices = mainUiState.availableVoices,
                            onSelectVoice = { voice ->
                                mainViewModel.setVoice(voice)
                            },
                            simulateFirstRun = mainUiState.simulateFirstRun,
                            onToggleSimulateFirstRun = { simulate ->
                                mainViewModel.setSimulateFirstRun(simulate)
                            },
                            onResetSampleText = {
                                mainViewModel.resetSampleText()
                            }
                        )
                    }
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        when (currentDestination) {
                            AppDestination.LISTEN -> {
                                MainScreen(
                                    viewModel = mainViewModel,
                                    onOpenDrawer = {
                                        activeDrawerSection = DrawerSection.ROOT
                                        scope.launch { drawerState.open() }
                                    },
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
                                        currentDestination = AppDestination.LISTEN
                                        mainViewModel.refresh()
                                    }
                                )
                            }
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
