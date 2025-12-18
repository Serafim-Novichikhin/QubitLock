package com.qubitlock.core.models

import kotlinx.serialization.Serializable

@Serializable
data class EncryptedPackage(
    val id: String,
    val encryptedData: String,
    val merkleRoot: String?,
    val compressionAlgorithm: String?,
    val originalSize: Int,
    val processedSize: Int
)