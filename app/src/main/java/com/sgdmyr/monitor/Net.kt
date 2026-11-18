package com.sgdmyr.monitor

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

object Net {
    const val CIMB_URL = "https://www.cimbclicks.com.sg/sgd-to-myr"
    const val WISE_URL = "https://wise.com/gb/compare/wise-sgd-to-myr"
    const val WISE_LIVE = "https://wise.com/rates/live?source=SGD&target=MYR"
    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    fun post(url: String, body: String, timeoutMs: Int = 12000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    fun get(url: String, timeoutMs: Int = 12000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9")
            c.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}

/** 用隐藏的 WebView 像真浏览器一样打开页面，拿渲染后的文字（能绕过普通请求被拦截的情况）。 */
object WebRender {
    private const val JS = "(function(){return (document.body?document.body.innerText:'')+' ||EXCH|| '+" +
        "Array.from(document.querySelectorAll('span.exchAnimate')).map(function(e){return e.textContent}).join(' ');})()"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun text(ctx: Context, url: String, timeoutMs: Long = 25000, ready: (String) -> Boolean): String? =
        withContext(Dispatchers.Main) {
            val wv = WebView(ctx.applicationContext)
            try {
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.userAgentString = Net.UA
                wv.webViewClient = WebViewClient()
                wv.loadUrl(url)
                var last: String? = null
                val end = SystemClock.elapsedRealtime() + timeoutMs
                while (SystemClock.elapsedRealtime() < end) {
                    delay(1500)
                    val r = eval(wv)
                    if (r != null) { last = r; if (ready(r)) return@withContext r }
                }
                last
            } finally {
                wv.stopLoading(); wv.destroy()
            }
        }

    private suspend fun eval(wv: WebView): String? = suspendCancellableCoroutine { cont ->
        wv.evaluateJavascript(JS) { v ->
            val s = try { JSONTokener(v ?: "null").nextValue() as? String } catch (e: Exception) { null }
            cont.resume(s)
        }
    }
}

/** 取数策略链：HTTP → WebView → 仅中间价；成功的方式会被记住，下次优先使用。 */
object Fetch {
    suspend fun cimb(ctx: Context, p: Prefs, log: StringBuffer): Pair<Double, String>? {
        val order = if (p.cimbMethod == "WEB") listOf("WEB", "HTTP") else listOf("HTTP", "WEB")
        for (m in order) {
            try {
                val v = if (m == "HTTP") Parsers.cimbRate(withContext(Dispatchers.IO) { Net.get(Net.CIMB_URL) })
                else {
                    // 页面上的数字有滚动动画：连续两次读到同一个值才采用，避免读到动画中途的数字
                    var prev: Double? = null
                    WebRender.text(ctx, Net.CIMB_URL) { txt ->
                        val cur = Parsers.cimbRate(txt)
                        val stable = cur != null && cur == prev
                        prev = cur
                        stable
                    }?.let { Parsers.cimbRate(it) }
                }
                if (v != null) { p.cimbMethod = m; return v to (if (m == "HTTP") "HTTP" else "浏览器") }
                log.append("CIMB·$m：页面里没找到汇率\n")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { log.append("CIMB·$m：${e.javaClass.simpleName} ${e.message ?: ""}\n") }
        }
        return null
    }

    suspend fun wise(ctx: Context, p: Prefs, amount: Double, log: StringBuffer): Pair<WiseQuote, String>? {
        // 第一优先：Wise 官方无需登录的报价接口（直接给汇率、手续费、到账金额）
        for (u in listOf("https://api.wise.com/2026Q4/quotes", "https://api.wise.com/v3/quotes/")) {
            try {
                val body = """{"sourceCurrency":"SGD","targetCurrency":"MYR","sourceAmount":$amount,"targetAmount":null}"""
                val q = Parsers.wiseApi(withContext(Dispatchers.IO) { Net.post(u, body) }, amount)
                if (q != null) return q to "Wise 接口"
                log.append("Wise·接口：返回里没找到可用报价\n")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { log.append("Wise·接口(${u.substringAfter("wise.com/").substringBefore("/quotes")})：${e.javaClass.simpleName} ${e.message ?: ""}\n") }
        }
        val url = Net.WISE_URL + "?sendAmount=" + amount.toLong()
        var partial: Pair<WiseQuote, String>? = null
        // 先用浏览器渲染（拿实时报价）；HTTP 拿到的可能是服务器缓存的旧页面，只作备用
        val order = listOf("WEB", "HTTP")
        for (m in order) {
            try {
                val q = if (m == "HTTP") Parsers.wiseQuote(withContext(Dispatchers.IO) { Net.get(url) }, amount)
                else {
                    // 页面先显示缓存值再刷新成实时值：连续 3 次读到相同的 汇率+到账金额 才采用
                    var prevKey: String? = null
                    var same = 0
                    WebRender.text(ctx, url, 40000) { txt ->
                        val cur = Parsers.wiseQuote(txt, amount)
                        val key = if (cur?.received != null) "${cur.mid}|${cur.received}" else null
                        same = if (key != null && key == prevKey) same + 1 else 0
                        prevKey = key
                        same >= 2
                    }?.let { Parsers.wiseQuote(it, amount) }
                }
                val via = if (m == "HTTP") "HTTP" else "浏览器"
                if (q != null && q.fee != null && q.received != null) { p.wiseMethod = m; return q to via }
                if (q != null && partial == null) partial = q to "$via（缺手续费）"
                log.append("Wise·$m：${if (q == null) "没找到汇率" else "只找到中间价，没找到手续费/到账金额"}\n")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { log.append("Wise·$m：${e.javaClass.simpleName} ${e.message ?: ""}\n") }
        }
        if (partial != null) return partial
        try {
            val v = Parsers.wiseLive(withContext(Dispatchers.IO) { Net.get(Net.WISE_LIVE) })
            if (v != null) return WiseQuote(v, null, null, amount) to "仅中间价"
            log.append("Wise·实时中间价：解析失败\n")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { log.append("Wise·实时中间价：${e.javaClass.simpleName} ${e.message ?: ""}\n") }
        return null
    }
}
