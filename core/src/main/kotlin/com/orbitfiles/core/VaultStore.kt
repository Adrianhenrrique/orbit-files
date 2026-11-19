package com.orbitfiles.core

import java.io.File
import java.io.IOException
import java.util.UUID

data class VaultItem(val id: String, val blob: File, val meta: VaultMeta) {
    val category: FileCategory get() = FileCategory.ofName(meta.name)
}

/** File system layout for the encrypted vault: `vault.cfg` + `items/<uuid>.orbv`. */
class VaultStore(val root: File) {
    private val cfg = File(root, "vault.cfg")
    private val items = File(root, "items")

    init { items.mkdirs(); File(root, ".nomedia").let { if (!it.exists()) runCatching { it.createNewFile() } } }

    val isSetUp: Boolean get() = cfg.exists()

    fun config(): VaultConfig = VaultConfig.parse(cfg.readText())

    fun setup(password: CharArray): ByteArray {
        if (isSetUp) throw IOException("O cofre já está configurado")
        val master = VaultCrypto.newMasterKey()
        cfg.writeText(VaultCrypto.createConfig(password, master).serialize())
        return master
    }

    fun unlock(password: CharArray): ByteArray = VaultCrypto.unlock(password, config())

    fun changePassword(old: CharArray, new: CharArray) {
        val master = unlock(old)
        val tmp = File(root, "vault.cfg.tmp")
        tmp.writeText(VaultCrypto.createConfig(new, master).serialize())
        if (!tmp.renameTo(cfg)) { cfg.delete(); tmp.renameTo(cfg) }
    }

    fun list(key: ByteArray): List<VaultItem> =
        items.listFiles { f -> f.name.endsWith(".orbv") }.orEmpty().mapNotNull { blob ->
            runCatching {
                val meta = blob.inputStream().buffered().use { VaultCrypto.readMeta(key, it) }
                // Stream imports don't know their size upfront: estimate from the ciphertext (20 B overhead per 64 KB chunk).
                val fixed = if (meta.size >= 0) meta else meta.copy(size = (blob.length() - 100 - (blob.length() / 65_556) * 20).coerceAtLeast(0))
                VaultItem(blob.name.removeSuffix(".orbv"), blob, fixed)
            }.getOrNull()
        }.sortedByDescending { it.meta.addedAt }

    fun add(key: ByteArray, file: File, token: CancelToken = CancelToken(), onBytes: (Long) -> Unit = {}): VaultItem {
        if (file.isDirectory) throw IOException("Adicione arquivos individualmente (pastas não são suportadas)")
        val id = UUID.randomUUID().toString()
        val blob = File(items, "$id.orbv")
        VaultCrypto.encryptFile(key, file, blob, token, onBytes)
        return VaultItem(id, blob, blob.inputStream().buffered().use { VaultCrypto.readMeta(key, it) })
    }

    /** Encrypts arbitrary content (e.g. from a content:// URI) into the vault. */
    fun addStream(
        key: ByteArray,
        name: String,
        modified: Long,
        input: java.io.InputStream,
        token: CancelToken = CancelToken(),
        onBytes: (Long) -> Unit = {},
    ): VaultItem {
        val id = UUID.randomUUID().toString()
        val blob = File(items, "$id.orbv")
        val tmp = File(items, "$id.orbv.tmp")
        var size = 0L
        try {
            tmp.outputStream().buffered().use { out ->
                VaultCrypto.encrypt(key, input, out, VaultMeta(name, -1, modified, System.currentTimeMillis()), token) { n -> size += n; onBytes(n) }
            }
            if (!tmp.renameTo(blob)) throw IOException("Falha ao gravar no cofre")
        } catch (e: Throwable) { tmp.delete(); throw e }
        return VaultItem(id, blob, VaultMeta(name, size, modified, System.currentTimeMillis()))
    }

    fun export(key: ByteArray, item: VaultItem, destDir: File, token: CancelToken = CancelToken(), onBytes: (Long) -> Unit = {}): File {
        destDir.mkdirs()
        val dest = File(destDir, FileOps.uniqueName(destDir, item.meta.name))
        VaultCrypto.decryptFile(key, item.blob, dest, token, onBytes)
        return dest
    }

    fun delete(item: VaultItem): Boolean = item.blob.delete()

    /** Destroys the vault entirely (used by "reset" after forgotten password). */
    fun destroy() { FileOps.deleteRecursively(root); items.mkdirs() }
}
