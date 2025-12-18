package com.qubitlock.core.models

import kotlinx.serialization.Serializable

@Serializable
data class EncryptOptions(
    val enableCompression: Boolean = true,
    val enableMerkleTree: Boolean = true,
    val customMetadata: Map<String, String> = emptyMap()
)
