package com.qubitlock.core

import com.qubitlock.core.config.QubitLockProperties
import com.qubitlock.core.models.EncryptOptions
import com.qubitlock.core.models.FileMetadata
import com.qubitlock.core.storage.FileRepository
import com.qubitlock.core.vault.VaultService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.Security
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QubitLockClientTest {

    private lateinit var client: QubitLockClient
    private lateinit var mockVaultService: VaultService
    private lateinit var mockFileRepository: FileRepository
    private val properties = QubitLockProperties(
        vault = QubitLockProperties.VaultConfig("http://localhost:8200", "token"),
        mongodb = QubitLockProperties.MongoDBConfig("mongodb://localhost:27017/test", "test"),
        features = QubitLockProperties.Features(compression = true, merkleTree = true)
    )

    @BeforeEach
    fun setUp() {
        mockVaultService = mockk()
        mockFileRepository = mockk()
        client = QubitLockClient(properties, mockVaultService, mockFileRepository)
    }

    @Test
    fun `encryptAndStore should successfully store file`() = runTest {
        val testData = "Test file content".toByteArray()
        val fileName = "test.txt"
        val ciphertext = "vault:v1:encrypted_data"
        val merkleRoot = "abc123def456"
        val fileId = "test-file-id"

        coEvery { mockVaultService.encryptData(any()) } returns ciphertext
        coEvery {
            mockFileRepository.save(any(), any())
        } returns FileMetadata(
            id = fileId,
            fileName = fileName,
            uploadedAt = Clock.System.now(),
            fileSize = testData.size,
            merkleRoot = merkleRoot,
            compressionEnabled = true,
            integrityVerified = true,
            originalName = fileName,
            contentType = "text/plain"
        )

        val result = client.encryptAndStore(testData, fileName, EncryptOptions())

        assertNotNull(result)
        assertEquals(fileId, result.id)
        assertEquals(fileName, result.fileName)
        assertEquals(testData.size, result.fileSize)
        assertEquals(merkleRoot, result.merkleRoot)
        assertTrue(result.compressionEnabled)
        assertTrue(result.integrityVerified)

        coVerify {
            mockVaultService.encryptData(any())
            mockFileRepository.save(any(), any())
        }
    }

    @Test
    fun `encryptAndStore should throw exception for empty file`() = runTest {
        val exception = assertThrows<IllegalArgumentException> {
            runTest { client.encryptAndStore(ByteArray(0), "empty.txt", EncryptOptions()) }
        }

        assertEquals("Нельзя сохранять пустые файлы", exception.message)
    }

    @Test
    fun `encryptAndStore should handle compression disabled`() = runTest {
        val testData = "Test without compression".toByteArray()
        val fileName = "test.txt"

        coEvery { mockVaultService.encryptData(any()) } returns "vault:v1:encrypted"
        coEvery { mockFileRepository.save(any(), any()) } returns mockk()

        val options = EncryptOptions(enableCompression = false, enableMerkleTree = false)
        val result = client.encryptAndStore(testData, fileName, options)

        assertNotNull(result)
        assertFalse(result.compressionEnabled)
        assertFalse(result.integrityVerified)
    }

    @Test
    fun `retrieveAndDecrypt should return original data`() = runTest {
        val fileId = "test-id"
        val originalData = "Original decrypted data".toByteArray()
        val ciphertext = "vault:v1:encrypted"

        val mockEncryptedPackage = mockk<com.qubitlock.core.models.EncryptedPackage> {
            every { id } returns fileId
            every { encryptedData } returns ciphertext
            every { merkleRoot } returns "merkle123"
            every { compressionAlgorithm } returns null
            every { originalSize } returns originalData.size
            every { processedSize } returns originalData.size
        }

        val mockMetadata = mockk<FileMetadata> {
            every { id } returns fileId
            every { compressionEnabled } returns false
            every { integrityVerified } returns true
        }

        coEvery { mockFileRepository.retrieve(fileId) } returns mockEncryptedPackage
        coEvery { mockFileRepository.getMetadata(fileId) } returns mockMetadata
        coEvery { mockVaultService.decryptData(ciphertext) } returns originalData

        val result = client.retrieveAndDecrypt(fileId)

        assertEquals(originalData, result)
        coVerify {
            mockFileRepository.retrieve(fileId)
            mockFileRepository.getMetadata(fileId)
            mockVaultService.decryptData(ciphertext)
        }
    }

    @Test
    fun `retrieveAndDecrypt should handle compressed data`() = runTest {
        val fileId = "compressed-id"
        val originalData = "A".repeat(1000).toByteArray()
        val compressedData = "Compressed data".toByteArray()
        val ciphertext = "vault:v1:compressed_encrypted"

        val mockEncryptedPackage = mockk<com.qubitlock.core.models.EncryptedPackage> {
            every { id } returns fileId
            every { encryptedData } returns ciphertext
            every { merkleRoot } returns "merkle456"
            every { compressionAlgorithm } returns "gzip"
            every { originalSize } returns originalData.size
            every { processedSize } returns compressedData.size
        }

        val mockMetadata = mockk<FileMetadata> {
            every { id } returns fileId
            every { compressionEnabled } returns true
            every { integrityVerified } returns true
        }

        coEvery { mockFileRepository.retrieve(fileId) } returns mockEncryptedPackage
        coEvery { mockFileRepository.getMetadata(fileId) } returns mockMetadata
        coEvery { mockVaultService.decryptData(ciphertext) } returns compressedData
        val exception = assertThrows<RuntimeException> {
            runTest { client.retrieveAndDecrypt(fileId) }
        }

        assertTrue(exception.message?.contains("Не получилось разжать") == true)
    }

    @Test
    fun `verifyIntegrity should return true for valid data`() = runTest {
        val fileId = "valid-file"
        val ciphertext = "vault:v1:encrypted"
        val merkleRoot = "abc123"

        val mockEncryptedPackage = mockk<com.qubitlock.core.models.EncryptedPackage> {
            every { id } returns fileId
            every { encryptedData } returns ciphertext
            every { merkleRoot } returns merkleRoot
            every { compressionAlgorithm } returns null
            every { originalSize } returns 100
            every { processedSize } returns 100
        }

        coEvery { mockFileRepository.retrieve(fileId) } returns mockEncryptedPackage

        val result = client.verifyIntegrity(fileId)
        assertNotNull(result)
        coVerify { mockFileRepository.retrieve(fileId) }
    }

    @Test
    fun `verifyIntegrity should throw exception when Merkle tree not enabled`() = runTest {
        val fileId = "no-merkle-file"

        val mockEncryptedPackage = mockk<com.qubitlock.core.models.EncryptedPackage> {
            every { id } returns fileId
            every { merkleRoot } returns null
        }

        coEvery { mockFileRepository.retrieve(fileId) } returns mockEncryptedPackage

        val exception = assertThrows<IllegalStateException> {
            runTest { client.verifyIntegrity(fileId) }
        }

        assertEquals("Проверка деревом Меркла отключена", exception.message)
    }

    @Test
    fun `detectCompressionAlgorithm should return correct algorithm`() {
        val method = QubitLockClient::class.java.getDeclaredMethod(
            "detectCompressionAlgorithm",
            String::class.java
        )
        method.isAccessible = true

        val testCases = mapOf(
            "test.txt" to "gz",
            "data.csv" to "gz",
            "document.json" to "gz",
            "image.jpg" to "bzip2",
            "photo.png" to "bzip2",
            "unknown.xyz" to "deflate"
        )

        testCases.forEach { (fileName, expectedSuffix) ->
            val result = method.invoke(client, fileName) as String
            assertTrue(result.endsWith(expectedSuffix),
                "For $fileName expected algorithm ending with $expectedSuffix, got $result")
        }
    }

    @Test
    fun `detectContentType should return correct MIME type`() {
        val method = QubitLockClient::class.java.getDeclaredMethod(
            "detectContentType",
            String::class.java
        )
        method.isAccessible = true

        val testCases = mapOf(
            "document.txt" to "text/plain",
            "data.json" to "application/json",
            "config.xml" to "application/xml",
            "image.jpg" to "image/jpeg",
            "photo.png" to "image/png",
            "manual.pdf" to "application/pdf",
            "unknown.xyz" to "application/octet-stream"
        )

        testCases.forEach { (fileName, expectedType) ->
            val result = method.invoke(client, fileName) as String
            assertEquals(expectedType, result,
                "For $fileName expected $expectedType, got $result")
        }
    }

    @Test
    fun `should handle different file sizes for compression decision`() = runTest {
        val smallData = "Small".toByteArray()
        val largeData = "A".repeat(1000).toByteArray()

        coEvery { mockVaultService.encryptData(any()) } returns "vault:v1:encrypted"
        coEvery { mockFileRepository.save(any(), any()) } returns mockk()
        client.encryptAndStore(smallData, "small.txt", EncryptOptions())
        client.encryptAndStore(largeData, "large.txt", EncryptOptions())

        coVerify(exactly = 2) {
            mockVaultService.encryptData(any())
            mockFileRepository.save(any(), any())
        }
    }
}