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

    const val FOREGROUND_ID = 1001

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
