package com.gmini.android.core.protocol

import com.gmini.android.core.GminiJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsContractTest {

    private fun params(text: String) = GminiJson.parseToJsonElement(text).jsonObject

    @Test
    fun `acepta un envio valido y recorta el destinatario`() {
        val result = SmsContract.parseSend(params("""{"to": " +51987654321 ", "text": "Hola"}"""))
        assertTrue(result.isSuccess)
        val request = result.getOrThrow()
        assertEquals("+51987654321", request.to)
        assertEquals("Hola", request.text)
    }

    @Test
    fun `rechaza cuando falta el destinatario`() {
        assertTrue(SmsContract.parseSend(params("""{"text": "Hola"}""")).isFailure)
    }

    @Test
    fun `rechaza un destinatario que no es un numero`() {
        assertTrue(SmsContract.parseSend(params("""{"to": "hola mundo", "text": "x"}""")).isFailure)
    }

    @Test
    fun `rechaza un texto vacio`() {
        assertTrue(SmsContract.parseSend(params("""{"to": "987654321", "text": "   "}""")).isFailure)
    }

    @Test
    fun `rechaza un texto demasiado largo`() {
        val long = "a".repeat(1601)
        assertTrue(SmsContract.parseSend(params("""{"to": "987654321", "text": "$long"}""")).isFailure)
    }

    @Test
    fun `reconoce formas de numero telefonico`() {
        assertTrue(SmsContract.looksLikePhoneNumber("+51 987 654 321"))
        assertTrue(SmsContract.looksLikePhoneNumber("(064) 123-456"))
        assertTrue(SmsContract.looksLikePhoneNumber("987654321"))
        assertFalse(SmsContract.looksLikePhoneNumber(""))
        assertFalse(SmsContract.looksLikePhoneNumber("no"))
        assertFalse(SmsContract.looksLikePhoneNumber("1234567890123456789012"))
        assertFalse(SmsContract.looksLikePhoneNumber("12ab34"))
    }
}
