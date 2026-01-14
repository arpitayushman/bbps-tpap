package org.npci.bbps.tpap.model

import kotlinx.serialization.Serializable

@Serializable
data class EncryptStatementRequest(
    val statementId: String,
    val consumerId: String,
    val deviceId: String
)
