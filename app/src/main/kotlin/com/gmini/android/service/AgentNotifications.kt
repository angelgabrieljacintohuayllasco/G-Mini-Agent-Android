package com.gmini.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gmini.android.MainActivity
import com.gmini.android.R
import com.gmini.android.data.ConnectionState
import com.gmini.android.data.ConnectionStatus
import java.util.concurrent.atomic.AtomicInteger

/** Canales, identificadores y constructores de notificaciones de la app. */
object AgentNotifications {

    const val CHANNEL_CONNECTION = "gmini_connection"
    const val CHANNEL_NOTIFY = "gmini_notify"
    const val CHANNEL_DISPLAY = "gmini_display"
    const val CHANNEL_SMS = "gmini_sms"
    const val CHANNEL_CONTROL = "gmini_control"

    const val FOREGROUND_ID = 1001
    const val SMS_ACTIVE_ID = 1002
    const val CONTROL_ACTIVE_ID = 1003

    private val dynamicId = AtomicInteger(2000)

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONNECTION,
                "Conexion con el agente",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Mantiene viva la conexion para recibir avisos del agente." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NOTIFY,
                "Avisos del agente",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Mensajes que el agente envia al telefono." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DISPLAY,
                "Mensajes en pantalla",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Textos que el agente pide mostrar (superficie display.text)." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SMS,
                "Pasarela SMS",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Estado de la pasarela SMS y confirmaciones para enviar mensajes." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONTROL,
                "Control del telefono",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Avisa cuando el agente puede manejar el telefono." },
        )
    }

    fun foregroundNotification(context: Context, state: ConnectionState): android.app.Notification {
        val text = when (state.status) {
            ConnectionStatus.CONNECTED ->
                state.agentName?.let { "Conectado a $it" } ?: "Conectado"
            ConnectionStatus.CONNECTING -> "Conectando..."
            ConnectionStatus.UNAUTHORIZED -> "El servidor rechazo el token"
            ConnectionStatus.ERROR -> "Sin conexion, reintentando"
            ConnectionStatus.DISCONNECTED -> "Desconectado"
        }
        return NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_stat_gmini)
            .setContentTitle("G-Mini")
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyFromAgent(context: Context, title: String?, body: String?, priority: String?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFY)
            .setSmallIcon(R.drawable.ic_stat_gmini)
            .setContentTitle(title ?: "G-Mini")
            .setContentText(body.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.orEmpty()))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setPriority(if (priority == "high") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        post(context, builder.build())
    }

    fun displayMessage(context: Context, text: String, seconds: Int?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_DISPLAY)
            .setSmallIcon(R.drawable.ic_stat_gmini)
            .setContentTitle("G-Mini")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (seconds != null && seconds in 1..300) {
            builder.setTimeoutAfter(seconds * 1000L)
        }
        post(context, builder.build())
    }

    /** Aviso continuo, no descartable, de que la pasarela SMS esta activa. */
    fun smsGatewayActiveNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_SMS)
            .setSmallIcon(R.drawable.ic_stat_gmini)
            .setContentTitle("Pasarela SMS activa")
            .setContentText("El agente puede leer los SMS entrantes y, con tu confirmacion, enviar mensajes.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "El agente puede leer los SMS entrantes y, con tu confirmacion, enviar mensajes. " +
                        "Apagala en Ajustes > Pasarela SMS.",
                ),
            )
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    /**
     * Confirmacion para enviar un SMS. Muestra destinatario y texto, y dos
     * acciones (Permitir / Rechazar) que el servicio recibe. No se descarta sola:
     * el mensaje no se envia hasta que el usuario pulse Permitir.
     */
    fun postSmsConfirmation(
        context: Context,
        notificationId: Int,
        to: String,
        text: String,
        allow: PendingIntent,
        deny: PendingIntent,
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_SMS)
            .setSmallIcon(R.drawable.ic_stat_gmini)
            .setContentTitle("El agente quiere enviar un SMS a $to")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Para: $to\n\n$text"))
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Rechazar", deny)
            .addAction(0, "Permitir", allow)
            .setDeleteIntent(deny)
        post(context, builder.build(), notificationId)
    }

    /** Aviso continuo, no descartable, de que el control del telefono esta activo. */
    fun controlActiveNotification(context: Context, stop: PendingIntent): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_CONTROL)
            .setSmallIcon(R.drawable.ic_stat_gmini)
            .setContentTitle("Control del telefono activo")
            .setContentText("El agente puede ver y manejar esta pantalla.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "El agente puede ver y manejar esta pantalla mientras esto este activo. " +
                        "Pulsa Detener para cortarlo de inmediato.",
                ),
            )
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Detener", stop)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    fun nextSmsNotificationId(): Int = dynamicId.incrementAndGet()

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun post(context: Context, notification: android.app.Notification, id: Int) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun post(context: Context, notification: android.app.Notification) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(dynamicId.incrementAndGet(), notification)
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
