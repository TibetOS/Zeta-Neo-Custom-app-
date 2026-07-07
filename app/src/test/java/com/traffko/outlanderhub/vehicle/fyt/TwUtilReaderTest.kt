package com.traffko.outlanderhub.vehicle.fyt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the [TwUtilReader] reflective lifecycle against fake stand-ins for
 * `android.tw.john.TWUtil` — the real class only exists on Topway firmware, so
 * these lock in that each outcome (serial acquired / port held / open threw /
 * wrong surface) is detected and reported correctly, and that the handle is
 * always torn down.
 */
class TwUtilReaderTest {

    /** open()==0 then start(): the "readable" path. Records lifecycle order. */
    class LiveTwUtil {
        fun open(ids: ShortArray, baud: Int): Int {
            calls += "open(${ids.size},$baud)"
            return 0
        }
        fun start() { calls += "start" }
        fun stop() { calls += "stop" }
        fun close() { calls += "close" }
        companion object { val calls = mutableListOf<String>() }
    }

    /** open() returns non-zero: serial held by the vendor app. */
    class BusyTwUtil {
        fun open(ids: ShortArray, baud: Int): Int = -1
        fun start() { error("must not start when open() failed") }
        fun stop() {}
        fun close() { closed = true }
        companion object { var closed = false }
    }

    /** open() throws: link brought up but errored. */
    class ThrowingTwUtil {
        fun open(ids: ShortArray, baud: Int): Int = throw IllegalStateException("serial boom")
        fun start() {}
        fun stop() {}
        fun close() { closed = true }
        companion object { var closed = false }
    }

    /** No open(short[],int) at all: surface differs from the reference. */
    class WrongSurfaceTwUtil {
        fun frobnicate(): Int = 0
    }

    @Test
    fun `open zero and start reports the link live`() {
        LiveTwUtil.calls.clear()
        val outcome = TwUtilReader.openStartClose(LiveTwUtil::class.java)
        assertTrue(outcome, outcome.contains("LIVE"))
        assertTrue(outcome, outcome.contains("readable via TWUtil"))
        // Opened, started, then always torn down (stop before close).
        assertTrue(LiveTwUtil.calls.toString(), LiveTwUtil.calls.first().startsWith("open"))
        assertTrue(LiveTwUtil.calls.toString(), LiveTwUtil.calls.containsAll(listOf("start", "stop", "close")))
    }

    @Test
    fun `non-zero open reports serial not acquired and does not start`() {
        BusyTwUtil.closed = false
        val outcome = TwUtilReader.openStartClose(BusyTwUtil::class.java)
        assertTrue(outcome, outcome.contains("serial not acquired"))
        assertFalse(outcome, outcome.contains("LIVE"))
        assertTrue("close() must run even on failure", BusyTwUtil.closed)
    }

    @Test
    fun `throwing open is reported with the real cause and still closes`() {
        ThrowingTwUtil.closed = false
        val outcome = TwUtilReader.openStartClose(ThrowingTwUtil::class.java)
        assertTrue(outcome, outcome.contains("threw"))
        assertTrue(outcome, outcome.contains("serial boom"))
        assertTrue("close() must run after a throw", ThrowingTwUtil.closed)
    }

    @Test
    fun `missing open method is reported as a differing surface`() {
        val outcome = TwUtilReader.openStartClose(WrongSurfaceTwUtil::class.java)
        assertTrue(outcome, outcome.contains("no open(short[], int) method"))
    }

    @Test
    fun `attemptLiveRead wraps the live path with the timeout guard`() {
        LiveTwUtil.calls.clear()
        val outcome = TwUtilReader.attemptLiveRead(LiveTwUtil::class.java, timeoutMs = 2000L)
        assertTrue(outcome, outcome.contains("LIVE"))
    }

    @Test
    fun `resolveClass returns null for an absent class`() {
        assertTrue(TwUtilReader.resolveClass(javaClass.classLoader) == null)
    }
}
