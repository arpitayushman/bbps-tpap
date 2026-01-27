package org.npci.bbps.tpap.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

/**
 * Manages device key pair in Android Keystore
 * Replaces dependency on callable app for key management
 */
object KeystoreManager {

    private const val KEY_ALIAS = "bbps_device_key"

    /**
     * Get or create the device key pair
     * Uses the same key alias as the callable app for compatibility
     */
    fun getOrCreateKeyPair(): KeyPair {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (ks.containsAlias(KEY_ALIAS)) {
            val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            Log.d("KeystoreManager", "Using existing key pair")
            return KeyPair(entry.certificate.publicKey, entry.privateKey)
        }

        Log.d("KeystoreManager", "Creating new key pair")
        return try {
            generateKeyPair(strongBox = true)
        } catch (e: Exception) {
            Log.w("KeystoreManager", "StrongBox not available, falling back to regular keystore", e)
            // StrongBox not available → fallback
            generateKeyPair(strongBox = false)
        }
    }

    private fun generateKeyPair(strongBox: Boolean): KeyPair {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)

        if (strongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        kpg.initialize(builder.build())
        val keyPair = kpg.generateKeyPair()
        Log.d("KeystoreManager", "Key pair generated successfully (StrongBox: $strongBox)")
        return keyPair
    }

    /**
     * Get the public key as Base64 encoded string
     */
    fun getPublicKeyBase64(): String {
        val keyPair = getOrCreateKeyPair()
        return android.util.Base64.encodeToString(
            keyPair.public.encoded,
            android.util.Base64.NO_WRAP
        )
    }
}
