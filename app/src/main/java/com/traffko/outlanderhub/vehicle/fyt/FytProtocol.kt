package com.traffko.outlanderhub.vehicle.fyt

/**
 * Known constants of the FYT platform toolkit service (`com.syu.ms`), which
 * Zeta Neo units are built on. These values come from community reverse
 * engineering of FYT firmwares and are NOT official. Anything that turns out
 * to be wrong for the Zeta Neo 14 / Outlander CAN decoder should be corrected
 * here after observing real traffic in the Diagnostics screen.
 */
object FytProtocol {

    /** Package that hosts the toolkit service on FYT firmwares. */
    const val HOST_PACKAGE = "com.syu.ms"

    /**
     * Candidate intent actions for binding the toolkit service. Different
     * firmware generations use different actions; FytVehicleBus tries them
     * in order and reports the winner in Diagnostics.
     */
    val SERVICE_ACTIONS = listOf(
        "com.syu.ms.toolkit",
        "com.syu.toolkit",
        "com.syu.ms.service.ToolkitService",
    )

    // Module codes passed to IRemoteToolkit.getRemoteModule(...)
    const val MODULE_MAIN = 0
    const val MODULE_SOUND = 1
    const val MODULE_RADIO = 2
    const val MODULE_CANBUS = 6

    /**
     * CAN update codes vary per vehicle/decoder protocol. Rather than
     * hardcoding a possibly-wrong Outlander map up front, we subscribe to a
     * broad code range and let the Diagnostics screen show which codes fire.
     * Once identified on the real unit, map them in [FytSignalMap].
     */
    const val CAN_SUBSCRIBE_FROM = 0
    const val CAN_SUBSCRIBE_TO = 255
}

/** The vehicle signals a CAN update code can be mapped to. */
enum class SignalKind(val label: String) {
    SPEED("Speed"),
    RPM("RPM"),
    BATTERY_VOLTS("Battery volts"),
    COOLANT_TEMP("Coolant temp"),
    FUEL("Fuel level"),
    OUTSIDE_TEMP("Outside temp"),
    ODOMETER("Odometer"),
    HANDBRAKE("Handbrake"),
    SEATBELT("Driver seatbelt"),
    GEAR("Gear"),
    DOORS("Doors"),
    CLIMATE("Climate"),
    TPMS("Tire pressure"),
}

/**
 * Default mapping of CAN-module update codes to vehicle signals for the
 * Mitsubishi Outlander (2019) decoder shipped with the Zeta Neo 14.
 *
 * These defaults follow the most common FYT "Raise/Hiworld Mitsubishi"
 * decoder layout but MUST be validated on the actual car. They no longer
 * need a rebuild to correct: tap an event in the CAN tab and assign the
 * code to a signal — the live mapping is DataStore-backed (see
 * [SignalMapRepository]) and these values only seed it.
 */
object FytSignalMap {
    val DEFAULTS: Map<Int, SignalKind> = mapOf(
        16 to SignalKind.DOORS,
        17 to SignalKind.OUTSIDE_TEMP,
        32 to SignalKind.SPEED,
        33 to SignalKind.RPM,
        34 to SignalKind.BATTERY_VOLTS,
        35 to SignalKind.COOLANT_TEMP,
        36 to SignalKind.FUEL,
        37 to SignalKind.ODOMETER,
        38 to SignalKind.HANDBRAKE,
        39 to SignalKind.SEATBELT,
        // GEAR at 40 is a guess — no observed code yet; placed after the
        // drive block so the Dash PRND strip has a chance of lighting up.
        40 to SignalKind.GEAR,
        48 to SignalKind.CLIMATE,
        64 to SignalKind.TPMS,
    )

    /** Serializes a mapping for DataStore, e.g. `"16:DOORS,32:SPEED"`. */
    fun encode(map: Map<Int, SignalKind>): String =
        map.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value.name}" }

    /** Tolerant inverse of [encode]: unknown kinds and malformed entries are dropped. */
    fun decode(raw: String): Map<Int, SignalKind> = raw.split(',')
        .mapNotNull { entry ->
            val (codeText, kindText) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val code = codeText.trim().toIntOrNull() ?: return@mapNotNull null
            val kind = SignalKind.entries.firstOrNull { it.name == kindText.trim() } ?: return@mapNotNull null
            code to kind
        }
        .toMap()
}
