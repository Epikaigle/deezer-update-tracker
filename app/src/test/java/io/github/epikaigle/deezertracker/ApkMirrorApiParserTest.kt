package io.github.epikaigle.deezertracker

import io.github.epikaigle.deezertracker.model.Source
import io.github.epikaigle.deezertracker.network.ApkMirrorApiParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ApkMirrorApiParserTest {
    private val sample = """
        {
          "data": [
            {
              "pname": "deezer.android.app", "exists": true,
              "release": {
                "version": "9.0.27.2 beta",
                "publish_date": "2026-11-11 05:27:00",
                "link": "/apk/deezer-music-player/deezer-music-podcast-player/example-release/"
              },
              "apks": [
                {
                  "version_code": 901027002, "format": "apkm",
                  "link": "/apk/deezer-music-player/deezer-music-podcast-player/example-release/a-android-apk-download/",
                  "arches": ["arm64-v8a", "armeabi-v7a"], "dpis": ["nodpi"],
                  "minapi": "26",
                  "publish_date": "2026-11-11 05:28:00"
                },
                {
                  "version_code": 901027003, "type": "APK",
                  "link": "/apk/deezer-music-player/deezer-music-podcast-player/example-release/b-android-apk-download/",
                  "arches": ["x86_64"], "dpis": ["480dpi"], "minapi": "28"
                },
                {
                  "version_code": 901027004,
                  "link": "/apk/deezer-music-player/deezer-music-podcast-player/example-release/c-android-apk-download/",
                  "arches": ["universal"]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test fun extractsAllVariantsWithDifferentArchAndBuilds() {
        val result = ApkMirrorApiParser.parse(sample)
        assertEquals(3, result.size)
        assertEquals(Source.APKMIRROR, result.first().source)
        assertEquals("9.0.27.2-beta", result.first().version)
        assertTrue(result.first().beta)
        assertEquals("APKM", result[0].fileType)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), result[0].architectures)
        assertEquals(listOf("nodpi"), result[0].densities)
        assertEquals(26, result[0].minApi)
        assertEquals(901027002L, result[0].versionCode)
        assertNotNull(result.first().publishedAt)
        assertEquals("APK", result[1].fileType)
        assertEquals(listOf("x86_64"), result[1].architectures)
        assertNull("No APK/APKM/APKS inference from URL slugs", result[2].fileType)
        assertEquals(3, result.map { it.key }.distinct().size)
    }

    @Test fun noVariantsStillRecordsTheVersion() {
        val empty = """{"data":[{"pname":"deezer.android.app","exists":true,
            "release":{"version":"9.0.26.3","publish_date":"2026-11-05 05:18:00",
            "link":"/apk/deezer-music-player/deezer-music-podcast-player/"}}]}"""
        val result = ApkMirrorApiParser.parse(empty)
        assertEquals(1, result.size)
        assertNull(result.single().fileType)
        assertNotNull(result.single().publishedAt)
    }

    @Test fun rejectsOtherPackagesAndUnrelatedOrInsecureLinks() {
        assertNull(ApkMirrorApiParser.absoluteLink("https://example.com/file.apk"))
        assertNull(ApkMirrorApiParser.absoluteLink("http://www.apkmirror.com/file.apk"))
        assertNotNull(ApkMirrorApiParser.absoluteLink("/apk/deezer/"))
        try {
            ApkMirrorApiParser.parse("""{"data":[{"pname":"com.other.app","exists":true,
                "release":{"version":"1.2.3"}}]}""")
            fail("Wrong package is not an APKMirror Deezer result")
        } catch (_: IOException) {}
    }
}
