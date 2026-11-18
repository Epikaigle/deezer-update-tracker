package io.github.epikaigle.deezertracker

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Snapshot
import io.github.epikaigle.deezertracker.model.Source
import io.github.epikaigle.deezertracker.network.VersionParsing
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Three genuinely independent, horizontally scrollable source columns.
 * A phone displays one readable column and a peek of the next; tablets see
 * several. Each source's releases are vertically lazy and grouped by version.
 */
@Composable
internal fun HistoryColumns(
    snapshot: Snapshot,
    search: String,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    val sourceOrder = listOf(Source.GOOGLE_PLAY, Source.APKPURE, Source.APKMIRROR)
    val filtered = snapshot.releases.filter { release ->
        (snapshot.includeBeta || !release.beta) &&
            (search.isBlank() ||
                release.version.contains(search, ignoreCase = true) ||
                release.source.title.contains(search, ignoreCase = true) ||
                release.fileType?.contains(search, ignoreCase = true) == true ||
                release.architectures.any { it.contains(search, ignoreCase = true) })
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Historique des versions", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Google Play  |  APKPure  |  APKMirror",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Fais glisser horizontalement pour parcourir les 3 colonnes →",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = search,
                onValueChange = { onSearch(it.take(80)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Rechercher une version, un format ou une architecture") }
            )
        }
        Row(
            modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sourceOrder.forEach { source ->
                val records = filtered.filter { it.source == source }
                val groups = records.groupBy { it.version }.toList().sortedWith { a, b ->
                    val mostRecentA = a.second.mapNotNull { it.publishedAt }.maxOrNull() ?: 0L
                    val mostRecentB = b.second.mapNotNull { it.publishedAt }.maxOrNull() ?: 0L
                    if (mostRecentA > 0 && mostRecentB > 0 && mostRecentA != mostRecentB)
                        mostRecentB.compareTo(mostRecentA)
                    else VersionParsing.compare(b.first, a.first)
                }
                Column(Modifier.width(310.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(source.title, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text("${groups.size} version(s) · ${records.size} entrée(s)",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            val status = snapshot.statuses.firstOrNull { it.source == source }
                            if (source !in snapshot.enabledSources) {
                                Text("Source désactivée ; anciennes données conservées.",
                                    style = MaterialTheme.typography.bodySmall)
                            } else if (status?.ok == false) {
                                Text("Source indisponible : ${status.message}. Les anciennes versions restent visibles.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            } else if (source == Source.GOOGLE_PLAY) {
                                Text("Google Play fournit la version courante. Les versions précédentes sont conservées après chaque relevé.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (source == Source.APKMIRROR) {
                                Text("L'API fournit la dernière version et ses variantes ; l'archive est ajoutée si le flux est accessible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (groups.isEmpty()) {
                            item {
                                Text("Aucune version connue pour cette source.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(groups, key = { it.first }) { (_, variants) ->
                                VersionGroupCard(variants, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionGroupCard(variants: List<Release>, onOpen: (String) -> Unit) {
    val sorted = variants.sortedWith(compareByDescending<Release> { it.publishedAt ?: 0L }
        .thenByDescending { it.versionCode ?: 0L })
    val first = sorted.first()
    val published = sorted.mapNotNull { it.publishedAt }.maxOrNull()
    OutlinedCard(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(first.version, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (first.beta) Text("BÊTA", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
            Text(if (published != null) "Sortie : ${dateOnly(published)}"
                 else "Date de sortie non fournie",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            if (sorted.size > 1) Text("${sorted.size} variantes",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            sorted.forEachIndexed { index, variant ->
                if (index > 0) HorizontalDivider()
                val hasTechnicalInfo = variant.versionCode != null ||
                    variant.fileType != null || variant.architectures.isNotEmpty() ||
                    variant.densities.isNotEmpty() || variant.minApi != null
                if (sorted.size > 1) Text("Variante ${index + 1}",
                    style = MaterialTheme.typography.labelLarge)
                if (hasTechnicalInfo) {
                    Text("Format : ${variant.fileType ?: "non renseigné"}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Architecture : ${variant.architectures.joinToString(" + ").ifEmpty { "non renseignée" }}",
                        style = MaterialTheme.typography.bodySmall)
                    if (variant.densities.isNotEmpty())
                        Text("Densité : ${variant.densities.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall)
                    if (variant.minApi != null)
                        Text("Android minimum : API ${variant.minApi}",
                            style = MaterialTheme.typography.bodySmall)
                    if (variant.versionCode != null)
                        Text("Build : ${variant.versionCode}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Format et architecture non communiqués par la source",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (variant.publishedAt != null && variant.publishedAt != published) {
                    Text("Variante publiée : ${dateOnly(variant.publishedAt)}",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onOpen(variant.url) }) {
                    Text("Voir sur ${variant.source.title}")
                }
            }
        }
    }
}

private fun dateOnly(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.FRENCH).format(Date(timestamp))
