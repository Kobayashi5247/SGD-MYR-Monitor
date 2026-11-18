package com.sgdmyr.monitor

import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** 所有设置（SharedPreferences）。 */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("sgdmyr", Context.MODE_PRIVATE)

    private fun d(k: String, def: Double) = sp.getString(k, null)?.toDoubleOrNull() ?: def
    private fun putD(k: String, v: Double) = sp.edit().putString(k, v.toString()).apply()

    var amount: Double get() = d("amount", 1000.0); set(v) = putD("amount", v)
    var target: Double get() = d("target", 3.18); set(v) = putD("target", v)
    var cimbFee: Double get() = d("cimbFee", 0.0); set(v) = putD("cimbFee", v)
    /** BEST / CIMB / WISE */
    var targetSrc: String get() = sp.getString("targetSrc", "BEST")!!; set(v) = sp.edit().putString("targetSrc", v).apply()
    var intervalSec: Int get() = sp.getInt("interval", 60); set(v) = sp.edit().putInt("interval", v).apply()
    var alerts: Boolean get() = sp.getBoolean("alerts", true); set(v) = sp.edit().putBoolean("alerts", v).apply()
    var sound: Boolean get() = sp.getBoolean("sound", true); set(v) = sp.edit().putBoolean("sound", v).apply()
    var vibrate: Boolean get() = sp.getBoolean("vibrate", true); set(v) = sp.edit().putBoolean("vibrate", v).apply()
    var dnd: Boolean get() = sp.getBoolean("dnd", false); set(v) = sp.edit().putBoolean("dnd", v).apply()
    var dndStart: Int get() = sp.getInt("dndStart", 23 * 60); set(v) = sp.edit().putInt("dndStart", v).apply()
    var dndEnd: Int get() = sp.getInt("dndEnd", 7 * 60); set(v) = sp.edit().putInt("dndEnd", v).apply()
    /** 0 跟随系统 1 白天 2 黑夜 */
    var theme: Int get() = sp.getInt("theme", 0); set(v) = sp.edit().putInt("theme", v).apply()
    var autostart: Boolean get() = sp.getBoolean("autostart", true); set(v) = sp.edit().putBoolean("autostart", v).apply()
    var monitoring: Boolean get() = sp.getBoolean("monitoring", true); set(v) = sp.edit().putBoolean("monitoring", v).apply()
    var wasAbove: Boolean get() = sp.getBoolean("wasAbove", false); set(v) = sp.edit().putBoolean("wasAbove", v).apply()
    var lastAlert: Long get() = sp.getLong("lastAlert", 0L); set(v) = sp.edit().putLong("lastAlert", v).apply()
    var cimbMethod: String get() = sp.getString("cimbMethod", "HTTP")!!; set(v) = sp.edit().putString("cimbMethod", v).apply()
    var wiseMethod: String get() = sp.getString("wiseMethod", "HTTP")!!; set(v) = sp.edit().putString("wiseMethod", v).apply()

    fun saveSnapshot(s: Snapshot) {
        sp.edit()
            .putLong("s_time", s.time)
            .putString("s_cimb", s.cimb?.toString()).putLong("s_cimbT", s.cimbTime).putString("s_cimbV", s.cimbVia)
            .putString("s_mid", s.wiseMid?.toString()).putString("s_fee", s.wiseFee?.toString())
            .putString("s_sent", s.wiseSent.toString()).putString("s_recv", s.wiseReceived?.toString())
            .putLong("s_wiseT", s.wiseTime).putString("s_wiseV", s.wiseVia).putString("s_diag", s.diag)
            .apply()
    }

    fun loadSnapshot() = Snapshot(
        time = sp.getLong("s_time", 0L),
        cimb = sp.getString("s_cimb", null)?.toDoubleOrNull(), cimbTime = sp.getLong("s_cimbT", 0L), cimbVia = sp.getString("s_cimbV", "") ?: "",
        wiseMid = sp.getString("s_mid", null)?.toDoubleOrNull(), wiseFee = sp.getString("s_fee", null)?.toDoubleOrNull(),
        wiseSent = sp.getString("s_sent", null)?.toDoubleOrNull() ?: 1000.0, wiseReceived = sp.getString("s_recv", null)?.toDoubleOrNull(),
        wiseTime = sp.getLong("s_wiseT", 0L), wiseVia = sp.getString("s_wiseV", "") ?: "", diag = sp.getString("s_diag", "") ?: ""
    )
}

/** Activity 与后台服务在同一进程内共享的状态。 */
object RateStore {
    val snap = MutableStateFlow(Snapshot())
    val running = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val refresh = Channel<Unit>(Channel.CONFLATED)
    private var loaded = false

    @Synchronized fun init(ctx: Context) {
        if (!loaded) { loaded = true; snap.value = Prefs(ctx).loadSnapshot() }
    }
}

data class Point(val t: Long, val cimb: Double?, val wise: Double?)

/** 历史记录：只记录本软件自己抓到的数据（CSV：时间,CIMB,Wise扣费后）。 */
object History {
    private const val MAX_BYTES = 1_500_000L
    private fun file(ctx: Context) = File(ctx.filesDir, "history.csv")

    @Synchronized fun append(ctx: Context, s: Snapshot, amount: Double, cimbFee: Double) {
        val os = Logic.offers(s, amount, cimbFee)
        val c = os.firstOrNull { it.name == "CIMB" }?.effective
        val w = os.firstOrNull { it.name == "Wise" }?.effective
        if (c == null && w == null) return
        val f = file(ctx)
        f.appendText("${s.time},${c ?: ""},${w ?: ""}\n")
        if (f.length() > MAX_BYTES) {
            val keep = f.readLines().takeLast(12000)
            f.writeText(keep.joinToString("\n") + "\n")
        }
    }

    @Synchronized fun read(ctx: Context, sinceMs: Long): List<Point> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        val out = ArrayList<Point>()
        f.forEachLine { line ->
            val p = line.split(",")
            val t = p.getOrNull(0)?.toLongOrNull()
            if (t != null && t >= sinceMs) out += Point(t, p.getOrNull(1)?.toDoubleOrNull(), p.getOrNull(2)?.toDoubleOrNull())
        }
        return out
    }

    @Synchronized fun clear(ctx: Context) { file(ctx).delete() }
}
