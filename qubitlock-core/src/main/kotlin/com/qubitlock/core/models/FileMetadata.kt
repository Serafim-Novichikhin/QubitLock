package com.qubitlock.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class FileMetadata(
    val id: String,
    val fileName: String,
    val uploadedAt: Instant,
    val fileSize: Int,
    val merkleRoot: String? = null,
    val compressionEnabled: Boolean = false,
    val integrityVerified: Boolean = false,
    val originalName: String? = null,
    val storageName: String? = null,
    val contentType: String? = null
)
