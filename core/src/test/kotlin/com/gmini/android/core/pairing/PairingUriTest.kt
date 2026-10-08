package com.gmini.android.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingUriTest {

    private fun ok(raw: String): PairingTarget {
        val result = PairingUri.parse(raw)
        assertTrue("Se esperaba Ok pero fue $result", result is PairingUriResult.Ok)
        return (result as PairingUriResult.Ok).target
    }

    private fun invalid(raw: String): String {
        val result = PairingUri.parse(raw)
        assertTrue("Se esperaba Invalid pero fue $result", result is PairingUriResult.Invalid)
        return (result as PairingUriResult.Invalid).reason
    }

    @Test
    fun `analiza un enlace completo`() {
        val target = ok("gmini://pair?host=192.168.1.50&port=8765&code=482913")
        assertEquals("192.168.1.50", target.host)
        assertEquals(8765, target.port)
        assertEquals("482913", target.code)
    }

    @Test
    fun `acepta una barra despues de pair`() {
        val target = ok("gmini://pair/?host=10.0.0.4&port=9000&code=000001")
        assertEquals("10.0.0.4", target.host)
        assertEquals(9000, target.port)
        assertEquals("000001", target.code)
    }

    @Test
    fun `tolera el esquema en mayusculas y espacios`() {
        val target = ok("  GMINI://pair?host=100.71.131.70&port=8765&code=123456  ")
        assertEquals("100.71.131.70", target.host)
    }

    @Test
    fun `decodifica el host percent-encoded`() {
        val target = ok("gmini://pair?host=mi-pc.local&port=8765&code=654321")
        assertEquals("mi-pc.local", target.host)
    }

    @Test
    fun `rechaza otro esquema`() {
        invalid("https://pair?host=192.168.1.1&port=8765&code=123456")
    }

    @Test
    fun `rechaza una accion distinta de pair`() {
        invalid("gmini://chat?host=192.168.1.1&port=8765&code=123456")
    }

    @Test
    fun `rechaza un codigo que no tiene seis digitos`() {
        invalid("gmini://pair?host=192.168.1.1&port=8765&code=12345")
        invalid("gmini://pair?host=192.168.1.1&port=8765&code=1234567")
        invalid("gmini://pair?host=192.168.1.1&port=8765&code=12a456")
    }

    @Test
    fun `rechaza un puerto no numerico o fuera de rango`() {
        invalid("gmini://pair?host=192.168.1.1&port=abc&code=123456")
        invalid("gmini://pair?host=192.168.1.1&port=70000&code=123456")
        invalid("gmini://pair?host=192.168.1.1&port=0&code=123456")
    }

    @Test
    fun `rechaza un host vacio`() {
        invalid("gmini://pair?host=&port=8765&code=123456")
    }

    @Test
    fun `rechaza cuando faltan parametros`() {
        invalid("gmini://pair")
    }

    @Test
    fun `build y parse son simetricos`() {
        val original = PairingTarget("192.168.0.21", 8765, "246802")
        val round = ok(PairingUri.build(original))
        assertEquals(original, round)
    }
}
