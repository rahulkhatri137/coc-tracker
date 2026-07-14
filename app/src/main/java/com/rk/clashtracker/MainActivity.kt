package com.rk.clashtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rk.clashtracker.ui.ClashViewModel
import com.rk.clashtracker.ui.screens.AccountsScreen
import com.rk.clashtracker.ui.screens.DashboardScreen
import com.rk.clashtracker.ui.screens.UpgradesScreen
import com.rk.clashtracker.ui.theme.*
import com.rk.clashtracker.util.UpgradeScheduler

sealed class Screen(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    object Upgrades : Screen("upgrades", "Upgrades", Icons.Filled.Build, Icons.Outlined.Build)
    object Accounts : Screen("accounts", "Accounts", Icons.Filled.Group, Icons.Outlined.Group)
}

class MainActivity : ComponentActivity() {
    private val viewModel: ClashViewModel by viewModels { ClashViewModel.Factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup notification channel on app launch
        UpgradeScheduler.createNotificationChannel(this)

        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
                val context = LocalContext.current

                // Handle Notification Permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (!isGranted) {
                            Toast.makeText(
                                context,
                                "Permission denied. Upgrade completion alerts cannot be shown.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    LaunchedEffect(Unit) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = ClashSlate,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .testTag("main_bottom_nav_bar")
                        ) {
                            val items = listOf(Screen.Dashboard, Screen.Upgrades, Screen.Accounts)
                            items.forEach { screen ->
                                val isSelected = currentScreen.route == screen.route
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentScreen = screen },
                                    label = {
                                        Text(
                                            text = screen.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.title,
                                            tint = if (isSelected) ClashGold else TextSecondary
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = ClashSlateLight,
                                        selectedTextColor = ClashGold,
                                        unselectedTextColor = TextSecondary,
                                        selectedIconColor = ClashGold,
                                        unselectedIconColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_item_${screen.route}")
                                )
                            }
                        }
                    },
                    containerColor = ClashObsidian
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            is Screen.Dashboard -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToAccounts = { currentScreen = Screen.Accounts },
                                    onNavigateToUpgrades = { currentScreen = Screen.Upgrades }
                                )
                            }
                            is Screen.Upgrades -> {
                                UpgradesScreen(
                                    viewModel = viewModel
                                )
                            }
                            is Screen.Accounts -> {
                                AccountsScreen(
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
