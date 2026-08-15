package com.routersync.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.routersync.app.ui.screens.DashboardScreen
import com.routersync.app.ui.screens.HddGalleryScreen
import com.routersync.app.ui.screens.ProfileWizardScreen
import com.routersync.app.ui.screens.SettingsScreen
import com.routersync.app.ui.theme.RouterSyncTheme

/** Durata/curva unica per tutte le transizioni tra schermate, per coerenza in tutta l'app. */
private const val TRANSITION_MS = 260

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* i permessi negati limitano solo le funzioni opzionali collegate (notifiche di avanzamento, riconoscimento Wi-Fi di casa) */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RouterSyncTheme {
                LaunchedEffect(Unit) {
                    val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions += Manifest.permission.POST_NOTIFICATIONS
                    }
                    requestPermissions.launch(permissions.toTypedArray())
                }

                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "dashboard",
                    // Transizione "avanti": la nuova schermata scorre da destra e sfuma in entrata,
                    // quella precedente scorre leggermente a sinistra e sfuma in uscita.
                    enterTransition = {
                        slideInHorizontally(animationSpec = tween(TRANSITION_MS), initialOffsetX = { it / 4 }) +
                            fadeIn(animationSpec = tween(TRANSITION_MS))
                    },
                    exitTransition = {
                        slideOutHorizontally(animationSpec = tween(TRANSITION_MS), targetOffsetX = { -it / 8 }) +
                            fadeOut(animationSpec = tween(TRANSITION_MS))
                    },
                    // Transizione "indietro" (tasto back o popBackStack): l'esatto contrario.
                    popEnterTransition = {
                        slideInHorizontally(animationSpec = tween(TRANSITION_MS), initialOffsetX = { -it / 8 }) +
                            fadeIn(animationSpec = tween(TRANSITION_MS))
                    },
                    popExitTransition = {
                        slideOutHorizontally(animationSpec = tween(TRANSITION_MS), targetOffsetX = { it / 4 }) +
                            fadeOut(animationSpec = tween(TRANSITION_MS))
                    }
                ) {
                    composable("dashboard") {
                        DashboardScreen(
                            onAddProfile = { navController.navigate("wizard") },
                            onEditProfile = { profileId -> navController.navigate("wizard_edit/$profileId") },
                            onBrowseProfile = { profileId -> navController.navigate("browse/$profileId") },
                            onAdminBrowse = { profileId -> navController.navigate("browse_full/$profileId") },
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onClose = { navController.popBackStack() })
                    }
                    composable("wizard") {
                        ProfileWizardScreen(
                            onDone = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "wizard_edit/{profileId}",
                        arguments = listOf(androidx.navigation.navArgument("profileId") { type = androidx.navigation.NavType.LongType })
                    ) { backStackEntry ->
                        val profileId = backStackEntry.arguments?.getLong("profileId") ?: 0L
                        ProfileWizardScreen(
                            editingProfileId = profileId,
                            onDone = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "browse/{profileId}",
                        arguments = listOf(androidx.navigation.navArgument("profileId") { type = androidx.navigation.NavType.LongType })
                    ) { backStackEntry ->
                        val profileId = backStackEntry.arguments?.getLong("profileId") ?: 0L
                        HddGalleryScreen(
                            profileId = profileId,
                            restrictToSyncFolder = true,
                            onClose = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "browse_full/{profileId}",
                        arguments = listOf(androidx.navigation.navArgument("profileId") { type = androidx.navigation.NavType.LongType })
                    ) { backStackEntry ->
                        val profileId = backStackEntry.arguments?.getLong("profileId") ?: 0L
                        HddGalleryScreen(
                            profileId = profileId,
                            restrictToSyncFolder = false,
                            onClose = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
