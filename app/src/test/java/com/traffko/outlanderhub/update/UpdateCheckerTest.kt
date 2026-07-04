package com.traffko.outlanderhub.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer tag wins across positions`() {
        assertTrue(UpdateChecker.isNewer("1.0.0", "v1.0.1"))
        assertTrue(UpdateChecker.isNewer("1.0.9", "v1.1.0"))
        assertTrue(UpdateChecker.isNewer("1.9.9", "v2.0.0"))
    }

    @Test
    fun `same or older tag does not win`() {
        assertFalse(UpdateChecker.isNewer("1.2.3", "v1.2.3"))
        assertFalse(UpdateChecker.isNewer("1.2.3", "v1.2.2"))
        assertFalse(UpdateChecker.isNewer("2.0.0", "v1.9.9"))
    }

    @Test
    fun `length differences are treated as zero-padded`() {
        assertTrue(UpdateChecker.isNewer("1.2", "v1.2.1"))
        assertFalse(UpdateChecker.isNewer("1.2.0", "v1.2"))
    }

    @Test
    fun `dev suffix on the installed version is ignored`() {
        assertTrue(UpdateChecker.isNewer("0.1.0-dev", "v0.2.0"))
        assertFalse(UpdateChecker.isNewer("0.2.0-dev", "v0.2.0"))
    }

    @Test
    fun `unparseable versions are never newer`() {
        assertFalse(UpdateChecker.isNewer("unknown", "v1.0.0"))
        assertFalse(UpdateChecker.isNewer("1.0.0", "latest"))
    }
}
