package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RateAlert

@Composable
fun AlertDialogModal(
    alert: RateAlert,
    onDismiss: () -> Unit,
    onSave: (id: Int, targetRate: Double, condition: String, provider: String, isEnabled: Boolean, label: String) -> Unit
) {
    var targetRateText by remember { mutableStateOf(alert.targetRate.toString()) }
    var condition by remember { mutableStateOf(alert.condition) }
    var provider by remember { mutableStateOf(alert.provider) }
    var isEnabled by remember { mutableStateOf(alert.isEnabled) }
    var label by remember { mutableStateOf(alert.label) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "设置汇率提醒 #${alert.id}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("提醒备注 / 名称") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alert_label_input")
                )

                // Target Rate Input
                OutlinedTextField(
                    value = targetRateText,
                    onValueChange = { targetRateText = it },
                    label = { Text("目标汇率 (1 SGD = MYR)") },
                    placeholder = { Text("例: 3.2850") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alert_target_rate_input")
                )

                // Condition Choice
                Text(
                    text = "触发条件:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { condition = RateAlert.CONDITION_GREATER_EQUAL }
                    ) {
                        RadioButton(
                            selected = condition == RateAlert.CONDITION_GREATER_EQUAL,
                            onClick = { condition = RateAlert.CONDITION_GREATER_EQUAL }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("高于或等于 (≥)")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { condition = RateAlert.CONDITION_LESS_EQUAL }
                    ) {
                        RadioButton(
                            selected = condition == RateAlert.CONDITION_LESS_EQUAL,
                            onClick = { condition = RateAlert.CONDITION_LESS_EQUAL }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("低于或等于 (≤)")
                    }
                }

                // Provider Choice
                Text(
                    text = "指定监控平台 (Wise / CIMB):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { provider = RateAlert.PROVIDER_ANY }
                    ) {
                        RadioButton(
                            selected = provider == RateAlert.PROVIDER_ANY,
                            onClick = { provider = RateAlert.PROVIDER_ANY }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("任意来源 (Wise 或 CIMB SG 满足即提醒)")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { provider = RateAlert.PROVIDER_WISE }
                    ) {
                        RadioButton(
                            selected = provider == RateAlert.PROVIDER_WISE,
                            onClick = { provider = RateAlert.PROVIDER_WISE }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("仅限 Wise 汇率")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { provider = RateAlert.PROVIDER_CIMB_SG }
                    ) {
                        RadioButton(
                            selected = provider == RateAlert.PROVIDER_CIMB_SG,
                            onClick = { provider = RateAlert.PROVIDER_CIMB_SG }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("仅限 CIMB SG 汇率")
                    }
                }

                // Enabled Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "启用此提醒", fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.testTag("alert_enable_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rateDouble = targetRateText.toDoubleOrNull() ?: 3.2500
                    onSave(alert.id, rateDouble, condition, provider, isEnabled, label)
                },
                modifier = Modifier.testTag("save_alert_button")
            ) {
                Text("保存设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
