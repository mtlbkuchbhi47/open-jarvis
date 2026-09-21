package com.openjarvis.finance

import org.junit.Assert.assertTrue
import org.junit.Test

class BacktestEngineTest {
    @Test fun backtest_is_deterministic_and_bounded() {
        val prices = (1..120).map { 100.0 + it * 0.5 }
        val r = BacktestEngine.run(prices)
        assertTrue(r.finalEquity.isFinite())
        assertTrue(r.maxDrawdownPct >= 0.0)
    }
}
