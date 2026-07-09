package com.routersync.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.routersync.app.ui.screens.DashboardScreen
import com.routersync.app.ui.screens.HddGalleryScreen
import com.routersync.app.ui.screens.ProfileWizardScreen
import com.routersync.app.ui.theme.RouterSyncTheme

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
                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(
                            onAddProfile = { navController.navigate("wizard") },
                            onEditProfile = { profileId -> navController.navigate("wizard_edit/$profileId") },
                            onBrowseProfile = { profileId -> navController.navigate("browse/$profileId") },
                            onAdminBrowse = { profileId -> navController.navigate("browse_full/$profileId") }
                        )
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
