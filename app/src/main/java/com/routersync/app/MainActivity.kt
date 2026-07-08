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

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* se negato, le notifiche di avanzamento sync semplicemente non appariranno */ }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* se negato, il riconoscimento del Wi-Fi di casa specifico non sarà disponibile */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RouterSyncTheme {
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }

                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(
                            onAddProfile = { navController.navigate("wizard") },
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
