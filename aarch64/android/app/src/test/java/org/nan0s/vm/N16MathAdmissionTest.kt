package org.nan0s.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class N16MathAdmissionTest {
    @Test
    fun keepsRuleAndTypedDenialsDisjoint() {
        val output = IntArray(8)
        val allowed = N16MathAdmission.Decision(0, output, 42)
        val staleRule = N16MathAdmission.Decision(18, output, 51)
        val badShape = N16MathAdmission.Decision(38, output, 57)
        val unrelated = N16MathAdmission.Decision(9, output, 10)

        assertTrue(allowed.allowed)
        assertEquals(null, allowed.ruleDenial)
        assertEquals(null, allowed.typedDenial)
        assertFalse(staleRule.allowed)
        assertEquals(2, staleRule.ruleDenial)
        assertEquals(null, staleRule.typedDenial)
        assertEquals(6, badShape.typedDenial)
        assertEquals(null, badShape.ruleDenial)
        assertEquals(null, unrelated.ruleDenial)
        assertEquals(null, unrelated.typedDenial)
    }
}
