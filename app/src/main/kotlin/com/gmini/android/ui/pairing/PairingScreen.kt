package com.gmini.android.ui.pairing

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.gmini.android.qr.BarcodeAnalyzer
import com.gmini.android.ui.GminiViewModelFactory
import com.gmini.android.ui.components.MessageCard
import java.util.concurrent.Executors

private enum class PairMode { SCAN, MANUAL }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PairingScreen(
    factory: GminiViewModelFactory,
    initialLink: String?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onPaired: () -> Unit,
) {
    val viewModel: PairingViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(if (initialLink != null) PairMode.MANUAL else PairMode.SCAN) }

    androidx.compose.runtime.LaunchedEffect(initialLink) {
        if (initialLink != null) viewModel.applyScannedLink(initialLink)
    }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conectar con tu G-Mini") },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Vincula este telefono con tu propia instancia de G-Mini. En tu app de " +
                    "escritorio o servidor abre Ajustes > Dispositivos y genera un codigo de 6 digitos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == PairMode.SCAN,
                    onClick = { mode = PairMode.SCAN },
                    leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                    label = { Text("Escanear QR") },
                )
                FilterChip(
                    selected = mode == PairMode.MANUAL,
                    onClick = { mode = PairMode.MANUAL },
                    leadingIcon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
                    label = { Text("Manual") },
                )
            }

            if (mode == PairMode.SCAN) {
                if (cameraPermission.status.isGranted) {
                    CameraQrScanner(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp)),
                        onScanned = { raw ->
                            viewModel.applyScannedLink(raw)
                            mode = PairMode.MANUAL
                        },
                    )
                } else {
                    MessageCard(
                        message = "Para leer el QR necesitamos la camara. El permiso se usa solo " +
                            "para esta pantalla.",
                        isWarning = true,
                    )
                    Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                        Text("Permitir camara")
                    }
                }
            }

            OutlinedTextField(
                value = state.host,
                onValueChange = viewModel::onHostChange,
                label = { Text("Host (ej. 192.168.1.50)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = viewModel::onPortChange,
                label = { Text("Puerto") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                label = { Text("Codigo de 6 digitos") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::onLabelChange,
                label = { Text("Nombre del servidor (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Usar HTTPS/WSS", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Actívalo solo si tu servidor esta detras de un proxy con TLS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.secure, onCheckedChange = viewModel::onSecureChange)
            }

            if (state.isRemoteHost) {
                RemoteWarning(
                    accepted = state.remoteAccepted,
                    onAcceptedChange = viewModel::onRemoteAcceptedChange,
                )
            }

            state.info?.let { MessageCard(message = it) }
            state.error?.let { MessageCard(message = it, isError = true) }

            Button(
                onClick = { viewModel.pair(onPaired) },
                enabled = !state.isPairing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isPairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Emparejar")
                }
            }
        }
    }
}

@Composable
private fun RemoteWarning(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MessageCard(
            message = "Este host no esta en tu red local. Conectarte a un servidor remoto expone " +
                "tus mensajes fuera de casa; hazlo solo por una VPN o un proxy con TLS de tu confianza.",
            isWarning = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = accepted, onCheckedChange = onAcceptedChange)
            Spacer(Modifier.width(12.dp))
            Text(
                "Entiendo el riesgo y quiero continuar.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CameraQrScanner(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val currentOnScanned by rememberUpdatedState(onScanned)

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, BarcodeAnalyzer { value -> currentOnScanned(value) }) }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
