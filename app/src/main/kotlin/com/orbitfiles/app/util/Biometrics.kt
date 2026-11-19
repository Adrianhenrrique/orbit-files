package com.orbitfiles.app.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

object Biometrics {
    fun canUseStrong(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Biometric OR screen lock. (BIOMETRIC_STRONG|DEVICE_CREDENTIAL is unsupported on API 28-29, WEAK is fine.) */
    private fun deviceAuthenticators(): Int = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun canUseDeviceAuth(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(deviceAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    /** App lock: biometric or screen lock (PIN/pattern/password). */
    fun authenticateDevice(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Confirme que é você")
            .setAllowedAuthenticators(deviceAuthenticators())
            .build()
        prompt.authenticate(info)
    }

    /** Vault: strong biometric bound to a Keystore cipher. */
    fun authenticateCipher(activity: FragmentActivity, title: String, cipher: Cipher, onSuccess: (Cipher) -> Unit, onError: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c != null) onSuccess(c) else onError("Falha na autenticação")
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Use sua digital ou rosto")
            .setNegativeButtonText("Usar senha")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
