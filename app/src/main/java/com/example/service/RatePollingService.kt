package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.RateHistoryRecord
import com.example.model.CombinedExchangeRates
import com.example.network.RateFetcher
import com.example.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RatePollingService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RatePollingService = this@RatePollingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        initFromPrefs(applicationContext)
        createForegroundNotificationChannel()
        startForegroundServiceNotification()
        startPollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_TOGGLE_AUTO_REFRESH) {
            val enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
            setAutoPollingEnabled(applicationContext, enabled)
            updateForegroundNotification()
        } else if (action == ACTION_REFRESH_NOW) {
            serviceScope.launch {
                performPoll()
            }
        }
        return START_STICKY
    }

    private fun createForegroundNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "SGD-MYR Background Rate Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        if (!_isPersistentNotificationEnabled.value) {
            val silentNotification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("SGD to MYR 汇率服务")
                .setContentText("后台服务运行中")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
            startForeground(FOREGROUND_NOTIFICATION_ID, silentNotification)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(FOREGROUND_NOTIFICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            return
        }

        val mins = _pollingIntervalMinutes.value
        val text = if (_isDndActiveNow.value) {
            "🌙 免打扰模式生效中，后台自动更新已暂停"
        } else if (_isAutoPollingEnabled.value) {
            "后台每 $mins 分钟自动同步 Wise & CIMB SG 最新汇率"
        } else {
            "后台自动轮询已暂停"
        }

        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("SGD to MYR 汇率监控中")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun updateForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!_isPersistentNotificationEnabled.value) {
            manager.cancel(FOREGROUND_NOTIFICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            return
        }

        val mins = _pollingIntervalMinutes.value
        val text = if (_isDndActiveNow.value) {
            "🌙 免打扰模式生效中，后台自动更新已暂停"
        } else if (_isAutoPollingEnabled.value) {
            "后台每 $mins 分钟自动同步 Wise & CIMB SG 最新汇率"
        } else {
            "后台自动轮询已暂停"
        }

        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("SGD to MYR 汇率监控中")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun startPollingLoop() {
        serviceScope.launch {
            var skipPollOnNextCycle = false
            while (true) {
                val isDndActive = checkAndUpdateDndState()
                if (_isAutoPollingEnabled.value && !isDndActive && !skipPollOnNextCycle) {
                    performPoll()
                }
                skipPollOnNextCycle = false

                val currentResetCount = resetTriggerCount
                val intervalMins = _pollingIntervalMinutes.value.coerceAtLeast(1)
                var totalSeconds = intervalMins * 60

                for (seconds in totalSeconds downTo 1) {
                    if (resetTriggerCount != currentResetCount) {
                        skipPollOnNextCycle = true
                        break
                    }
                    val currentIntervalMins = _pollingIntervalMinutes.value.coerceAtLeast(1)
                    if (currentIntervalMins * 60 < seconds) {
                        break
                    }
                    _secondsUntilNextUpdate.value = seconds
                    delay(1000L)
                    val currentDnd = checkAndUpdateDndState()
                    if (!_isAutoPollingEnabled.value || currentDnd) {
                        _secondsUntilNextUpdate.value = 0
                        break
                    }
                }
            }
        }
    }

    private fun checkAndUpdateDndState(): Boolean {
        val active = isDndActiveNow()
        if (_isDndActiveNow.value != active) {
            _isDndActiveNow.value = active
            updateForegroundNotification()
        }
        return active
    }

    suspend fun performPoll() {
        try {
            _isFetching.value = true
            val rates = RateFetcher.fetchRates()
            _latestRates.value = rates

            // Save history record to Room
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.rateDao()

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

            // Check alerts
            val alerts = dao.getAllAlertsList()
            val updatedAlerts = NotificationHelper.evaluateAndNotifyAlerts(
                applicationContext,
                alerts,
                rates,
                isDndActive = _isDndActiveNow.value
            )
            for (alert in updatedAlerts) {
                dao.insertAlert(alert)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in polling step", e)
        } finally {
            _isFetching.value = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        private const val TAG = "RatePollingService"
        private const val SERVICE_CHANNEL_ID = "rate_polling_service_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 8881

        const val ACTION_TOGGLE_AUTO_REFRESH = "com.example.service.TOGGLE_AUTO_REFRESH"
        const val ACTION_REFRESH_NOW = "com.example.service.REFRESH_NOW"
        const val EXTRA_ENABLED = "extra_enabled"

        private val _latestRates = MutableStateFlow<CombinedExchangeRates?>(null)
        val latestRates: StateFlow<CombinedExchangeRates?> = _latestRates.asStateFlow()

        private val _isAutoPollingEnabled = MutableStateFlow(true)
        val isAutoPollingEnabled: StateFlow<Boolean> = _isAutoPollingEnabled.asStateFlow()

        private val _isFetching = MutableStateFlow(false)
        val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

        private val _secondsUntilNextUpdate = MutableStateFlow(60)
        val secondsUntilNextUpdate: StateFlow<Int> = _secondsUntilNextUpdate.asStateFlow()

        private val _pollingIntervalMinutes = MutableStateFlow(1)
        val pollingIntervalMinutes: StateFlow<Int> = _pollingIntervalMinutes.asStateFlow()

        private val _isDndEnabled = MutableStateFlow(false)
        val isDndEnabled: StateFlow<Boolean> = _isDndEnabled.asStateFlow()

        private val _dndStartHour = MutableStateFlow(23)
        val dndStartHour: StateFlow<Int> = _dndStartHour.asStateFlow()

        private val _dndStartMinute = MutableStateFlow(0)
        val dndStartMinute: StateFlow<Int> = _dndStartMinute.asStateFlow()

        private val _dndEndHour = MutableStateFlow(7)
        val dndEndHour: StateFlow<Int> = _dndEndHour.asStateFlow()

        private val _dndEndMinute = MutableStateFlow(0)
        val dndEndMinute: StateFlow<Int> = _dndEndMinute.asStateFlow()

        private val _isDndActiveNow = MutableStateFlow(false)
        val isDndActiveNow: StateFlow<Boolean> = _isDndActiveNow.asStateFlow()

        private val _isPersistentNotificationEnabled = MutableStateFlow(true)
        val isPersistentNotificationEnabled: StateFlow<Boolean> = _isPersistentNotificationEnabled.asStateFlow()

        var resetTriggerCount = 0
            private set

        fun resetPollingTimer() {
            resetTriggerCount++
            val safeMins = _pollingIntervalMinutes.value.coerceAtLeast(1)
            _secondsUntilNextUpdate.value = safeMins * 60
        }

        fun isDndActiveNow(): Boolean {
            if (!_isDndEnabled.value) return false
            val cal = java.util.Calendar.getInstance()
            val curMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val startMin = _dndStartHour.value * 60 + _dndStartMinute.value
            val endMin = _dndEndHour.value * 60 + _dndEndMinute.value

            return if (startMin == endMin) {
                true
            } else if (startMin < endMin) {
                curMin in startMin until endMin
            } else {
                curMin >= startMin || curMin < endMin
            }
        }

        fun initFromPrefs(context: Context) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            _isAutoPollingEnabled.value = prefs.getBoolean("is_auto_polling_enabled", true)
            val mins = prefs.getInt("polling_interval_minutes", 1).coerceAtLeast(1)
            _pollingIntervalMinutes.value = mins
            _secondsUntilNextUpdate.value = mins * 60

            _isDndEnabled.value = prefs.getBoolean("is_dnd_enabled", false)
            _dndStartHour.value = prefs.getInt("dnd_start_hour", 23)
            _dndStartMinute.value = prefs.getInt("dnd_start_minute", 0)
            _dndEndHour.value = prefs.getInt("dnd_end_hour", 7)
            _dndEndMinute.value = prefs.getInt("dnd_end_minute", 0)
            _isDndActiveNow.value = isDndActiveNow()

            _isPersistentNotificationEnabled.value = prefs.getBoolean("is_persistent_notification_enabled", true)
        }

        fun setAutoPollingEnabled(context: Context, enabled: Boolean) {
            _isAutoPollingEnabled.value = enabled
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("is_auto_polling_enabled", enabled).apply()
        }

        fun setPollingIntervalMinutes(context: Context, minutes: Int) {
            val safeMins = minutes.coerceAtLeast(1)
            _pollingIntervalMinutes.value = safeMins
            _secondsUntilNextUpdate.value = safeMins * 60
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putInt("polling_interval_minutes", safeMins).apply()
        }

        fun setDndEnabled(context: Context, enabled: Boolean) {
            _isDndEnabled.value = enabled
            _isDndActiveNow.value = isDndActiveNow()
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("is_dnd_enabled", enabled).apply()
        }

        fun setDndTimeRange(context: Context, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
            _dndStartHour.value = startHour
            _dndStartMinute.value = startMinute
            _dndEndHour.value = endHour
            _dndEndMinute.value = endMinute
            _isDndActiveNow.value = isDndActiveNow()

            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit()
                .putInt("dnd_start_hour", startHour)
                .putInt("dnd_start_minute", startMinute)
                .putInt("dnd_end_hour", endHour)
                .putInt("dnd_end_minute", endMinute)
                .apply()
        }

        fun setPersistentNotificationEnabled(context: Context, enabled: Boolean) {
            _isPersistentNotificationEnabled.value = enabled
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("is_persistent_notification_enabled", enabled).apply()
        }

        fun updateLatestRates(rates: CombinedExchangeRates) {
            _latestRates.value = rates
        }

        fun setIsFetching(fetching: Boolean) {
            _isFetching.value = fetching
        }
    }
}
