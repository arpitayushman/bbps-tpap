package org.npci.bbps.tpap.model

import kotlinx.serialization.Serializable

/**
 * Data model for QR code content
 * Contains encrypted statement data that can be scanned and decrypted
 */
@Serializable
data class QrCodeData(
    val encryptedPayload: String,
    val wrappedDek: String,
    val iv: String,
    val senderPublicKey: String,
    val expiry: Long? = null
)
