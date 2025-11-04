package com.retard.swag

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.retard.swag.ui.home.HomeScreen
import com.retard.swag.ui.home.HomeViewModel
import com.retard.swag.ui.servers.ServersScreen
import com.retard.swag.ui.servers.ServersViewModel
import com.retard.swag.ui.settings.SettingsScreen
import com.retard.swag.ui.theme.SwagvpnTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

sealed class Screen(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.screen_home, Icons.Default.Home)
    object Servers : Screen("servers", R.string.screen_servers, Icons.AutoMirrored.Filled.List)
    object Settings : Screen("settings", R.string.screen_settings, Icons.Default.Settings)
}

val items = listOf(
    Screen.Home,
    Screen.Servers,
    Screen.Settings,
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val serversViewModel: ServersViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            homeViewModel.onPermissionResult(true)
        } else {
            homeViewModel.onPermissionResult(false)
        }
    }

    private val configFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            serversViewModel.onConfigFileImported(it)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* We don't need to do anything with the result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            homeViewModel.permissionRequest.collect { intent ->
                vpnPermissionLauncher.launch(intent)
            }
        }

        lifecycleScope.launch {
            serversViewModel.importFileRequest.collect {
                configFileLauncher.launch("*/*")
            }
        }

        // Apply language setting
        lifecycleScope.launch {
            mainViewModel.isDarkThemeEnabled.collect { /* keep subscription alive */ }
        }

        setContent {
            val isDarkTheme by mainViewModel.isDarkThemeEnabled.collectAsState()
            // Observe language from Settings via a lightweight ViewModel accessor
            val settingsVm: com.retard.swag.ui.settings.SettingsViewModel by viewModels()
            val lang by settingsVm.uiState.collectAsState()
            val locales = LocaleListCompat.forLanguageTags(lang.language)
            AppCompatDelegate.setApplicationLocales(locales)
            SwagvpnTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(stringResource(screen.labelRes)) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding),
                        enterTransition = { fadeIn(animationSpec = tween(300)) },
                        exitTransition = { fadeOut(animationSpec = tween(300)) }
                    ) {
                        composable(Screen.Home.route) { HomeScreen(viewModel = homeViewModel) }
                        composable(Screen.Servers.route) { ServersScreen(viewModel = serversViewModel) }
                        composable(Screen.Settings.route) { SettingsScreen() }
                    }
                }
            }
        }
    }
}