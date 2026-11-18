package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RateQuote
import com.example.ui.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatesCalculatorScreen(
    state: MainUiState,
    onSgdAmountChange: (String) -> Unit,
    onPresetClick: (Int) -> Unit,
    onRefreshNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .testTag("rates_calculator_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Live Rate Header & Auto-Refresh Bar
        item {
            HeaderStatusCard(
                state = state,
                onRefreshNow = onRefreshNow
            )
        }

        // Custom Calculator Input Card
        item {
            CalculatorInputCard(
                amountText = state.inputSgdAmountText,
                onSgdAmountChange = onSgdAmountChange,
                onPresetClick = onPresetClick
            )
        }

        // Side by Side Comparison (Wise vs CIMB SG)
        item {
            if (state.rates != null) {
                ComparisonCards(
                    wiseQuote = state.rates.wiseQuote,
                    cimbQuote = state.rates.cimbQuote,
                    sgdAmount = state.parsedSgdAmount
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("正在同步最新汇率数据...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Informational Footer Card for Wise & CIMB SG
        item {
            InfoFooterCard(pollingIntervalMinutes = state.pollingIntervalMinutes)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun HeaderStatusCard(
    state: MainUiState,
    onRefreshNow: () -> Unit
) {
    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier
                .background(cardGradient)
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SGD",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Text(
                                text = "MYR",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "新加坡元 兑 马来西亚林吉特 实时比价",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Manual refresh button
                    IconButton(
                        onClick = onRefreshNow,
                        enabled = !state.isRefreshing,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .testTag("refresh_now_button")
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Rate highlights preview
                if (state.rates != null) {
                    val wiseRateStr = String.format(Locale.US, "%.5f", state.rates.wiseQuote.rate).trimEnd('0').trimEnd('.')
                    val cimbRateStr = String.format(Locale.US, "%.4f", state.rates.cimbQuote.rate)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RateChip(
                            label = "Wise 汇率",
                            rateStr = wiseRateStr,
                            color = MaterialTheme.colorScheme.primary
                        )
                        RateChip(
                            label = "CIMB SG 汇率",
                            rateStr = cimbRateStr,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto Refresh Timer Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.isDndActiveNow) {
                                "🌙 免打扰生效中 (后台更新与提醒已暂停)"
                            } else if (state.isAutoPollingEnabled) {
                                "${state.pollingIntervalMinutes}分钟轮询检测 (倒计时 ${state.secondsUntilNextUpdate}s)"
                            } else {
                                "自动轮询已暂停"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.isDndActiveNow) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val formattedTime = if (state.rates != null) {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.rates.timestamp))
                    } else "--:--:--"

                    Text(
                        text = "更新时间: $formattedTime",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.isAutoPollingEnabled && !state.isDndActiveNow) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val maxSecs = (state.pollingIntervalMinutes * 60).coerceAtLeast(1)
                    val progress = (maxSecs - state.secondsUntilNextUpdate).coerceIn(0, maxSecs) / maxSecs.toFloat()
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun RateChip(
    label: String,
    rateStr: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "1 SGD = ",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = rateStr,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = " MYR",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CalculatorInputCard(
    amountText: String,
    onSgdAmountChange: (String) -> Unit,
    onPresetClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "汇率换算计算器 (SGD 换 MYR)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = onSgdAmountChange,
                label = { Text("输入换算新币金额 (SGD)") },
                prefix = { Text("S$ ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                suffix = { Text("SGD", fontWeight = FontWeight.SemiBold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sgd_amount_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick preset chips
            Text(
                text = "快捷金额:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(500, 1000, 2000, 5000).forEach { amount ->
                    val isSelected = amountText == amount.toString()
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPresetClick(amount) }
                            .testTag("preset_$amount"),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "S$$amount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonCards(
    wiseQuote: RateQuote,
    cimbQuote: RateQuote,
    sgdAmount: Double
) {
    val wiseNet = wiseQuote.calculateNetReceived(sgdAmount)
    val cimbNet = cimbQuote.calculateNetReceived(sgdAmount)

    val isWiseBetter = wiseNet >= cimbNet
    val difference = Math.abs(wiseNet - cimbNet)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Winner Banner Badge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    val winnerName = if (isWiseBetter) "Wise" else "CIMB SG"
                    val formattedDiff = String.format("%,.2f", difference)
                    Text(
                        text = "更划算渠道: $winnerName",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "换算 $sgdAmount SGD，多拿约 MYR $formattedDiff 马币",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Wise Detail Card
        QuoteProviderCard(
            quote = wiseQuote,
            sgdAmount = sgdAmount,
            netMyr = wiseNet,
            isWinner = isWiseBetter,
            feeDetailText = "Wise 扣除透明手续费 (~3.08 SGD / 1,000 SGD) 后按中间汇率兑换"
        )

        // CIMB SG Detail Card
        QuoteProviderCard(
            quote = cimbQuote,
            sgdAmount = sgdAmount,
            netMyr = cimbNet,
            isWinner = !isWiseBetter,
            feeDetailText = "CIMB SG 直接按银行电汇/汇款牌价兑换"
        )
    }
}

@Composable
fun QuoteProviderCard(
    quote: RateQuote,
    sgdAmount: Double,
    netMyr: Double,
    isWinner: Boolean,
    feeDetailText: String
) {
    val cardBorder = if (isWinner) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${quote.providerCode}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isWinner) 4.dp else 1.dp)
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (quote.providerCode == "WISE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = quote.providerName,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (isWinner) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "最划算",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "基准汇率 (1 SGD)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val formattedRateStr = String.format(Locale.US, "%.5f", quote.rate).trimEnd('0').trimEnd('.')
                    Text(
                        text = "$formattedRateStr MYR",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Calculated Actual Received Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "实际到账总额 (MYR)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%,.2f", netMyr) + " MYR",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Fee badge
                val totalFeeSgd = quote.calculateTotalFeeSgd(sgdAmount)
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "预估手续费", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (totalFeeSgd > 0) String.format("%.2f SGD", totalFeeSgd) else "免手续费",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (totalFeeSgd > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = feeDetailText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InfoFooterCard(pollingIntervalMinutes: Int = 1) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "提示: Wise 提取的是实际扣除转账手续费后的到账马币金额；CIMB SG 为官方汇款电汇参考牌价。后台每 ${pollingIntervalMinutes} 分钟自动读取最新数据。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// BorderStroke helper
fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)
