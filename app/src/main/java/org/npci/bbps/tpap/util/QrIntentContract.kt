package org.npci.bbps.tpap.util

/**
 * Shared Intent extras contract for the QR -> PaymentScreen flow.
 */
object QrIntentContract {
    const val EXTRA_FROM_QR = "tpap.extra.FROM_QR"
    const val EXTRA_BILLER_ID = "tpap.extra.BILLER_ID"
    const val EXTRA_BILLER_NAME = "tpap.extra.BILLER_NAME"
    const val EXTRA_CONSUMER_NUMBER = "tpap.extra.CONSUMER_NUMBER"
    const val EXTRA_AMOUNT = "tpap.extra.AMOUNT"
    const val EXTRA_DUE_DATE = "tpap.extra.DUE_DATE"
    const val EXTRA_ENCRYPTED_PAYLOAD = "tpap.extra.ENCRYPTED_PAYLOAD"
    const val EXTRA_WRAPPED_DEK = "tpap.extra.WRAPPED_DEK"
    const val EXTRA_IV = "tpap.extra.IV"
    const val EXTRA_SENDER_PUBLIC_KEY = "tpap.extra.SENDER_PUBLIC_KEY"
    const val EXTRA_EXPIRY = "tpap.extra.EXPIRY"
}

