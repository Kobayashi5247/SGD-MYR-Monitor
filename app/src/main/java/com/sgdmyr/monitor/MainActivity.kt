@file:OptIn(ExperimentalMaterial3Api::class)

package com.sgdmyr.monitor

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ensureMonitor() }

    private fun ensureMonitor() { if (Prefs(this).monitoring) Monitor.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RateStore.init(this)
        Notifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else ensureMonitor()
        setContent { App() }
    }
}

fun numText(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
fun parseNum(s: String) = s.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }
fun hm(m: Int) = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

@Composable
fun App() {
    val ctx = LocalContext.current
    val p = remember { Prefs(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    val t = tick
    val change: (() -> Unit) -> Unit = { it(); tick++ }
    val dark = when (p.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme) {
        var page by remember { mutableIntStateOf(0) }
        Scaffold(bottomBar = {
            NavigationBar {
                listOf("首页" to "💱", "历史" to "📈", "设置" to "⚙️").forEachIndexed { i, (name, icon) ->
                    NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Text(icon) }, label = { Text(name) })
                }
            }
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (page) {
                    0 -> Home(p, change, t)
                    1 -> HistoryPage(p, t)
                    else -> SettingsPage(p, change, t)
                }
            }
        }
    }
}

private fun open(ctx: android.content.Context, url: String) =
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

@Composable
fun Home(p: Prefs, change: (() -> Unit) -> Unit, t: Int) {
    val ctx = LocalContext.current
    val snap by RateStore.snap.collectAsState()
    val running by RateStore.running.collectAsState()
    val busy by RateStore.busy.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    var amountText by remember { mutableStateOf(numText(p.amount)) }
    LaunchedEffect(amountText) {
        val v = parseNum(amountText)
        if (v != null && v != p.amount) { delay(1200); p.amount = v; RateStore.refresh.trySend(Unit) }
    }
    val amount = parseNum(amountText) ?: p.amount
    val offers = Logic.offers(snap, amount, p.cimbFee)
    val best = Logic.best(offers)
    val staleMs = maxOf(p.intervalSec * 3000L, 300_000L)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("新币 → 马币", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = amountText, onValueChange = { amountText = it }, label = { Text("兑换金额（SGD）") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
        )
        if (best != null && offers.size > 1) {
            val other = offers.first { it !== best }
            val diff = best.received - other.received
            Text(
                "智能推荐：用 ${best.name}，比 ${other.name} 多收 ${Logic.f2(diff)} MYR（${String.format(Locale.US, "%.3f", diff / other.received * 100)}%）",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
            )
        } else if (best != null) {
            Text("目前只有 ${best.name} 的数据，另一家取数失败（见下方提示）", color = MaterialTheme.colorScheme.error)
        }
        if (offers.isEmpty()) Text(if (busy) "正在获取汇率…" else "还没有汇率数据，点“立即刷新”")
        offers.forEach { o -> OfferCard(o, o === best, o.time > 0 && now - o.time > staleMs, now) }

        val hist = remember(snap.time) { History.read(ctx, System.currentTimeMillis() - 86_400_000L) }
        val vals = hist.mapNotNull { pt -> listOfNotNull(pt.cimb, pt.wise).maxOrNull() }
        if (best != null && vals.size >= 10) {
            Text(
                "时机参考：当前最佳 ${Logic.f4(best.effective)}，高于过去 24 小时内 ${Logic.percentile(vals, best.effective)}% 的记录" +
                    "（最高 ${Logic.f4(vals.max())} / 最低 ${Logic.f4(vals.min())}）",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { open(ctx, Net.CIMB_URL) }, modifier = Modifier.weight(1f)) { Text("打开 CIMB 页面") }
            OutlinedButton(onClick = { open(ctx, Net.WISE_URL) }, modifier = Modifier.weight(1f)) { Text("打开 Wise 页面") }
        }
        Button(onClick = { RateStore.refresh.trySend(Unit); if (!running) Monitor.start(ctx) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "刷新中…" else "立即刷新")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "后台监控（每 ${if (p.intervalSec < 60) "${p.intervalSec} 秒" else "${p.intervalSec / 60} 分钟"}检查一次）" + if (running) "：运行中" else "：已停止",
                Modifier.weight(1f)
            )
            Switch(checked = p.monitoring, onCheckedChange = { on ->
                change { p.monitoring = on }
                if (on) Monitor.start(ctx) else Monitor.stop(ctx)
            })
        }
        if (snap.diag.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("取数提示（App 已自动切换备用方式；若长期失败，说明对方页面改版太大）", fontWeight = FontWeight.Bold)
                    Text(snap.diag, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("数据来源：CIMB Clicks 与 Wise 官方页面。Wise 已扣手续费；金额非 1000 时按比例估算手续费。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun OfferCard(o: Offer, best: Boolean, stale: Boolean, now: Long) {
    val cs = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = if (best) cs.primaryContainer else cs.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (best) Text("  ✅ 最佳", color = cs.primary)
            }
            Text("${Logic.f2(o.amount)} SGD → ${Logic.f2(o.received)} MYR", style = MaterialTheme.typography.headlineSmall)
            val rateLine = if (o.name == "Wise") "中间价 ${Logic.f4(o.rate)} · 扣费后实际 ${Logic.f4(o.effective)}"
            else "汇率 ${Logic.f4(o.rate)}" + (if (o.feeSgd > 0) " · 扣费后实际 ${Logic.f4(o.effective)}" else "")
            Text(rateLine)
            Text("手续费 ${Logic.f2(o.feeSgd)} SGD" + (if (o.estimated) "（估算）" else ""))
            Text(
                "更新于 ${Logic.ago(o.time, now)} · 来源 ${o.via}" + (if (stale) "  ⚠ 数据已过期" else ""),
                style = MaterialTheme.typography.bodySmall, color = if (stale) cs.error else cs.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HistoryPage(p: Prefs, t: Int) {
    val ctx = LocalContext.current
    val snap by RateStore.snap.collectAsState()
    var range by remember { mutableIntStateOf(1) }
    val spans = listOf(3_600_000L, 86_400_000L, 7 * 86_400_000L, -1L)
    val points = remember(range, snap.time) {
        History.read(ctx, if (spans[range] < 0) 0L else System.currentTimeMillis() - spans[range])
    }
    val cimbC = Color(0xFF1E88E5); val wiseC = Color(0xFF43A047); val tgtC = Color(0xFFE53935)
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("历史波动", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Chips(listOf("1小时" to 0, "24小时" to 1, "7天" to 2, "全部" to 3), range) { range = it }
        if (points.size < 2) {
            Text("数据还不够画图。这里只记录本 App 运行期间自己抓到的汇率（不含网上的历史数据），让后台监控多跑一会儿即可。")
        } else {
            val step = (points.size / 500).coerceAtLeast(1)
            val shown = points.filterIndexed { i, _ -> i % step == 0 } + points.last()
            Chart(shown, p.target, cimbC, wiseC, tgtC)
            Text("${fmt.format(Date(points.first().t))}  →  ${fmt.format(Date(points.last().t))}（共 ${points.size} 条记录）", style = MaterialTheme.typography.bodySmall)
            Text("🔵 CIMB   🟢 Wise（扣费后）   🔴 目标 ${Logic.f4(p.target)}", style = MaterialTheme.typography.bodySmall)
            Stat("CIMB", points.mapNotNull { it.cimb })
            Stat("Wise", points.mapNotNull { it.wise })
        }
    }
}

@Composable
fun Stat(name: String, v: List<Double>) {
    if (v.isEmpty()) return
    Text(
        "$name　最新 ${Logic.f4(v.last())}　最高 ${Logic.f4(v.max())}　最低 ${Logic.f4(v.min())}　平均 ${Logic.f4(v.average())}",
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun Chart(points: List<Point>, target: Double, cimbC: Color, wiseC: Color, tgtC: Color) {
    Canvas(Modifier.fillMaxWidth().height(230.dp)) {
        val all = points.flatMap { listOfNotNull(it.cimb, it.wise) } + target
        var lo = all.min(); var hi = all.max()
        val pad = (hi - lo).coerceAtLeast(0.002) * 0.1
        lo -= pad; hi += pad
        val t0 = points.first().t
        val t1 = maxOf(points.last().t, t0 + 1)
        fun x(t: Long) = ((t - t0).toFloat() / (t1 - t0).toFloat()) * size.width
        fun y(v: Double) = ((1.0 - (v - lo) / (hi - lo)) * size.height).toFloat()
        drawLine(tgtC, Offset(0f, y(target)), Offset(size.width, y(target)), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        fun line(sel: (Point) -> Double?, c: Color) {
            val path = Path(); var started = false
            for (pt in points) {
                val v = sel(pt) ?: continue
                if (!started) { path.moveTo(x(pt.t), y(v)); started = true } else path.lineTo(x(pt.t), y(v))
            }
            drawPath(path, c, style = Stroke(width = 4f))
        }
        line({ it.cimb }, cimbC)
        line({ it.wise }, wiseC)
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun <T> Chips(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (name, v) -> FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(name) }) }
    }
}

@Composable
fun NumField(label: String, initial: Double, allowZero: Boolean = false, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(numText(initial)) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; it.replace(",", "").toDoubleOrNull()?.takeIf { v -> v > 0 || (allowZero && v == 0.0) }?.let(onValue) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SettingsPage(p: Prefs, change: (() -> Unit) -> Unit, t: Int) {
    val ctx = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Section("到价提醒") {
            SwitchRow("开启到价提醒", p.alerts) { v -> change { p.alerts = v } }
            NumField("目标汇率（1 SGD = ? MYR）", p.target) { v -> change { p.target = v; p.wasAbove = false } }
            Text("按哪个汇率比较（均为扣费后实际汇率）", style = MaterialTheme.typography.bodySmall)
            Chips(listOf("最佳" to "BEST", "CIMB" to "CIMB", "Wise" to "WISE"), p.targetSrc) { v -> change { p.targetSrc = v; p.wasAbove = false } }
            Text("刚升到目标时提醒；一直高于目标则每 30 分钟再提醒一次。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { Notifier.ensureChannels(ctx); Notifier.alert(ctx, p, p.target) }) { Text("发送一条测试提醒") }
        }

        Section("检查频率") {
            Chips(listOf("30秒" to 30, "1分钟" to 60, "2分钟" to 120, "5分钟" to 300, "15分钟" to 900), p.intervalSec) { v ->
                change { p.intervalSec = v }; RateStore.refresh.trySend(Unit)
            }
            Text("频率越高越耗电；后台保持运行会常驻一条状态通知。", style = MaterialTheme.typography.bodySmall)
        }

        Section("通知") {
            SwitchRow("提醒时响铃", p.sound) { v -> change { p.sound = v } }
            SwitchRow("提醒时震动", p.vibrate) { v -> change { p.vibrate = v } }
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.alertChannel(p)))
            }) { Text("系统通知设置（可换铃声）") }
        }

        Section("免打扰") {
            SwitchRow("免打扰时段内不发提醒", p.dnd) { v -> change { p.dnd = v } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m -> change { p.dndStart = h * 60 + m } }, p.dndStart / 60, p.dndStart % 60, true).show()
                }) { Text("开始 ${hm(p.dndStart)}") }
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m -> change { p.dndEnd = h * 60 + m } }, p.dndEnd / 60, p.dndEnd % 60, true).show()
                }) { Text("结束 ${hm(p.dndEnd)}") }
            }
            Text("免打扰期间仍在记录汇率，只是不弹提醒；结束后若仍高于目标会立刻提醒。", style = MaterialTheme.typography.bodySmall)
        }

        Section("后台运行") {
            SwitchRow("开机自动启动监控", p.autostart) { v -> change { p.autostart = v } }
            val ignoring = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
            OutlinedButton(onClick = {
                ctx.startActivity(
                    if (!ignoring) Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            }) { Text(if (ignoring) "电池优化：已允许后台运行" else "允许后台运行（关闭电池优化）") }
            Text("想要每分钟都准时，务必允许后台运行；部分手机还需在系统里把本 App 设为“自启动/不受限制”。", style = MaterialTheme.typography.bodySmall)
        }

        Section("费用与数据") {
            NumField("CIMB 手续费（SGD，页面只显示汇率，默认 0）", p.cimbFee, allowZero = true) { v -> change { p.cimbFee = v } }
            OutlinedButton(onClick = { History.clear(ctx); change { } }) { Text("清空历史记录") }
        }

        Section("主题") {
            Chips(listOf("跟随系统" to 0, "白天" to 1, "黑夜" to 2), p.theme) { v -> change { p.theme = v } }
        }
    }
}
