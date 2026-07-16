package com.traffko.outlanderhub.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectionAppsTest {

    private val installed = setOf(
        "com.zjinnova.zlink",
        "net.easyconn",
        "com.spotify.music",
    )

    @Test
    fun `auto-detect prefers the first known client`() {
        assertEquals("com.zjinnova.zlink", ProjectionApps.pick(installed, override = null))
    }

    @Test
    fun `user override wins over auto-detection`() {
        assertEquals("com.spotify.music", ProjectionApps.pick(installed, "com.spotify.music"))
    }

    @Test
    fun `uninstalled override falls back to auto-detection`() {
        assertEquals("com.zjinnova.zlink", ProjectionApps.pick(installed, "com.gone.app"))
    }

    @Test
    fun `nothing found when no known client and no valid override`() {
        assertNull(ProjectionApps.pick(setOf("com.spotify.music"), override = null))
    }
}
