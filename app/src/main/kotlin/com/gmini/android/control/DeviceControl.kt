package com.gmini.android.control

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.gmini.android.core.protocol.DeviceAction
import kotlinx.serialization.json.JsonObject

/**
 * Puente entre el servicio de agente y el servicio de accesibilidad. El sistema
 * instancia [GMiniAccessibilityService] cuando el usuario lo activa a mano; ese
 * servicio se registra aqui. Si no esta activo, [isConnected] es falso y ninguna
 * accion se ejecuta.
 */
object DeviceControl {

    @Volatile
    private var service: GMiniAccessibilityService? = null

    fun attach(svc: GMiniAccessibilityService) {
        service = svc
    }

    fun detach(svc: GMiniAccessibilityService) {
        if (service === svc) service = null
    }

    /** `true` si el servicio de accesibilidad esta conectado en este momento. */
    fun isConnected(): Boolean = service != null

    /** Ejecuta la accion si el servicio esta conectado; si no, falla. */
    fun perform(action: DeviceAction, onResult: (Result<JsonObject?>) -> Unit) {
        val svc = service
        if (svc == null) {
            onResult(Result.failure(IllegalStateException("El servicio de accesibilidad no esta activo.")))
            return
        }
        svc.perform(action, onResult)
    }

    /** Apaga el servicio de accesibilidad desde la propia app (para el boton Detener). */
    fun disableService() {
        service?.disableSelf()
    }

    /**
     * `true` si el usuario activo el servicio de accesibilidad de G-Mini en los
     * Ajustes de Android. Sirve para guiar al usuario; la ejecucion real depende
     * de [isConnected].
     */
    fun isEnabledInSettings(context: Context): Boolean {
        val expected = ComponentName(context, GMiniAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (component in splitter) {
            if (component.equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
