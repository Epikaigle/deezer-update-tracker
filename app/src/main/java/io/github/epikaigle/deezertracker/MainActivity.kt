package io.github.epikaigle.deezertracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Snapshot
import io.github.epikaigle.deezertracker.model.Source
import io.github.epikaigle.deezertracker.selfupdate.OwnRelease
import io.github.epikaigle.deezertracker.selfupdate.ReleaseUpdater
import io.github.epikaigle.deezertracker.storage.HistoryStore
import io.github.epikaigle.deezertracker.tracker.TrackerEngine
import io.github.epikaigle.deezertracker.tracker.TrackerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackerScheduler.configure(this)
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                TrackerScreen()
            }
        }
    }
}

@Composable
private fun TrackerScreen() {
    val context = LocalContext.current
    val store = remember { HistoryStore(context) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(store.snapshot()) }
    var refreshing by remember { mutableStateOf(false) }
    var selfRelease by remember { mutableStateOf<OwnRelease?>(null) }
    var ownUpdateMessage by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Android handles denial; manual refresh remains available. */ }

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            try {
                snapshot = TrackerEngine(context).check(background = false)
            } catch (e: Exception) {
                ownUpdateMessage = "Vérification impossible : ${e.message}"
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // In "Jamais" mode, opening the app is the only automatic refresh.
        refresh()
        try {
            selfRelease = ReleaseUpdater.latest(context)
        } catch (e: Exception) {
            ownUpdateMessage = "Mise à jour de l'application : ${e.message}"
        }
        if (snapshot.frequencyMinutes > 0 && Build.VERSION.SDK_INT >= 33) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val frequencies = listOf(0 to "Jamais · uniquement à l'ouverture", 30 to "30 minutes",
        60 to "1 heure", 180 to "3 heures", 720 to "12 heures")
    val visibleHistory = snapshot.releases.filter { snapshot.includeBeta || !it.beta }.take(80)

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Deezer Update Tracker", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("Versions officielles, bêta et dates de publication",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Surveillance Deezer", style = MaterialTheme.typography.titleLarge)
                        Text("Les versions sont surveillées même si Deezer n'est pas installé.")
                        Button(onClick = { refresh() }, enabled = !refreshing) {
                            if (refreshing) CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                            ) else Text("Vérifier maintenant")
                        }
                        if (refreshing) Text("Interrogation des sources en cours…")
                    }
                }
            }
            item { SectionTitle("État des sources") }
            items(Source.entries.toList()) { source ->
                val status = snapshot.statuses.firstOrNull { it.source == source }
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(source.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                status == null -> "Pas encore vérifiée"
                                status.ok -> "✓ ${status.message}"
                                else -> "⚠ ${status.message}"
                            },
                            color = if (status?.ok == false) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (status != null) Text("Vérification : ${formatted(status.checkedAt)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionTitle("Notifications et fréquence") }
            item {
                OutlinedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("Jamais = aucune notification et aucun contrôle en arrière-plan.",
                            style = MaterialTheme.typography.bodyMedium)
                        frequencies.forEach { (minutes, description) ->
                            Row(Modifier.fillMaxWidth().clickable {
                                store.frequencyMinutes = minutes
                                TrackerScheduler.configure(context)
                                snapshot = store.snapshot()
                                if (minutes > 0 && Build.VERSION.SDK_INT >= 33) {
                                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }.padding(vertical = 5.dp)) {
                                RadioButton(selected = snapshot.frequencyMinutes == minutes,
                                    onClick = {
                                        store.frequencyMinutes = minutes
                                        TrackerScheduler.configure(context)
                                        snapshot = store.snapshot()
                                        if (minutes > 0 && Build.VERSION.SDK_INT >= 33) {
                                            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    })
                                Spacer(Modifier.width(8.dp))
                                Text(description, modifier = Modifier.padding(top = 13.dp))
                            }
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                                Text("Inclure les versions bêta")
                                Text("Bêta, alpha et versions de test",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = snapshot.includeBeta, onCheckedChange = {
                                store.includeBeta = it
                                snapshot = store.snapshot()
                            })
                        }
                        Text("Android peut décaler les vérifications pour économiser la batterie.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionTitle("Historique des versions") }
            if (visibleHistory.isEmpty()) {
                item { Text("Aucune version enregistrée. Lance une vérification ou consulte l'état des sources.") }
            } else {
                items(visibleHistory, key = { it.key }) { release ->
                    ReleaseTile(release) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)))
                    }
                }
            }
            item { SectionTitle("Mises à jour de cette application") }
            item {
                OutlinedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selfRelease == null) {
                            Text("Aucune version plus récente détectée sur GitHub Releases.")
                        } else {
                            Text("Version ${selfRelease!!.version} disponible",
                                fontWeight = FontWeight.SemiBold)
                            if (selfRelease!!.notes.isNotBlank()) Text(
                                selfRelease!!.notes.take(400), style = MaterialTheme.typography.bodySmall)
                            Button(onClick = {
                                val info = selfRelease ?: return@Button
                                scope.launch {
                                    installing = true
                                    try {
                                        val file = ReleaseUpdater.download(context, info)
                                        val requested = ReleaseUpdater.requestInstall(context, file)
                                        ownUpdateMessage = if (requested)
                                            "Confirme l'installation dans Android."
                                        else "Autorise l'installation depuis cette application, puis réessaie."
                                    } catch (e: Exception) {
                                        ownUpdateMessage = "Installation impossible : ${e.message}"
                                    } finally { installing = false }
                                }
                            }, enabled = !installing) {
                                Text(if (installing) "Téléchargement…" else "Télécharger et installer")
                            }
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                ownUpdateMessage = ""
                                try { selfRelease = ReleaseUpdater.latest(context) }
                                catch (e: Exception) { ownUpdateMessage = e.message.orEmpty() }
                            }
                        }) { Text("Rechercher une mise à jour") }
                        if (ownUpdateMessage.isNotBlank()) Text(ownUpdateMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Le téléchargement est vérifié par SHA-256. Android doit confirmer l'installation.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text("Projet open source non officiel · GPLv3 · Historique enregistré sur cet appareil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ReleaseTile(release: Release, onOpen: () -> Unit) {
    OutlinedCard(onClick = onOpen, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(release.version, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                if (release.beta) Text("BÊTA", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium)
            }
            Text(release.source.title, color = MaterialTheme.colorScheme.primary)
            if (release.versionCode != null) {
                Text("Build Android : ${release.versionCode}",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (release.publishedAt != null) {
                Text("Publiée : ${formatted(release.publishedAt)}",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Date de publication non fournie", style = MaterialTheme.typography.bodySmall)
            }
            Text("Détectée : ${formatted(release.detectedAt)} · Ouvrir la source",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatted(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRENCH)
        .format(Date(timestamp))
