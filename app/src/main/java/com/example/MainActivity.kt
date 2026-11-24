package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.JarvisFirestoreRepository
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AssistantViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.screens.WorkspaceViewModel
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JarvisAiTheme
import com.example.ui.theme.ObsidianSpace
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_ASSISTANT = "assistant"
const val ROUTE_IMAGE_STUDIO = "image_studio"
const val ROUTE_VIDEO_STUDIO = "video_studio"
const val ROUTE_WORKSPACE = "workspace"

private data class JarvisDest(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val jarvisDestinations = listOf(
    JarvisDest(ROUTE_ASSISTANT, "Assistant", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    JarvisDest(ROUTE_IMAGE_STUDIO, "Images", Icons.Filled.Image, Icons.Outlined.Image),
    JarvisDest(ROUTE_VIDEO_STUDIO, "Video", Icons.Filled.MovieCreation, Icons.Outlined.MovieCreation),
    JarvisDest(ROUTE_WORKSPACE, "Workspace", Icons.Filled.FolderSpecial, Icons.Outlined.FolderSpecial)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            JarvisAiTheme {
                JarvisAppRoot()
            }
        }
    }
}

@Composable
fun JarvisAppRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            JarvisMainShell(currentUserId = user.uid)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun JarvisMainShell(currentUserId: String) {
    val appContext = LocalContext.current.applicationContext as Application
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_ASSISTANT

    val assistantViewModel: AssistantViewModel = viewModel(
        key = "assistant_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] ?: appContext
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                AssistantViewModel(
                    repository = JarvisFirestoreRepository(db),
                    researchService = com.example.data.repository.InternetResearchService(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val workspaceViewModel: WorkspaceViewModel = viewModel(
        key = "workspace_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] ?: appContext
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                WorkspaceViewModel(
                    repository = JarvisFirestoreRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianSpace,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = DeepHudSurface) {
                        jarvisDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = DeepHudSurface) {
                        jarvisDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_ASSISTANT,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_ASSISTANT) {
                        AssistantScreen(viewModel = assistantViewModel)
                    }
                    composable(ROUTE_IMAGE_STUDIO) {
                        ImageStudioScreen()
                    }
                    composable(ROUTE_VIDEO_STUDIO) {
                        VideoStudioScreen()
                    }
                    composable(ROUTE_WORKSPACE) {
                        WorkspaceScreen(
                            viewModel = workspaceViewModel,
                            onSignedOut = {}
                        )
                    }
                }
            }
        }
    }
}
