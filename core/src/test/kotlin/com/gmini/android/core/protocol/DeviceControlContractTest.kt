package com.gmini.android.core.protocol

import com.gmini.android.core.GminiJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceControlContractTest {

    private fun params(text: String) = GminiJson.parseToJsonElement(text).jsonObject

    @Test
    fun `parsea un tap valido`() {
        val action = DeviceControlContract.parse(DeviceControlContract.SURFACE_TAP, params("""{"x": 500, "y": 250}"""))
        assertTrue(action.isSuccess)
        assertEquals(DeviceAction.Tap(500, 250), action.getOrThrow())
    }

    @Test
    fun `rechaza un tap fuera de rango`() {
        assertTrue(DeviceControlContract.parse(DeviceControlContract.SURFACE_TAP, params("""{"x": 1200, "y": 0}""")).isFailure)
    }

    @Test
    fun `rechaza un tap sin coordenada`() {
        assertTrue(DeviceControlContract.parse(DeviceControlContract.SURFACE_TAP, params("""{"x": 10}""")).isFailure)
    }

    @Test
    fun `parsea un swipe con duracion por defecto`() {
        val action = DeviceControlContract.parse(
            DeviceControlContract.SURFACE_SWIPE,
            params("""{"x1": 100, "y1": 900, "x2": 100, "y2": 100}"""),
        )
        assertEquals(DeviceAction.Swipe(100, 900, 100, 100, 300), action.getOrThrow())
    }

    @Test
    fun `rechaza un swipe con duracion invalida`() {
        assertTrue(
            DeviceControlContract.parse(
                DeviceControlContract.SURFACE_SWIPE,
                params("""{"x1": 0, "y1": 0, "x2": 0, "y2": 0, "duration_ms": 999999}"""),
            ).isFailure,
        )
    }

    @Test
    fun `parsea texto y rechaza vacio`() {
        assertEquals(
            DeviceAction.Text("hola"),
            DeviceControlContract.parse(DeviceControlContract.SURFACE_TEXT, params("""{"text": "hola"}""")).getOrThrow(),
        )
        assertTrue(DeviceControlContract.parse(DeviceControlContract.SURFACE_TEXT, params("""{"text": ""}""")).isFailure)
    }

    @Test
    fun `acepta solo las teclas conocidas`() {
        assertEquals(
            DeviceAction.Key("home"),
            DeviceControlContract.parse(DeviceControlContract.SURFACE_KEY, params("""{"key": "HOME"}""")).getOrThrow(),
        )
        assertTrue(DeviceControlContract.parse(DeviceControlContract.SURFACE_KEY, params("""{"key": "power"}""")).isFailure)
    }

    @Test
    fun `screenshot no necesita parametros`() {
        assertEquals(
            DeviceAction.Screenshot,
            DeviceControlContract.parse(DeviceControlContract.SURFACE_SCREENSHOT, null).getOrThrow(),
        )
    }
}
