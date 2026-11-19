package com.orbitfiles.core

import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LanShareServerTest {
    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun req(url: String, method: String = "GET", body: ByteArray? = null, headers: Map<String, String> = emptyMap()): Pair<Int, ByteArray> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) { c.doOutput = true; c.setFixedLengthStreamingMode(body.size); c.outputStream.use { it.write(body) } }
        val code = c.responseCode
        val bytes = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        return code to bytes
    }

    @Test fun serverEndToEnd() {
        val root = tempDir()
        root.write("Fotos/praia.jpg", "0123456789")
        root.write("leia-me \"x\".txt", "olá")
        val events = mutableListOf<String>()
        val port = freePort()
        val server = LanShareServer(root, port, key = "chave123") { events += it }
        server.start()
        try {
            val base = "http://127.0.0.1:$port"
            assertEquals(403, req("$base/").first)
            assertEquals(403, req("$base/api/list?k=errada").first)
            val (code, html) = req("$base/?k=chave123")
            assertEquals(200, code)
            assertTrue(String(html).contains("Orbit Files"))
            val list = String(req("$base/api/list?k=chave123&p=").second)
            assertTrue(list.contains("\"name\":\"Fotos\",\"dir\":true"))
            assertTrue(list.contains("leia-me \\\"x\\\".txt"))
            val (c2, part) = req("$base/dl?k=chave123&p=Fotos%2Fpraia.jpg", headers = mapOf("Range" to "bytes=2-5"))
            assertEquals(206, c2)
            assertEquals("2345", String(part))
            assertEquals("0123456789", String(req("$base/dl?k=chave123&p=Fotos/praia.jpg").second))
            assertEquals(500, req("$base/api/list?k=chave123&p=../../").first)
            val (c3, _) = req("$base/up?k=chave123&p=Fotos&name=nova.txt", "PUT", "enviado".toByteArray())
            assertEquals(200, c3)
            assertEquals("enviado", File(root, "Fotos/nova.txt").readText())
            assertTrue(events.any { it.contains("nova.txt") })
            assertFailsWith<SecurityException> { server.resolve("../fora") }
        } finally {
            server.stop()
        }
    }
}
