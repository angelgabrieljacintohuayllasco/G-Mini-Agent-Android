package com.gmini.android.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Recibe los SMS entrantes y los entrega a [onReceived]. El servicio lo registra
 * en tiempo de ejecucion solo mientras la pasarela esta activa; no hay un
 * receptor declarado en el manifiesto, asi que no escucha nada si la pasarela
 * esta apagada. Las partes de un mensaje multipart del mismo remitente se unen.
 */
class IncomingSmsReceiver(
    private val onReceived: (from: String, body: String) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val from = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }
        if (body.isEmpty()) return
        onReceived(from, body)
    }

    companion object {
        val INTENT_FILTER get() = android.content.IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
    }
}
