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
    
    fun encryptStatement(
        baseUrl: String,
        request: EncryptStatementRequest
    ): EncryptedStatementResponse {
        val jsonBody = json.encodeToString(request)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/statement/encrypt")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(httpRequest).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            val errorMessage = "Failed to encrypt statement: ${response.code} ${response.message}"
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
}
