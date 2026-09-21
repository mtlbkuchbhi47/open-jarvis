package com.openjarvis.science

import org.junit.Assert.assertTrue
import org.junit.Test

class ScienceEngineTest {
    @Test fun force_is_correct() { assertTrue(ScienceEngine.calculate("force mass 2 acceleration 3").contains("6.0 N")) }
}
