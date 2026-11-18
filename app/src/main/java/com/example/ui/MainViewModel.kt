package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RateAlert
import com.example.data.RateHistoryRecord
import com.example.model.CombinedExchangeRates
import com.example.repository.RateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val rates: CombinedExchangeRates? = null,
    val inputSgdAmountText: String = "1000",
    val parsedSgdAmount: Double = 1000.0,
    val alerts: List<RateAlert> = emptyList(),
    val history: List<RateHistoryRecord> = emptyList(),
    val isAutoPollingEnabled: Boolean = true,
    val isRefreshing: Boolean = false,
    val secondsUntilNextUpdate: Int = 60,
    val pollingIntervalMinutes: Int = 1,
    val isDndEnabled: Boolean = false,
    val dndStartHour: Int = 23,
    val dndStartMinute: Int = 0,
    val dndEndHour: Int = 7,
    val dndEndMinute: Int = 0,
    val isDndActiveNow: Boolean = false,
    val isPersistentNotificationEnabled: Boolean = true,
    val activeTab: Int = 0, // 0: Rates & Calculator, 1: Alerts, 2: History, 3: Settings
    val editingAlert: RateAlert? = null,
    val toastMessage: String? = null,
    val isDarkMode: Boolean = false // false = Light, true = Dark
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RateRepository(application.applicationContext)

    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _inputSgdAmountText = MutableStateFlow("1000")
    private val _activeTab = MutableStateFlow(0)
    private val _editingAlert = MutableStateFlow<RateAlert?>(null)
    private val _toastMessage = MutableStateFlow<String?>(null)
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("is_dark_mode", false))

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            repository.latestRates,
            _inputSgdAmountText,
            repository.alertsFlow,
            repository.historyFlow,
            repository.isAutoPollingEnabled,
            repository.isFetching,
            repository.secondsUntilNextUpdate
        ) { array -> array },
        combine(
            repository.pollingIntervalMinutes,
            _activeTab,
            _editingAlert,
            _toastMessage,
            _isDarkMode,
            repository.isDndEnabled,
            repository.dndStartHour
        ) { array -> array },
        combine(
            repository.dndStartMinute,
            repository.dndEndHour,
            repository.dndEndMinute,
            repository.isDndActiveNow,
            repository.isPersistentNotificationEnabled
        ) { array -> array }
    ) { group1, group2, group3 ->
        val rates = group1[0] as CombinedExchangeRates?
        val sgdText = group1[1] as String
        @Suppress("UNCHECKED_CAST")
        val alerts = group1[2] as List<RateAlert>
        @Suppress("UNCHECKED_CAST")
        val history = group1[3] as List<RateHistoryRecord>
        val autoPolling = group1[4] as Boolean
        val isFetching = group1[5] as Boolean
        val seconds = group1[6] as Int

        val intervalMins = group2[0] as Int
        val tab = group2[1] as Int
        val editingAlert = group2[2] as RateAlert?
        val toast = group2[3] as String?
        val isDark = group2[4] as Boolean
        val dndEnabled = group2[5] as Boolean
        val dndStartH = group2[6] as Int

        val dndStartM = group3[0] as Int
        val dndEndH = group3[1] as Int
        val dndEndM = group3[2] as Int
        val isDndActive = group3[3] as Boolean
        val isPersistentNotif = group3[4] as Boolean

        val parsedAmount = sgdText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1000.0

        MainUiState(
            rates = rates,
            inputSgdAmountText = sgdText,
            parsedSgdAmount = parsedAmount,
            alerts = alerts,
            history = history,
            isAutoPollingEnabled = autoPolling,
            isRefreshing = isFetching,
            secondsUntilNextUpdate = seconds,
            pollingIntervalMinutes = intervalMins,
            isDndEnabled = dndEnabled,
            dndStartHour = dndStartH,
            dndStartMinute = dndStartM,
            dndEndHour = dndEndH,
            dndEndMinute = dndEndM,
            isDndActiveNow = isDndActive,
            isPersistentNotificationEnabled = isPersistentNotif,
            activeTab = tab,
            editingAlert = editingAlert,
            toastMessage = toast,
            isDarkMode = isDark
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        // Initial rate fetch
        refreshNow()
    }

    fun onSgdAmountChange(newText: String) {
        // Only allow numbers and decimal point
        if (newText.isEmpty() || newText.matches(Regex("^\\d*\\.?\\d*$"))) {
            _inputSgdAmountText.value = newText
        }
    }

    fun setSgdPreset(amount: Int) {
        _inputSgdAmountText.value = amount.toString()
    }

    fun setActiveTab(tabIndex: Int) {
        _activeTab.value = tabIndex
    }

    fun toggleAutoPolling(enabled: Boolean) {
        repository.setAutoPollingEnabled(enabled)
        _toastMessage.value = if (enabled) "已开启后台自动轮询" else "已关闭后台自动刷新"
    }

    fun setPollingIntervalMinutes(minutes: Int) {
        repository.setPollingIntervalMinutes(minutes)
        _toastMessage.value = "已设置更新频率为每 $minutes 分钟"
    }

    fun toggleDndEnabled(enabled: Boolean) {
        repository.setDndEnabled(enabled)
        _toastMessage.value = if (enabled) "已开启免打扰模式 (暂停后台抓取与推送)" else "已关闭免打扰模式"
    }

    fun setDndTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        repository.setDndTimeRange(startHour, startMinute, endHour, endMinute)
        val startFormatted = String.format("%02d:%02d", startHour, startMinute)
        val endFormatted = String.format("%02d:%02d", endHour, endMinute)
        _toastMessage.value = "免打扰时段设为 $startFormatted - $endFormatted"
    }

    fun togglePersistentNotification(enabled: Boolean) {
        repository.setPersistentNotificationEnabled(enabled)
        _toastMessage.value = if (enabled) "已开启后台常驻通知卡片" else "已关闭后台常驻通知卡片"
    }

    fun refreshNow() {
        viewModelScope.launch {
            try {
                repository.refreshRatesNow()
                _toastMessage.value = "汇率已成功更新！"
            } catch (e: Exception) {
                _toastMessage.value = "刷新失败: ${e.localizedMessage}"
            }
        }
    }

    fun onEditAlertClick(alert: RateAlert) {
        _editingAlert.value = alert
    }

    fun onDismissAlertModal() {
        _editingAlert.value = null
    }

    fun onSaveAlert(
        id: Int,
        targetRate: Double,
        condition: String,
        provider: String,
        isEnabled: Boolean,
        label: String
    ) {
        viewModelScope.launch {
            val alert = RateAlert(
                id = id,
                targetRate = targetRate,
                condition = condition,
                provider = provider,
                isEnabled = isEnabled,
                label = label.ifBlank { "提醒 #$id" }
            )
            repository.saveAlert(alert)
            _editingAlert.value = null
            _toastMessage.value = "已保存提醒 #$id 设置"
        }
    }

    fun toggleAlertEnabled(alert: RateAlert) {
        viewModelScope.launch {
            val updated = alert.copy(isEnabled = !alert.isEnabled)
            repository.saveAlert(updated)
            _toastMessage.value = if (updated.isEnabled) "已启用 ${alert.label}" else "已禁用 ${alert.label}"
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _toastMessage.value = "已清除历史汇率记录"
        }
    }

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        prefs.edit().putBoolean("is_dark_mode", newMode).apply()
        _toastMessage.value = if (newMode) "已切换至黑暗模式" else "已切换至亮色模式"
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
