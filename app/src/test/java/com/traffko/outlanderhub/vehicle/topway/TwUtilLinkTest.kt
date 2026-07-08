package com.traffko.outlanderhub.vehicle.topway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the [TwUtilLink] reflective session against fake stand-ins for
 * `android.tw.john.TWUtil`, mirroring [TwUtilReaderTest]: the real class only
 * exists on Topway firmware, so these lock in the lifecycle order, the
 * handler-attach fallbacks, the write-arity ladder, and that every failure
 * path tears the instance down.
 */
class TwUtilLinkTest {

    class Receiver

    class LiveTw(channel: Int) {
        init { constructed += channel }
        fun open(ids: ShortArray, baud: Int): Int { calls += "open(${ids.size},$baud)"; return 0 }
        fun addHandler(tag: String, h: Receiver) { calls += "addHandler($tag)" }
        fun removeHandler(tag: String) { calls += "removeHandler($tag)" }
        fun start() { calls += "start" }
        fun stop() { calls += "stop" }
        fun close() { calls += "close" }
        fun write(what: Int, a: Int): Int { calls += "write($what,$a)"; return 0 }
        fun write(what: Int, a: Int, b: Int): Int { calls += "write($what,$a,$b)"; return 1 }
        companion object {
            val calls = mutableListOf<String>()
            val constructed = mutableListOf<Int>()
        }
    }

    class SetHandlerTw {
        fun open(ids: ShortArray, baud: Int): Int = 0
        fun setHandler(h: Receiver) { attached = true }
        fun start() {}
        fun stop() {}
        fun close() {}
        companion object { var attached = false }
    }

    class BusyTw(channel: Int) {
        fun open(ids: ShortArray, baud: Int): Int = -1
        fun start() { error("must not start when open() failed") }
        fun stop() {}
        fun close() { closed = true }
        companion object { var closed = false }
    }

    class NoAttachTw {
        fun open(ids: ShortArray, baud: Int): Int = 0
        fun start() {}
        fun stop() {}
        fun close() { closed = true }
        companion object { var closed = false }
    }

    class WriteThreeOnlyTw {
        fun open(ids: ShortArray, baud: Int): Int = 0
        fun addHandler(tag: String, h: Receiver) {}
        fun start() {}
        fun stop() {}
        fun close() {}
        fun write(what: Int, a: Int, b: Int): Int { last = "write($what,$a,$b)"; return 0 }
        companion object { var last = "" }
    }

    private val ids = shortArrayOf(1, 2, 3)
    private val info = mutableListOf<String>()

    private fun openLive(): TwUtilLink? {
        LiveTw.calls.clear()
        LiveTw.constructed.clear()
        info.clear()
        return TwUtilLink.open(LiveTw::class.java, 7, ids, 115200, Receiver(), info::add)
    }

    @Test
    fun `live session opens in order construct-open-start-attach`() {
        val link = openLive()
        assertNotNull(info.joinToString(), link)
        assertEquals(listOf(7), LiveTw.constructed)
        assertEquals(listOf("open(3,115200)", "start", "addHandler(${TwUtilLink.HANDLER_TAG})"), LiveTw.calls)
        assertTrue(info.joinToString(), info.any { it.contains("started") })
    }

    @Test
    fun `close removes the handler then stops then closes`() {
        val link = openLive()!!
        LiveTw.calls.clear()
        link.close(info::add)
        assertEquals(listOf("removeHandler(${TwUtilLink.HANDLER_TAG})", "stop", "close"), LiveTw.calls)
    }

    @Test
    fun `write picks the overload matching the argument count`() {
        val link = openLive()!!
        LiveTw.calls.clear()
        assertEquals(0, link.write(40448, intArrayOf(7)))
        assertEquals(1, link.write(769, intArrayOf(192, 7)))
        assertEquals(listOf("write(40448,7)", "write(769,192,7)"), LiveTw.calls)
    }

    @Test
    fun `write falls back to a wider overload padding with zeros`() {
        val link = TwUtilLink.open(WriteThreeOnlyTw::class.java, 7, ids, 115200, Receiver(), info::add)!!
        assertEquals(0, link.write(517, intArrayOf()))
        assertEquals("write(517,0,0)", WriteThreeOnlyTw.last)
    }

    @Test
    fun `setHandler is found when addHandler is absent`() {
        SetHandlerTw.attached = false
        val link = TwUtilLink.open(SetHandlerTw::class.java, 7, ids, 115200, Receiver(), info::add)
        assertNotNull(info.joinToString(), link)
        assertTrue(SetHandlerTw.attached)
    }

    @Test
    fun `non-zero open reports and tears down`() {
        BusyTw.closed = false
        info.clear()
        val link = TwUtilLink.open(BusyTw::class.java, 7, ids, 115200, Receiver(), info::add)
        assertNull(link)
        assertTrue(info.joinToString(), info.any { it.contains("returned -1") })
        assertTrue("close() must run on failure", BusyTw.closed)
    }

    @Test
    fun `missing attach method dumps the class surface and tears down`() {
        NoAttachTw.closed = false
        info.clear()
        val link = TwUtilLink.open(NoAttachTw::class.java, 7, ids, 115200, Receiver(), info::add)
        assertNull(link)
        assertTrue(info.joinToString(), info.any { it.contains("class surface") && it.contains("open") })
        assertTrue("close() must run when no attach method exists", NoAttachTw.closed)
    }
}
