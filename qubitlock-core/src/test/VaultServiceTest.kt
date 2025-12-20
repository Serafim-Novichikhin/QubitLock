package com.qubitlock.core.vault

import com.qubitlock.core.config.QubitLockProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VaultServiceTest {

    private lateinit var vaultService: VaultService
    private lateinit var mockHttpClient: HttpClient
    private val properties = QubitLockProperties(
        vault = QubitLockProperties.VaultConfig(
            url = "http://localhost:8200",
            token = "test-token",
            path = "transit"
        ),
        mongodb = QubitLockProperties.MongoDBConfig("", ""),
        features = QubitLockProperties.Features()
    )

    @BeforeEach
    fun setUp() {
        mockHttpClient = mockk()
        vaultService = VaultService(properties)
        vaultService.client = mockHttpClient
    }

    @Test
    fun `encryptData should return ciphertext`() = runTest {
        val testData = "Test encryption data".toByteArray()
        val encodedData = Base64.getEncoder().encodeToString(testData)
        val expectedCiphertext = "vault:v1:encrypted_data"

        coEvery {
            mockHttpClient.post<String>(any<String>())
        } coAnswers {
            """
            {
                "data": {
                    "ciphertext": "$expectedCiphertext",
                    "key_version": 1
                }
            }
            """.trimIndent()
        }

        val result = vaultService.encryptData(testData)

        assertEquals(expectedCiphertext, result)
        coVerify {
            mockHttpClient.post<String>("encrypt/qubitlock") {
                body = mapOf("plaintext" to encodedData)
                header("X-Vault-Token", "test-token")
                contentType(ContentType.Application.Json)
            }
        }
    }

    @Test
    fun `encryptData should throw exception when Vault returns error`() = runTest {
        val testData = "Test data".toByteArray()

        coEvery {
            mockHttpClient.post<String>(any<String>())
        } coAnswers {
            """
            {
                "errors": ["Permission denied"]
            }
            """.trimIndent()
        }

        val exception = assertThrows<RuntimeException> {
            runBlocking { vaultService.encryptData(testData) }
        }

        assertTrue(exception.message?.contains("No data") == true ||
                exception.message?.contains("Permission") == true)
    }

    @Test
    fun `decryptData should return decrypted bytes`() = runTest {
        val ciphertext = "vault:v1:encrypted_data"
        val expectedPlaintext = "Decrypted test data"
        val encodedPlaintext = Base64.getEncoder().encodeToString(expectedPlaintext.toByteArray())

        coEvery {
            mockHttpClient.post<String>(any<String>())
        } coAnswers {
            """
            {
                "data": {
                    "plaintext": "$encodedPlaintext"
                }
            }
            """.trimIndent()
        }

        val result = vaultService.decryptData(ciphertext)

        assertEquals(expectedPlaintext, String(result))
        coVerify {
            mockHttpClient.post<String>("decrypt/qubitlock") {
                body = mapOf("ciphertext" to ciphertext)
            }
        }
    }

    @Test
    fun `decryptData should throw exception for invalid ciphertext`() = runTest {
        val invalidCiphertext = "invalid_ciphertext"

        coEvery {
            mockHttpClient.post<String>(any<String>())
        } coAnswers {
            """
            {
                "errors": ["invalid ciphertext"]
            }
            """.trimIndent()
        }

        val exception = assertThrows<RuntimeException> {
            runBlocking { vaultService.decryptData(invalidCiphertext) }
        }

        assertTrue(exception.message?.contains("No data") == true)
    }

    @Test
    fun `healthCheck should return true when Vault is accessible`() = runTest {
        coEvery {
            mockHttpClient.get<String>("keys/qubitlock")
        } coAnswers {
            """
            {
                "data": {
                    "name": "qubitlock",
                    "type": "aes256-gcm96"
                }
            }
            """.trimIndent()
        }

        val result = vaultService.healthCheck()

        assertTrue(result)
    }

    @Test
    fun `healthCheck should return false when Vault is not accessible`() = runTest {
        coEvery {
            mockHttpClient.get<String>("keys/qubitlock")
        } throws Exception("Connection refused")

        val result = vaultService.healthCheck()

        assertTrue(!result)
    }

    @Test
    fun `should handle empty data encryption`() = runTest {
        val emptyData = ByteArray(0)
        val encodedEmpty = Base64.getEncoder().encodeToString(emptyData)

        coEvery {
            mockHttpClient.post<String>(any<String>())
        } coAnswers {
            """
            {
                "data": {
                    "ciphertext": "vault:v1:empty",
                    "key_version": 1
                }
            }
            """.trimIndent()
        }

        val result = vaultService.encryptData(emptyData)

        assertNotNull(result)
        coVerify {
            mockHttpClient.post<String>("encrypt/qubitlock") {
                body = mapOf("plaintext" to encodedEmpty)
            }
        }
    }

    @Test
    fun `should handle special characters in data`() = runTest {
        val specialData = "Test data with special chars: \n\t\r\u0000\u00FF".toByteArray()
        val encodedData = Base64.getEncoder().encodeToString(specialData)

        coEvery {
            mockHttpClient.post<String>(any<String>())
        } coAnswers {
            """
            {
                "data": {
                    "ciphertext": "vault:v1:special",
                    "key_version": 1
                }
            }
            """.trimIndent()
        }

        val result = vaultService.encryptData(specialData)

        assertNotNull(result)
        coVerify {
            mockHttpClient.post<String>("encrypt/qubitlock") {
                body = mapOf("plaintext" to encodedData)
            }
        }
    }
}