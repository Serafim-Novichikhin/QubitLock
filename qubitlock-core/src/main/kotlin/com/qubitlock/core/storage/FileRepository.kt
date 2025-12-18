package com.qubitlock.core.storage

import com.qubitlock.core.models.EncryptedPackage
import com.qubitlock.core.models.FileMetadata

interface FileRepository {
    suspend fun save(
        encryptedPackage: EncryptedPackage,
        metadata: FileMetadata
    ): FileMetadata

    suspend fun retrieve(fileId: String): EncryptedPackage

    suspend fun getMetadata(fileId: String): FileMetadata

    suspend fun delete(fileId: String): Boolean

    suspend fun listMetadata(
        limit: Int = 50,
        offset: Int = 0
    ): List<FileMetadata>
}
