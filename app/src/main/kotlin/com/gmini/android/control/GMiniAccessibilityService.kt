package com.gmini.android.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.gmini.android.core.protocol.DeviceAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

/**
 * Servicio de accesibilidad para el control del telefono (android-use). El
 * usuario lo activa a mano en Ajustes de Android; mientras no lo haga, no existe.
 * Solo ejecuta acciones cuando [com.gmini.android.service.AgentConnectionService]
 * se lo pide, y ese servicio ya comprobo que el modulo esta encendido y que el
 * servidor es de red local. No observa eventos: solo actua a peticion.
 */
class GMiniAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        DeviceControl.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No se observan eventos; el control es a peticion.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        DeviceControl.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        DeviceControl.detach(this)
        super.onDestroy()
    }

    /** Ejecuta [action] y entrega el resultado por [onResult] (puede ser asincrono). */
    fun perform(action: DeviceAction, onResult: (Result<JsonObject?>) -> Unit) {
        when (action) {
            is DeviceAction.Tap -> dispatchTap(action, onResult)
            is DeviceAction.Swipe -> dispatchSwipe(action, onResult)
            is DeviceAction.Text -> onResult(inputText(action.text))
            is DeviceAction.Key -> onResult(pressKey(action.key))
            DeviceAction.Screenshot -> screenshot(onResult)
        }
    }

    private fun dispatchTap(action: DeviceAction.Tap, onResult: (Result<JsonObject?>) -> Unit) {
        val (px, py) = toPixels(action.x, action.y)
        val path = Path().apply { moveTo(px, py) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        dispatch(gesture, onResult)
    }

    private fun dispatchSwipe(action: DeviceAction.Swipe, onResult: (Result<JsonObject?>) -> Unit) {
        val (x1, y1) = toPixels(action.x1, action.y1)
        val (x2, y2) = toPixels(action.x2, action.y2)
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, action.durationMs.toLong()))
            .build()
        dispatch(gesture, onResult)
    }

    private fun dispatch(gesture: GestureDescription, onResult: (Result<JsonObject?>) -> Unit) {
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onResult(okResult())
            override fun onCancelled(gestureDescription: GestureDescription?) =
                onResult(Result.failure(IllegalStateException("El gesto se cancelo.")))
        }
        if (!dispatchGesture(gesture, callback, null)) {
            onResult(Result.failure(IllegalStateException("No se pudo enviar el gesto.")))
        }
    }

    private fun inputText(text: String): Result<JsonObject?> {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return Result.failure(IllegalStateException("No hay ningun campo de texto enfocado."))
        if (!node.isEditable) {
            node.recycle()
            return Result.failure(IllegalStateException("El elemento enfocado no admite texto."))
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        return if (ok) okResult() else Result.failure(IllegalStateException("No se pudo escribir el texto."))
    }

    private fun pressKey(key: String): Result<JsonObject?> {
        val action = when (key) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return Result.failure(IllegalArgumentException("Tecla no soportada: $key"))
        }
        return if (performGlobalAction(action)) okResult() else Result.failure(IllegalStateException("No se pudo pulsar $key."))
    }

    private fun screenshot(onResult: (Result<JsonObject?>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(Result.failure(IllegalStateException("La captura necesita Android 11 o superior.")))
            return
        }
        takeScreenshotCompat(onResult)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshotCompat(onResult: (Result<JsonObject?>) -> Unit) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val result = runCatching {
                        val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                            ?: throw IllegalStateException("No se pudo leer la captura.")
                        val scaled = downscale(bitmap)
                        val bytes = ByteArrayOutputStream().use { out ->
                            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                            out.toByteArray()
                        }
                        if (scaled !== bitmap) scaled.recycle()
                        bitmap.recycle()
                        buildJsonObject {
                            put("image_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                            put("mime", "image/jpeg")
                        } as JsonObject?
                    }.also { screenshot.hardwareBuffer.close() }
                    onResult(result)
                }

                override fun onFailure(errorCode: Int) {
                    onResult(Result.failure(IllegalStateException("La captura fallo (codigo $errorCode).")))
                }
            },
        )
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val max = 1280
        val longer = maxOf(bitmap.width, bitmap.height)
        if (longer <= max) return bitmap
        val scale = max.toFloat() / longer
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    }

    private fun toPixels(x: Int, y: Int): Pair<Float, Float> {
        val metrics = resources.displayMetrics
        return (x / 1000f * metrics.widthPixels) to (y / 1000f * metrics.heightPixels)
    }

    private fun okResult(): Result<JsonObject?> = Result.success(buildJsonObject { put("ok", true) })
}
