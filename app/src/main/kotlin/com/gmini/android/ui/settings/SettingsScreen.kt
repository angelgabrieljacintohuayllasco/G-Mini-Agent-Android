package com.gmini.android.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.gmini.android.control.DeviceControl
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
    val smsPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
    )

    var unpairTarget by remember { mutableStateOf<ServerProfile?>(null) }
    var showSmsConsent by remember { mutableStateOf(false) }
    var showControlConsent by remember { mutableStateOf(false) }

    val activeIsLocal = activeProfile?.isRemote == false
    val accessibilityOn = DeviceControl.isEnabledInSettings(context)

    fun openAccessibilitySettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

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

            SectionTitle("Pasarela SMS (opcional)")
            SectionCard {
                SettingSwitch(
                    title = "Pasarela SMS",
                    description = "Apagada por defecto. Reenvia los SMS entrantes a tu agente y le permite " +
                        "pedir el envio de un SMS, que solo se manda tras tu confirmacion y solo en red local.",
                    checked = settings.smsGatewayEnabled,
                    enabled = activeIsLocal || settings.smsGatewayEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showSmsConsent = true
                        } else {
                            viewModel.setSmsGateway(false)
                        }
                    },
                )
                if (!activeIsLocal) {
                    Text(
                        "Solo disponible cuando el servidor activo es de tu red local.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (settings.smsGatewayEnabled && !smsPermissions.allPermissionsGranted) {
                    Text(
                        "Falta conceder los permisos de SMS. Hasta entonces no podra enviar ni leer mensajes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { smsPermissions.launchMultiplePermissionRequest() }) {
                        Text("Conceder permisos")
                    }
                }
            }

            SectionTitle("Control del telefono (opcional)")
            SectionCard {
                SettingSwitch(
                    title = "Control del telefono (android-use)",
                    description = "Apagado por defecto. Permite que tu agente toque, deslice, escriba y " +
                        "capture la pantalla, como el computer use del escritorio. Solo en red local y " +
                        "con una notificacion permanente con un boton para detenerlo.",
                    checked = settings.deviceControlEnabled,
                    enabled = activeIsLocal || settings.deviceControlEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showControlConsent = true
                        } else {
                            viewModel.setDeviceControl(false)
                            DeviceControl.disableService()
                        }
                    },
                )
                if (!activeIsLocal) {
                    Text(
                        "Solo disponible cuando el servidor activo es de tu red local.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (settings.deviceControlEnabled && !accessibilityOn) {
                    Text(
                        "Falta activar el servicio de accesibilidad de G-Mini en Ajustes de Android. " +
                            "Hasta entonces el agente no puede controlar el telefono.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { openAccessibilitySettings() }) {
                        Text("Abrir Ajustes de accesibilidad")
                    }
                }
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

    if (showSmsConsent) {
        AlertDialog(
            onDismissRequest = { showSmsConsent = false },
            title = { Text("Encender la pasarela SMS") },
            text = {
                Text(
                    "Con la pasarela encendida:\n\n" +
                        "- Tu agente recibira el texto de los SMS que te lleguen, para que los use (por ejemplo, un codigo).\n" +
                        "- El agente podra pedir que se envie un SMS, pero cada envio te pide confirmacion con el destinatario y el texto a la vista.\n" +
                        "- Solo funciona cuando el servidor activo es de tu red local.\n" +
                        "- Mientras este activa veras una notificacion permanente.\n\n" +
                        "Se usa en tu propio telefono y con tu propio agente. Puedes apagarla cuando quieras.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSmsConsent = false
                    smsPermissions.launchMultiplePermissionRequest()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsPermission.status.isGranted) {
                        notificationsPermission.launchPermissionRequest()
                    }
                    viewModel.setSmsGateway(true)
                    AgentConnectionService.start(context)
                }) { Text("Entiendo, encender") }
            },
            dismissButton = { TextButton(onClick = { showSmsConsent = false }) { Text("Cancelar") } },
        )
    }

    if (showControlConsent) {
        AlertDialog(
            onDismissRequest = { showControlConsent = false },
            title = { Text("Encender el control del telefono") },
            text = {
                Text(
                    "Con el control encendido, tu agente podra tocar, deslizar, escribir y capturar la " +
                        "pantalla de este telefono, como el computer use del escritorio.\n\n" +
                        "- Ademas de este interruptor, tienes que activar el servicio de accesibilidad de " +
                        "G-Mini en Ajustes de Android (se abre al continuar).\n" +
                        "- Solo funciona cuando el servidor activo es de tu red local.\n" +
                        "- Mientras este activo veras una notificacion permanente con un boton para " +
                        "detenerlo de inmediato.\n\n" +
                        "Es tu propio telefono y tu propio agente. Puedes apagarlo cuando quieras.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showControlConsent = false
                    viewModel.setDeviceControl(true)
                    AgentConnectionService.start(context)
                    openAccessibilitySettings()
                }) { Text("Entiendo, continuar") }
            },
            dismissButton = { TextButton(onClick = { showControlConsent = false }) { Text("Cancelar") } },
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
    enabled: Boolean = true,
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
