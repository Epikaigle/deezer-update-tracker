package io.github.epikaigle.deezertracker

import io.github.epikaigle.deezertracker.network.VersionParsing
import org.junit.Assert.*
import org.junit.Test

class VersionParsingTest {
    @Test fun extractsNumericRelease() {
        assertEquals("8.0.21.4", VersionParsing.extract("Deezer: Music & Podcast Player 8.0.21.4"))
    }
    @Test fun detectsBeta() {
        assertTrue(VersionParsing.isBeta("Deezer 8.0.22.0 beta"))
        assertFalse(VersionParsing.isBeta("Deezer 8.0.22.0"))
    }
    @Test fun versionComparisonOrdersReleases() {
        assertTrue(VersionParsing.compare("8.0.22.1", "8.0.21.9") > 0)
        assertTrue(VersionParsing.compare("8.0.22.0", "8.0.22.0-beta") > 0)
        assertTrue(VersionParsing.compare("8.0.22.0-alpha", "8.0.22.0-beta") < 0)
        assertEquals(0, VersionParsing.compare("8.0.22.0", "8.0.22"))
    }
    @Test fun supportsISOAndLocalDates() {
        assertNotNull(VersionParsing.date("2026-11-18"))
        assertNotNull(VersionParsing.date("2026-11-18 09:30:00"))
        assertNotNull(VersionParsing.date("2026-11-18T09:30:00Z"))
        assertNull(VersionParsing.date("unknown"))
    }
    @Test fun parsesFeedDate() {
        assertNotNull(VersionParsing.date("Wed, 18 Nov 2026 10:00:00 GMT"))
    }
}
