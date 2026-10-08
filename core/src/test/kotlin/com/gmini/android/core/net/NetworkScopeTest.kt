package com.gmini.android.core.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkScopeTest {

    @Test
    fun `direcciones locales se reconocen`() {
        val local = listOf(
            "localhost",
            "equipo.local",
            "127.0.0.1",
            "10.0.0.5",
            "192.168.1.50",
            "172.16.0.1",
            "172.31.255.254",
            "169.254.10.10",
            "::1",
            "[::1]",
        )
        local.forEach { assertTrue("$it deberia ser local", NetworkScope.isLocalNetwork(it)) }
    }

    @Test
    fun `direcciones remotas no se toman por locales`() {
        val remote = listOf(
            "8.8.8.8",
            "172.15.0.1",
            "172.32.0.1",
            "mi-servidor.com",
            "agent.example.org",
            "100.71.131.70", // Tailscale (CGNAT): se trata como remoto
            "203.0.113.4",
        )
        remote.forEach { assertFalse("$it no deberia ser local", NetworkScope.isLocalNetwork(it)) }
    }

    @Test
    fun `isRemote es el complemento de isLocalNetwork`() {
        assertTrue(NetworkScope.isRemote("1.1.1.1"))
        assertFalse(NetworkScope.isRemote("192.168.0.1"))
    }
}
