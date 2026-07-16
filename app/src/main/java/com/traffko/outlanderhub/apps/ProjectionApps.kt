package com.traffko.outlanderhub.apps

import android.content.pm.PackageManager

/**
 * Finds the CarPlay/Android Auto projection app on the unit. FYT firmwares
 * bundle one of a handful of known projection clients (ZLink on the Zeta Neo);
 * auto-detection covers those, and Setup can pin any other installed app.
 */
object ProjectionApps {

    /** Projection clients seen on FYT units, in preference order. */
    val KNOWN = listOf(
        "com.zjinnova.zlink",
        "com.zjinnova.zlink3",
        "com.zjinnova.zbox",
        "net.easyconn",
        "com.autokit.efc",
    )

    /**
     * Picks the projection package from [installed]: the user's [override]
     * when it is still installed, otherwise the first known client found.
     * Pure, for JVM tests; [resolve] is the PackageManager-backed variant.
     */
    fun pick(installed: Collection<String>, override: String?): String? =
        (listOfNotNull(override) + KNOWN).firstOrNull { it in installed }

    fun resolve(pm: PackageManager, override: String?): String? =
        (listOfNotNull(override) + KNOWN).firstOrNull { pm.getLaunchIntentForPackage(it) != null }
}
