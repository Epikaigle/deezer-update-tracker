package io.github.epikaigle.deezertracker

import io.github.epikaigle.deezertracker.selfupdate.ReleaseUpdater
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReleaseUpdaterTest {
    private val expected = "https://github.com/Epikaigle/deezer-update-tracker/releases/download/v0.1.1/deezer-update-tracker-v0.1.1.apk"
    private val checksum = "a".repeat(64)

    private fun release(tag: String, filename: String = "deezer-update-tracker-v0.1.1.apk",
                        url: String = expected, digest: String = "sha256:" + checksum): String =
        """{"tag_name":"$tag","body":"Corrections","assets":[{"name":"$filename","browser_download_url":"$url","digest":"$digest"}]}"""

    @Test fun previousProductionVersionSeesSignedUpdate() {
        val result = ReleaseUpdater.parseRelease(release("v0.1.1"), "0.1.0")
        assertNotNull(result)
        assertEquals("0.1.1", result!!.version)
        assertEquals(expected, result.url)
        assertEquals(checksum, result.expectedSha256)
    }

    @Test fun sameOrNewerVersionNeverOffersDowngrade() {
        assertNull(ReleaseUpdater.parseRelease(release("v0.1.1"), "0.1.1"))
        assertNull(ReleaseUpdater.parseRelease(release("v0.1.1"), "0.1.2"))
    }

    @Test fun releaseFromAnotherRepositoryIsRejected() {
        val wrong = expected.replace("Epikaigle", "Untrusted")
        assertNull(ReleaseUpdater.parseRelease(
            release("v0.1.1", url = wrong), "0.1.0"
        ))
    }

    @Test fun releaseWithWrongAssetNameIsRejected() {
        assertNull(ReleaseUpdater.parseRelease(
            release("v0.1.1", filename = "malware.apk"), "0.1.0"
        ))
    }

    @Test fun invalidChecksumPreventsUpdate() {
        try {
            ReleaseUpdater.parseRelease(release("v0.1.1", digest = "sha256:invalid"), "0.1.0")
            fail("Expected IOException")
        } catch (_: IOException) {
            // Invalid integrity metadata must be a hard failure.
        }
    }

    @Test fun invalidVersionTagPreventsUpdate() {
        try {
            ReleaseUpdater.parseRelease(release("malformed"), "0.1.0")
            fail("Expected IOException")
        } catch (_: IOException) {}
    }
}
