package com.gmini.android.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.gmini.android.core.store.ServerProfile
import com.gmini.android.data.AppSettings
import com.gmini.android.service.AgentConnectionService
import com.gmini.android.ui.GminiViewModelFactory
import com.gmini.android.ui.components.MessageCard
import com.gmini.android.ui.components.SectionCard
import com.gmini.android.ui.components.StatusPill
import com.gmini.android.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    factory: GminiViewModelFactory,
    onAddServer: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationsPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

    var unpairTarget by remember { mutableStateOf<ServerProfile?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ajustes") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("Servidores")
            SectionCard {
                if (profiles.isEmpty()) {
                    Text(
                        "No hay servidores. Agrega el tuyo para empezar.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    profiles.forEach { profile ->
                        ProfileRow(
                            profile = profile,
                            active = profile.id == activeProfile?.id,
                            onSelect = { viewModel.selectProfile(profile.id) },
                            onDelete = { unpairTarget = profile },
                        )
                    }
                }
                OutlinedButton(
                    onClick = onAddServer,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Agregar servidor") }
            }

            SectionTitle("Conexion")
            SectionCard {
                SettingSwitch(
                    title = "Mantener conexion en segundo plano",
                    description = "Un servicio en primer plano mantiene viva la conexion para " +
                        "recibir avisos del agente y responder como nodo. Requiere notificaciones.",
                    checked = settings.backgroundConnectionEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setBackgroundConnection(enabled)
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !notificationsPermission.status.isGranted
                            ) {
                                notificationsPermission.launchPermissionRequest()
                            }
                            AgentConnectionService.start(context)
                        } else {
                            AgentConnectionService.stop(context)
                        }
                    },
                )
            }

            SectionTitle("Lo que el agente puede hacer en este telefono")
            SectionCard {
                SettingSwitch(
                    title = "Mostrar mensajes (display.text)",
                    description = "Permite que el agente muestre un texto como notificacion.",
                    checked = settings.surfaceDisplayText,
                    onCheckedChange = viewModel::setDisplayTextSurface,
                )
                SettingSwitch(
                    title = "Leer en voz alta (tts.speak)",
                    description = "Permite que el agente pronuncie un texto con la voz del telefono.",
                    checked = settings.surfaceTtsSpeak,
                    onCheckedChange = viewModel::setTtsSpeakSurface,
                )
            }

            SectionTitle("Tema")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip("Sistema", settings.themeMode == ThemeMode.SYSTEM) { viewModel.setTheme(ThemeMode.SYSTEM) }
                ThemeChip("Claro", settings.themeMode == ThemeMode.LIGHT) { viewModel.setTheme(ThemeMode.LIGHT) }
                ThemeChip("Oscuro", settings.themeMode == ThemeMode.DARK) { viewModel.setTheme(ThemeMode.DARK) }
            }

            SectionTitle("Seguridad")
            MessageCard(
                message = "Por omision el cliente solo se conecta a servidores de tu red local. " +
                    "El token vive cifrado en el Keystore de Android. Todo lo que llega del agente " +
                    "se trata como dato: solo se muestra o se pronuncia, nunca se ejecuta.",
            )
        }
    }

    unpairTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unpairTarget = null },
            title = { Text("Desvincular servidor") },
            text = {
                Text(
                    "Se eliminara este dispositivo del servidor \"${target.label}\" y se borrara " +
                        "el token guardado. Podras volver a emparejarlo con un codigo nuevo.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unpair(target)
                    unpairTarget = null
                }) { Text("Desvincular") }
            },
            dismissButton = { TextButton(onClick = { unpairTarget = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ProfileRow(
    profile: ServerProfile,
    active: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Dns, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.label.ifBlank { profile.host }, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${profile.host}:${profile.port}${if (profile.isRemote) "  (remoto)" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (active) {
            StatusPill(text = "Activo", color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "Desvincular")
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
