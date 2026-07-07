package com.traffko.outlanderhub.vehicle.fyt

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Reflective driver for the Topway framework class `android.tw.john.TWUtil` —
 * the serial multiplexer that carries CAN/MCU data on Topway TS units (see
 * `research/topway-ts18/`). Kept free of Android dependencies so the lifecycle
 * can be unit-tested against a fake class with the same method surface.
 *
 * This is a *probe*, not a full bus: it stands the serial pipeline up
 * (`open()`/`start()`) far enough to learn whether the MCU is reachable, then
 * always tears it back down — it does not stream frames. Constructing a full
 * `TopwayVehicleBus` on this surface is the follow-up once a real unit confirms
 * open() succeeds.
 */
object TwUtilReader {

    const val CLASS_NAME = "android.tw.john.TWUtil"

    // The CANBUS channel is module 7 (same index the FYT toolkit uses for
    // CANBUS); 115200 baud is the rate both reference implementations open at.
    // The message-id array is what open() subscribes; for a probe the point is
    // whether open() acquires the port (returns 0), so a minimal id set is
    // enough — the live channel/id map is harvested from the unit's own canbus
    // APK once TWUtil is confirmed reachable.
    const val CANBUS_CHANNEL = 7
    const val SERIAL_BAUD = 115200
    val CANBUS_MESSAGE_IDS = shortArrayOf(CANBUS_CHANNEL.toShort())

    const val LIVE_READ_TIMEOUT_MS = 2500L

    /**
     * Resolve [CLASS_NAME] on [classLoader] without initializing it. Returns the
     * class if present on the boot classpath (Topway firmware), else null.
     */
    fun resolveClass(classLoader: ClassLoader): Class<*>? = try {
        Class.forName(CLASS_NAME, false, classLoader)
    } catch (_: Throwable) {
        null
    }

    /**
     * Bring the serial link up on a short-lived worker with a hard timeout
     * (open() on a held port can block), and always close it. Returns a single
     * human-readable outcome line. [timeoutMs] is injectable for tests.
     */
    fun attemptLiveRead(twUtilClass: Class<*>, timeoutMs: Long = LIVE_READ_TIMEOUT_MS): String {
        val worker = Executors.newSingleThreadExecutor()
        val future = worker.submit<String> { openStartClose(twUtilClass) }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            "TWUtil live read timed out after ${timeoutMs}ms — open() likely blocked (serial held?)"
        } catch (e: Exception) {
            val cause = e.cause ?: e
            "TWUtil live read failed: ${cause.javaClass.simpleName}: ${cause.message}"
        } finally {
            worker.shutdownNow()
        }
    }

    /** open()+start(), then always close(); returns a human-readable outcome. */
    internal fun openStartClose(twUtilClass: Class<*>): String {
        val instance = construct(twUtilClass)
            ?: return "TWUtil present but could not be constructed (no usable constructor)"
        var opened = false
        try {
            val rc = invokeOpen(instance)
                ?: return "TWUtil constructed but has no open(short[], int) method — surface differs, dump the class"
            if (rc != 0) return "TWUtil.open() returned $rc (non-zero) — serial not acquired; MCU may be held by the vendor app"
            opened = true
            invokeNoArg(instance, "start")
            return "TWUtil.open()=0 and start() OK — MCU serial link is LIVE; this unit's data is readable via TWUtil"
        } catch (e: Throwable) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            return "TWUtil open/start threw ${cause.javaClass.simpleName}: ${cause.message}"
        } finally {
            if (opened) runCatching { invokeNoArg(instance, "stop") }
            runCatching { invokeNoArg(instance, "close") }
        }
    }

    /** Construct via the (int) channel constructor, falling back to no-arg. */
    internal fun construct(cls: Class<*>): Any? {
        runCatching { return cls.getConstructor(Int::class.javaPrimitiveType).newInstance(CANBUS_CHANNEL) }
        runCatching { return cls.getConstructor().newInstance() }
        return null
    }

    /**
     * open(short[] messageIds, int baud), preferring the 2-arg form and falling
     * back to open(short[]); null only if neither method exists. A method that
     * exists but *throws* is not swallowed — the exception propagates so the
     * caller reports it as a live-read error, not a missing surface.
     */
    internal fun invokeOpen(instance: Any): Int? {
        val method = resolveMethod(instance, "open", ShortArray::class.java, Int::class.javaPrimitiveType!!)
        if (method != null) {
            return (method.invoke(instance, CANBUS_MESSAGE_IDS, SERIAL_BAUD) as Number).toInt()
        }
        val method1 = resolveMethod(instance, "open", ShortArray::class.java)
        if (method1 != null) {
            return (method1.invoke(instance, CANBUS_MESSAGE_IDS) as Number).toInt()
        }
        return null
    }

    /** getMethod or null if absent — separates "no such method" from "method threw". */
    private fun resolveMethod(instance: Any, name: String, vararg params: Class<*>) = try {
        instance.javaClass.getMethod(name, *params)
    } catch (_: NoSuchMethodException) {
        null
    }

    internal fun invokeNoArg(instance: Any, name: String) {
        instance.javaClass.getMethod(name).invoke(instance)
    }
}
