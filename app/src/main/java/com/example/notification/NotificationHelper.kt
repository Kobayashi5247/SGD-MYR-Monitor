package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.RateAlert
import com.example.model.CombinedExchangeRates

object NotificationHelper {
    private const val CHANNEL_ID = "rate_alerts_channel"
    private const val CHANNEL_NAME = "汇率到达提醒 Notification Channel"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "当新币兑马币到达您设置的目标汇率时触发系统通知"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendAlertNotification(
        context: Context,
        alert: RateAlert,
        triggeredProviderName: String,
        currentRate: Double,
        netMyrFor1000Sgd: Double
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val conditionSymbol = if (alert.condition == RateAlert.CONDITION_GREATER_EQUAL) "≥" else "≤"
        val formattedRate = String.format("%.4f", currentRate)
        val formattedTarget = String.format("%.4f", alert.targetRate)
        val formattedNetMyr = String.format("%,.2f", netMyrFor1000Sgd)

        val title = "🚨 汇率提醒触发: $triggeredProviderName 到达 $formattedRate!"
        val contentText = "$triggeredProviderName 汇率已达 $formattedRate MYR (目标 $conditionSymbol $formattedTarget)\n换算 1,000 SGD 实际可得: MYR $formattedNetMyr"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(alert.id, builder.build())
    }

    /**
     * Checks all enabled alerts against current Wise & CIMB SG rates, and triggers notifications if matched.
     * Returns the list of updated alerts that were newly triggered.
     */
    fun evaluateAndNotifyAlerts(
        context: Context,
        alerts: List<RateAlert>,
        rates: CombinedExchangeRates,
        isDndActive: Boolean = false
    ): List<RateAlert> {
        if (isDndActive) {
            // Suppress notifications during Do Not Disturb mode
            return emptyList()
        }

        val updatedAlerts = mutableListOf<RateAlert>()
        val now = System.currentTimeMillis()
        // Minimum 5 minutes cooldowm between repeating the exact same notification for the same alert
        val cooldownMs = 5 * 60 * 1000L

        for (alert in alerts) {
            if (!alert.isEnabled) continue
            if (now - alert.lastTriggeredAt < cooldownMs) continue

            val target = alert.targetRate
            var triggered = false
            var triggeredProviderName = ""
            var triggeredRate = 0.0
            var netMyr = 0.0

            // Check Wise if provider is WISE or ANY
            if (alert.provider == RateAlert.PROVIDER_WISE || alert.provider == RateAlert.PROVIDER_ANY) {
                val wiseRate = rates.wiseQuote.rate
                val isMatch = if (alert.condition == RateAlert.CONDITION_GREATER_EQUAL) wiseRate >= target else wiseRate <= target
                if (isMatch) {
                    triggered = true
                    triggeredProviderName = "Wise"
                    triggeredRate = wiseRate
                    netMyr = rates.wiseQuote.calculateNetReceived(1000.0)
                }
            }

            // Check CIMB SG if not already triggered and provider is CIMB_SG or ANY
            if (!triggered && (alert.provider == RateAlert.PROVIDER_CIMB_SG || alert.provider == RateAlert.PROVIDER_ANY)) {
                val cimbRate = rates.cimbQuote.rate
                val isMatch = if (alert.condition == RateAlert.CONDITION_GREATER_EQUAL) cimbRate >= target else cimbRate <= target
                if (isMatch) {
                    triggered = true
                    triggeredProviderName = "CIMB SG"
                    triggeredRate = cimbRate
                    netMyr = rates.cimbQuote.calculateNetReceived(1000.0)
                }
            }

            if (triggered) {
                sendAlertNotification(context, alert, triggeredProviderName, triggeredRate, netMyr)
                updatedAlerts.add(alert.copy(lastTriggeredAt = now))
            }
        }
        return updatedAlerts
    }
}
