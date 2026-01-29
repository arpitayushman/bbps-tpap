package org.npci.bbps.tpap.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.npci.bbps.tpap.model.DeviceRegistrationRequest
import org.npci.bbps.tpap.model.EncryptStatementRequest
import org.npci.bbps.tpap.model.EncryptedStatementResponse
import java.util.concurrent.TimeUnit

object BackendApi {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true  // Include default values in JSON
    }
    
    fun registerDevice(
        baseUrl: String,
        request: DeviceRegistrationRequest
    ) {
        val jsonBody = json.encodeToString(request)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/device/register")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(httpRequest).execute()
        
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to register device: ${response.code} ${response.message}")
        }
        
        response.close()
    }

    private fun postEncryptedPayload(
        baseUrl: String,
        path: String,
        request: EncryptStatementRequest
    ): EncryptedStatementResponse {
        val jsonBody = json.encodeToString(request)
        // Debug: Log the JSON being sent
        android.util.Log.d("BackendApi", "Sending request to $path")
        android.util.Log.d("BackendApi", "Request JSON body: $jsonBody")
        android.util.Log.d("BackendApi", "Request category field value: '${request.category}'")
        android.util.Log.d("BackendApi", "Request statementId: '${request.statementId}'")
        android.util.Log.d("BackendApi", "Request consumerId: '${request.consumerId}'")
        android.util.Log.d("BackendApi", "Request deviceId: '${request.deviceId}'")
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url("$baseUrl$path")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(httpRequest).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            val errorMessage = "Failed to call $path: ${response.code} ${response.message}"
            val fullError = if (errorBody.isNotEmpty()) {
                "$errorMessage\nResponse: $errorBody"
            } else {
                errorMessage
            }
            throw RuntimeException(fullError)
        }
        
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response body")
        
        return json.decodeFromString<EncryptedStatementResponse>(responseBody)
    }

    /**
     * Bill statement encryption API (preferred).
     */
    fun encryptBillStatement(
        baseUrl: String,
        request: EncryptStatementRequest
    ): EncryptedStatementResponse = postEncryptedPayload(baseUrl, "/v1/bill-statement/encrypt", request)

    /**
     * Payment history encryption API (preferred).
     */
    fun encryptPaymentHistory(
        baseUrl: String,
        request: EncryptStatementRequest
    ): EncryptedStatementResponse = postEncryptedPayload(baseUrl, "/v1/payment-history/encrypt", request)

    /**
     * Legacy endpoint (kept for backward compatibility).
     */
    fun encryptStatement(
        baseUrl: String,
        request: EncryptStatementRequest
    ): EncryptedStatementResponse = postEncryptedPayload(baseUrl, "/v1/statement/encrypt", request)
}
