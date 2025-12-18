package com.qubitlock.core.vault

import com.qubitlock.core.config.QubitLockProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

class VaultService(private val properties: QubitLockProperties) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        defaultRequest {
            url("${properties.vault.url}/v1/transit/")
            header("X-Vault-Token", properties.vault.token)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun encryptData(data: ByteArray): String {
        val plaintext = Base64.getEncoder().encodeToString(data)
        val response = client.post("encrypt/qubitlock") {
            setBody(mapOf("plaintext" to plaintext))
        }

        val json = Json.parseToJsonElement(response.body<String>()).jsonObject
        val dataObj = json["data"]?.jsonObject
            ?: throw RuntimeException("No data in response: $json")

        val ciphertext = dataObj["ciphertext"]?.jsonPrimitive?.content
            ?: throw RuntimeException("No ciphertext in response: $dataObj")

        return ciphertext
    }

    suspend fun decryptData(ciphertext: String): ByteArray {
        val response = client.post("decrypt/qubitlock") {
            setBody(mapOf("ciphertext" to ciphertext))
        }

        val json = Json.parseToJsonElement(response.body<String>()).jsonObject
        val dataObj = json["data"]?.jsonObject
            ?: throw RuntimeException("No data in response: $json")

        val plaintext = dataObj["plaintext"]?.jsonPrimitive?.content
            ?: throw RuntimeException("No plaintext in response: $dataObj")

        return Base64.getDecoder().decode(plaintext)
    }

    suspend fun healthCheck(): Boolean {
        return try {
            client.get("keys/qubitlock")
            true
        } catch (e: Exception) {
            false
        }
    }
}