package io.github.epikaigle.deezertracker

import io.github.epikaigle.deezertracker.network.ApkMirrorArchive
import org.junit.Assert.*
import org.junit.Test

class ApkMirrorArchiveTest {
    private fun row(version: String, date: String): String = """
        <div class="appRow">
          <h5 class="appRowTitle">
            <a href="/apk/deezer-music-player/deezer-music-podcast-player/deezer-$version-release/">
              Deezer: Music & Podcast Player $version
            </a>
          </h5>
        </div>
        <div class="infoSlide"><span class="infoSlide-value">$date</span></div>
    """.trimIndent()

    @Test fun readsFiveRecentVersionsAndOfficialPublicationDates() {
        val html = "<html><body>" +
            row("9.0.27.3", "November 18, 2026") +
            row("9.0.26.3", "Nov 5, 2026") +
            row("9.0.25.3", "October 12, 2026") +
            row("9.0.24.3", "Sep 25, 2026") +
            row("9.0.23.3", "August 31, 2026") +
            row("9.0.22.3", "August 2, 2026") +
            "</body></html>"
        val result = ApkMirrorArchive.parse(html, limit = 5)
        assertEquals(5, result.size)
        assertEquals("9.0.27.3", result[0].version)
        assertEquals("9.0.23.3", result[4].version)
        assertTrue(result.all { it.publishedAt != null })
        assertTrue(result[0].publishedAt!! > result[1].publishedAt!!)
        assertTrue(result.all { it.url.startsWith("https://www.apkmirror.com/") })
    }

    @Test fun rejectsOtherAppsAndNoVersionRows() {
        val html = """
            <div class="appRow"><h5 class="appRowTitle">
              <a href="/apk/other/music/other-1-2-3-release/">Other Music 1.2.3</a>
            </h5></div>
            <div class="appRow"><h5 class="appRowTitle">
              <a href="/apk/deezer-music-player/deezer-music-podcast-player/">
                Deezer
              </a>
            </h5></div>
        """
        assertTrue(ApkMirrorArchive.parse(html).isEmpty())
    }

    @Test fun marksMissingDateUnknownAndDeduplicatesVersions() {
        val html = row("9.0.26.3", "") + row("9.0.26.3", "")
        val results = ApkMirrorArchive.parse(html)
        assertEquals(1, results.size)
        assertNull(results.single().publishedAt)
    }

    @Test fun challengePageCannotBecomeFakeVersion() {
        assertTrue(ApkMirrorArchive.parse("<html>Cloudflare: Just a moment</html>").isEmpty())
    }
}
