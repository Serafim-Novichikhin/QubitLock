package com.qubitlock.starter.storage

import com.mongodb.client.gridfs.GridFSBuckets
import com.mongodb.client.gridfs.model.GridFSUploadOptions
import com.mongodb.client.model.Filters
import com.qubitlock.core.models.EncryptedPackage
import com.qubitlock.core.models.FileMetadata
import com.qubitlock.core.storage.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MongoFileRepository(
    private val database: com.mongodb.client.MongoDatabase
) : FileRepository {
    private val bucket = GridFSBuckets.create(database, "files")
    private val metadataCollection = database.getCollection("metadata")

    override suspend fun save(
        encryptedPackage: EncryptedPackage,
        metadata: FileMetadata
    ): FileMetadata = withContext(Dispatchers.IO) {
        // Опции загрузки
        val options = GridFSUploadOptions()
            .metadata(
                Document()
                    .append("contentType", metadata.contentType)
                    .append("originalName", metadata.originalName)
                    .append("merkleRoot", metadata.merkleRoot)
                    .append("compressionAlgorithm", encryptedPackage.compressionAlgorithm)
            )

        // Загрузка
        val inputStream = ByteArrayInputStream(encryptedPackage.encryptedData.toByteArray())
        val objectId = bucket.uploadFromStream(
            metadata.fileName,
            inputStream,
            options
        )

        // Обновление метаданных...
        val updatedMetadata = metadata.copy(
            storageName = objectId.toString()
        )
        // ...и сохранение
        val metadataDoc = Document()
            .append("_id", updatedMetadata.id)
            .append("fileName", updatedMetadata.fileName)
            .append("uploadedAt", updatedMetadata.uploadedAt.toString())
            .append("fileSize", updatedMetadata.fileSize)
            .append("merkleRoot", updatedMetadata.merkleRoot)
            .append("compressionEnabled", updatedMetadata.compressionEnabled)
            .append("integrityVerified", updatedMetadata.integrityVerified)
            .append("originalName", updatedMetadata.originalName)
            .append("storageName", updatedMetadata.storageName)
            .append("contentType", updatedMetadata.contentType)
            .append("compressionAlgorithm", encryptedPackage.compressionAlgorithm)

        metadataCollection.insertOne(metadataDoc)
        return@withContext updatedMetadata
    }

    override suspend fun retrieve(fileId: String): EncryptedPackage = withContext(Dispatchers.IO) {
        // Получение метаданных
        val metadataDoc = metadataCollection.find(Filters.eq("_id", fileId)).firstOrNull()
            ?: throw NoSuchElementException("Файл с id $fileId не найден")
        val storageName = metadataDoc.getString("storageName")
            ?: throw IllegalStateException("Нет данных GridFS")
        val objectId = ObjectId(storageName)
        val outputStream = ByteArrayOutputStream()
        bucket.downloadToStream(objectId, outputStream)
        val encryptedData = outputStream.toString(Charsets.UTF_8.name())

        return@withContext EncryptedPackage(
            id = fileId,
            encryptedData = encryptedData,
            merkleRoot = metadataDoc.getString("merkleRoot"),
            compressionAlgorithm = metadataDoc.getString("compressionAlgorithm"),
            originalSize = metadataDoc.getInteger("fileSize", 0),
            processedSize = encryptedData.length
        )
    }

    override suspend fun getMetadata(fileId: String): FileMetadata = withContext(Dispatchers.IO) {
        val doc = metadataCollection.find(Filters.eq("_id", fileId)).firstOrNull()
            ?: throw NoSuchElementException("File $fileId not found")

        return@withContext FileMetadata(
            id = doc.getString("_id") ?: fileId,
            fileName = doc.getString("fileName") ?: "",
            uploadedAt = kotlinx.datetime.Instant.parse(doc.getString("uploadedAt")),
            fileSize = doc.getInteger("fileSize", 0),
            merkleRoot = doc.getString("merkleRoot"),
            compressionEnabled = doc.getBoolean("compressionEnabled", false),
            integrityVerified = doc.getBoolean("integrityVerified", false),
            originalName = doc.getString("originalName"),
            storageName = doc.getString("storageName"),
            contentType = doc.getString("contentType")
        )
    }

    override suspend fun delete(fileId: String): Boolean = withContext(Dispatchers.IO) {
        var isDeleted = false
        val metadataDoc = metadataCollection.find(Filters.eq("_id", fileId)).firstOrNull()
        if (metadataDoc != null) {
            val storageName = metadataDoc.getString("storageName")
            if (storageName != null) {
                bucket.delete(ObjectId(storageName))
            }
            val result = metadataCollection.deleteOne(Filters.eq("_id", fileId))
           isDeleted = result.deletedCount > 0
        }
        return@withContext isDeleted
    }

    override suspend fun listMetadata(
        limit: Int,
        offset: Int
    ): List<FileMetadata> = withContext(Dispatchers.IO) {
        metadataCollection.find().skip(offset).limit(limit).toList().map { doc ->
                FileMetadata(
                    id = doc.getString("_id") ?: "",
                    fileName = doc.getString("fileName") ?: "",
                    uploadedAt = kotlinx.datetime.Instant.parse(doc.getString("uploadedAt")),
                    fileSize = doc.getInteger("fileSize", 0),
                    merkleRoot = doc.getString("merkleRoot"),
                    compressionEnabled = doc.getBoolean("compressionEnabled", false),
                    integrityVerified = doc.getBoolean("integrityVerified", false),
                    originalName = doc.getString("originalName"),
                    storageName = doc.getString("storageName"),
                    contentType = doc.getString("contentType")
                )
        }
    }
}
