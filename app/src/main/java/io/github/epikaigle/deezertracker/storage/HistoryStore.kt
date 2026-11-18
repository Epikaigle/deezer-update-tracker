package io.github.epikaigle.deezertracker.storage

import android.content.Context
import io.github.epikaigle.deezertracker.model.*
import io.github.epikaigle.deezertracker.network.VersionParsing
import org.json.JSONArray
import org.json.JSONObject

/** Private local persistence; no installed-package inventory or cloud account. */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("tracker", Context.MODE_PRIVATE)

    var frequencyMinutes: Int
        get() = prefs.getInt("frequency", 60)
        set(value) { require(value in listOf(0, 30, 60, 180, 720)); prefs.edit().putInt("frequency", value).apply() }

    var includeBeta: Boolean
        get() = prefs.getBoolean("include_beta", true)
        set(value) { prefs.edit().putBoolean("include_beta", value).apply() }

    @Synchronized
    fun snapshot(): Snapshot {
        val history = prefs.getString("releases", "[]") ?: "[]"
        val statuses = prefs.getString("status", "[]") ?: "[]"
        return Snapshot(
            decodeReleases(history).sortedByDescending { it.detectedAt },
            decodeStatus(statuses).sortedBy { it.source.ordinal },
            frequencyMinutes, includeBeta
        )
    }

    /**
     * Each source establishes its own baseline on its first successful request,
     * so a source recovering after a CAPTCHA does not notify its entire archive.
     * A version reported by another source does not trigger a duplicate alert.
     */
    @Synchronized
    fun merge(results: List<SourceResult>): List<Release> {
        val previous = snapshot().releases.toMutableList()
        val previouslyNewest = previous.maxWithOrNull { a, b -> VersionParsing.compare(a.version, b.version) }?.version
        val knownKeys = previous.mapTo(mutableSetOf()) { it.key }
        val knownVersions = previous.mapTo(mutableSetOf()) { it.version.lowercase() }
        val baseline = (prefs.getStringSet("baseline", emptySet()) ?: emptySet()).toMutableSet()
        val state = snapshot().statuses.associateBy { it.source }.toMutableMap()
        val newlyDetected = mutableListOf<Release>()
        val now = System.currentTimeMillis()

        for (result in results) {
            val success = result.error == null
            state[result.source] = SourceStatus(
                result.source, success,
                if (success) "${result.releases.size} version(s) lue(s)" else result.error.orEmpty(),
                now
            )
            if (!success) continue
            val firstSuccess = !baseline.contains(result.source.name)
            for (release in result.releases) {
                if (!knownKeys.add(release.key)) continue
                val entry = release.copy(detectedAt = now)
                previous.add(entry)
                if (!firstSuccess && knownVersions.add(entry.version.lowercase())
                    && (includeBeta || !entry.beta)
                    && (previouslyNewest == null || VersionParsing.compare(entry.version, previouslyNewest) > 0)) {
                    newlyDetected.add(entry)
                } else {
                    knownVersions.add(entry.version.lowercase())
                }
            }
            baseline.add(result.source.name)
        }

        prefs.edit()
            .putString("releases", encodeReleases(previous.sortedByDescending { it.detectedAt }.take(500)))
            .putString("status", encodeStatus(state.values.toList()))
            .putStringSet("baseline", baseline)
            .apply()
        return newlyDetected.distinctBy { it.version.lowercase() }
    }

    private fun decodeReleases(raw: String) = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { idx ->
            val o = a.getJSONObject(idx)
            Release(
                Source.valueOf(o.getString("source")), o.getString("version"),
                o.getString("url"), o.optBoolean("beta"),
                if (o.isNull("published")) null else o.optLong("published"),
                o.optLong("detected")
            )
        }
    }.getOrDefault(emptyList())

    private fun encodeReleases(entries: List<Release>): String = JSONArray().apply {
        entries.forEach { r ->
            put(JSONObject().put("source", r.source.name).put("version", r.version)
                .put("url", r.url).put("beta", r.beta)
                .put("published", r.publishedAt ?: JSONObject.NULL)
                .put("detected", r.detectedAt))
        }
    }.toString()

    private fun decodeStatus(raw: String) = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { idx ->
            val o = a.getJSONObject(idx)
            SourceStatus(Source.valueOf(o.getString("source")), o.getBoolean("ok"),
                o.getString("message"), o.getLong("checked"))
        }
    }.getOrDefault(emptyList())

    private fun encodeStatus(entries: List<SourceStatus>) = JSONArray().apply {
        entries.forEach { s ->
            put(JSONObject().put("source", s.source.name).put("ok", s.ok)
                .put("message", s.message).put("checked", s.checkedAt))
        }
    }.toString()
}
