package com.traffko.outlanderhub.vehicle.fyt

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Reads the *shape* of `android.tw.john.TWUtil` (and nested types) purely from
 * reflection metadata — constructors, methods, fields — without invoking any of
 * them. Invoking a vendor method (e.g. `open()` on the CANBUS channel) can abort
 * the process natively; reading its signature cannot. This is what replaced the
 * live-read probe after the live read SIGABRT-looped on the real unit, and it
 * doubles as an on-device decompile of the exact API we must call.
 */
object TwUtilInspector {

    /** Full human-readable dump of the class surface, one entry per line. */
    fun dump(cls: Class<*>): List<String> {
        val out = mutableListOf<String>()
        out += "class ${cls.name}"
        cls.superclass?.let { out += "  extends ${it.name}" }

        cls.declaredConstructors.sortedBy { it.parameterCount }.forEach { out += "  ${render(it)}" }

        cls.declaredMethods
            .sortedWith(compareBy({ it.name }, { it.parameterCount }))
            .forEach { out += "  ${render(it)}" }

        cls.declaredFields.sortedBy { it.name }.forEach { out += "  ${render(it)}" }

        cls.declaredClasses.forEach { nested ->
            out += "  nested ${nested.simpleName}:"
            nested.declaredConstructors.sortedBy { it.parameterCount }.forEach { out += "    ${render(it)}" }
            nested.declaredFields.sortedBy { it.name }.forEach { out += "    ${render(it)}" }
        }
        return out
    }

    private fun render(c: Constructor<*>): String =
        "${mods(c.modifiers)}ctor(${c.parameterTypes.joinToString(", ") { it.simpleName }})"

    private fun render(m: Method): String =
        "${mods(m.modifiers)}${m.returnType.simpleName} ${m.name}(" +
            m.parameterTypes.joinToString(", ") { it.simpleName } + ")"

    private fun render(f: Field): String =
        "${mods(f.modifiers)}${f.type.simpleName} ${f.name}"

    private fun mods(m: Int): String {
        val parts = buildList {
            if (Modifier.isPublic(m)) add("public")
            if (Modifier.isProtected(m)) add("protected")
            if (Modifier.isPrivate(m)) add("private")
            if (Modifier.isStatic(m)) add("static")
            if (Modifier.isNative(m)) add("native")
        }
        return if (parts.isEmpty()) "" else parts.joinToString(" ") + " "
    }
}
