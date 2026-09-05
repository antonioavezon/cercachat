package cl.antonioavezon.cercachat.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cl.antonioavezon.cercachat.AppProcessState
import cl.antonioavezon.cercachat.session.ConnectionPhase
import cl.antonioavezon.cercachat.session.SessionController
import cl.antonioavezon.cercachat.session.SessionSnapshot
import cl.antonioavezon.cercachat.storage.IncomingFileStore
import cl.antonioavezon.cercachat.storage.SavedFilesRepository
import cl.antonioavezon.cercachat.storage.SettingsRepository
import cl.antonioavezon.cercachat.ui.chat.ChatScreen
import cl.antonioavezon.cercachat.ui.files.SavedFilesScreen
import cl.antonioavezon.cercachat.ui.home.HomeScreen
import cl.antonioavezon.cercachat.ui.pairing.ScanQrScreen
import cl.antonioavezon.cercachat.ui.pairing.ShowQrScreen
import cl.antonioavezon.cercachat.ui.settings.SettingsScreen
import cl.antonioavezon.cercachat.ui.welcome.WelcomeScreen

@Composable
fun CercaChatRoot(
    session: SessionController,
    settingsRepository: SettingsRepository,
    savedFiles: SavedFilesRepository,
    fileStore: IncomingFileStore,
    snapshot: SessionSnapshot,
) {
    var welcomeDone by rememberSaveable { mutableStateOf(AppProcessState.welcomeAccepted) }
    if (!welcomeDone) {
        WelcomeScreen(
            onAccept = {
                AppProcessState.welcomeAccepted = true
                welcomeDone = true
            }
        )
        return
    }

    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(snapshot.phase, route) {
        if (snapshot.phase == ConnectionPhase.Connected && route != "chat") {
            nav.navigate("chat") {
                popUpTo("home") { inclusive = false }
                launchSingleTop = true
            }
        } else if (snapshot.phase == ConnectionPhase.Idle && route == "chat") {
            nav.popBackStack("home", inclusive = false)
        }
    }

    NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
        composable("home") {
            HomeScreen(
                snapshot = snapshot,
                settingsRepository = settingsRepository,
                onShowQr = { nav.navigate("showQr") },
                onScanQr = { nav.navigate("scanQr") },
                onSettings = { nav.navigate("settings") },
                onSavedFiles = { nav.navigate("files") },
            )
        }
        composable("showQr") {
            ShowQrScreen(
                snapshot = snapshot,
                session = session,
                onBack = {
                    if (snapshot.phase != ConnectionPhase.Connected) session.cancelPairing()
                    nav.popBackStack()
                },
            )
        }
        composable("scanQr") {
            ScanQrScreen(
                snapshot = snapshot,
                session = session,
                onBack = {
                    if (snapshot.phase != ConnectionPhase.Connected) session.cancelPairing()
                    nav.popBackStack()
                },
            )
        }
        composable("chat") {
            ChatScreen(
                snapshot = snapshot,
                session = session,
                settingsRepository = settingsRepository,
            )
        }
        composable("settings") {
            SettingsScreen(
                settingsRepository = settingsRepository,
                onBack = { nav.popBackStack() },
            )
        }
        composable("files") {
            SavedFilesScreen(
                savedFiles = savedFiles,
                fileStore = fileStore,
                onBack = { nav.popBackStack() },
            )
        }
    }

    snapshot.retention?.let { prompt ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("¿Quieres conservar o borrar los archivos de esta conversación?") },
            text = {
                Text(
                    "Hay ${prompt.fileCount} archivo(s) administrados por CercaChat " +
                        "(${formatSize(prompt.totalBytes)}). " +
                        "Solo se borran copias internas. No se eliminan archivos originales que elegiste " +
                        "ni copias que hayas exportado fuera de la aplicación."
                )
            },
            confirmButton = {
                TextButton(onClick = { session.decideRetention(true) }) { Text("Conservar archivos") }
            },
            dismissButton = {
                TextButton(onClick = { session.decideRetention(false) }) { Text("Borrar archivos") }
            },
        )
    }
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
