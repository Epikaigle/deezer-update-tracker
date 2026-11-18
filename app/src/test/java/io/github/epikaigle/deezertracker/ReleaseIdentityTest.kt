package io.github.epikaigle.deezertracker

import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Source
import org.junit.Assert.*
import org.junit.Test

class ReleaseIdentityTest {
    @Test fun apkpureBuildVariantsAreDistinct() {
        val a = Release(Source.APKPURE, "8.0.22.1", Source.APKPURE.website,
            beta = false, publishedAt = null, detectedAt = 1L, versionCode = 1234)
        val b = a.copy(versionCode = 1235)
        assertNotEquals(a.key, b.key)
        assertEquals(a.version, b.version)
    }

    @Test fun sameBuildKeepsStableIdentity() {
        val original = Release(Source.APKPURE, "8.0.22.1", Source.APKPURE.website,
            beta = false, publishedAt = null, detectedAt = 1L, versionCode = 1234)
        val updated = original.copy(detectedAt = 2L)
        assertEquals(original.key, updated.key)
    }
}
