package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.RatePollingService
import com.example.ui.screens.AlertDialogModal
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.RatesCalculatorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Wrap entire content in selected theme
    MyApplicationTheme(darkTheme = state.isDarkMode) {
        // Start background service
        LaunchedEffect(Unit) {
            val serviceIntent = Intent(context, RatePollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        // Permission launcher for Notifications (Android 13+)
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "通知权限已授予，可在后台接收到达提醒", Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Handle toast messages
        LaunchedEffect(state.toastMessage) {
            state.toastMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                viewModel.clearToast()
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SGD 兑 MYR 汇率比价",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (state.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "切换主题模式",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = state.activeTab == 0,
                        onClick = { viewModel.setActiveTab(0) },
                        icon = { Icon(Icons.Default.CurrencyExchange, contentDescription = null) },
                        label = { Text("比价器") },
                        modifier = Modifier.testTag("tab_calculator")
                    )

                    NavigationBarItem(
                        selected = state.activeTab == 1,
                        onClick = { viewModel.setActiveTab(1) },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        label = { Text("汇率提醒") },
                        modifier = Modifier.testTag("tab_alerts")
                    )

                    NavigationBarItem(
                        selected = state.activeTab == 2,
                        onClick = { viewModel.setActiveTab(2) },
                        icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                        label = { Text("历史波动") },
                        modifier = Modifier.testTag("tab_history")
                    )

                    NavigationBarItem(
                        selected = state.activeTab == 3,
                        onClick = { viewModel.setActiveTab(3) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("设置") },
                        modifier = Modifier.testTag("tab_settings")
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (state.activeTab) {
                    0 -> RatesCalculatorScreen(
                        state = state,
                        onSgdAmountChange = viewModel::onSgdAmountChange,
                        onPresetClick = viewModel::setSgdPreset,
                        onRefreshNow = viewModel::refreshNow
                    )

                    1 -> AlertsScreen(
                        state = state,
                        onEditAlertClick = viewModel::onEditAlertClick,
                        onToggleAlertEnabled = viewModel::toggleAlertEnabled
                    )

                    2 -> HistoryScreen(
                        state = state,
                        onClearHistory = viewModel::clearHistory
                    )

                    3 -> SettingsScreen(
                        state = state,
                        onToggleAutoPolling = viewModel::toggleAutoPolling,
                        onSelectPollingInterval = viewModel::setPollingIntervalMinutes,
                        onToggleDndEnabled = viewModel::toggleDndEnabled,
                        onSetDndTimeRange = viewModel::setDndTimeRange,
                        onTogglePersistentNotification = viewModel::togglePersistentNotification,
                        onToggleDarkMode = viewModel::toggleDarkMode
                    )
                }

                // Alert Edit Modal Dialog
                state.editingAlert?.let { alert ->
                    AlertDialogModal(
                        alert = alert,
                        onDismiss = viewModel::onDismissAlertModal,
                        onSave = viewModel::onSaveAlert
                    )
                }
            }
        }
    }
}
