package io.github.epikaigle.deezertracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Snapshot
import io.github.epikaigle.deezertracker.model.Source
import io.github.epikaigle.deezertracker.network.VersionParsing
import io.github.epikaigle.deezertracker.selfupdate.OwnRelease
import io.github.epikaigle.deezertracker.selfupdate.ReleaseUpdater
import io.github.epikaigle.deezertracker.storage.HistoryStore
import io.github.epikaigle.deezertracker.tracker.TrackerEngine
import io.github.epikaigle.deezertracker.tracker.TrackerScheduler
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val label: String) {
    OVERVIEW("Accueil"), HISTORY("Historique"), SETTINGS("Paramètres")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackerScheduler.configure(this)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                TrackerScreen()
            }
        }
    }
}

@Composable
private fun TrackerScreen() {
    val context = LocalContext.current
    val store = remember(context) { HistoryStore(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(Tab.OVERVIEW.ordinal) }
    val tab = Tab.entries[selectedTab]
    var snapshot by remember { mutableStateOf(store.snapshot()) }
    var refreshing by remember { mutableStateOf(false) }
    var searchingForUpdate by remember { mutableStateOf(true) }
    var selfRelease by remember { mutableStateOf<OwnRelease?>(null) }
    var updaterMessage by remember { mutableStateOf("") }
    var trackerMessage by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var savedApk by remember { mutableStateOf<File?>(null) }
    var search by rememberSaveable { mutableStateOf("") }

    var permissionRefresh by remember { mutableIntStateOf(0) }
    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionRefresh++ }
    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    // Triggers recomposition of the permission hint after an Android permission decision.
    @Suppress("UNUSED_VARIABLE") val trackedPermission = permissionRefresh

    fun refresh() {
        if (refreshing) return
        if (store.enabledSources.isEmpty()) {
            trackerMessage = "Active au moins une source dans Paramètres."
            return
        }
        scope.launch {
            refreshing = true
            trackerMessage = ""
            try {
                snapshot = TrackerEngine(context).check(background = false)
            } catch (e: Exception) {
                trackerMessage = "Vérification Deezer impossible : " + (e.message ?: "erreur réseau")
            } finally {
                refreshing = false
            }
        }
    }

    fun checkOurUpdate() {
        scope.launch {
            searchingForUpdate = true
            updaterMessage = ""
            try {
                val latest = ReleaseUpdater.latest(context)
                if (latest?.url != selfRelease?.url) savedApk = null
                selfRelease = latest
            } catch (e: Exception) {
                updaterMessage = "Recherche de mise à jour impossible : " + (e.message ?: "erreur réseau")
            } finally {
                searchingForUpdate = false
            }
        }
    }

    fun installLatest() {
        val release = selfRelease ?: return
        if (installing) return
        scope.launch {
            installing = true
            updaterMessage = ""
            try {
                val apk = savedApk?.takeIf { it.isFile && it.length() > 0L }
                    ?: ReleaseUpdater.download(context, release).also { savedApk = it }
                val started = ReleaseUpdater.requestInstall(context, apk)
                updaterMessage = if (started) {
                    "Confirme l'installation proposée par Android."
                } else {
                    "Autorise l'installation depuis cette application puis réappuie sur Installer."
                }
            } catch (e: Exception) {
                updaterMessage = "Installation impossible : " + (e.message ?: "erreur inconnue")
                savedApk = null
            } finally {
                installing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (store.enabledSources.isNotEmpty()) refresh()
        checkOurUpdate()
    }

    BackHandler(enabled = tab != Tab.OVERVIEW) { selectedTab = Tab.OVERVIEW.ordinal }

    val frequencies = listOf(
        0 to "Jamais · vérification seulement à l'ouverture",
        30 to "Toutes les 30 minutes",
        60 to "Toutes les heures",
        180 to "Toutes les 3 heures",
        720 to "Toutes les 12 heures"
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { option ->
                    NavigationBarItem(
                        selected = tab == option,
                        onClick = { selectedTab = option.ordinal },
                        icon = {
                            when (option) {
                                Tab.OVERVIEW -> Icon(Icons.Default.Home, contentDescription = null)
                                Tab.HISTORY -> Icon(Icons.Default.History, contentDescription = null)
                                Tab.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(option.label) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { scaffoldPadding ->
        Box(Modifier.fillMaxSize().padding(scaffoldPadding), contentAlignment = Alignment.TopCenter) {
            val listModifier = Modifier.widthIn(max = 760.dp).fillMaxSize()
            when (tab) {
                Tab.OVERVIEW -> LazyColumn(
                    modifier = listModifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text("Deezer Update Tracker", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                        Text("Suivi indépendant des versions de Deezer",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Dernières versions de Deezer",
                                    style = MaterialTheme.typography.titleLarge)
                                Text("Sources : APKMirror, APKPure, Google Play. Aucune installation de Deezer nécessaire.")
                                Button(
                                    onClick = { refresh() },
                                    enabled = !refreshing && snapshot.enabledSources.isNotEmpty()
                                ) {
                                    if (refreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                                        )
                                    } else Text("Vérifier maintenant")
                                }
                                if (refreshing) Text("Interrogation des sources…",
                                    style = MaterialTheme.typography.bodySmall)
                                if (trackerMessage.isNotEmpty()) Text(trackerMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                                if (snapshot.enabledSources.isEmpty()) Text(
                                    "Toutes les sources sont désactivées. Active-les dans Paramètres.")
                                if (snapshot.frequencyMinutes > 0 && !notificationsGranted) {
                                    Text("Les alertes sont désactivées dans Android.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            requestNotificationPermission.launch(
                                                Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }) { Text("Autoriser les alertes") }
                                }
                            }
                        }
                    }
                    item {
                        SectionTitle("Mises à jour de cette application")
                        Spacer(Modifier.height(8.dp))
                        OutlinedCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val current = remember {
                                    context.packageManager.getPackageInfo(context.packageName, 0)
                                        .versionName ?: "?"
                                }
                                Text("Version installée : $current")
                                if (searchingForUpdate) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Text("Vérification de GitHub Releases…")
                                } else if (selfRelease == null) {
                                    Text("Aucune nouvelle version proposée.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("Version ${selfRelease!!.version} disponible",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold)
                                    if (selfRelease!!.notes.isNotBlank()) Text(
                                        selfRelease!!.notes.take(500),
                                        style = MaterialTheme.typography.bodySmall)
                                    Button(
                                        enabled = !installing,
                                        onClick = { installLatest() }
                                    ) {
                                        Text(if (installing) "Téléchargement en cours…"
                                             else if (savedApk != null) "Installer la mise à jour"
                                             else "Télécharger et installer")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { checkOurUpdate() },
                                    enabled = !searchingForUpdate && !installing
                                ) { Text("Rechercher une mise à jour") }
                                if (updaterMessage.isNotEmpty()) Text(updaterMessage,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("APK signée, empreinte SHA-256 vérifiée ; confirmation Android obligatoire.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item { SectionTitle("État des sources") }
                    items(Source.entries.toList(), key = { it.name }) { source ->
                        val active = source in snapshot.enabledSources
                        val state = snapshot.statuses.firstOrNull { it.source == source }
                        OutlinedCard {
                            Column(Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(source.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        !active -> "Désactivée dans Paramètres"
                                        state == null -> "En attente de la première vérification"
                                        state.ok -> "✓ " + state.message
                                        else -> "⚠ " + state.message
                                    },
                                    color = if (active && state?.ok == false)
                                        MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                val latest = snapshot.releases
                                    .filter { it.source == source && (snapshot.includeBeta || !it.beta) }
                                    .maxWithOrNull { a, b -> VersionParsing.compare(a.version, b.version) }
                                if (latest != null) {
                                    Text("Dernière version observée : " + latest.version)
                                    if (latest.publishedAt != null) Text(
                                        "Publiée : " + formatted(latest.publishedAt),
                                        style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(latest.url)))
                                    }) { Text("Consulter la source") }
                                }
                            }
                        }
                    }
                    item {
                        Text("Les sources tierces peuvent être indisponibles ou ne pas exposer leur version.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                Tab.HISTORY -> HistoryColumns(
                    snapshot = snapshot,
                    search = search,
                    onSearch = { search = it },
                    onOpen = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )

                Tab.SETTINGS -> LazyColumn(
                    modifier = listModifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SectionTitle("Paramètres")
                        Text("Configure la surveillance et les notifications.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        OutlinedCard {
                            Column(Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Fréquence des notifications", fontWeight = FontWeight.Bold)
                                Text("Jamais : aucune notification ni vérification en arrière-plan. Les données sont actualisées à l'ouverture.",
                                    style = MaterialTheme.typography.bodySmall)
                                frequencies.forEach { (minutes, description) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            store.frequencyMinutes = minutes
                                            TrackerScheduler.configure(context)
                                            snapshot = store.snapshot()
                                        }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = snapshot.frequencyMinutes == minutes,
                                            onClick = {
                                                store.frequencyMinutes = minutes
                                                TrackerScheduler.configure(context)
                                                snapshot = store.snapshot()
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(description, modifier = Modifier.weight(1f))
                                    }
                                }
                                if (snapshot.frequencyMinutes > 0 && !notificationsGranted) {
                                    HorizontalDivider()
                                    Text("Android n'autorise pas encore les notifications.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            requestNotificationPermission.launch(
                                                Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }) { Text("Autoriser les notifications") }
                                }
                                Text("Les horaires peuvent être décalés par l'économie de batterie Android.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        OutlinedCard {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Sources surveillées", fontWeight = FontWeight.Bold)
                                Text("Une source indisponible n'empêche pas la surveillance des autres.",
                                    style = MaterialTheme.typography.bodySmall)
                                Source.entries.forEach { source ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            val newSelection = snapshot.enabledSources.toMutableSet()
                                            if (source in newSelection) newSelection.remove(source)
                                            else newSelection.add(source)
                                            store.enabledSources = newSelection
                                            TrackerScheduler.configure(context)
                                            snapshot = store.snapshot()
                                        }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = source in snapshot.enabledSources,
                                            onCheckedChange = { checked ->
                                                val selected = snapshot.enabledSources.toMutableSet()
                                                if (checked) selected.add(source) else selected.remove(source)
                                                store.enabledSources = selected
                                                TrackerScheduler.configure(context)
                                                snapshot = store.snapshot()
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(source.title)
                                    }
                                }
                                if (snapshot.enabledSources.isEmpty()) Text(
                                    "Aucune source activée : aucune version ne sera récupérée.",
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item {
                        OutlinedCard {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Inclure les versions bêta", fontWeight = FontWeight.Bold)
                                    Text("Affiche aussi alpha, beta et RC",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = snapshot.includeBeta,
                                    onCheckedChange = {
                                        store.includeBeta = it
                                        snapshot = store.snapshot()
                                    }
                                )
                            }
                        }
                    }
                    item {
                        Text("Application non officielle · GNU GPLv3 · sans compte ni télémétrie.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}


private fun formatted(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRENCH)
        .format(Date(timestamp))
