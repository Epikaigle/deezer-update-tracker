package io.github.epikaigle.deezertracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Snapshot
import io.github.epikaigle.deezertracker.model.Source
import io.github.epikaigle.deezertracker.network.VersionParsing
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Three vertical source rows, each with horizontally scrollable version cards.
 * Dense UI deliberately avoids explanatory text while preserving accessible icons.
 */
@Composable
internal fun HistoryColumns(
    snapshot: Snapshot,
    search: String,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    val sources = listOf(Source.GOOGLE_PLAY, Source.APKMIRROR, Source.APKPURE)
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val releases = snapshot.releases.filter { release ->
        (snapshot.includeBeta || !release.beta) && (search.isBlank() ||
            release.version.contains(search, ignoreCase = true) ||
            release.source.title.contains(search, ignoreCase = true) ||
            release.fileType?.contains(search, ignoreCase = true) == true ||
            release.architectures.any { it.contains(search, ignoreCase = true) })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Historique", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) onSearch("")
                    }) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showSearch) "Fermer la recherche" else "Rechercher")
                    }
                }
                if (showSearch) {
                    OutlinedTextField(
                        value = search, onValueChange = { onSearch(it.take(80)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Version / APK / arm64…") }
                    )
                }
            }
        }
        sources.forEach { source ->
            val entries = releases.filter { it.source == source }
            val grouped = entries.groupBy { it.version }.toList().sortedWith { a, b ->
                val versionOrder = VersionParsing.compare(b.first, a.first)
                if (versionOrder != 0) versionOrder
                else (b.second.mapNotNull { it.publishedAt }.maxOrNull() ?: 0L)
                    .compareTo(a.second.mapNotNull { it.publishedAt }.maxOrNull() ?: 0L)
            }
            item(key = "header-${source.name}") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(source.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${grouped.size}", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        val status = snapshot.statuses.firstOrNull { it.source == source }
                        if (status != null) {
                            Icon(
                                if (status.ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = if (status.ok) "Source accessible"
                                else "Source indisponible : ${status.message}",
                                tint = if (status.ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (grouped.isEmpty()) {
                        Text("—", modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(grouped, key = { it.first }) { (_, variants) ->
                                CompactVersionCard(variants, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactVersionCard(variants: List<Release>, onOpen: (String) -> Unit) {
    val sorted = variants.sortedWith(compareByDescending<Release> { it.publishedAt ?: 0L }
        .thenByDescending { it.versionCode ?: 0L })
    val first = sorted.first()
    val date = sorted.mapNotNull { it.publishedAt }.maxOrNull()
    var expanded by rememberSaveable(first.key) { mutableStateOf(false) }
    OutlinedCard(
        modifier = Modifier.width(244.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(first.version, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { onOpen(first.url) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Ouvrir la version sur ${first.source.title}",
                        modifier = Modifier.size(18.dp))
                }
            }
            Text(date?.let(::dateOnly) ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (first.beta) Text("BÊTA", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            if (sorted.size == 1) {
                TechnicalLine(first)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${sorted.size} variantes", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Masquer les variantes"
                                else "Afficher les ${sorted.size} variantes",
                            modifier = Modifier.size(19.dp))
                    }
                }
                if (expanded) {
                    sorted.forEachIndexed { index, variant ->
                        if (index > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                TechnicalLine(variant)
                            }
                            IconButton(onClick = { onOpen(variant.url) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.OpenInNew,
                                    contentDescription = "Ouvrir variante ${index + 1}",
                                    modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalLine(release: Release) {
    val parts = buildList {
        release.fileType?.let { add(it) }
        if (release.architectures.isNotEmpty()) add(release.architectures.joinToString("/"))
        release.versionCode?.let { add("#$it") }
    }
    if (parts.isNotEmpty()) {
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (release.densities.isNotEmpty() || release.minApi != null) {
        val info = buildList {
            if (release.densities.isNotEmpty()) add(release.densities.joinToString("/"))
            release.minApi?.let { add("API $it+") }
        }
        Text(info.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun dateOnly(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.FRENCH).format(Date(timestamp))
