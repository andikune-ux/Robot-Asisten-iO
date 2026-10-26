package dev.andikune.robotasisten.io.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.andikune.robotasisten.io.ui.screens.AboutScreen
import dev.andikune.robotasisten.io.ui.screens.CloudModeScreen
import dev.andikune.robotasisten.io.ui.screens.DebugScreen
import dev.andikune.robotasisten.io.ui.screens.DiagnosticScreen
import dev.andikune.robotasisten.io.ui.screens.HistoryScreen
import dev.andikune.robotasisten.io.ui.screens.HomeScreen
import dev.andikune.robotasisten.io.ui.screens.LibraryModeScreen
import dev.andikune.robotasisten.io.ui.screens.LicenseScreen
import dev.andikune.robotasisten.io.ui.screens.OfflineModeScreen
import dev.andikune.robotasisten.io.ui.screens.PermissionChecklistScreen
import dev.andikune.robotasisten.io.ui.screens.PrivacyPolicyScreen
import dev.andikune.robotasisten.io.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val OFFLINE = "offline"
    const val CLOUD = "cloud"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val PERMISSIONS = "permissions"
    const val DIAGNOSTIC = "diagnostic"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val LICENSE = "license"
    const val DEBUG = "debug"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateLibrary = { navController.navigate(Routes.LIBRARY) },
                onNavigateOffline = { navController.navigate(Routes.OFFLINE) },
                onNavigateCloud = { navController.navigate(Routes.CLOUD) },
                onNavigatePermissions = { navController.navigate(Routes.PERMISSIONS) }
            )
        }

        composable(Routes.LIBRARY) {
            LibraryModeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.OFFLINE) {
            OfflineModeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CLOUD) {
            CloudModeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigatePermissions = { navController.navigate(Routes.PERMISSIONS) },
                onNavigateDiagnostic = { navController.navigate(Routes.DIAGNOSTIC) },
                onNavigateAbout = { navController.navigate(Routes.ABOUT) },
                onNavigatePrivacy = { navController.navigate(Routes.PRIVACY) },
                onNavigateLicense = { navController.navigate(Routes.LICENSE) },
                onNavigateDebug = { navController.navigate(Routes.DEBUG) }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PERMISSIONS) {
            PermissionChecklistScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DIAGNOSTIC) {
            DiagnosticScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.LICENSE) {
            LicenseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DEBUG) {
            DebugScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
