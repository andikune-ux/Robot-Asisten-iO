package dev.andikune.robotasisten.io.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.ui.screens.AboutScreen
import dev.andikune.robotasisten.io.ui.screens.CloudModeScreen
import dev.andikune.robotasisten.io.ui.screens.DebugScreen
import dev.andikune.robotasisten.io.ui.screens.DiagnosticScreen
import dev.andikune.robotasisten.io.ui.screens.HistoryScreen
import dev.andikune.robotasisten.io.ui.screens.HomeScreen
import dev.andikune.robotasisten.io.ui.screens.LibraryModeScreen
import dev.andikune.robotasisten.io.ui.screens.LicenseScreen
import dev.andikune.robotasisten.io.ui.screens.OfflineModeScreen
import dev.andikune.robotasisten.io.ui.screens.OnboardingScreen
import dev.andikune.robotasisten.io.ui.screens.PermissionChecklistScreen
import dev.andikune.robotasisten.io.ui.screens.PrivacyPolicyScreen
import dev.andikune.robotasisten.io.ui.screens.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
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
    val context = LocalContext.current
    val settingsRepo = AppModule.provideSettingsRepository(context)
    val isOnboarded by settingsRepo.isOnboarded.collectAsState()

    val navController = rememberNavController()
    val startDestination = if (isOnboarded) Routes.HOME else Routes.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    settingsRepo.setOnboarded(true)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToLibrary = { navController.navigate(Routes.LIBRARY) },
                onNavigateToOffline = { navController.navigate(Routes.OFFLINE) },
                onNavigateToCloud = { navController.navigate(Routes.CLOUD) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToHelp = { navController.navigate(Routes.ABOUT) },
                onNavigateToPermissions = { navController.navigate(Routes.PERMISSIONS) }
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
