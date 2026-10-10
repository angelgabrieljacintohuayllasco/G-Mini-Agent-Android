package com.gmini.android.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import com.gmini.android.core.net.NetworkScope
import com.gmini.android.core.protocol.NodeSurfaces
import com.gmini.android.core.protocol.SmsContract
import com.gmini.android.core.protocol.WsClientFrame
import com.gmini.android.core.protocol.WsServerFrame
import com.gmini.android.data.ConnectionStatus
import com.gmini.android.di.AppContainer
import com.gmini.android.di.appContainer
import com.gmini.android.sms.IncomingSmsReceiver
import com.gmini.android.sms.SmsGateway
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Servicio en primer plano que mantiene viva la conexion WebSocket con el
 * servidor activo. Convierte los frames `notify` en notificaciones de Android y
 * atiende `node.invoke` para las superficies seguras del nucleo (`display.text`,
 * `tts.speak`) y, si el usuario encendio la pasarela SMS, para `sms.send`.
 *
 * Todo lo que llega del agente se trata como dato: se muestra, se pronuncia o,
 * en el caso de un SMS, se ofrece al usuario para que confirme el envio.
 */
class AgentConnectionService : LifecycleService() {

    private lateinit var container: AppContainer
    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    @Volatile
    private var pendingSpeech: String? = null

    private var foregroundActive = false

    // --- Pasarela SMS ------------------------------------------------------
    private data class PendingSms(
        val requestId: String,
        val to: String,
        val text: String,
        val notificationId: Int,
    )

    private val pendingSms = ConcurrentHashMap<String, PendingSms>()
    private var smsReceiver: IncomingSmsReceiver? = null
    private var smsActiveShown = false

    override fun onCreate() {
        super.onCreate()
        container = appContainer()
        AgentNotifications.ensureChannels(this)
        initTts()

        lifecycleScope.launch {
            container.connection.state.collect { state ->
                if (foregroundActive && NotificationManagerCompat.from(this@AgentConnectionService).areNotificationsEnabled()) {
                    NotificationManagerCompat.from(this@AgentConnectionService)
                        .notify(AgentNotifications.FOREGROUND_ID, AgentNotifications.foregroundNotification(this@AgentConnectionService, state))
                }
            }
        }

        lifecycleScope.launch {
            container.connection.frames.collect { frame -> handleFrame(frame) }
        }

        lifecycleScope.launch {
            container.settings.settings.collect { refreshSmsGateway() }
        }

        lifecycleScope.launch {
            container.repository.activeProfile.collect { refreshSmsGateway() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return Service.START_NOT_STICKY
            }
            ACTION_SMS_ALLOW -> {
                intent.getStringExtra(EXTRA_SMS_TOKEN)?.let { resolveSms(it, approved = true) }
                return Service.START_STICKY
            }
            ACTION_SMS_DENY -> {
                intent.getStringExtra(EXTRA_SMS_TOKEN)?.let { resolveSms(it, approved = false) }
                return Service.START_STICKY
            }
        }
        startInForeground()
        ensureConnected()
        return Service.START_STICKY
    }

    private fun startInForeground() {
        val notification = AgentNotifications.foregroundNotification(this, container.connection.state.value)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, AgentNotifications.FOREGROUND_ID, notification, type)
        foregroundActive = true
    }

    private fun ensureConnected() {
        val client = container.repository.currentClient()
        if (client == null) {
            stopEverything()
            return
        }
        lifecycleScope.launch {
            val settings = container.settings.settings.first()
            val surfaces = buildList {
                if (settings.surfaceDisplayText) add(NodeSurfaces.DISPLAY_TEXT)
                if (settings.surfaceTtsSpeak) add(NodeSurfaces.TTS_SPEAK)
                if (settings.smsGatewayEnabled && activeHostIsLocal() && SmsGateway.hasSendPermission(this@AgentConnectionService)) {
                    add(SmsContract.SURFACE_SEND)
                }
            }
            container.connection.start(client, container.repository.deviceName(), surfaces)
            refreshSmsGateway()
        }
    }

    private suspend fun handleFrame(frame: WsServerFrame) {
        when (frame) {
            is WsServerFrame.Notify ->
                AgentNotifications.notifyFromAgent(this, frame.title, frame.body, frame.priority)

            is WsServerFrame.NodeInvoke -> handleNodeInvoke(frame)

            else -> Unit
        }
    }

    private suspend fun handleNodeInvoke(frame: WsServerFrame.NodeInvoke) {
        val settings = container.settings.settings.first()
        val text = frame.params?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
        when (frame.surface) {
            NodeSurfaces.DISPLAY_TEXT -> {
                if (!settings.surfaceDisplayText) {
                    replyError(frame.requestId, "La superficie display.text esta desactivada.")
                    return
                }
                val seconds = frame.params?.get("seconds")?.jsonPrimitive?.intOrNull
                AgentNotifications.displayMessage(this, text, seconds)
                replyOk(frame.requestId)
            }

            NodeSurfaces.TTS_SPEAK -> {
                if (!settings.surfaceTtsSpeak) {
                    replyError(frame.requestId, "La superficie tts.speak esta desactivada.")
                    return
                }
                speak(text)
                replyOk(frame.requestId)
            }

            SmsContract.SURFACE_SEND -> handleSmsSend(frame, settings.smsGatewayEnabled)

            else -> replyError(frame.requestId, "Superficie no soportada: ${frame.surface}")
        }
    }

    // --- sms.send: solo en red local y solo tras confirmacion del usuario --

    private fun handleSmsSend(frame: WsServerFrame.NodeInvoke, gatewayEnabled: Boolean) {
        if (!gatewayEnabled) {
            replyError(frame.requestId, "La pasarela SMS esta desactivada.")
            return
        }
        if (!activeHostIsLocal()) {
            replyError(frame.requestId, "La pasarela SMS solo funciona con un servidor de red local.")
            return
        }
        if (!SmsGateway.hasSendPermission(this)) {
            replyError(frame.requestId, "Falta el permiso de Android para enviar SMS.")
            return
        }
        val parsed = SmsContract.parseSend(frame.params)
        val request = parsed.getOrElse {
            replyError(frame.requestId, it.message ?: "Peticion de SMS invalida.")
            return
        }
        val token = UUID.randomUUID().toString()
        val notificationId = AgentNotifications.nextSmsNotificationId()
        pendingSms[token] = PendingSms(frame.requestId, request.to, request.text, notificationId)
        AgentNotifications.postSmsConfirmation(
            context = this,
            notificationId = notificationId,
            to = request.to,
            text = request.text,
            allow = smsActionIntent(token, ACTION_SMS_ALLOW, requestCode = 1),
            deny = smsActionIntent(token, ACTION_SMS_DENY, requestCode = 2),
        )
        // Si el usuario no responde, se cancela y se avisa al agente.
        lifecycleScope.launch {
            delay(CONFIRMATION_TIMEOUT_MS)
            if (pendingSms.containsKey(token)) {
                resolveSms(token, approved = false, timedOut = true)
            }
        }
    }

    private fun resolveSms(token: String, approved: Boolean, timedOut: Boolean = false) {
        val pending = pendingSms.remove(token) ?: return
        AgentNotifications.cancel(this, pending.notificationId)
        if (!approved) {
            val reason = if (timedOut) "El usuario no confirmo a tiempo." else "Envio rechazado por el usuario."
            replyError(pending.requestId, reason)
            return
        }
        // Reconfirma las condiciones en el momento del envio.
        if (!activeHostIsLocal()) {
            replyError(pending.requestId, "La pasarela SMS solo funciona con un servidor de red local.")
            return
        }
        val result = SmsGateway.send(this, pending.to, pending.text)
        result.fold(
            onSuccess = {
                replyOk(pending.requestId)
                AgentNotifications.notifyFromAgent(this, "SMS enviado", "Para ${pending.to}", null)
            },
            onFailure = { replyError(pending.requestId, it.message ?: "No se pudo enviar el SMS.") },
        )
    }

    private fun smsActionIntent(token: String, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AgentConnectionService::class.java)
            .setAction(action)
            .putExtra(EXTRA_SMS_TOKEN, token)
        return PendingIntent.getService(
            this,
            token.hashCode() * 2 + requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // --- Pasarela SMS: aviso continuo y recepcion de entrantes -------------

    private suspend fun refreshSmsGateway() {
        val settings = container.settings.settings.first()
        val shouldBeActive = settings.smsGatewayEnabled && activeHostIsLocal()
        if (shouldBeActive && !smsActiveShown) {
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                NotificationManagerCompat.from(this)
                    .notify(AgentNotifications.SMS_ACTIVE_ID, AgentNotifications.smsGatewayActiveNotification(this))
            }
            registerSmsReceiver()
            smsActiveShown = true
        } else if (!shouldBeActive && smsActiveShown) {
            AgentNotifications.cancel(this, AgentNotifications.SMS_ACTIVE_ID)
            unregisterSmsReceiver()
            smsActiveShown = false
        }
    }

    private fun registerSmsReceiver() {
        if (smsReceiver != null || !SmsGateway.hasReceivePermission(this)) return
        val receiver = IncomingSmsReceiver { from, body -> onIncomingSms(from, body) }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IncomingSmsReceiver.INTENT_FILTER,
            ContextCompat.RECEIVER_EXPORTED,
        )
        smsReceiver = receiver
    }

    private fun unregisterSmsReceiver() {
        smsReceiver?.let { runCatching { unregisterReceiver(it) } }
        smsReceiver = null
    }

    private fun onIncomingSms(from: String, body: String) {
        lifecycleScope.launch {
            val settings = container.settings.settings.first()
            val connected = container.connection.state.value.status == ConnectionStatus.CONNECTED
            if (!settings.smsGatewayEnabled || !activeHostIsLocal() || !connected) return@launch
            container.connection.send(
                WsClientFrame.NodeEvent(
                    event = SmsContract.EVENT_RECEIVED,
                    data = buildJsonObject {
                        put("from", from)
                        put("text", body)
                        put("received_at", System.currentTimeMillis())
                    },
                ),
            )
        }
    }

    private fun activeHostIsLocal(): Boolean {
        val host = container.repository.activeProfile.value?.host ?: return false
        return NetworkScope.isLocalNetwork(host)
    }

    private fun replyOk(requestId: String) {
        container.connection.send(
            WsClientFrame.NodeResult(
                requestId = requestId,
                ok = true,
                data = buildJsonObject { put("ok", true) },
            ),
        )
    }

    private fun replyError(requestId: String, message: String) {
        container.connection.send(
            WsClientFrame.NodeResult(requestId = requestId, ok = false, error = message),
        )
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es")
                ttsReady = true
                pendingSpeech?.let { speak(it) }
                pendingSpeech = null
            }
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !ttsReady) {
            pendingSpeech = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gmini_tts")
    }

    private fun stopEverything() {
        pendingSms.keys.toList().forEach { resolveSms(it, approved = false) }
        unregisterSmsReceiver()
        AgentNotifications.cancel(this, AgentNotifications.SMS_ACTIVE_ID)
        smsActiveShown = false
        container.connection.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundActive = false
        stopSelf()
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        unregisterSmsReceiver()
        container.connection.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.gmini.android.action.START"
        const val ACTION_STOP = "com.gmini.android.action.STOP"
        const val ACTION_SMS_ALLOW = "com.gmini.android.action.SMS_ALLOW"
        const val ACTION_SMS_DENY = "com.gmini.android.action.SMS_DENY"
        const val EXTRA_SMS_TOKEN = "com.gmini.android.extra.SMS_TOKEN"

        private const val CONFIRMATION_TIMEOUT_MS = 120_000L

        fun start(context: Context) {
            val intent = Intent(context, AgentConnectionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AgentConnectionService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
