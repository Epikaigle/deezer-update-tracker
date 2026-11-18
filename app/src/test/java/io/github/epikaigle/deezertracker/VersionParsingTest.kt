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
    @Test fun parsesFeedDate() {
        assertNotNull(VersionParsing.date("Wed, 18 Nov 2026 10:00:00 GMT"))
    }
}
