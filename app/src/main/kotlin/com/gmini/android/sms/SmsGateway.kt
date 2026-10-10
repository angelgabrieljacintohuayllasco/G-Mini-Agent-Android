package com.gmini.android.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Envio de SMS para la pasarela. Es deliberadamente pequeno y sin estado: toda
 * la logica de consentimiento (interruptor opt-in, confirmacion por mensaje,
 * solo red local) vive en el servicio; aqui solo esta el envio en si, con la
 * comprobacion del permiso del sistema.
 */
object SmsGateway {

    fun hasSendPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasReceivePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun managerOf(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    /**
     * Envia [text] a [to]. Divide los textos largos en varias partes. Devuelve
     * un fallo si no hay permiso o si la plataforma lo rechaza. No valida el
     * contenido: eso lo hace [com.gmini.android.core.protocol.SmsContract] antes.
     */
    fun send(context: Context, to: String, text: String): Result<Unit> {
        if (!hasSendPermission(context)) {
            return Result.failure(SecurityException("Falta el permiso para enviar SMS."))
        }
        return runCatching {
            val manager = managerOf(context)
            val parts = manager.divideMessage(text)
            if (parts.size <= 1) {
                manager.sendTextMessage(to, null, text, null, null)
            } else {
                manager.sendMultipartTextMessage(to, null, parts, null, null)
            }
        }
    }
}
