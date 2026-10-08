package com.gmini.android.service

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
import com.gmini.android.core.protocol.NodeSurfaces
import com.gmini.android.core.protocol.WsClientFrame
import com.gmini.android.core.protocol.WsServerFrame
import com.gmini.android.di.AppContainer
import com.gmini.android.di.appContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * Servicio en primer plano que mantiene viva la conexion WebSocket con el
 * servidor activo. Convierte los frames `notify` en notificaciones de Android y
 * atiende `node.invoke` para dos superficies seguras: mostrar un texto en el
 * telefono (`display.text`) y leerlo en voz alta (`tts.speak`).
 *
 * Todo lo que llega del agente se trata como dato: solo se muestra o se
 * pronuncia, nunca se ejecuta.
 */
class AgentConnectionService : LifecycleService() {

    private lateinit var container: AppContainer
    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    @Volatile
    private var pendingSpeech: String? = null

    private var foregroundActive = false

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return Service.START_NOT_STICKY
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
            }
            container.connection.start(client, container.repository.deviceName(), surfaces)
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

            else -> replyError(frame.requestId, "Superficie no soportada: ${frame.surface}")
        }
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
        container.connection.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundActive = false
        stopSelf()
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        container.connection.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.gmini.android.action.START"
        const val ACTION_STOP = "com.gmini.android.action.STOP"

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
