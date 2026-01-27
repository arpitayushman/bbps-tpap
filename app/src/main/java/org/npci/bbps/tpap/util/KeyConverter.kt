package org.npci.bbps.tpap.util

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Utility to convert Android Keystore EC keys to JWK format for Web Crypto API
 */
object KeyConverter {

    /**
     * Convert EC private key from Android Keystore to JWK format
     * Handles both standard ECPrivateKey and Android Keystore keys
     */
    fun convertEcPrivateKeyToJwk(privateKey: PrivateKey, publicKey: ECPublicKey): JSONObject {
        // Try to cast to ECPrivateKey first (standard keys)
        val ecPrivateKey = if (privateKey is ECPrivateKey) {
            privateKey
        } else {
            // For Android Keystore keys, we need to extract the key material
            // Android Keystore keys don't allow direct access to key material
            // We need to use the public key's parameters and extract private key from encoded format
            try {
                // Get the encoded key and parse it
                val keyFactory = KeyFactory.getInstance("EC")
                val keySpec = PKCS8EncodedKeySpec(privateKey.encoded)
                keyFactory.generatePrivate(keySpec) as ECPrivateKey
            } catch (e: Exception) {
                throw IllegalArgumentException("Unable to extract EC private key parameters", e)
            }
        }
        val publicPoint = publicKey.w

        // Convert private key value (BigInteger) to base64url
        val privateKeyBytes = ecPrivateKey.s.toByteArray()
        // Remove leading zero if present (for unsigned representation)
        val dBytes = if (privateKeyBytes[0].toInt() == 0 && privateKeyBytes.size > 32) {
            privateKeyBytes.sliceArray(1 until privateKeyBytes.size)
        } else {
            privateKeyBytes
        }
        val d = Base64.encodeToString(dBytes, Base64.URL_SAFE or Base64.NO_WRAP)

        // Convert public key coordinates to base64url
        val x = publicPoint.affineX.toByteArray()
        val y = publicPoint.affineY.toByteArray()

        // Remove leading zeros if present
        val xBytes = if (x[0].toInt() == 0 && x.size > 32) {
            x.sliceArray(1 until x.size)
        } else {
            x
        }
        val yBytes = if (y[0].toInt() == 0 && y.size > 32) {
            y.sliceArray(1 until y.size)
        } else {
            y
        }

        val xBase64 = Base64.encodeToString(xBytes, Base64.URL_SAFE or Base64.NO_WRAP)
        val yBase64 = Base64.encodeToString(yBytes, Base64.URL_SAFE or Base64.NO_WRAP)

        return JSONObject().apply {
            put("kty", "EC")
            put("crv", "P-256")
            put("d", d)
            put("x", xBase64)
            put("y", yBase64)
        }
    }

    /**
     * Convert X509/SPKI public key bytes to JWK format (for reference)
     */
    fun convertPublicKeyToJwk(publicKey: ECPublicKey): JSONObject {
        val point = publicKey.w

        val x = point.affineX.toByteArray()
        val y = point.affineY.toByteArray()

        val xBytes = if (x[0].toInt() == 0 && x.size > 32) {
            x.sliceArray(1 until x.size)
        } else {
            x
        }
        val yBytes = if (y[0].toInt() == 0 && y.size > 32) {
            y.sliceArray(1 until y.size)
        } else {
            y
        }

        val xBase64 = Base64.encodeToString(xBytes, Base64.URL_SAFE or Base64.NO_WRAP)
        val yBase64 = Base64.encodeToString(yBytes, Base64.URL_SAFE or Base64.NO_WRAP)

        return JSONObject().apply {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", xBase64)
            put("y", yBase64)
        }
    }
}
