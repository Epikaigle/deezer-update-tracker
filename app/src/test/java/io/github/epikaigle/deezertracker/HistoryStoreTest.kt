package io.github.epikaigle.deezertracker

import android.content.Context
import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Source
import io.github.epikaigle.deezertracker.model.SourceResult
import io.github.epikaigle.deezertracker.storage.HistoryStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HistoryStoreTest {
    private lateinit var store: HistoryStore

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("tracker", Context.MODE_PRIVATE).edit().clear().commit()
        store = HistoryStore(app)
    }

    private fun release(version: String, source: Source = Source.APKMIRROR,
                        beta: Boolean = false, code: Long? = null) =
        Release(source, version, source.website, beta, null, versionCode = code)

    @Test fun firstSuccessfulFetchCreatesBaselineWithoutNotification() {
        val new = store.merge(listOf(
            SourceResult(Source.APKMIRROR, listOf(release("9.0.24.5")))
        ))
        assertTrue(new.isEmpty())
        assertEquals(1, store.snapshot().releases.size)
    }

    @Test fun repeatedVersionDoesNotNotifyAndNewerVersionNotifiesOnce() {
        store.merge(listOf(SourceResult(Source.APKMIRROR, listOf(release("9.0.24.5")))))
        assertTrue(store.merge(listOf(
            SourceResult(Source.APKMIRROR, listOf(release("9.0.24.5")))
        )).isEmpty())
        val news = store.merge(listOf(
            SourceResult(Source.APKMIRROR, listOf(release("9.0.25.0")))
        ))
        assertEquals(1, news.size)
        assertEquals("9.0.25.0", news.single().version)
        assertTrue(store.merge(listOf(
            SourceResult(Source.APKMIRROR, listOf(release("9.0.25.0")))
        )).isEmpty())
    }

    @Test fun variantsPersistButDoNotGenerateDuplicateNotification() {
        store.merge(listOf(SourceResult(Source.APKPURE,
            listOf(release("9.0.24.5", Source.APKPURE, code = 100L)))))
        val news = store.merge(listOf(SourceResult(Source.APKPURE,
            listOf(release("9.0.25.0", Source.APKPURE, code = 101L),
                   release("9.0.25.0", Source.APKPURE, code = 102L)))))
        assertEquals(1, news.size)
        assertEquals(3, store.snapshot().releases.size)
        assertEquals(2, store.snapshot().releases.count { it.version == "9.0.25.0" })
    }

    @Test fun olderHistoricalReleasesNeverTriggerFalseUpdate() {
        store.merge(listOf(SourceResult(Source.APKPURE,
            listOf(release("9.0.25.0", Source.APKPURE)))))
        val changes = store.merge(listOf(SourceResult(Source.APKPURE,
            listOf(release("9.0.24.5", Source.APKPURE)))))
        assertTrue(changes.isEmpty())
        assertEquals(2, store.snapshot().releases.size)
    }

    @Test fun providerFailureIsReportedAndHistoryRetained() {
        store.merge(listOf(SourceResult(Source.APKMIRROR,
            listOf(release("9.0.24.5")))))
        store.merge(listOf(SourceResult(Source.APKMIRROR,
            error = "HTTP 403")))
        val snapshot = store.snapshot()
        assertEquals(1, snapshot.releases.size)
        assertEquals("HTTP 403", snapshot.statuses.single().message)
        assertFalse(snapshot.statuses.single().ok)
    }

    @Test fun betaVersionsCanBeHiddenFromNotificationsWithoutLosingHistory() {
        store.includeBeta = false
        store.merge(listOf(SourceResult(Source.APKMIRROR,
            listOf(release("9.0.24.5")))))
        val new = store.merge(listOf(SourceResult(Source.APKMIRROR,
            listOf(release("9.0.25.0-beta", beta = true)))))
        assertTrue(new.isEmpty())
        assertEquals(2, store.snapshot().releases.size)
    }

    @Test fun disabledSourcesAndNeverModeAreSaved() {
        store.frequencyMinutes = 0
        store.enabledSources = setOf(Source.APKMIRROR, Source.APKPURE)
        val reopened = HistoryStore(RuntimeEnvironment.getApplication())
        assertEquals(0, reopened.frequencyMinutes)
        assertEquals(setOf(Source.APKMIRROR, Source.APKPURE), reopened.enabledSources)
        reopened.enabledSources = emptySet()
        assertTrue(HistoryStore(RuntimeEnvironment.getApplication()).enabledSources.isEmpty())
    }
}
