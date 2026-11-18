package com.sgdmyr.monitor

import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/** 纯 Kotlin 逻辑（不依赖 Android），方便单独测试。 */

data class Snapshot(
    val time: Long = 0L,
    val cimb: Double? = null, val cimbTime: Long = 0L, val cimbVia: String = "",
    val wiseMid: Double? = null, val wiseFee: Double? = null, val wiseSent: Double = 1000.0,
    val wiseReceived: Double? = null, val wiseTime: Long = 0L, val wiseVia: String = "",
    val diag: String = ""
)

data class WiseQuote(val mid: Double, val fee: Double?, val received: Double?, val sent: Double)

data class Offer(
    val name: String, val rate: Double, val feeSgd: Double, val received: Double,
    val estimated: Boolean, val time: Long, val via: String, val amount: Double
) {
    /** 扣费后的实际汇率 */
    val effective: Double get() = if (amount > 0) received / amount else 0.0
}

object Logic {
    fun offers(s: Snapshot, amount: Double, cimbFee: Double): List<Offer> {
        val out = ArrayList<Offer>()
        val c = s.cimb
        if (c != null) {
            out += Offer("CIMB", c, cimbFee, (amount - cimbFee).coerceAtLeast(0.0) * c, false, s.cimbTime, s.cimbVia, amount)
        }
        val mid = s.wiseMid
        if (mid != null) {
            val wf = s.wiseFee
            val pct = if (wf != null && s.wiseSent > 0) wf / s.wiseSent else 0.003
            val exact = wf != null && s.wiseReceived != null && abs(s.wiseSent - amount) < 0.005
            val fee = if (exact) wf!! else amount * pct
            val received = if (exact) s.wiseReceived!! else (amount - fee).coerceAtLeast(0.0) * mid
            out += Offer("Wise", mid, fee, received, !exact, s.wiseTime, s.wiseVia, amount)
        }
        return out
    }

    fun best(os: List<Offer>): Offer? = os.maxByOrNull { it.received }

    fun targetRate(os: List<Offer>, src: String): Double? = when (src) {
        "CIMB" -> os.firstOrNull { it.name == "CIMB" }?.effective
        "WISE" -> os.firstOrNull { it.name == "Wise" }?.effective
        else -> os.maxOfOrNull { it.effective }
    }

    fun inDnd(enabled: Boolean, start: Int, end: Int, nowMin: Int): Boolean {
        if (!enabled || start == end) return false
        return if (start < end) nowMin in start until end else (nowMin >= start || nowMin < end)
    }

    fun percentile(values: List<Double>, v: Double): Int =
        if (values.isEmpty()) 0 else values.count { it <= v } * 100 / values.size

    fun f4(v: Double): String = String.format(Locale.US, "%.4f", v)
    fun f2(v: Double): String = String.format(Locale.US, "%,.2f", v)

    fun ago(t: Long, now: Long): String {
        if (t <= 0) return "—"
        val s = ((now - t) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "${s}秒前"
            s < 3600 -> "${s / 60}分钟前"
            s < 86400 -> "${s / 3600}小时前"
            else -> "${s / 86400}天前"
        }
    }
}

object Parsers {
    private val RATE_RANGE = 2.0..5.0
    private val IC = RegexOption.IGNORE_CASE

    fun toNum(s: String?): Double? = s?.replace(",", "")?.toDoubleOrNull()

    fun htmlToText(html: String): String {
        var t = html.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ")
        t = t.replace(Regex("(?s)<[^>]+>"), " ")
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&#8594;", "→").replace("&lt;", "<").replace("&gt;", ">")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    /** CIMB：不依赖 CSS 类名的多重匹配，类名只作为其中一个线索。 */
    fun cimbRate(src: String): Double? {
        val texts = if (src.contains('<')) listOf(src, htmlToText(src)) else listOf(src)
        val pats = listOf(
            Regex("""SGD\s*1(?:\.0+)?\s*=\s*MYR\s*([\d,]+\.\d+)""", IC),
            Regex("""1\s*SGD\s*=\s*(?:MYR\s*)?([\d,]+\.\d+)""", IC),
            Regex("""\|\|EXCH\|\|\s*([\d,]+\.\d+)"""),
            Regex("""exchAnimate[^>]*>\s*([\d,]+\.\d+)\s*<""")
        )
        for (t in texts) for (p in pats) for (m in p.findAll(t)) {
            val v = toNum(m.groupValues[1])
            if (v != null && v in RATE_RANGE) return v
        }
        return null
    }

    private fun consistent(sent: Double, fee: Double, mid: Double, recv: Double): Boolean =
        abs((sent - fee) * mid - recv) <= maxOf(0.5, recv * 0.002)

    /**
     * Wise：先按文字标签取值；标签变了就改用“数字自洽校验”：
     * 在页面里找 (汇率, 手续费, 到账金额) 的组合，满足 (发送额-手续费)×汇率≈到账金额 才采用。
     */
    fun wiseQuote(src: String, requested: Double): WiseQuote? {
        val text = if (src.contains('<')) htmlToText(src) else src
        val sentM = Regex("""Sending\s*([\d,]+(?:\.\d+)?)\s*SGD""", IC).find(text)
        val midM = Regex("""1\s*SGD\s*=\s*([\d,]+\.\d+)\s*MYR""", IC).find(text)
        val sent = toNum(sentM?.groupValues?.get(1)) ?: requested
        val midLabel = toNum(midM?.groupValues?.get(1))?.takeIf { it in RATE_RANGE }
        val anchor = sentM?.range?.last ?: midM?.range?.last ?: 0
        val tail = text.substring((anchor + 1).coerceAtMost(text.length)).take(2500)

        val rateCands = Regex("""(?<![\d.])([2-4]\.\d{3,6})(?!\d)""").findAll(tail)
            .mapNotNull { toNum(it.groupValues[1]) }.toList()
        val mid = midLabel ?: rateCands.firstOrNull { it in RATE_RANGE }
        val feeLabel = Regex("""([\d,]+(?:\.\d+)?)\s*SGD\s*(?:Transparent\s*)?fee""", IC).find(tail)?.let { toNum(it.groupValues[1]) }
        val recvLabel = Regex("""([\d,]+\.\d+)\s*MYR""").find(tail)?.let { toNum(it.groupValues[1]) }

        if (mid != null && feeLabel != null && recvLabel != null && consistent(sent, feeLabel, mid, recvLabel))
            return WiseQuote(mid, feeLabel, recvLabel, sent)

        // 先定位 "Wise" 那一行：只在每个 "Wise" 字样之后的一小段里找 汇率/手续费/到账金额，
        // 并用自洽校验确认，避免抓到别家银行的数字；发送额同时尝试 请求值 和 1000（页面可能忽略 sendAmount）
        val sentCands = listOf(sent, 1000.0).distinct()
        val rateRe = Regex("""(?<![\d.])([2-4]\.\d{3,6})(?!\d)""")
        val feeRe = Regex("""([\d,]+(?:\.\d+)?)\s*SGD""")
        val recvRe = Regex("""([\d,]+\.\d+)\s*MYR""")
        for (st in Regex("Wise").findAll(text).map { it.range.last + 1 }.take(10).toList()) {
            val w = text.substring(st, (st + 700).coerceAtMost(text.length))
            val ms = rateRe.findAll(w).mapNotNull { toNum(it.groupValues[1]) }.filter { it in RATE_RANGE }.distinct().toList()
            val fs = feeRe.findAll(w).mapNotNull { toNum(it.groupValues[1]) }.filter { it > 0 }.distinct().toList()
            val rs = recvRe.findAll(w).mapNotNull { toNum(it.groupValues[1]) }.distinct().toList()
            for (s0 in sentCands) for (m in ms) for (f in fs) for (r in rs)
                if (f < s0 * 0.1 && r > s0 * 2 && consistent(s0, f, m, r)) return WiseQuote(m, f, r, s0)
        }

        val fees = Regex("""([\d,]+(?:\.\d+)?)\s*SGD""").findAll(tail).mapNotNull { toNum(it.groupValues[1]) }
            .filter { it > 0 && it < sent * 0.1 }.distinct().take(8).toList()
        val recvs = Regex("""([\d,]+\.\d+)\s*MYR""").findAll(tail).mapNotNull { toNum(it.groupValues[1]) }
            .filter { it > sent * 2 }.distinct().take(8).toList()
        val rateAll = Regex("""(?<![\d.])([2-4]\.\d{3,6})(?!\d)""").findAll(text.take(8000))
            .mapNotNull { toNum(it.groupValues[1]) }.toList()
        val mids = (listOfNotNull(midLabel) + rateCands + rateAll).filter { it in RATE_RANGE }.distinct().take(10)
        for (m in mids) for (f in fees) for (r in recvs) if (consistent(sent, f, m, r)) return WiseQuote(m, f, r, sent)

        return mid?.takeIf { it in RATE_RANGE }?.let { WiseQuote(it, null, null, sent) }
    }

    /** Wise 官方无需登录的报价接口（POST /quotes）：汇率 + 手续费 + 到账金额 */
    fun wiseApi(json: String, sent: Double): WiseQuote? {
        val o = JSONObject(json)
        val rate = o.optDouble("rate", Double.NaN)
        if (rate.isNaN() || rate !in RATE_RANGE) return null
        val opts = o.optJSONArray("paymentOptions") ?: return null
        var best: Triple<Double, Double, Boolean>? = null // fee, target, isBankTransfer
        for (i in 0 until opts.length()) {
            val op = opts.optJSONObject(i) ?: continue
            if (op.optBoolean("disabled", false)) continue
            val fee = op.optJSONObject("fee")?.optDouble("total", Double.NaN) ?: Double.NaN
            val tgt = op.optDouble("targetAmount", Double.NaN)
            if (fee.isNaN() || tgt.isNaN()) continue
            if (!consistent(sent, fee, rate, tgt)) continue
            val bank = op.optString("payIn") == "BANK_TRANSFER"
            val cur = best
            if (cur == null || (bank && !cur.third) || (bank == cur.third && fee < cur.first)) best = Triple(fee, tgt, bank)
        }
        return best?.let { WiseQuote(rate, it.first, it.second, sent) }
    }

    /** wise.com/rates/live 返回的中间价 JSON */
    fun wiseLive(json: String): Double? =
        Regex(""""value"\s*:\s*([\d.]+)""").find(json)?.let { toNum(it.groupValues[1]) }?.takeIf { it in RATE_RANGE }
}
