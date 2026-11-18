package com.example.network

import android.util.Log
import com.example.model.CombinedExchangeRates
import com.example.model.RateQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.round

object RateFetcher {

    private const val TAG = "RateFetcher"

    // ============================================================
    // EXACT SOURCE PAGES
    // ============================================================

    /**
     * IMPORTANT:
     * We only use the two pages you specified.
     *
     * Wise:
     * https://wise.com/gb/compare/wise-sgd-to-myr
     *
     * CIMB:
     * https://www.cimbclicks.com.sg/sgd-to-myr
     *
     * No Open ER API and no Wise v1/v3 APIs.
     */
    private const val WISE_URL =
        "https://wise.com/gb/compare/wise-sgd-to-myr"

    private const val CIMB_URL =
        "https://www.cimbclicks.com.sg/sgd-to-myr"

    /**
     * Wise comparison page is based on 1,000 SGD.
     */
    private const val WISE_SEND_AMOUNT = 1000.0

    /**
     * Last successfully parsed values.
     *
     * These are ONLY fallback values.
     * They are NEVER reported as live.
     */
    private var lastWiseRate: Double? = null
    private var lastWiseFeeSgd: Double? = null
    private var lastWiseUpdated: Long = 0L

    private var lastCimbRate: Double? = null
    private var lastCimbUpdated: Long = 0L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // ============================================================
    // PUBLIC ENTRY
    // ============================================================

    suspend fun fetchRates(): CombinedExchangeRates = coroutineScope {

        val requestTime = System.currentTimeMillis()

        Log.i(TAG, "========================================")
        Log.i(TAG, "RATE FETCH STARTED")
        Log.i(TAG, "========================================")

        val wiseDeferred = async(Dispatchers.IO) {
            fetchWiseRate()
        }

        val cimbDeferred = async(Dispatchers.IO) {
            fetchCimbRate()
        }

        val wiseResult = wiseDeferred.await()
        val cimbResult = cimbDeferred.await()

        Log.i(
            TAG,
            "WISE result: rate=${wiseResult.rate}, " +
                    "fee=${wiseResult.feeSgd}, " +
                    "live=${wiseResult.isLive}, " +
                    "updated=${wiseResult.updatedAt}, " +
                    "error=${wiseResult.error}"
        )

        Log.i(
            TAG,
            "CIMB result: rate=${cimbResult.rate}, " +
                    "live=${cimbResult.isLive}, " +
                    "updated=${cimbResult.updatedAt}, " +
                    "error=${cimbResult.error}"
        )

        val wiseFee = wiseResult.feeSgd ?: 0.0

        val wiseFeePercentage =
            if (WISE_SEND_AMOUNT > 0.0 && wiseFee > 0.0) {
                (wiseFee / WISE_SEND_AMOUNT) * 100.0
            } else {
                0.0
            }

        val wiseQuote = RateQuote(
            providerName = "Wise",
            providerCode = "WISE",
            rate = wiseResult.rate ?: 0.0,

            /**
             * This is calculated from the actual fee read from
             * the Wise page, instead of hard-coding 0.308%.
             */
            feePercentage = roundRate(wiseFeePercentage, 4),

            fixedFeeSgd = wiseFee,

            lastUpdated = wiseResult.updatedAt.takeIf { it > 0L }
                ?: requestTime,

            sourceUrl = WISE_URL,
            isLiveWeb = wiseResult.isLive
        )

        val cimbQuote = RateQuote(
            providerName = "CIMB SG",
            providerCode = "CIMB_SG",
            rate = cimbResult.rate ?: 0.0,
            feePercentage = 0.0,
            fixedFeeSgd = 0.0,

            lastUpdated = cimbResult.updatedAt.takeIf { it > 0L }
                ?: requestTime,

            sourceUrl = CIMB_URL,
            isLiveWeb = cimbResult.isLive
        )

        CombinedExchangeRates(
            wiseQuote = wiseQuote,
            cimbQuote = cimbQuote,
            timestamp = requestTime,
            statusMessage = buildStatusMessage(
                wiseResult,
                cimbResult
            )
        )
    }

    // ============================================================
    // WISE
    // ============================================================

    private fun fetchWiseRate(): FetchResult {
        Log.i(TAG, "Wise request started")

        return try {

            val request = Request.Builder()
                .url(WISE_URL)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) " +
                            "Chrome/151.0 Safari/537.36"
                )
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/json"
                )
                .header(
                    "Accept-Language",
                    "en-GB,en;q=0.9"
                )
                .header(
                    "Cache-Control",
                    "no-cache"
                )
                .build()

            client.newCall(request).execute().use { response ->

                Log.i(
                    TAG,
                    "Wise HTTP ${response.code} (web page)"
                )

                if (!response.isSuccessful) {
                    return fallbackWise(
                        "Wise HTTP ${response.code}"
                    )
                }

                val html = response.body?.string()

                if (html.isNullOrBlank()) {
                    return fallbackWise(
                        "Wise empty response"
                    )
                }

                Log.i(
                    TAG,
                    "Wise body length=${html.length}"
                )

                val data = extractWiseData(html)

                if (data == null) {

                    /**
                     * IMPORTANT:
                     * HTTP 200 does NOT mean the rate was parsed.
                     *
                     * We do NOT call this live.
                     */
                    Log.w(
                        TAG,
                        "Wise HTTP 200 but rate was NOT parsed"
                    )

                    return fallbackWise(
                        "Wise page received but rate not found"
                    )
                }

                val rate = data.rate
                val fee = data.feeSgd

                lastWiseRate = rate

                if (fee != null) {
                    lastWiseFeeSgd = fee
                }

                lastWiseUpdated = System.currentTimeMillis()

                Log.i(
                    TAG,
                    "Wise parsed LIVE rate=$rate"
                )

                Log.i(
                    TAG,
                    "Wise parsed fee=${fee ?: lastWiseFeeSgd}"
                )

                Log.i(
                    TAG,
                    "Wise live = true"
                )

                FetchResult(
                    rate = rate,
                    feeSgd = fee ?: lastWiseFeeSgd,
                    isLive = true,
                    updatedAt = lastWiseUpdated,
                    error = null
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Wise fetch exception",
                e
            )

            fallbackWise(
                e.message ?: "Wise network error"
            )
        }
    }

    /**
     * Wise page currently exposes text equivalent to:
     *
     * 1 SGD = 3.19228 MYR Mid-market exchange rate
     *
     * and:
     *
     * 3.08 SGD Transparent fee
     *
     * 3,182.45 MYR Recipient gets
     *
     * We parse the actual page instead of using deprecated API
     * endpoints.
     */
    private fun extractWiseData(
        html: String
    ): WiseWebData? {

        val ratePatterns = listOf(

            // Next.js embedded JSON midMarketRate object (Wise web page structure)
            Regex(
                """"midMarketRate"\s*:\s*\{[^}]*"rate"\s*:\s*([0-9]+(?:\.[0-9]+)?)"""
            ),

            // In quotes array in Next.js JSON
            Regex(
                """"quotes"\s*:\s*\[[^\]]*"rate"\s*:\s*([0-9]+(?:\.[0-9]+)?)"""
            ),

            // Direct source SGD target MYR rate
            Regex(
                """"rate"\s*:\s*([0-9]+(?:\.[0-9]+)?)[^}]*"source"\s*:\s*"SGD"\s*,\s*"target"\s*:\s*"MYR""""
            ),

            // Plain HTML/text form
            Regex(
                """1\s*SGD\s*=\s*([0-9]+(?:\.[0-9]+)?)\s*MYR"""
            ),

            // Embedded JSON
            Regex(
                """"midMarketRate"\s*:\s*"?([0-9]+(?:\.[0-9]+)?)"""
            ),

            Regex(
                """"mid_market_rate"\s*:\s*"?([0-9]+(?:\.[0-9]+)?)"""
            ),

            Regex(
                """"midMarketExchangeRate"\s*:\s*"?([0-9]+(?:\.[0-9]+)?)"""
            )
        )

        var rate: Double? = null

        for (pattern in ratePatterns) {

            val match = pattern.find(html)

            if (match != null) {

                val value = match
                    .groupValues
                    .getOrNull(1)
                    ?.toDoubleOrNull()

                if (value != null && isValidWiseRate(value)) {
                    rate = roundRate(value, 5)
                    break
                }
            }
        }

        if (rate == null) {
            Log.w(
                TAG,
                "Wise parser: rate pattern not matched"
            )
            return null
        }

        /**
         * Wise page uses the 1,000 SGD comparison shown in your
         * screenshot.
         *
         * We try to read the actual current fee from the page.
         */
        val feePatterns = listOf(

            // In quotes array: "fees":3.08,"rate":
            Regex(
                """"fees"\s*:\s*([0-9]+(?:\.[0-9]+)?)[^}]*"rate""""
            ),

            // Wise quotes block alias wise
            Regex(
                """"alias"\s*:\s*"wise"[^}]*"fees"\s*:\s*([0-9]+(?:\.[0-9]+)?)"""
            ),

            // Example:
            // 3.08 SGD Transparent fee
            Regex(
                """([0-9]+(?:\.[0-9]+)?)\s*SGD\s*Transparent\s+fee"""
            ),

            // More tolerant HTML/text variant
            Regex(
                """([0-9]+(?:\.[0-9]+)?)\s*SGD[^<]{0,80}Transparent\s+fee"""
            ),

            // General fees field
            Regex(
                """"fees"\s*:\s*([0-9]+(?:\.[0-9]+)?)"""
            )
        )

        var fee: Double? = null

        for (pattern in feePatterns) {

            val match = pattern.find(html)

            if (match != null) {

                val value = match
                    .groupValues
                    .getOrNull(1)
                    ?.toDoubleOrNull()

                if (value != null && value >= 0.0 && value < 100.0) {
                    fee = roundRate(value, 2)
                    break
                }
            }
        }

        /**
         * Recipient amount is deliberately not used as the primary
         * rate source. The rate and fee are the authoritative fields
         * for our calculation.
         */
        Log.i(
            TAG,
            "Wise extracted: rate=$rate, fee=$fee"
        )

        return WiseWebData(
            rate = rate,
            feeSgd = fee
        )
    }

    private fun fallbackWise(
        error: String
    ): FetchResult {

        val rate = lastWiseRate
        val fee = lastWiseFeeSgd

        if (rate != null) {

            Log.i(
                TAG,
                "Wise FALLBACK rate=$rate, fee=$fee"
            )

            Log.i(
                TAG,
                "Wise live = false"
            )

            return FetchResult(
                rate = rate,
                feeSgd = fee,
                isLive = false,
                updatedAt = lastWiseUpdated,
                error = error
            )
        }

        Log.i(
            TAG,
            "Wise has no previous successful rate"
        )

        return FetchResult(
            rate = null,
            feeSgd = null,
            isLive = false,
            updatedAt = 0L,
            error = error
        )
    }

    // ============================================================
    // CIMB
    // ============================================================

    private fun fetchCimbRate(): FetchResult {
        Log.i(TAG, "CIMB request started")

        return try {

            val request = Request.Builder()
                .url(CIMB_URL)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) " +
                            "Chrome/151.0 Safari/537.36"
                )
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/json"
                )
                .header(
                    "Accept-Language",
                    "en-SG,en;q=0.9"
                )
                .header(
                    "Cache-Control",
                    "no-cache"
                )
                .build()

            client.newCall(request).execute().use { response ->

                Log.i(
                    TAG,
                    "CIMB HTTP ${response.code}"
                )

                if (!response.isSuccessful) {
                    return fallbackCimb(
                        "CIMB HTTP ${response.code}"
                    )
                }

                val html = response.body?.string()

                if (html.isNullOrBlank()) {
                    return fallbackCimb(
                        "CIMB empty response"
                    )
                }

                Log.i(
                    TAG,
                    "CIMB body length=${html.length}"
                )

                val rate = extractCimbRate(html)

                if (rate == null) {

                    Log.w(
                        TAG,
                        "CIMB HTTP 200 but rate was NOT parsed"
                    )

                    return fallbackCimb(
                        "CIMB page received but rate not found"
                    )
                }

                lastCimbRate = rate
                lastCimbUpdated = System.currentTimeMillis()

                Log.i(
                    TAG,
                    "CIMB parsed LIVE rate=$rate"
                )

                Log.i(
                    TAG,
                    "CIMB live = true"
                )

                FetchResult(
                    rate = rate,
                    feeSgd = null,
                    isLive = true,
                    updatedAt = lastCimbUpdated,
                    error = null
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "CIMB fetch exception",
                e
            )

            fallbackCimb(
                e.message ?: "CIMB network error"
            )
        }
    }

    /**
     * The CIMB page is the source specified by the user.
     *
     * We first look for explicit SGD -> MYR text, then the known
     * exchange-rate labels used by the page.
     *
     * We intentionally DO NOT accept an arbitrary "rate" field,
     * because the page can contain multiple rate types.
     */
    private fun extractCimbRate(
        html: String
    ): Double? {

        val patterns = listOf(

            // CIMB WebSphere portal hidden input for rate list (e.g. value="[3.1840]")
            Regex(
                """rateList[^>]*value="\[?([0-9]+\.[0-9]+)\]?"""
            ),

            // Array value like value="[3.1840]"
            Regex(
                """value="\[([0-9]+\.[0-9]+)\]""""
            ),

            // Field with name rateList
            Regex(
                """_rateList"[^>]*value="\[?([0-9]+\.[0-9]+)\]?"""
            ),

            // Example page text:
            // SGD 1.00 = MYR 3.1716
            Regex(
                """SGD\s*1(?:\.0+)?\s*=\s*MYR\s*([0-9]+(?:\.[0-9]+)?)"""
            ),

            // Reverse text layout
            Regex(
                """SGD\s*1(?:\.0+)?[^0-9]{0,100}MYR\s*([0-9]+(?:\.[0-9]+)?)"""
            ),

            // Common embedded field names, but only explicit
            // CIMB/TT-related fields.
            Regex(
                """"ttRate"\s*:\s*"?([0-9]+(?:\.[0-9]+)?)"""
            ),

            Regex(
                """"cimbRate"\s*:\s*"?([0-9]+(?:\.[0-9]+)?)"""
            ),

            Regex(
                """"sgdMyrRate"\s*:\s*"?([0-9]+(?:\.[0-9]+)?)"""
            ),

            Regex(
                """"SGD_MYR"\s*:\s*"?([0-9]+(?:\.[0-9]+)?)"""
            )
        )

        for (pattern in patterns) {

            val match = pattern.find(html)

            if (match != null) {

                val value = match
                    .groupValues
                    .getOrNull(1)
                    ?.toDoubleOrNull()

                if (value != null && isValidCimbRate(value)) {

                    val rounded = roundRate(value, 4)

                    Log.i(
                        TAG,
                        "CIMB parser matched rate=$rounded"
                    )

                    return rounded
                }
            }
        }

        /**
         * Do not blindly use "buyRate", "sellRate", or generic
         * "exchangeRate" here. Those can represent a different
         * direction/type of quote.
         */
        Log.w(
            TAG,
            "CIMB parser: no explicit SGD -> MYR rate found"
        )

        return null
    }

    private fun fallbackCimb(
        error: String
    ): FetchResult {

        val rate = lastCimbRate

        if (rate != null) {

            Log.i(
                TAG,
                "CIMB FALLBACK rate=$rate"
            )

            Log.i(
                TAG,
                "CIMB live = false"
            )

            return FetchResult(
                rate = rate,
                feeSgd = null,
                isLive = false,
                updatedAt = lastCimbUpdated,
                error = error
            )
        }

        Log.i(
            TAG,
            "CIMB has no previous successful rate"
        )

        return FetchResult(
            rate = null,
            feeSgd = null,
            isLive = false,
            updatedAt = 0L,
            error = error
        )
    }

    // ============================================================
    // WISE CALCULATION
    // ============================================================

    /**
     * Calculate the amount received from the actual Wise fee.
     *
     * Example from the supplied page:
     *
     * 1,000 SGD
     * - 3.08 SGD fee
     * = 996.92 SGD
     *
     * 996.92 × 3.19228
     * = 3,182.45 MYR
     */
    fun calculateWiseReceived(
        sendAmountSgd: Double,
        wiseRate: Double
    ): Double {

        val fee = calculateWiseFee(sendAmountSgd)

        val amountAfterFee =
            (sendAmountSgd - fee)
                .coerceAtLeast(0.0)

        return roundRate(
            amountAfterFee * wiseRate,
            2
        )
    }

    /**
     * Uses the latest fee parsed from the Wise page.
     *
     * If the current request failed, the last successfully parsed
     * fee is used.
     *
     * For the supplied 1,000 SGD page:
     * 3.08 SGD.
     */
    fun calculateWiseFee(
        sendAmountSgd: Double
    ): Double {

        val currentFee = lastWiseFeeSgd

        if (currentFee != null) {

            val feeRate =
                currentFee / WISE_SEND_AMOUNT

            return roundRate(
                sendAmountSgd * feeRate,
                2
            )
        }

        return 0.0
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private fun isValidWiseRate(
        rate: Double
    ): Boolean {
        return rate > 2.5 &&
                rate < 4.0 &&
                rate.isFinite()
    }

    private fun isValidCimbRate(
        rate: Double
    ): Boolean {
        return rate > 2.5 &&
                rate < 4.0 &&
                rate.isFinite()
    }

    // ============================================================
    // STATUS
    // ============================================================

    private fun buildStatusMessage(
        wise: FetchResult,
        cimb: FetchResult
    ): String {

        return when {

            wise.isLive && cimb.isLive ->
                "Wise 与 CIMB 实时网络抓取成功"

            wise.isLive && !cimb.isLive ->
                "Wise 实时成功，CIMB 使用上次成功汇率"

            !wise.isLive && cimb.isLive ->
                "CIMB 实时成功，Wise 使用上次成功汇率"

            wise.rate != null || cimb.rate != null ->
                "部分实时抓取失败，使用上次成功汇率"

            else ->
                "无法获取汇率"
        }
    }

    // ============================================================
    // ROUNDING
    // ============================================================

    private fun roundRate(
        value: Double,
        decimals: Int
    ): Double {

        val multiplier =
            Math.pow(
                10.0,
                decimals.toDouble()
            )

        return round(
            value * multiplier
        ) / multiplier
    }

    // ============================================================
    // INTERNAL DATA
    // ============================================================

    private data class WiseWebData(
        val rate: Double,
        val feeSgd: Double?
    )

    private data class FetchResult(
        val rate: Double?,
        val feeSgd: Double?,
        val isLive: Boolean,
        val updatedAt: Long,
        val error: String?
    )
}
