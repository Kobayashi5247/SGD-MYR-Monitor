package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RateAlert
import com.example.notification.NotificationHelper
import com.example.ui.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsScreen(
    state: MainUiState,
    onEditAlertClick: (RateAlert) -> Unit,
    onToggleAlertEnabled: (RateAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .testTag("alerts_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "自定义汇率提醒 (上限5个)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (state.isDndActiveNow)
                            "🌙 当前处于免打扰时段，到达提醒通知将自动静音暂停。"
                        else
                            "设置目标汇率后，后台自动轮询检测若达到条件且非免打扰时段，手机将收到推送提醒。",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Notification Button
                    OutlinedButton(
                        onClick = {
                            val dummyAlert = RateAlert(
                                id = 99,
                                targetRate = 3.1950,
                                condition = RateAlert.CONDITION_GREATER_EQUAL,
                                provider = RateAlert.PROVIDER_WISE,
                                label = "测试提醒"
                            )
                            NotificationHelper.sendAlertNotification(
                                context = context,
                                alert = dummyAlert,
                                triggeredProviderName = "Wise (测试)",
                                currentRate = 3.1958,
                                netMyrFor1000Sgd = 3185.93
                            )
                        },
                        modifier = Modifier.testTag("test_notification_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAlert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试手机推送通知", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List of 5 Alert Slots
        items(
            items = state.alerts.sortedBy { it.id },
            key = { it.id }
        ) { alert ->
            AlertSlotCard(
                alert = alert,
                onEditClick = { onEditAlertClick(alert) },
                onToggleEnabled = { onToggleAlertEnabled(alert) }
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun AlertSlotCard(
    alert: RateAlert,
    onEditClick: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alert_slot_${alert.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (alert.isEnabled) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (alert.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${alert.id}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = alert.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (alert.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.testTag("edit_alert_${alert.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Switch(
                        checked = alert.isEnabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.testTag("toggle_alert_${alert.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "触发条件", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val conditionStr = if (alert.condition == RateAlert.CONDITION_GREATER_EQUAL) "≥ 高于或等于" else "≤ 低于或等于"
                    Text(
                        text = "$conditionStr ${String.format("%.4f", alert.targetRate)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "指定监控平台", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val providerText = when (alert.provider) {
                        RateAlert.PROVIDER_WISE -> "Wise"
                        RateAlert.PROVIDER_CIMB_SG -> "CIMB SG"
                        else -> "Wise / CIMB SG"
                    }
                    val providerColor = when (alert.provider) {
                        RateAlert.PROVIDER_WISE -> Color(0xFF00B9FF)
                        RateAlert.PROVIDER_CIMB_SG -> Color(0xFFD32F2F)
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    Text(
                        text = providerText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = providerColor
                    )
                }
            }

            if (alert.lastTriggeredAt > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(alert.lastTriggeredAt))
                Text(
                    text = "上次提醒触发时间: $timeStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
