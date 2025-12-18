package com.qubitlock.starter.models

import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(
    val id: String,
    val fileName: String,
    val fileSize: Int,
    val uploadedAt: String
)

@Serializable
data class VerifyResponse(
    val fileId: String,
    val verified: Boolean,
    val timestamp: String
)

@Serializable
data class ErrorResponse(
    val error: String,
    val timestamp: String = java.time.Instant.now().toString()
)

@Serializable
data class HealthResponse(
    val status: String,
    val service: String = "QubitLock",
    val timestamp: String = java.time.Instant.now().toString()
)

@Serializable
data class VaultTestResponse(
    val status: String,
    val original: String? = null,
    val decrypted: String? = null,
    val match: Boolean? = null,
    val error: String? = null
)

@Serializable
data class MongoTestResponse(
    val status: String,
    val collections: List<String> = emptyList(),
    val error: String? = null
)