package com.example.repository

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.RateAlert
import com.example.data.RateHistoryRecord
import com.example.model.CombinedExchangeRates
import com.example.network.RateFetcher
import com.example.notification.NotificationHelper
import com.example.service.RatePollingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class RateRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.rateDao()

    init {
        RatePollingService.initFromPrefs(context)
    }

    val alertsFlow: Flow<List<RateAlert>> = dao.getAllAlerts()
    val historyFlow: Flow<List<RateHistoryRecord>> = dao.getRecentHistory(100)

    val latestRates: StateFlow<CombinedExchangeRates?> = RatePollingService.latestRates
    val isAutoPollingEnabled: StateFlow<Boolean> = RatePollingService.isAutoPollingEnabled
    val isFetching: StateFlow<Boolean> = RatePollingService.isFetching
    val secondsUntilNextUpdate: StateFlow<Int> = RatePollingService.secondsUntilNextUpdate
    val pollingIntervalMinutes: StateFlow<Int> = RatePollingService.pollingIntervalMinutes

    val isDndEnabled: StateFlow<Boolean> = RatePollingService.isDndEnabled
    val dndStartHour: StateFlow<Int> = RatePollingService.dndStartHour
    val dndStartMinute: StateFlow<Int> = RatePollingService.dndStartMinute
    val dndEndHour: StateFlow<Int> = RatePollingService.dndEndHour
    val dndEndMinute: StateFlow<Int> = RatePollingService.dndEndMinute
    val isDndActiveNow: StateFlow<Boolean> = RatePollingService.isDndActiveNow
    val isPersistentNotificationEnabled: StateFlow<Boolean> = RatePollingService.isPersistentNotificationEnabled

    suspend fun saveAlert(alert: RateAlert) = withContext(Dispatchers.IO) {
        dao.insertAlert(alert)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearHistory()
    }

    fun setAutoPollingEnabled(enabled: Boolean) {
        RatePollingService.setAutoPollingEnabled(context, enabled)
    }

    fun setPollingIntervalMinutes(minutes: Int) {
        RatePollingService.setPollingIntervalMinutes(context, minutes)
    }

    fun setDndEnabled(enabled: Boolean) {
        RatePollingService.setDndEnabled(context, enabled)
    }

    fun setDndTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        RatePollingService.setDndTimeRange(context, startHour, startMinute, endHour, endMinute)
    }

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        RatePollingService.setPersistentNotificationEnabled(context, enabled)
    }

    suspend fun refreshRatesNow(): CombinedExchangeRates = withContext(Dispatchers.IO) {
        RatePollingService.setIsFetching(true)
        try {
            val rates = RateFetcher.fetchRates()
            RatePollingService.updateLatestRates(rates)
            RatePollingService.resetPollingTimer()
            val historyRecord = RateHistoryRecord(
                timestamp = rates.timestamp,
                wiseRate = rates.wiseQuote.rate,
                wiseFeeFor1000 = rates.wiseQuote.calculateTotalFeeSgd(1000.0),
                wiseNetReceivedFor1000 = rates.wiseQuote.calculateNetReceived(1000.0),
                cimbRate = rates.cimbQuote.rate,
                cimbFeeFor1000 = rates.cimbQuote.calculateTotalFeeSgd(1000.0),
                cimbNetReceivedFor1000 = rates.cimbQuote.calculateNetReceived(1000.0),
                source = if (rates.wiseQuote.isLiveWeb) "LIVE" else "SIMULATED_LIVE"
            )
            dao.insertHistory(historyRecord)

            val alerts = dao.getAllAlertsList()
            val updatedAlerts = NotificationHelper.evaluateAndNotifyAlerts(context, alerts, rates)
            for (alert in updatedAlerts) {
                dao.insertAlert(alert)
            }
            rates
        } finally {
            RatePollingService.setIsFetching(false)
        }
    }
}
