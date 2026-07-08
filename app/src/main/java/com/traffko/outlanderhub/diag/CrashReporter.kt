package com.traffko.outlanderhub.diag

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Last-resort crash capture for the unit, which has no adb: an uncaught JVM
 * exception is written to a file before the process dies, then surfaced into
 * the CAN log on the next launch so the stack trace can be read on-screen.
 *
 * Native aborts (SIGABRT from the vendor MCU library) bypass the JVM handler
 * entirely and cannot be caught here — their absence from this file is itself
 * the signal that the crash was native, not Kotlin.
 */
object CrashReporter {

    private const val FILE_NAME = "last-crash.txt"
    private const val BREADCRUMB_NAME = "last-breadcrumb.txt"
    private const val ARMED_NAME = "topway-armed"

    @Volatile
    private var breadcrumbFile: File? = null

    @Volatile
    private var armedFile: File? = null

    /**
     * A crash-loop guard for the MCU bring-up: [arm] writes a latch file
     * synchronously before the first vendor call; [disarm] removes it once the
     * session is safely LIVE. If [isArmed] is still true at the next start, the
     * previous attempt took the whole process down before going live — the
     * caller must refuse to retry automatically and fall back to a safe source.
     */
    fun arm() {
        runCatching { armedFile?.writeText("1") }
    }

    fun disarm() {
        runCatching { armedFile?.delete() }
    }

    fun isArmed(): Boolean = armedFile?.exists() == true

    /**
     * Persist the last-reached step synchronously. A native abort leaves no JVM
     * exception, so on the next launch this file is the only record of which
     * vendor call took the process down. Best-effort and cheap — it runs in the
     * hot path of the MCU bring-up.
     */
    fun breadcrumb(step: String) {
        runCatching { breadcrumbFile?.writeText(step) }
    }

    fun install(context: Context) {
        breadcrumbFile = File(context.filesDir, BREADCRUMB_NAME)
        armedFile = File(context.filesDir, ARMED_NAME)
        val file = File(context.filesDir, FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                file.writeText("thread=${thread.name}\n$trace")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * The stored crash text and/or the last breadcrumb, then delete both so
     * they surface once. A JVM crash yields the full trace; a native abort
     * yields only the breadcrumb (the step that aborted). Null if neither.
     */
    fun consume(context: Context): String? {
        val crash = File(context.filesDir, FILE_NAME)
        val crumb = File(context.filesDir, BREADCRUMB_NAME)
        val trace = crash.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
        val step = crumb.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
        runCatching { crash.delete() }
        runCatching { crumb.delete() }
        return when {
            trace != null && step != null -> "$trace\n(last MCU step reached: $step)"
            trace != null -> trace
            step != null -> "no JVM exception — native abort. last MCU step reached: $step"
            else -> null
        }
    }
}
