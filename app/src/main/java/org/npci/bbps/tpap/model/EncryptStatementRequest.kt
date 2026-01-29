package org.npci.bbps.tpap.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class EncryptStatementRequest(
    val statementId: String,
    val consumerId: String,
    val deviceId: String,
    @SerialName("category")
    val category: String? = null  // "ELECTRICITY" or "CREDIT_CARD", null defaults to ELECTRICITY
)
