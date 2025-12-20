package com.qubitlock.starter.storage

import com.mongodb.client.MongoClients
import com.qubitlock.core.models.EncryptedPackage
import com.qubitlock.core.models.FileMetadata
import de.bwaldvogel.mongo.MongoServer
import de.bwaldvogel.mongo.backend.memory.MemoryBackend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MongoFileRepositoryTest {

    private lateinit var mongoServer: MongoServer
    private lateinit var repository: MongoFileRepository

    @BeforeEach
    fun setup() {
        mongoServer = MongoServer(MemoryBackend())
        val connectionString = mongoServer.bind()
        val mongoClient = MongoClients.create("mongodb://$connectionString")
        val database = mongoClient.getDatabase("test_db")
        repository = MongoFileRepository(database)
    }

    @AfterEach
    fun tearDown() {
        mongoServer.shutdown()
    }

    @Test
    fun `save and retrieve should work correctly`() = runTest {
        val encryptedPackage = EncryptedPackage(
            id = "test-id-1",
            encryptedData = "encrypted-test-data-123",
            merkleRoot = "merkle-root-hash",
            compressionAlgorithm = "gzip",
            originalSize = 1024,
            processedSize = 512
        )

        val metadata = FileMetadata(
            id = "test-id-1",
            fileName = "test-file.txt",
            uploadedAt = Clock.System.now(),
            fileSize = 1024,
            merkleRoot = "merkle-root-hash",
            compressionEnabled = true,
            integrityVerified = true,
            originalName = "test-file.txt",
            contentType = "text/plain"
        )

        // Save
        val savedMetadata = repository.save(encryptedPackage, metadata)
        assertEquals(metadata.id, savedMetadata.id)
        assertNotNull(savedMetadata.storageName)

        // Retrieve
        val retrievedPackage = repository.retrieve("test-id-1")
        assertEquals(encryptedPackage.id, retrievedPackage.id)
        assertEquals(encryptedPackage.encryptedData, retrievedPackage.encryptedData)
        assertEquals(encryptedPackage.merkleRoot, retrievedPackage.merkleRoot)
        assertEquals(encryptedPackage.compressionAlgorithm, retrievedPackage.compressionAlgorithm)

        // Get metadata
        val retrievedMetadata = repository.getMetadata("test-id-1")
        assertEquals(metadata.id, retrievedMetadata.id)
        assertEquals(metadata.fileName, retrievedMetadata.fileName)
        assertEquals(metadata.fileSize, retrievedMetadata.fileSize)
        assertEquals(metadata.compressionEnabled, retrievedMetadata.compressionEnabled)
    }

    @Test
    fun `retrieve should throw exception for non-existent file`() = runTest {
        val exception = assertThrows<NoSuchElementException> {
            runTest { repository.retrieve("non-existent-id") }
        }

        assertTrue(exception.message?.contains("не найден") == true)
    }

    @Test
    fun `getMetadata should throw exception for non-existent file`() = runTest {
        val exception = assertThrows<NoSuchElementException> {
            runTest { repository.getMetadata("non-existent-id") }
        }

        assertTrue(exception.message?.contains("not found") == true)
    }

    @Test
    fun `delete should remove file and metadata`() = runTest {
        // First, save a file
        val encryptedPackage = EncryptedPackage(
            id = "to-delete",
            encryptedData = "data-to-delete",
            merkleRoot = null,
            compressionAlgorithm = null,
            originalSize = 100,
            processedSize = 100
        )

        val metadata = FileMetadata(
            id = "to-delete",
            fileName = "delete-me.txt",
            uploadedAt = Clock.System.now(),
            fileSize = 100,
            merkleRoot = null,
            compressionEnabled = false,
            integrityVerified = false,
            originalName = "delete-me.txt",
            contentType = "text/plain"
        )

        repository.save(encryptedPackage, metadata)

        // Verify it exists
        val retrieved = repository.retrieve("to-delete")
        assertEquals("to-delete", retrieved.id)

        // Delete
        val result = repository.delete("to-delete")
        assertTrue(result)

        // Verify it's deleted
        val exception = assertThrows<NoSuchElementException> {
            runTest { repository.retrieve("to-delete") }
        }
        assertTrue(exception.message?.contains("не найден") == true)
    }

    @Test
    fun `delete should return false for non-existent file`() = runTest {
        val result = repository.delete("does-not-exist")
        assertFalse(result)
    }

    @Test
    fun `listMetadata should return all files with pagination`() = runTest {
        // Save multiple files
        for (i in 1..5) {
            val encryptedPackage = EncryptedPackage(
                id = "file-$i",
                encryptedData = "data-$i",
                merkleRoot = null,
                compressionAlgorithm = null,
                originalSize = i * 100,
                processedSize = i * 100
            )

            val metadata = FileMetadata(
                id = "file-$i",
                fileName = "file-$i.txt",
                uploadedAt = Clock.System.now(),
                fileSize = i * 100,
                merkleRoot = null,
                compressionEnabled = false,
                integrityVerified = false,
                originalName = "file-$i.txt",
                contentType = "text/plain"
            )

            repository.save(encryptedPackage, metadata)
        }

        // List all
        val allFiles = repository.listMetadata(limit = 10, offset = 0)
        assertEquals(5, allFiles.size)

        // List with limit
        val limitedFiles = repository.listMetadata(limit = 2, offset = 0)
        assertEquals(2, limitedFiles.size)

        // List with offset
        val offsetFiles = repository.listMetadata(limit = 10, offset = 2)
        assertEquals(3, offsetFiles.size)

        // Verify ordering (should be by insertion)
        val orderedFiles = repository.listMetadata(limit = 5, offset = 0)
        assertEquals("file-1", orderedFiles[0].id)
        assertEquals("file-2", orderedFiles[1].id)
    }

    @Test
    fun `should handle special characters in encrypted data`() = runTest {
        val specialData = """
            {"data": "Special chars: \n\t\r\\"'}
            Line 2
            Line 3 with emoji 😀
        """.trimIndent()

        val encryptedPackage = EncryptedPackage(
            id = "special-chars",
            encryptedData = specialData,
            merkleRoot = null,
            compressionAlgorithm = null,
            originalSize = specialData.length,
            processedSize = specialData.length
        )

        val metadata = FileMetadata(
            id = "special-chars",
            fileName = "special.txt",
            uploadedAt = Clock.System.now(),
            fileSize = specialData.length,
            merkleRoot = null,
            compressionEnabled = false,
            integrityVerified = false,
            originalName = "special.txt",
            contentType = "text/plain"
        )

        repository.save(encryptedPackage, metadata)

        val retrieved = repository.retrieve("special-chars")
        assertEquals(specialData, retrieved.encryptedData)
    }

    @Test
    fun `should handle large encrypted data`() = runTest {
        val largeData = "A".repeat(1024 * 1024) // 1MB of data

        val encryptedPackage = EncryptedPackage(
            id = "large-file",
            encryptedData = largeData,
            merkleRoot = null,
            compressionAlgorithm = null,
            originalSize = largeData.length,
            processedSize = largeData.length
        )

        val metadata = FileMetadata(
            id = "large-file",
            fileName = "large.txt",
            uploadedAt = Clock.System.now(),
            fileSize = largeData.length,
            merkleRoot = null,
            compressionEnabled = false,
            integrityVerified = false,
            originalName = "large.txt",
            contentType = "text/plain"
        )

        repository.save(encryptedPackage, metadata)

        val retrieved = repository.retrieve("large-file")
        assertEquals(largeData, retrieved.encryptedData)
        assertEquals(largeData.length, retrieved.processedSize)
    }

    @Test
    fun `should preserve all metadata fields`() = runTest {
        val encryptedPackage = EncryptedPackage(
            id = "metadata-test",
            encryptedData = "test data",
            merkleRoot = "abc123",
            compressionAlgorithm = "gzip",
            originalSize = 1000,
            processedSize = 500
        )

        val metadata = FileMetadata(
            id = "metadata-test",
            fileName = "test.txt",
            uploadedAt = Clock.System.now(),
            fileSize = 1000,
            merkleRoot = "abc123",
            compressionEnabled = true,
            integrityVerified = true,
            originalName = "original.txt",
            storageName = null,
            contentType = "text/plain"
        )

        val savedMetadata = repository.save(encryptedPackage, metadata)
        assertNotNull(savedMetadata.storageName)

        val retrievedMetadata = repository.getMetadata("metadata-test")

        assertEquals(metadata.id, retrievedMetadata.id)
        assertEquals(metadata.fileName, retrievedMetadata.fileName)
        assertEquals(metadata.fileSize, retrievedMetadata.fileSize)
        assertEquals(metadata.merkleRoot, retrievedMetadata.merkleRoot)
        assertEquals(metadata.compressionEnabled, retrievedMetadata.compressionEnabled)
        assertEquals(metadata.integrityVerified, retrievedMetadata.integrityVerified)
        assertEquals(metadata.originalName, retrievedMetadata.originalName)
        assertEquals(metadata.contentType, retrievedMetadata.contentType)
        assertEquals(savedMetadata.storageName, retrievedMetadata.storageName)
    }
}