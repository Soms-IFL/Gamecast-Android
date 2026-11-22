package com.iflgamecast.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The score celebration is drawn from these curves; check they start, settle and end where the animation expects. */
class CelebrationMathTest {
    @Test fun segmentClampsToZeroAndOne() {
        assertEquals(0f, segment(-1f, 0.2f, 0.4f), 0f)
        assertEquals(0.5f, segment(0.3f, 0.2f, 0.4f), 1e-6f)
        assertEquals(1f, segment(2f, 0.2f, 0.4f), 0f)
    }

    @Test fun easeOutCubicRunsFromZeroToOne() {
        assertEquals(0f, easeOutCubic(0f), 1e-6f)
        assertEquals(1f, easeOutCubic(1f), 1e-6f)
        assertTrue(easeOutCubic(0.5f) > 0.5f)
    }

    @Test fun logoPopOvershootsThenSettlesAtOne() {
        assertEquals(0f, easeOutBack(0f), 1e-6f)
        assertEquals(1f, easeOutBack(1f), 1e-6f)
        assertTrue("the pop should overshoot 1.0", (1..99).any { easeOutBack(it / 100f) > 1.05f })
    }

    @Test fun celebrationIsFullyVisibleInTheMiddleAndGoneAtTheEnd() {
        assertEquals(1f, fadeOut(0f), 0f)
        assertEquals(1f, fadeOut(0.5f), 0f)
        assertEquals(0f, fadeOut(1f), 1e-6f)
    }
}
