package org.npci.bbps.tpap.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationRequest(
    val consumerId: String,
    val deviceId: String,
    val devicePublicKey: String
)
