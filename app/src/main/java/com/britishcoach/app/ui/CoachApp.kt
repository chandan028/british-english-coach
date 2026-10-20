package com.britishcoach.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.britishcoach.app.AppContainer
import com.britishcoach.app.ui.screens.ConversationScreen
import com.britishcoach.app.ui.screens.DailySessionScreen
import com.britishcoach.app.ui.screens.HomeScreen
import com.britishcoach.app.ui.screens.LearnScreen
import com.britishcoach.app.ui.screens.LessonScreen
import com.britishcoach.app.ui.screens.MistakesScreen
import com.britishcoach.app.ui.screens.ProgressScreen
import com.britishcoach.app.ui.screens.SettingsScreen
import com.britishcoach.app.ui.screens.SpeakScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Suppress("DEPRECATION")
private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("learn", "Learn", Icons.Outlined.MenuBook),
    Tab("speak", "Speak", Icons.Outlined.RecordVoiceOver),
    Tab("mistakes", "Mistakes", Icons.Outlined.Book),
    Tab("progress", "Progress", Icons.Outlined.BarChart),
    Tab("settings", "Settings", Icons.Outlined.Settings),
)

/** Routes for screens outside the bottom bar. */
object Routes {
    fun lesson(id: String, item: Int = 0, mistakeId: Long = -1) = "lesson/$id?item=$item&mistake=$mistakeId"
    fun conversation(lessonId: String?) = "conversation?lesson=${lessonId ?: ""}"
    const val DAILY = "daily"
}

@Composable
fun CoachApp(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Clip, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(container, nav) }
            composable("learn") { LearnScreen(container, nav) }
            composable("speak") { SpeakScreen(container, nav) }
            composable("mistakes") { MistakesScreen(container, nav) }
            composable("progress") { ProgressScreen(container) }
            composable("settings") { SettingsScreen(container) }
            composable(
                "lesson/{id}?item={item}&mistake={mistake}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("item") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("mistake") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val args = entry.arguments
                LessonScreen(
                    container = container,
                    lessonId = args?.getString("id").orEmpty(),
                    startItem = args?.getInt("item") ?: 0,
                    mistakeId = args?.getLong("mistake")?.takeIf { it >= 0 },
                    onBack = { nav.popBackStack() },
                    onConversation = { nav.navigate(Routes.conversation(it)) },
                )
            }
            composable(
                "conversation?lesson={lesson}",
                arguments = listOf(navArgument("lesson") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                ConversationScreen(
                    container = container,
                    lessonId = entry.arguments?.getString("lesson")?.takeIf { it.isNotBlank() },
                    onBack = { nav.popBackStack() },
                    onOpenSettings = { nav.navigateTab("settings") },
                )
            }
            composable(Routes.DAILY) { DailySessionScreen(container, nav) }
        }
    }
}

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Builds a ViewModel from the container without a DI framework. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline factoryBlock: () -> VM): VM =
    viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = factoryBlock() as T
        },
    )
