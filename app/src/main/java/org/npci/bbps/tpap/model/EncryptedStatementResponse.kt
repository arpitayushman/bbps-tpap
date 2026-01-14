package org.npci.bbps.tpap.model

import kotlinx.serialization.Serializable

@Serializable
data class EncryptedStatementResponse(
    val encryptedPayload: String,
    val wrappedDek: String,
    val iv: String,
    val senderPublicKey: String,
    val expiry: Long
)
