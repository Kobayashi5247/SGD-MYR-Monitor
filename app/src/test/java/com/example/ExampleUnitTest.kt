package com.example

import com.example.network.RateFetcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun inspectLivePages() = runBlocking {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val wiseReq = Request.Builder()
            .url("https://wise.com/gb/compare/wise-sgd-to-myr")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-GB,en;q=0.9")
            .build()

        client.newCall(wiseReq).execute().use { resp ->
            val html = resp.body?.string() ?: ""
            println("DIAG WISE HTTP=${resp.code} FINAL_URL=${resp.request.url} LEN=${html.length}")

            // Inspect __NEXT_DATA__ or quotes or midMarketRate or fee
            val nextDataIdx = html.indexOf("__NEXT_DATA__")
            println("DIAG __NEXT_DATA__ idx=$nextDataIdx")

            val keys = listOf("midMarketRate", "\"quotes\"", "\"fees\"", "\"fee\"", "Transparent", "receivedAmount", "sourceAmount", "sendAmount", "\"wise\"", "3.1", "3.2", "3.0")
            for (k in keys) {
                var start = 0
                var count = 0
                while (count < 5) {
                    val idx = html.indexOf(k, start)
                    if (idx == -1) break
                    val snippet = html.substring((idx - 80).coerceAtLeast(0), (idx + 280).coerceAtMost(html.length))
                        .replace("\n", " ")
                    println("DIAG MATCH [$k #$count]: $snippet")
                    start = idx + k.length
                    count++
                }
            }
        }

        val result = RateFetcher.fetchRates()
        println("DIAG FETCHER wiseRate=${result.wiseQuote.rate}, wiseFixedFee=${result.wiseQuote.fixedFeeSgd}, wiseFeePct=${result.wiseQuote.feePercentage}, isLive=${result.wiseQuote.isLiveWeb}")
        println("DIAG FETCHER wiseNet1000=${result.wiseQuote.calculateNetReceived(1000.0)}, wiseFee1000=${result.wiseQuote.calculateTotalFeeSgd(1000.0)}")
        println("DIAG FETCHER cimbRate=${result.cimbQuote.rate}, isLive=${result.cimbQuote.isLiveWeb}")
        println("DIAG FETCHER status=${result.statusMessage}")
    }
}
