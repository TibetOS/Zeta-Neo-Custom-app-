package com.traffko.outlanderhub.vehicle.topway

/**
 * One reflective session on the Topway framework serial mux
 * `android.tw.john.TWUtil` (see `research/topway-ts18/`): construct → open →
 * attach receive handler → start, plus write() for commands and close() for
 * teardown. The receiver is typed [Any] so the whole resolution/lifecycle can
 * be unit-tested against fakes off-device — the real caller passes an
 * `android.os.Handler`, to which TWUtil delivers each MCU message as a
 * `Message` carrying what/arg1/arg2/obj.
 *
 * The receive-attach method name is not in our decompiled references, so it is
 * resolved by candidate name and shape; when none matches, the class's actual
 * method surface is reported through [info] so the next build can adapt.
 */
class TwUtilLink private constructor(
    private val instance: Any,
    private val attachedTag: String?,
) {

    /**
     * TWUtil.write(what[, arg1[, arg2]]) — picks the widest overload the class
     * offers up to the requested arity, padding missing ints with 0. The MCU
     * write surface carries at most what+two ints; longer payloads need the
     * byte[]/String overloads, which stay out until a real message needs them.
     * Null when the class has no matching write overload at all.
     */
    fun write(what: Int, args: IntArray): Int? {
        val int = Int::class.javaPrimitiveType!!
        val values = intArrayOf(what, args.getOrElse(0) { 0 }, args.getOrElse(1) { 0 })
        val preferred = minOf(args.size, 2) + 1
        for (arity in listOf(preferred, 3, 2, 1).distinct()) {
            val method = runCatching {
                instance.javaClass.getMethod("write", *Array(arity) { int })
            }.getOrNull() ?: continue
            val boxed = values.take(arity).map { it as Any }.toTypedArray()
            return (method.invoke(instance, *boxed) as Number).toInt()
        }
        return null
    }

    fun close(info: (String) -> Unit) {
        attachedTag?.let { tag ->
            runCatching {
                instance.javaClass.getMethod("removeHandler", String::class.java).invoke(instance, tag)
            }
        }
        runCatching { instance.javaClass.getMethod("stop").invoke(instance) }
        runCatching { instance.javaClass.getMethod("close").invoke(instance) }
        info("TWUtil session closed")
    }

    companion object {
        // "radio", not our own name: the framework's sendHandler() routes every
        // MCU push through getHandler("radio") with no fallback
        // (research/topway-ts18/community/TWUtil-real.java:158), so any other tag
        // receives nothing on that path. If the undecompiled pollEventFromNative
        // path fans out to all tags instead (how KaierUtils works with its own
        // tag), "radio" still receives — it wins under either dispatch.
        const val HANDLER_TAG = "radio"

        /**
         * Flatten a received msg.obj into event payload lists. The firmware
         * class delivers text as String, binary as byte[]/short[]/int[], and —
         * when one message carries two payloads — a TWUtil.TWObject pairing
         * obj3+obj4 (Fernflower decompile in research/topway-ts18/community/),
         * matched by name and unpacked via its public fields.
         */
        fun decodePayload(obj: Any?, ints: MutableList<Int>, strings: MutableList<String>) {
            when (obj) {
                null -> Unit
                is ByteArray -> {
                    ints += obj.map { it.toInt() and 0xFF }
                    strings += obj.joinToString(" ") { "%02x".format(it) }
                }
                is ShortArray -> ints += obj.map { it.toInt() }
                is IntArray -> ints += obj.toList()
                is String -> strings += obj
                else ->
                    if (obj.javaClass.simpleName == "TWObject") {
                        for (field in listOf("obj3", "obj4")) {
                            runCatching { obj.javaClass.getField(field).get(obj) }.getOrNull()
                                ?.let { decodePayload(it, ints, strings) }
                        }
                    } else {
                        strings += obj.toString()
                    }
            }
        }

        /**
         * Stand a full session up; every step and failure is narrated through
         * [info]. Null means no session — the instance is always torn back
         * down on the way out.
         */
        fun open(
            cls: Class<*>,
            ids: ShortArray,
            baud: Int,
            receiver: Any,
            info: (String) -> Unit,
        ): TwUtilLink? {
            // These breadcrumbs are load-bearing: a native SIGABRT in the vendor
            // library takes the process down with no JVM exception, so the last
            // breadcrumb in the exported log is the only evidence of which step
            // aborted. Emit one before every vendor call.
            info("step: constructing ${cls.simpleName}")
            val instance = construct(cls)
            if (instance == null) {
                info("TWUtil has no usable constructor")
                return null
            }
            try {
                info("step: open(${ids.size} ids, $baud)")
                val rc = invokeOpen(instance, ids, baud)
                if (rc == null) {
                    info("TWUtil has no open(short[]) surface — ${dumpMethods(cls)}")
                    return teardown(instance)
                }
                if (rc != 0) {
                    info("TWUtil.open(${ids.size} ids) returned $rc — serial not acquired")
                    return teardown(instance)
                }
                // open → start → addHandler is the order every reference client
                // uses (KaierUtils, d51x/TWUtil, com.tw.bt) — keep it.
                info("step: start")
                instance.javaClass.getMethod("start").invoke(instance)
                info("step: attach receiver")
                val attached = attach(instance, receiver)
                if (attached == null) {
                    info("no handler-attach method found — ${dumpMethods(cls)}")
                    return teardown(instance)
                }
                info("TWUtil open(${ids.size} ids)=0, started, receiver via $attached")
                return TwUtilLink(instance, if (attached.contains("tag")) HANDLER_TAG else null)
            } catch (e: Throwable) {
                val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
                info("TWUtil session failed: ${cause.javaClass.simpleName}: ${cause.message}")
                return teardown(instance)
            }
        }

        // Both ctors exist in the wild: Orbit/dvd-bt/KaierUtils construct no-arg,
        // while fytFM and Radio-MST768 use the (int) ctor — its arg is a vendor
        // *mode id* (Radio passes 1 = radio), NOT a CAN channel, so feeding it an
        // invented index can SIGABRT in native code (uncatchable by JVM). Prefer
        // no-arg; fall back to (int) with mode 0 only if no-arg is truly absent.
        // See research/topway-ts18/community/README.md for the per-client split.
        private fun construct(cls: Class<*>): Any? {
            runCatching { return cls.getConstructor().newInstance() }
            runCatching { return cls.getConstructor(Int::class.javaPrimitiveType).newInstance(0) }
            return null
        }

        private fun invokeOpen(instance: Any, ids: ShortArray, baud: Int): Int? {
            val cls = instance.javaClass
            runCatching { cls.getMethod("open", ShortArray::class.java, Int::class.javaPrimitiveType) }
                .getOrNull()?.let { return (it.invoke(instance, ids, baud) as Number).toInt() }
            runCatching { cls.getMethod("open", ShortArray::class.java) }
                .getOrNull()?.let { return (it.invoke(instance, ids) as Number).toInt() }
            return null
        }

        /** Try the known attach spellings; returns a description or null. */
        private fun attach(instance: Any, receiver: Any): String? {
            for (name in listOf("addHandler", "setHandler", "registerHandler")) {
                for (method in instance.javaClass.methods.filter { it.name == name }) {
                    val p = method.parameterTypes
                    when {
                        p.size == 2 && p[0] == String::class.java && p[1].isAssignableFrom(receiver.javaClass) -> {
                            method.invoke(instance, HANDLER_TAG, receiver)
                            return "$name(tag, handler)"
                        }
                        p.size == 1 && p[0].isAssignableFrom(receiver.javaClass) -> {
                            method.invoke(instance, receiver)
                            return "$name(handler)"
                        }
                    }
                }
            }
            return null
        }

        private fun teardown(instance: Any): TwUtilLink? {
            runCatching { instance.javaClass.getMethod("stop").invoke(instance) }
            runCatching { instance.javaClass.getMethod("close").invoke(instance) }
            return null
        }

        private fun dumpMethods(cls: Class<*>): String =
            "class surface: " + cls.methods
                .filter { it.declaringClass != Any::class.java }
                .joinToString(", ") { m ->
                    "${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }})"
                }.take(1200)
    }
}
