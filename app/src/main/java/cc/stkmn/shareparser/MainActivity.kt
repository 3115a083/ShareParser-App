package cc.stkmn.shareparser

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.ui.FailureScreen
import cc.stkmn.shareparser.ui.HomeScreen
import cc.stkmn.shareparser.ui.ProfileEditorScreen
import cc.stkmn.shareparser.ui.RegionalSettingsScreen
import cc.stkmn.shareparser.ui.SharedScreen

class MainActivity : ComponentActivity() {
    private val latestIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashRecorder.install(this)
        val pendingCrash = CrashRecorder.consumePending(this)
        latestIntent.value = if (pendingCrash && intent.action == Intent.ACTION_MAIN) {
            Intent(Intent.ACTION_VIEW, Uri.parse("shareparser://failure/crash"))
        } else {
            intent
        }
        setContent {
            ShareParserApp(
                startIntent = latestIntent.value,
                onIntentConsumed = ::clearIncomingIntent
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent.value = intent
    }

    private fun clearIncomingIntent() {
        latestIntent.value = null
        setIntent(Intent())
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Editor(
        val profile: Profile?,
        val sample: SharedPayload? = null,
        val highlightField: String? = null
    ) : Screen
    data class Shared(val payload: SharedPayload) : Screen
    data object Failure : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareParserApp(startIntent: Intent?, onIntentConsumed: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository(context) }
    var profiles by remember { mutableStateOf(repository.profiles()) }
    var screen by remember { mutableStateOf<Screen>(initialScreen(startIntent)) }
    var importError by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Datei konnte nicht gelesen werden")
                repository.import(text)
            }.onSuccess {
                profiles = repository.profiles()
                screen = Screen.Editor(it)
                importError = null
            }.onFailure {
                importError = "Profil konnte nicht importiert werden: ${it.message}"
            }
        }
    }

    LaunchedEffect(startIntent) {
        if (startIntent == null) return@LaunchedEffect
        val payload = startIntent.sharedPayload()
        val failure = startIntent.isFailureLink()
        when {
            failure -> screen = Screen.Failure
            payload != null -> screen = if (profiles.isEmpty()) Screen.Editor(null, sample = payload) else Screen.Shared(payload)
        }
        onIntentConsumed()
    }

    MaterialTheme(colorScheme = dynamicOrDefaultScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (val current = screen) {
                                Screen.Home -> "ShareParser"
                                Screen.Settings -> "Einstellungen"
                                is Screen.Editor -> if (current.profile == null) "Profil erstellen" else "Profil bearbeiten"
                                is Screen.Shared -> "Geteilter Inhalt"
                                Screen.Failure -> "Fehlerbericht"
                            }
                        )
                    },
                    navigationIcon = {
                        if (screen !is Screen.Home) {
                            IconButton(onClick = { screen = Screen.Home }) {
                                Icon(Icons.Outlined.ArrowBack, "Zurück zur Profilübersicht")
                            }
                        }
                    },
                    actions = {
                        if (screen is Screen.Home) {
                            IconButton(onClick = { screen = Screen.Settings }) {
                                Icon(Icons.Outlined.Settings, "Einstellungen")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (screen is Screen.Home) {
                    FloatingActionButton(onClick = { screen = Screen.Editor(null) }) {
                        Icon(Icons.Outlined.Add, "Profil erstellen")
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (val current = screen) {
                    Screen.Home -> HomeScreen(
                        profiles = profiles,
                        importError = importError,
                        onEdit = { screen = Screen.Editor(it) },
                        onCreate = { screen = Screen.Editor(null) },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                        onToggle = { profile, enabled ->
                            repository.setEnabled(profile.id, enabled)
                            profiles = repository.profiles()
                        },
                        onDelete = { profile ->
                            repository.delete(profile.id)
                            profiles = repository.profiles()
                        },
                        onSettings = { screen = Screen.Settings }
                    )
                    Screen.Settings -> RegionalSettingsScreen(repository = repository)
                    is Screen.Editor -> ProfileEditorScreen(
                        existing = current.profile,
                        sample = current.sample,
                        highlightField = current.highlightField,
                        repository = repository,
                        onSaved = {
                            profiles = repository.profiles()
                            screen = Screen.Home
                        },
                        onDeleted = {
                            profiles = repository.profiles()
                            screen = Screen.Home
                        }
                    )
                    is Screen.Shared -> SharedScreen(
                        payload = current.payload,
                        profiles = profiles,
                        repository = repository,
                        onEditProfile = { screen = Screen.Editor(it, sample = current.payload) },
                        onCreateFromSample = { screen = Screen.Editor(null, sample = current.payload) }
                    )
                    Screen.Failure -> {
                        val report = repository.lastFailure()
                        FailureScreen(
                            report = report,
                            profile = report?.profileId?.let { id -> profiles.firstOrNull { it.id == id } },
                            onEdit = { profile, field -> screen = Screen.Editor(profile, highlightField = field) }
                        )
                    }
                }
            }
        }
    }
}

private fun initialScreen(intent: Intent?): Screen {
    if (intent?.isFailureLink() == true) return Screen.Failure
    intent?.sharedPayload()?.let { return Screen.Shared(it) }
    return Screen.Home
}

private fun Intent.sharedPayload(): SharedPayload? {
    if (action != Intent.ACTION_SEND) return null
    val text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        ?: getStringExtra(Intent.EXTRA_HTML_TEXT)
        ?: ""
    val subject = getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
    if (text.isBlank() && subject.isBlank()) return null
    return SharedPayload(text = text, subject = subject, mimeType = type ?: "text/plain")
}

private fun Intent.isFailureLink(): Boolean =
    data?.scheme == "shareparser" && data?.host == "failure"

@Composable
private fun dynamicOrDefaultScheme(): ColorScheme {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
}
