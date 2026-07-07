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

    /**
     * Service that hosts the toolkit binder inside [HOST_PACKAGE]. Binding by
     * action alone is unreliable on FYT firmwares; the reference client binds
     * this explicit component (see docs/CAN-INTEGRATION.md).
     */
    const val TOOLKIT_COMPONENT = "app.ToolkitService"

    // Module codes passed to IRemoteToolkit.getRemoteModule(...). Verified
    // against the decompiled com.syu.ms toolkit (see docs/CAN-INTEGRATION.md).
    const val MODULE_MAIN = 0
    const val MODULE_RADIO = 1
    const val MODULE_BT = 2
    const val MODULE_DVD = 3
    const val MODULE_SOUND = 4
    const val MODULE_TV = 6
    const val MODULE_CANBUS = 7
    const val MODULE_TPMS = 8
    const val MODULE_OBD = 12

    /**
     * CAN update codes on the syu toolkit live in the 1000+ range (0..255 was
     * the wrong ID space). We subscribe across the observed band and let the
     * Diagnostics screen show which codes fire; identified ones are mapped in
     * [FytSignalMap]. Reference IDs: U_CANBUS_ID=1000, U_CUR_SPEED=1031,
     * U_ENGINE_SPEED=1032, U_CANBUS_FRAME_TO_UI=1019.
     */
    const val CAN_SUBSCRIBE_FROM = 1000
    const val CAN_SUBSCRIBE_TO = 1200
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
 * Only the two update codes confirmed against the decompiled syu toolkit are
 * seeded here (U_CUR_SPEED=1031, U_ENGINE_SPEED=1032). Body signals
 * (doors/climate/fuel/…) use decoder-specific codes in the same 1000+ band
 * that are not yet observed on this car — identify them on the real unit and
 * assign live: tap an event in the CAN tab and pick its signal. The mapping is
 * DataStore-backed (see [SignalMapRepository]) and survives restarts; these
 * values only seed it.
 */
object FytSignalMap {
    val DEFAULTS: Map<Int, SignalKind> = mapOf(
        1031 to SignalKind.SPEED,
        1032 to SignalKind.RPM,
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
