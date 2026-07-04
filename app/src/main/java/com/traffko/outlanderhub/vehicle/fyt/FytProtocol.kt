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

/**
 * Mapping of observed CAN-module update codes to vehicle signals for the
 * Mitsubishi Outlander (2019) decoder shipped with the Zeta Neo 14.
 *
 * These defaults follow the most common FYT "Raise/Hiworld Mitsubishi"
 * decoder layout but MUST be validated with the Diagnostics screen on the
 * actual car: drive, watch which codes change with speed/RPM/doors, then
 * adjust here.
 */
object FytSignalMap {
    const val CODE_DOORS = 16
    const val CODE_OUTSIDE_TEMP = 17
    const val CODE_SPEED = 32
    const val CODE_RPM = 33
    const val CODE_BATTERY_VOLTS = 34
    const val CODE_COOLANT_TEMP = 35
    const val CODE_FUEL = 36
    const val CODE_ODOMETER = 37
    const val CODE_HANDBRAKE = 38
    const val CODE_SEATBELT = 39

    /**
     * Gear position. GUESS — no observed code yet, placed after the drive
     * block so the Dash PRND strip has a chance of lighting up. Validate in
     * the car (shift P→R→N→D, watch which code fires in the CAN tab) and
     * correct both this constant and [FytSignalDecoder.decodeGear].
     */
    const val CODE_GEAR = 40
    const val CODE_CLIMATE = 48
    const val CODE_TPMS = 64
}
