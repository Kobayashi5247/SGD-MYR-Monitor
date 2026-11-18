package com.example.model

data class RateQuote(
    val providerName: String, // "Wise" or "CIMB SG"
    val providerCode: String, // "WISE" or "CIMB_SG"
    val rate: Double, // e.g. 3.2850 MYR
    val feePercentage: Double = 0.0, // e.g. 0.43% for Wise, 0.0 for CIMB
    val fixedFeeSgd: Double = 0.0, // e.g. 0.45 SGD for Wise, 0.0 for CIMB
    val lastUpdated: Long = System.currentTimeMillis(),
    val sourceUrl: String = "",
    val isLiveWeb: Boolean = true
) {
    /**
     * Wise and CIMB actual received amount calculation.
     * Wise: deducts fixed fee + percentage fee from SGD input, then converts at mid-market rate.
     * CIMB: applies direct remittance exchange rate.
     */
    fun calculateNetReceived(sgdAmount: Double): Double {
        val totalFee = fixedFeeSgd + (sgdAmount * (feePercentage / 100.0))
        val netSgd = (sgdAmount - totalFee).coerceAtLeast(0.0)
        return netSgd * rate
    }

    fun calculateTotalFeeSgd(sgdAmount: Double): Double {
        return fixedFeeSgd + (sgdAmount * (feePercentage / 100.0))
    }
}

data class CombinedExchangeRates(
    val wiseQuote: RateQuote,
    val cimbQuote: RateQuote,
    val timestamp: Long = System.currentTimeMillis(),
    val statusMessage: String = "Live Rate Connected"
)
