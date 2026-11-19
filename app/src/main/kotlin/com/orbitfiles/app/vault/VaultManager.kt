package com.orbitfiles.app.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.orbitfiles.core.CancelToken
import com.orbitfiles.core.VaultItem
import com.orbitfiles.core.VaultStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the encrypted vault. The decrypted master key only lives in memory while unlocked and
 * is wiped on lock / auto-lock. Biometric unlock wraps the master key with a hardware-backed
 * Android Keystore key that requires a strong biometric for every use.
 */
class VaultManager(private val context: Context) {
    val store = VaultStore(File(context.noBackupFilesDir, "vault"))
    private val bioFile = File(context.noBackupFilesDir, "vault/bio.key")

    private var key: ByteArray? = null
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    val isSetUp: Boolean get() = store.isSetUp
    val hasBiometricKey: Boolean get() = bioFile.exists()

    /** Scratch area for opening vault files; wiped on lock. */
    val previewDir: File get() = File(context.cacheDir, "vault_preview").apply { mkdirs() }

    fun setup(password: CharArray) { key = store.setup(password); _unlocked.value = true }

    fun unlock(password: CharArray) { key = store.unlock(password); _unlocked.value = true }

    fun lock() {
        key?.fill(0)
        key = null
        _unlocked.value = false
        previewDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun changePassword(old: CharArray, new: CharArray) = store.changePassword(old, new)

    private fun requireKey(): ByteArray = key ?: throw IllegalStateException("Cofre bloqueado")

    fun list(): List<VaultItem> = store.list(requireKey())

    fun add(file: File, token: CancelToken, onBytes: (Long) -> Unit): VaultItem = store.add(requireKey(), file, token, onBytes)

    fun addStream(name: String, modified: Long, input: java.io.InputStream, token: CancelToken, onBytes: (Long) -> Unit): VaultItem =
        store.addStream(requireKey(), name, modified, input, token, onBytes)

    fun export(item: VaultItem, destDir: File, token: CancelToken, onBytes: (Long) -> Unit): File =
        store.export(requireKey(), item, destDir, token, onBytes)

    fun decryptForPreview(item: VaultItem): File {
        val dir = File(previewDir, item.id).apply { mkdirs() }
        val existing = File(dir, item.meta.name)
        if (existing.exists()) return existing
        return store.export(requireKey(), item, dir, CancelToken()) {}
    }

    fun delete(item: VaultItem) = store.delete(item)

    fun reset() { lock(); bioFile.delete(); deleteKeystoreKey(); store.destroy() }

    // ---------------- Biometric (Android Keystore) ----------------

    private val alias = "orbit_vault_bio"

    private fun keystoreKey(createIfMissing: Boolean): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!createIfMissing) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return gen.generateKey()
    }

    private fun deleteKeystoreKey() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }

    /** Cipher to be authorised by BiometricPrompt before [enableBiometric]. Vault must be unlocked. */
    fun encryptCipher(): Cipher {
        deleteKeystoreKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey(true)) }
    }

    fun enableBiometric(authorized: Cipher) {
        val ct = authorized.doFinal(requireKey())
        bioFile.parentFile?.mkdirs()
        bioFile.writeText(Base64.encodeToString(authorized.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP))
    }

    fun disableBiometric() { bioFile.delete(); deleteKeystoreKey() }

    /** Returns null when biometric data changed (key invalidated) – user must use the password. */
    fun decryptCipher(): Cipher? {
        if (!bioFile.exists()) return null
        return try {
            val iv = Base64.decode(bioFile.readText().substringBefore(':'), Base64.NO_WRAP)
            val k = keystoreKey(false) ?: return null
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            disableBiometric(); null
        } catch (_: Exception) { null }
    }

    fun unlockWithBiometric(authorized: Cipher) {
        val ct = Base64.decode(bioFile.readText().substringAfter(':'), Base64.NO_WRAP)
        key = authorized.doFinal(ct)
        _unlocked.value = true
    }
}
