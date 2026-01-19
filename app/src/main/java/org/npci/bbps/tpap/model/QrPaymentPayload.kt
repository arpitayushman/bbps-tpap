package org.npci.bbps.tpap.model

import kotlinx.serialization.Serializable

/**
 * QR payload for "scan -> open PaymentScreen" flow.
 *
 * - Bill details are expected to be present as plain text (or JSON) fields.
 * - Encrypted fields are passed through to the callable UI for statement rendering.
 */
@Serializable
data class QrPaymentPayload(
    val billerId: String? = null,
    val billerName: String? = null,
    val consumerNumber: String? = null,
    val amount: String? = null,
    val dueDate: String? = null,
    val encryptedPayload: String? = null,
    val wrappedDek: String? = null,
    val iv: String? = null,
    val senderPublicKey: String? = null,
    val expiry: Long? = null
)

