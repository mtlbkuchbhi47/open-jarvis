package com.openjarvis.finance

import kotlin.math.max

/** Deterministic local backtester. Historical results are not guarantees of future performance. */
object BacktestEngine {
    data class Config(val startingCash: Double = 10_000.0, val feePct: Double = 0.10, val stopLossPct: Double = 1.5, val takeProfitPct: Double = 3.0)
    data class Result(val finalEquity: Double, val returnPct: Double, val maxDrawdownPct: Double, val trades: Int, val wins: Int)

    fun sma(values: List<Double>, n: Int): Double? = if (values.size < n) null else values.takeLast(n).average()

    fun run(closes: List<Double>, config: Config = Config()): Result {
        require(closes.size >= 50) { "At least 50 closes are required" }
        var cash = config.startingCash
        var shares = 0.0
        var entry = 0.0
        var peak = cash
        var maxDd = 0.0
        var trades = 0
        var wins = 0
        for (i in 50 until closes.size) {
            val price = closes[i]
            val s20 = closes.subList(i - 20, i).average()
            val s50 = closes.subList(i - 50, i).average()
            val equity = cash + shares * price
            peak = max(peak, equity)
            maxDd = max(maxDd, if (peak == 0.0) 0.0 else (peak - equity) / peak * 100.0)
            if (shares > 0 && (price <= entry * (1 - config.stopLossPct / 100) || price >= entry * (1 + config.takeProfitPct / 100) || s20 < s50)) {
                cash += shares * price * (1 - config.feePct / 100)
                if (price > entry) wins++
                shares = 0.0; trades++
            } else if (shares == 0.0 && s20 > s50) {
                val allocation = cash * 0.10
                shares = allocation / price
                cash -= allocation * (1 + config.feePct / 100)
                entry = price
            }
        }
        val finalEquity = cash + shares * closes.last()
        return Result(finalEquity, (finalEquity / config.startingCash - 1) * 100, maxDd, trades, wins)
    }
}
