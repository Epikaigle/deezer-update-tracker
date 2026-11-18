package io.github.epikaigle.deezertracker

import io.github.epikaigle.deezertracker.model.Source
import io.github.epikaigle.deezertracker.network.PlayVersionMapping
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlayVersionMappingTest {
    @Test fun mapsPlayRelease() {
        val release = PlayVersionMapping.map("9.0.26.3", 903026003L)
        assertEquals(Source.GOOGLE_PLAY, release.source)
        assertEquals("9.0.26.3", release.version)
        assertEquals(903026003L, release.versionCode)
        assertFalse(release.beta)
    }
    @Test fun refusesUnavailableVersions() {
        try {
            PlayVersionMapping.map("Varies with device", 0L)
            fail("Unparseable version should fail")
        } catch (_: IOException) {}
    }
    @Test fun doesNotInventVersionCode() {
        assertNull(PlayVersionMapping.map("9.0.26.3", 0L).versionCode)
    }
}
