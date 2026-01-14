package org.npci.bbps.tpap.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
            throw RuntimeException("Failed to encrypt statement: ${response.code} ${response.message}")
        }
        
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response body")
        
        return json.decodeFromString<EncryptedStatementResponse>(responseBody)
    }
}
