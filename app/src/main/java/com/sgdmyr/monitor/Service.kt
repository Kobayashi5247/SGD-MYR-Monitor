package com.sgdmyr.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Calendar

object Notifier {
    const val STATUS_CH = "status"
    const val STATUS_ID = 1
    const val ALERT_ID = 2

    fun alertChannel(p: Prefs) = "alert_" + (if (p.sound) "s" else "") + (if (p.vibrate) "v" else "")

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(STATUS_CH, "实时汇率（常驻）", NotificationManager.IMPORTANCE_LOW))
        for (sound in listOf(true, false)) for (vib in listOf(true, false)) {
            val id = "alert_" + (if (sound) "s" else "") + (if (vib) "v" else "")
            val name = "到价提醒（" + (if (sound) "响铃" else "静音") + (if (vib) "+震动" else "") + "）"
            val ch = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
            if (!sound) ch.setSound(null, null)
            ch.enableVibration(vib)
            nm.createNotificationChannel(ch)
        }
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun status(ctx: Context, s: Snapshot, p: Prefs): Notification {
        val os = Logic.offers(s, p.amount, p.cimbFee)
        val best = Logic.best(os)
        val title = if (best == null) "正在获取汇率…" else "最佳 ${Logic.f4(best.effective)}（${best.name}）"
        val text = if (os.isEmpty()) "SGD→MYR" else os.joinToString("  ·  ") { "${it.name} ${Logic.f4(it.effective)}" } +
            "  ·  ${Logic.f2(p.amount)} SGD→${Logic.f2(best!!.received)} MYR"
        return NotificationCompat.Builder(ctx, STATUS_CH)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openApp(ctx)).build()
    }

    fun updateStatus(ctx: Context, s: Snapshot, p: Prefs) {
        ctx.getSystemService(NotificationManager::class.java).notify(STATUS_ID, status(ctx, s, p))
    }

    fun alert(ctx: Context, p: Prefs, rate: Double) {
        val n = NotificationCompat.Builder(ctx, alertChannel(p))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("汇率到价：${Logic.f4(rate)}")
            .setContentText("已达到你设定的目标 ${Logic.f4(p.target)}（新币→马币）")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true).setContentIntent(openApp(ctx)).build()
        ctx.getSystemService(NotificationManager::class.java).notify(ALERT_ID, n)
    }
}

object Monitor {
    fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
    fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MonitorService::class.java)) }
}

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        RateStore.init(this)
        Notifier.ensureChannels(this)
        val n = Notifier.status(this, RateStore.snap.value, Prefs(this))
        if (Build.VERSION.SDK_INT >= 34) startForeground(Notifier.STATUS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(Notifier.STATUS_ID, n)
        RateStore.running.value = true
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel(); scope.cancel()
        wl?.let { if (it.isHeld) it.release() }
        RateStore.running.value = false
        super.onDestroy()
    }

    private suspend fun loop() {
        val p = Prefs(this)
        val pm = getSystemService(PowerManager::class.java)
        while (true) {
            val ms = p.intervalSec.coerceAtLeast(15) * 1000L
            // 保持 CPU 唤醒，保证屏幕关闭后定时仍然准点（耗电略增；频率越低越省电）
            wl?.let { if (it.isHeld) it.release() }
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sgdmyr:monitor").also { it.acquire(ms + 90_000L) }
            try { runOnce(p) } catch (e: CancellationException) { throw e } catch (e: Exception) { /* 本轮失败，下轮继续 */ }
            withTimeoutOrNull(ms) { RateStore.refresh.receive() }
        }
    }

    private suspend fun runOnce(p: Prefs) = coroutineScope {
        RateStore.busy.value = true
        try {
            val amount = p.amount
            val log = StringBuffer()
            val c = async { Fetch.cimb(this@MonitorService, p, log) }
            val w = async { Fetch.wise(this@MonitorService, p, amount, log) }
            val cr = c.await(); val wr = w.await()
            val now = System.currentTimeMillis()
            var s = RateStore.snap.value
            if (cr != null) s = s.copy(cimb = cr.first, cimbTime = now, cimbVia = cr.second)
            if (wr != null) {
                val q = wr.first
                s = if (q.fee != null && q.received != null)
                    s.copy(wiseMid = q.mid, wiseFee = q.fee, wiseSent = q.sent, wiseReceived = q.received, wiseTime = now, wiseVia = wr.second)
                else s.copy(wiseMid = q.mid, wiseReceived = null, wiseTime = now, wiseVia = wr.second) // 沿用上次手续费比例估算
            }
            s = s.copy(time = now, diag = log.toString().trim())
            RateStore.snap.value = s
            p.saveSnapshot(s)
            if (cr != null || wr != null) History.append(this@MonitorService, s, amount, p.cimbFee)
            Notifier.updateStatus(this@MonitorService, s, p)
            checkAlert(p, s)
        } finally { RateStore.busy.value = false }
    }

    private fun checkAlert(p: Prefs, s: Snapshot) {
        if (!p.alerts) return
        val cal = Calendar.getInstance()
        if (Logic.inDnd(p.dnd, p.dndStart, p.dndEnd, cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))) return
        val rate = Logic.targetRate(Logic.offers(s, p.amount, p.cimbFee), p.targetSrc) ?: return
        val above = rate >= p.target
        val now = System.currentTimeMillis()
        // 刚越过目标就提醒；一直高于目标则每 30 分钟再提醒一次
        if (above && (!p.wasAbove || now - p.lastAlert > 30 * 60_000L)) {
            Notifier.alert(this, p, rate); p.lastAlert = now
        }
        p.wasAbove = above
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = Prefs(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && p.autostart && p.monitoring) Monitor.start(context)
    }
}
