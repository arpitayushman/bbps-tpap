package org.npci.bbps.tpap.util

import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Helper to perform ECDH key agreement using Android Keystore keys
 * Since Android Keystore keys can't export private key material,
 * we perform ECDH in Android and pass the shared secret to JavaScript
 */
object EcdhHelper {

    /**
     * Perform ECDH key agreement and return the shared secret
     * @param devicePrivateKey Device's private key from Keystore
     * @param senderPublicKeyBase64 Sender's public key in Base64 (X509/SPKI format)
     * @return Shared secret as Base64 encoded string
     */
    fun deriveSharedSecret(devicePrivateKey: PrivateKey, senderPublicKeyBase64: String): String {
        try {
            // Decode sender's public key
            val senderPubKeyBytes = Base64.decode(senderPublicKeyBase64, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance("EC")
            val senderPublicKey = keyFactory.generatePublic(
                X509EncodedKeySpec(senderPubKeyBytes)
            ) as ECPublicKey

            // Perform ECDH key agreement
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(devicePrivateKey)
            keyAgreement.doPhase(senderPublicKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            // Return as Base64
            return Base64.encodeToString(sharedSecret, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("EcdhHelper", "Failed to derive shared secret", e)
            throw RuntimeException("ECDH key agreement failed", e)
        }
    }

    /**
     * Derive the wrapping key from shared secret (first 32 bytes)
     */
    fun deriveWrappingKey(sharedSecretBase64: String): String {
        val sharedSecret = Base64.decode(sharedSecretBase64, Base64.NO_WRAP)
        val wrappingKey = sharedSecret.sliceArray(0 until 32)
        return Base64.encodeToString(wrappingKey, Base64.NO_WRAP)
    }

    /**
     * Derive the wrap IV from shared secret (SHA-256 hash, first 12 bytes)
     */
    fun deriveWrapIv(sharedSecretBase64: String): String {
        val sharedSecret = Base64.decode(sharedSecretBase64, Base64.NO_WRAP)
        val hash = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        val wrapIv = hash.sliceArray(0 until 12)
        return Base64.encodeToString(wrapIv, Base64.NO_WRAP)
    }
}
