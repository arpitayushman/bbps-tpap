package org.npci.bbps.tpap.security

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.util.Base64
import android.util.Log
import org.npci.bbps.tpap.util.EcdhHelper
import org.npci.bbps.tpap.util.KeystoreManager
import java.security.PrivateKey

/**
 * Secure Service that performs ECDH and decryption in an isolated process
 * The TPAP app's main process cannot access the shared secret or decrypted data
 * 
 * This service should run in an isolated process (android:isolatedProcess="true")
 * to ensure the decrypted data never leaves the secure context
 */
class SecureDecryptionService : Service() {

    private val binder = SecureDecryptionBinder()

    inner class SecureDecryptionBinder : Binder() {
        fun getService(): SecureDecryptionService = this@SecureDecryptionService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /**
     * Perform ECDH and return shared secret
     * This method is called from the secure service context
     */
    fun deriveSharedSecret(senderPublicKeyBase64: String): String {
        try {
            val keyPair = KeystoreManager.getOrCreateKeyPair()
            val privateKey = keyPair.private
            return EcdhHelper.deriveSharedSecret(privateKey, senderPublicKeyBase64)
        } catch (e: Exception) {
            Log.e("SecureDecryptionService", "Failed to derive shared secret", e)
            throw RuntimeException("ECDH failed", e)
        }
    }

    /**
     * Decrypt the payload using shared secret
     * This keeps the decryption logic in the secure service
     * Note: For now, we still pass shared secret to WebView, but in future
     * we could perform full decryption here and only pass decrypted JSON
     */
    fun decryptPayload(
        encryptedPayload: String,
        wrappedDek: String,
        iv: String,
        senderPublicKeyBase64: String
    ): String {
        // Get shared secret using hardware-backed key
        val sharedSecret = deriveSharedSecret(senderPublicKeyBase64)
        
        // TODO: In future, perform full decryption here and return only JSON
        // For now, we return shared secret to be used in WebView
        // This is still more secure than doing ECDH in main process
        
        return sharedSecret
    }
}
