package com.orbitfiles.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : IOException("Senha incorreta")

data class VaultMeta(val name: String, val size: Long, val modified: Long, val addedAt: Long)

/** Password protected master key description, stored next to the vault. Contains no secrets in clear. */
data class VaultConfig(val salt: ByteArray, val iterations: Int, val wrappedKey: ByteArray) {
    fun serialize(): String = listOf(
        "v=1",
        "salt=" + Base64.getEncoder().encodeToString(salt),
        "iter=$iterations",
        "key=" + Base64.getEncoder().encodeToString(wrappedKey),
    ).joinToString("\n")

    companion object {
        fun parse(text: String): VaultConfig {
            val map = text.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
            return VaultConfig(
                Base64.getDecoder().decode(map["salt"] ?: throw IOException("Cofre corrompido")),
                map["iter"]?.toInt() ?: throw IOException("Cofre corrompido"),
                Base64.getDecoder().decode(map["key"] ?: throw IOException("Cofre corrompido")),
            )
        }
    }

    override fun equals(other: Any?) = other is VaultConfig && salt.contentEquals(other.salt) &&
        iterations == other.iterations && wrappedKey.contentEquals(other.wrappedKey)
    override fun hashCode() = salt.contentHashCode() * 31 + wrappedKey.contentHashCode()
}

/**
 * AES-256-GCM vault.
 *  - A random 256-bit master key encrypts every file.
 *  - The master key is wrapped with a key derived from the password (PBKDF2-HMAC-SHA256),
 *    so changing the password never re-encrypts files.
 *  - Files are encrypted in authenticated 64 KB chunks (streaming, constant memory). The chunk
 *    index and a "final" flag are bound as AAD, which defeats reordering and truncation attacks.
 */
object VaultCrypto {
    private const val MAGIC = "ORBV"
    private const val VERSION: Byte = 1
    private const val CHUNK = 64 * 1024
    private const val TAG_BITS = 128
    const val DEFAULT_ITERATIONS = 150_000
    private val random = SecureRandom()

    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    fun newMasterKey(): ByteArray = randomBytes(32)

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun createConfig(password: CharArray, masterKey: ByteArray, iterations: Int = DEFAULT_ITERATIONS): VaultConfig {
        val salt = randomBytes(16)
        val kek = deriveKey(password, salt, iterations)
        return VaultConfig(salt, iterations, seal(kek, masterKey, "orbit-vault-key".toByteArray()))
    }

    fun unlock(password: CharArray, config: VaultConfig): ByteArray {
        val kek = deriveKey(password, config.salt, config.iterations)
        return try {
            open(kek, config.wrappedKey, "orbit-vault-key".toByteArray())
        } catch (_: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    /** One-shot AES-GCM: output = nonce(12) || ciphertext+tag. */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val nonce = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        aad?.let { c.updateAAD(it) }
        return nonce + c.doFinal(plain)
    }

    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray? = null): ByteArray {
        if (sealed.size < 12 + 16) throw IOException("Dados inválidos")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, 12))
        aad?.let { c.updateAAD(it) }
        return c.doFinal(sealed, 12, sealed.size - 12)
    }

    private fun nonce(prefix: ByteArray, counter: Int): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).array()

    private fun aad(counter: Int, final: Boolean): ByteArray =
        ByteBuffer.allocate(5).putInt(counter).put(if (final) 1 else 0).array()

    private fun chunkCipher(mode: Int, key: ByteArray, prefix: ByteArray, counter: Int, final: Boolean): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce(prefix, counter)))
            updateAAD(aad(counter, final))
        }

    fun encrypt(key: ByteArray, input: InputStream, output: OutputStream, meta: VaultMeta, token: CancelToken = CancelToken(), onBytes: (Long) -> Unit = {}) {
        val out = DataOutputStream(output)
        val prefix = randomBytes(8)
        out.write(MAGIC.toByteArray()); out.writeByte(VERSION.toInt()); out.write(prefix)
        // Chunk 0: metadata
        val metaBytes = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).apply { writeUTF(meta.name); writeLong(meta.size); writeLong(meta.modified); writeLong(meta.addedAt) }
        }.toByteArray()
        val metaCt = chunkCipher(Cipher.ENCRYPT_MODE, key, prefix, 0, false).doFinal(metaBytes)
        out.writeInt(metaCt.size); out.write(metaCt)
        // Data chunks: read ahead one buffer so we know which one is final.
        var counter = 1
        var current = readFully(input, CHUNK)
        while (true) {
            token.throwIfCancelled()
            val next = if (current.size == CHUNK) readFully(input, CHUNK) else ByteArray(0)
            val final = current.size < CHUNK || next.isEmpty()
            val ct = chunkCipher(Cipher.ENCRYPT_MODE, key, prefix, counter, final).doFinal(current)
            out.writeInt(ct.size); out.write(ct)
            onBytes(current.size.toLong())
            if (final) {
                if (next.isNotEmpty()) error("unreachable")
                break
            }
            counter++
            current = next
        }
        out.flush()
    }

    fun readMeta(key: ByteArray, input: InputStream): VaultMeta {
        val din = DataInputStream(input)
        val prefix = readHeader(din)
        return decodeMeta(key, prefix, din)
    }

    fun decrypt(key: ByteArray, input: InputStream, output: OutputStream, token: CancelToken = CancelToken(), onBytes: (Long) -> Unit = {}): VaultMeta {
        val din = DataInputStream(if (input.markSupported()) input else java.io.BufferedInputStream(input))
        val prefix = readHeader(din)
        val meta = decodeMeta(key, prefix, din)
        var counter = 1
        while (true) {
            token.throwIfCancelled()
            val len = try { din.readInt() } catch (_: EOFException) { throw IOException("Arquivo do cofre truncado") }
            if (len < 16 || len > CHUNK + 16) throw IOException("Arquivo do cofre corrompido")
            val ct = ByteArray(len); din.readFully(ct)
            val isLast = peekEof(din)
            val plain = try {
                chunkCipher(Cipher.DECRYPT_MODE, key, prefix, counter, isLast).doFinal(ct)
            } catch (_: AEADBadTagException) {
                throw IOException("Falha de integridade: o arquivo foi alterado ou está incompleto")
            }
            output.write(plain)
            onBytes(plain.size.toLong())
            if (isLast) break
            counter++
        }
        output.flush()
        return meta
    }

    fun encryptFile(key: ByteArray, src: File, dest: File, token: CancelToken = CancelToken(), onBytes: (Long) -> Unit = {}) {
        val meta = VaultMeta(src.name, src.length(), src.lastModified(), System.currentTimeMillis())
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        try {
            src.inputStream().buffered().use { i -> tmp.outputStream().buffered().use { o -> encrypt(key, i, o, meta, token, onBytes) } }
            if (!tmp.renameTo(dest)) throw IOException("Falha ao gravar no cofre")
        } catch (e: Throwable) { tmp.delete(); throw e }
    }

    fun decryptFile(key: ByteArray, src: File, dest: File, token: CancelToken = CancelToken(), onBytes: (Long) -> Unit = {}): VaultMeta {
        val tmp = File(dest.parentFile, ".${dest.name}.orbitpart")
        try {
            val meta = src.inputStream().buffered().use { i -> tmp.outputStream().buffered().use { o -> decrypt(key, i, o, token, onBytes) } }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("Falha ao exportar")
            dest.setLastModified(meta.modified)
            return meta
        } catch (e: Throwable) { tmp.delete(); throw e }
    }

    private fun readHeader(din: DataInputStream): ByteArray {
        val magic = ByteArray(4); din.readFully(magic)
        if (String(magic) != MAGIC) throw IOException("Não é um arquivo do cofre")
        if (din.readByte() != VERSION) throw IOException("Versão de cofre não suportada")
        return ByteArray(8).also { din.readFully(it) }
    }

    private fun decodeMeta(key: ByteArray, prefix: ByteArray, din: DataInputStream): VaultMeta {
        val len = din.readInt()
        if (len < 16 || len > 70_000) throw IOException("Cabeçalho corrompido")
        val ct = ByteArray(len); din.readFully(ct)
        val plain = try {
            chunkCipher(Cipher.DECRYPT_MODE, key, prefix, 0, false).doFinal(ct)
        } catch (_: AEADBadTagException) {
            throw WrongPasswordException()
        }
        val d = DataInputStream(ByteArrayInputStream(plain))
        return VaultMeta(d.readUTF(), d.readLong(), d.readLong(), d.readLong())
    }

    private fun peekEof(din: DataInputStream): Boolean {
        din.mark(1)
        val b = din.read()
        din.reset()
        return b < 0
    }

    private fun readFully(input: InputStream, size: Int): ByteArray {
        val buf = ByteArray(size)
        var off = 0
        while (off < size) {
            val n = input.read(buf, off, size - off)
            if (n < 0) break
            off += n
        }
        return if (off == size) buf else buf.copyOf(off)
    }
}
