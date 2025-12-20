package com.qubitlock.starter.service

import com.qubitlock.core.QubitLockClient
import com.qubitlock.core.models.EncryptOptions
import com.qubitlock.core.models.FileMetadata
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FileServiceTest {

    private lateinit var fileService: FileService
    private lateinit var mockQubitLockClient: QubitLockClient
    private lateinit var mockApplicationCall: ApplicationCall

    @BeforeEach
    fun setUp() {
        mockQubitLockClient = mockk()
        mockApplicationCall = mockk()
        fileService = FileService(mockQubitLockClient)
    }

    @Test
    fun `uploadFile should process multipart request successfully`() = runTest {
        val fileName = "test.txt"
        val fileContent = "Test file content".toByteArray()
        val fileId = "test-id-123"

        val mockPartData = mockk<PartData.FileItem> {
            every { originalFileName } returns fileName
            every { streamProvider() } returns { fileContent.inputStream() }
            every { dispose() } just Runs
        }

        val mockMultipart = mockk<MultiPartData> {
            val parts = listOf(mockPartData)
            every { forEachPart(any()) } answers {
                val lambda = arg<suspend (PartData) -> Unit>(0)
                parts.forEach { part -> lambda(part) }
            }
        }

        coEvery { mockApplicationCall.receiveMultipart() } returns mockMultipart
        coEvery {
            mockQubitLockClient.encryptAndStore(
                fileData = fileContent,
                fileName = fileName,
                options = EncryptOptions(enableCompression = true, enableMerkleTree = true)
            )
        } returns FileMetadata(
            id = fileId,
            fileName = fileName,
            uploadedAt = Clock.System.now(),
            fileSize = fileContent.size,
            merkleRoot = "abc123",
            compressionEnabled = true,
            integrityVerified = true,
            originalName = fileName,
            contentType = "text/plain"
        )

        val result = fileService.uploadFile(mockApplicationCall)

        assertEquals(fileId, result["id"])
        assertEquals(fileName, result["fileName"])
        assertEquals(fileContent.size, result["fileSize"])
        assertNotNull(result["uploadedAt"])
    }

    @Test
    fun `uploadFile should handle empty file`() = runTest {
        val mockMultipart = mockk<MultiPartData> {
            every { forEachPart(any()) } answers {
            }
        }

        coEvery { mockApplicationCall.receiveMultipart() } returns mockMultipart

        val exception = kotlin.runCatching {
            fileService.uploadFile(mockApplicationCall)
        }.exceptionOrNull()

        assertNotNull(exception)
        assertEquals("No file uploaded", exception?.message)
    }

    @Test
    fun `downloadFile should respond with file data`() = runTest {
        val fileId = "test-id"
        val fileData = "Test file content".toByteArray()

        coEvery { mockQubitLockClient.retrieveAndDecrypt(fileId) } returns fileData
        coEvery { mockApplicationCall.respondBytes(any(), any(), any()) } just Runs

        fileService.downloadFile(mockApplicationCall, fileId)

        coVerify {
            mockQubitLockClient.retrieveAndDecrypt(fileId)
            mockApplicationCall.respondBytes(
                bytes = fileData,
                contentType = ContentType.Application.OctetStream,
                status = HttpStatusCode.OK
            )
        }
    }

    @Test
    fun `verifyFile should return verification result`() = runTest {
        val fileId = "verify-id"
        val verificationResult = true

        coEvery { mockQubitLockClient.verifyIntegrity(fileId) } returns verificationResult

        val result = fileService.verifyFile(mockApplicationCall, fileId)

        assertEquals(fileId, result["fileId"])
        assertEquals(verificationResult, result["verified"])
        assertNotNull(result["timestamp"])

        coVerify { mockQubitLockClient.verifyIntegrity(fileId) }
    }

    @Test
    fun `should handle multiple file parts`() = runTest {
        val fileName = "test.txt"
        val fileContent = "Test".toByteArray()

        val mockFilePart = mockk<PartData.FileItem> {
            every { originalFileName } returns fileName
            every { streamProvider() } returns { fileContent.inputStream() }
            every { dispose() } just Runs
        }

        val mockFormPart = mockk<PartData.FormItem> {
            every { dispose() } just Runs
        }

        val mockMultipart = mockk<MultiPartData> {
            val parts = listOf(mockFormPart, mockFilePart)
            every { forEachPart(any()) } answers {
                val lambda = arg<suspend (PartData) -> Unit>(0)
                parts.forEach { part -> lambda(part) }
            }
        }

        coEvery { mockApplicationCall.receiveMultipart() } returns mockMultipart
        coEvery { mockQubitLockClient.encryptAndStore(any(), any(), any()) } returns mockk()

        fileService.uploadFile(mockApplicationCall)

        coVerify(exactly = 1) {
            mockQubitLockClient.encryptAndStore(any(), any(), any())
        }
    }

    @Test
    fun `should handle different content types`() = runTest {
        val testCases = listOf(
            "document.pdf" to "application/pdf",
            "image.png" to "image/png",
            "data.json" to "application/json",
            "unknown.xyz" to "application/octet-stream"
        )

        testCases.forEach { (fileName, expectedContent) ->
            val fileContent = "Test".toByteArray()

            val mockPartData = mockk<PartData.FileItem> {
                every { originalFileName } returns fileName
                every { streamProvider() } returns { fileContent.inputStream() }
                every { dispose() } just Runs
            }

            val mockMultipart = mockk<MultiPartData> {
                every { forEachPart(any()) } answers {
                    val lambda = arg<suspend (PartData) -> Unit>(0)
                    lambda(mockPartData)
                }
            }

            coEvery { mockApplicationCall.receiveMultipart() } returns mockMultipart
            coEvery {
                mockQubitLockClient.encryptAndStore(
                    fileData = fileContent,
                    fileName = fileName,
                    options = any()
                )
            } returns FileMetadata(
                id = "test-id",
                fileName = fileName,
                uploadedAt = Clock.System.now(),
                fileSize = fileContent.size,
                merkleRoot = null,
                compressionEnabled = false,
                integrityVerified = false,
                originalName = fileName,
                contentType = expectedContent
            )

            val result = fileService.uploadFile(mockApplicationCall)

            assertEquals(fileName, result["fileName"])
        }
    }
}