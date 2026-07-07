package com.traffko.outlanderhub.vehicle.fyt

import android.content.Context
import android.content.pm.PackageManager

/**
 * Diagnostic probe for the **Topway** TS-platform integration surface, run when
 * the FYT (`com.syu.ms`) toolkit is absent — which is the case on the real
 * ZETA NEO 14 (it's a Topway TS18-class unit, not FYT). See
 * `docs/CAN-INTEGRATION.md` and the decompiled reference in
 * `research/topway-ts18/`.
 *
 * On Topway firmware, CAN/MCU data does not come from a bindable toolkit
 * service. It flows over a serial multiplexer exposed by the framework class
 * `android.tw.john.TWUtil` (baked into the boot classpath), which the vendor's
 * own canbus app (`com.tw.carchoose` / `com.tw.car*` / a `CanBus.apk`)
 * consumes. This probe answers the questions that decide the integration path,
 * emitting each result as a `tw-info` line so it lands in the CAN tab and the
 * exportable log:
 *
 *  1. Is `android.tw.john.TWUtil` present? (the decisive fork)
 *  2. Which `com.tw.*` / vehicle-ish packages are actually installed? — checked
 *     by explicit name AND by scanning every package, because Topway names
 *     (`com.tw.carchoose`, …) match none of [FytVehicleBus] CAN keywords and
 *     were being missed by the old keyword-only scan.
 *  3. A full installed-package dump, so nothing is silently overlooked again.
 *
 * Pure diagnostics: it reads and reports, and does not open the serial link or
 * send any MCU command.
 */
class TopwayProbe(
    private val context: Context,
    private val emit: (String) -> Unit,
    private val deepInspect: (String) -> Unit,
) {

    fun run() {
        emit("── Topway platform probe ──")
        val twUtilClass = probeTwUtilClass()
        val installed = probeKnownPackages()
        scanAllPackages(installed)
        // Deep-inspect any confirmed vehicle package (services/providers +
        // binder descriptors) — its own AIDL is a second, bindable CAN path
        // besides TWUtil, and the descriptor tells us how to talk to it.
        installed.filter { it in VEHICLE_PACKAGES }.forEach(deepInspect)
        // If the class resolved, go past "present" to "actually readable":
        // stand up the serial pipeline and see whether the MCU is reachable.
        if (twUtilClass != null) attemptLiveRead(twUtilClass)
    }

    /**
     * The decisive check: `android.tw.john.TWUtil` lives on the boot classpath
     * of Topway firmware, so it resolves by name even though no app declares
     * it. Returns the loaded class if present (so a live read can follow), else
     * null — in which case this isn't a Topway TWUtil unit and OBD-II is the
     * fallback.
     */
    private fun probeTwUtilClass(): Class<*>? {
        val cls = TwUtilReader.resolveClass(context.classLoader)
        emit(
            if (cls != null) "FOUND framework class ${TwUtilReader.CLASS_NAME} — Topway MCU serial surface is present"
            else "${TwUtilReader.CLASS_NAME} NOT resolvable — not a Topway TWUtil unit"
        )
        return cls
    }

    /**
     * Past class-presence: actually bring the serial link up, because "the
     * class exists" and "the MCU answers" are different facts and finding out
     * in-car once beats a second trip. [TwUtilReader] constructs TWUtil, opens
     * the CANBUS channel and starts it under a hard timeout, always tearing the
     * handle back down; an `open()==0` result is the strongest signal short of
     * decoded frames that this unit's data is readable.
     */
    private fun attemptLiveRead(twUtilClass: Class<*>) {
        emit("attempting TWUtil live read on CANBUS channel ${TwUtilReader.CANBUS_CHANNEL} …")
        emit(TwUtilReader.attemptLiveRead(twUtilClass))
    }

    /**
     * Directly resolve the known Topway vehicle/system package names. getPackageInfo
     * by exact name is immune to the keyword blindness of a name-contains scan.
     */
    private fun probeKnownPackages(): Set<String> {
        val present = mutableSetOf<String>()
        for (pkg in KNOWN_TW_PACKAGES) {
            val version = installedVersion(pkg)
            if (version != null) {
                present += pkg
                emit("installed: $pkg ($version)")
            }
        }
        if (present.isEmpty()) emit("none of the ${KNOWN_TW_PACKAGES.size} known Topway packages are installed")
        return present
    }

    /** versionName+code if [pkg] is installed, else null. */
    private fun installedVersion(pkg: String): String? = try {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        "v${info.versionName ?: "?"}"
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        "query failed: ${e.javaClass.simpleName}"
    }

    /**
     * Full sweep of installed package names: report every `com.tw.*` package
     * (already-listed ones excluded) plus anything vehicle-ish, then a total
     * count. This is the keyword-blindness fix — the previous scan only matched
     * a fixed CAN keyword set and missed the entire `com.tw.*` namespace.
     */
    private fun scanAllPackages(alreadyReported: Set<String>) {
        val names = try {
            context.packageManager.getInstalledPackages(0).map { it.packageName }
        } catch (e: Exception) {
            emit("could not list installed packages: ${e.javaClass.simpleName}")
            return
        }
        val extraTw = names.filter { it.startsWith("com.tw.") && it !in alreadyReported }.sorted()
        if (extraTw.isNotEmpty()) emit("other com.tw.* packages: ${extraTw.joinToString()}")

        val vehicleish = names.filter { name ->
            VEHICLE_HINTS.any { name.contains(it, ignoreCase = true) } && name !in alreadyReported
        }.sorted()
        if (vehicleish.isNotEmpty()) emit("vehicle-ish packages: ${vehicleish.joinToString()}")

        emit(
            "package sweep done: ${names.size} installed, " +
                "${alreadyReported.size} known-TW, ${extraTw.size} other com.tw.*, ${vehicleish.size} vehicle-ish"
        )
    }

    private companion object {
        /**
         * Known Topway system/vehicle packages to resolve by exact name. Media
         * ones (music/radio/…) are confirmed from decompiled TS18 APKs; the
         * vehicle/canbus ones are the community-reported names for the CAN app.
         */
        val KNOWN_TW_PACKAGES = listOf(
            "com.tw.carchoose",
            "com.tw.car",
            "com.tw.carapps",
            "com.tw.ac2",
            "com.tw.canbus",
            "com.tw.service.xt",
            "com.tw.music",
            "com.tw.radio",
            "com.tw.video",
            "com.tw.bt",
        )

        /** Subset of [KNOWN_TW_PACKAGES] worth a services/binder deep-inspect if present. */
        val VEHICLE_PACKAGES = setOf(
            "com.tw.carchoose",
            "com.tw.car",
            "com.tw.carapps",
            "com.tw.ac2",
            "com.tw.canbus",
            "com.tw.service.xt",
        )

        // Name fragments that hint at a vehicle/CAN app on a non-com.tw namespace.
        val VEHICLE_HINTS = listOf("canbus", "vehicle", "carinfo", "cardvr", "mcu", "obd", "cleancan")
    }
}
