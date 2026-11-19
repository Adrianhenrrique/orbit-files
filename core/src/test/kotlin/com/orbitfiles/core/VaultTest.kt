package com.orbitfiles.core

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultTest {
    private val pw = "orbit-2026".toCharArray()

    @Test fun roundTripManySizes() {
        val key = VaultCrypto.newMasterKey()
        for (size in listOf(0, 1, 65_535, 65_536, 65_537, 131_072, 250_000)) {
            val data = Random(size).nextBytes(size)
            val enc = ByteArrayOutputStream()
            VaultCrypto.encrypt(key, ByteArrayInputStream(data), enc, VaultMeta("f$size.bin", size.toLong(), 1L, 2L))
            val dec = ByteArrayOutputStream()
            val meta = VaultCrypto.decrypt(key, ByteArrayInputStream(enc.toByteArray()), dec)
            assertContentEquals(data, dec.toByteArray(), "size $size")
            assertEquals("f$size.bin", meta.name)
        }
    }

    @Test fun tamperAndTruncationDetected() {
        val key = VaultCrypto.newMasterKey()
        val data = Random(1).nextBytes(200_000)
        val enc = ByteArrayOutputStream().also { VaultCrypto.encrypt(key, ByteArrayInputStream(data), it, VaultMeta("x", 0, 0, 0)) }.toByteArray()
        val flipped = enc.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertFailsWith<IOException> { VaultCrypto.decrypt(key, ByteArrayInputStream(flipped), ByteArrayOutputStream()) }
        // Drop the final chunk entirely: must not be accepted as a shorter valid file.
        val lastChunkBytes = 4 + (200_000 - 3 * 65_536) + 16 // length prefix + plaintext + GCM tag
        val truncated = enc.copyOf(enc.size - lastChunkBytes)
        assertFailsWith<IOException> { VaultCrypto.decrypt(key, ByteArrayInputStream(truncated), ByteArrayOutputStream()) }
        val wrongKey = VaultCrypto.newMasterKey()
        assertFailsWith<WrongPasswordException> { VaultCrypto.decrypt(wrongKey, ByteArrayInputStream(enc), ByteArrayOutputStream()) }
    }

    @Test fun storeLifecycle() {
        val root = tempDir()
        val store = VaultStore(File(root, ".OrbitVault"))
        assertFalse(store.isSetUp)
        val key = store.setup(pw)
        assertTrue(store.isSetUp)
        assertFailsWith<WrongPasswordException> { store.unlock("errada".toCharArray()) }
        assertContentEquals(key, store.unlock(pw))
        assertFalse(store.config().serialize().contains(String(pw)))

        val secret = root.write("segredo.txt", "dados sigilosos")
        val item = store.add(key, secret)
        assertEquals("segredo.txt", item.meta.name)
        assertFalse(item.blob.readBytes().toString(Charsets.ISO_8859_1).contains("sigilosos"))
        assertEquals(1, store.list(key).size)

        store.changePassword(pw, "nova-senha".toCharArray())
        val key2 = store.unlock("nova-senha".toCharArray())
        assertContentEquals(key, key2)
        val out = store.export(key2, store.list(key2).single(), File(root, "export"))
        assertEquals("dados sigilosos", out.readText())
        val streamed = store.addStream(key2, "nota.txt", 123L, "via stream".byteInputStream())
        assertEquals(10, streamed.meta.size)
        assertEquals("via stream", store.export(key2, store.list(key2).first { it.meta.name == "nota.txt" }, File(root, "export2")).readText())
        assertTrue(store.delete(streamed))
        assertTrue(store.delete(item))
        assertTrue(store.list(key).isEmpty())
    }
}
