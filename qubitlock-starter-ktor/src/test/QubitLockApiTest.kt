package com.qubitlock.starter

import com.qubitlock.core.QubitLockClient
import com.qubitlock.core.config.QubitLockProperties
import com.qubitlock.core.models.FileMetadata
import com.qubitlock.core.vault.VaultService
import com.qubitlock.starter.storage.MongoFileRepository
import com.mongodb.client.MongoClients
import de.bwaldvogel.mongo.MongoServer
import de.bwaldvogel.mongo.backend.memory.MemoryBackend
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QubitLockApiTest {

    private lateinit var mongoServer: MongoServer
    private lateinit var httpClient: HttpClient

    @BeforeEach
    fun setup() {
        mongoServer = MongoServer(MemoryBackend())
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    @AfterEach
    fun tearDown() {
        mongoServer.shutdown()
        httpClient.close()
    }

    @Test
    fun `health endpoint should return UP`() = testApplication {
        val connectionString = mongoServer.bind()

        application {
            val properties = QubitLockProperties(
                vault = QubitLockProperties.VaultConfig(
                    url = "http://localhost:8200",
                    token = "test-token",
                    path = "transit"
                ),
                mongodb = QubitLockProperties.MongoDBConfig(
                    connectionString = "mongodb://$connectionString",
                    database = "test"
                ),
                features = QubitLockProperties.Features(
                    compression = true,
                    merkleTree = true
                )
            )

            QubitLockAutoConfiguration.configure(this)
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val json = Json.parseToJsonElement(response.bodyAsText())
        assertTrue(json.jsonObject["status"]?.jsonPrimitive?.content == "UP")
    }

    @Test
    fun `should upload and verify file`() = testApplication {
        val connectionString = mongoServer.bind()

        application {
            val properties = QubitLockProperties(
                vault = QubitLockProperties.VaultConfig(
                    url = "http://localhost:8200",
                    token = "test-token",
                    path = "transit"
                ),
                mongodb = QubitLockProperties.MongoDBConfig(
                    connectionString = "mongodb://$connectionString",
                    database = "test"
                ),
                features = QubitLockProperties.Features(
                    compression = true,
                    merkleTree = true
                )
            )

            // Create real components
            val mongoClient = MongoClients.create("mongodb://$connectionString")
            val database = mongoClient.getDatabase("test")
            val fileRepository = MongoFileRepository(database)

            // Mock VaultService since we don't have Vault running
            val mockVaultService = io.mockk.mockk<VaultService> {
                coEvery { encryptData(any()) } returns "vault:v1:mock_encrypted"
                coEvery { decryptData(any()) } returns "Decrypted content".toByteArray()
                coEvery { healthCheck() } returns true
            }

            val qubitLockClient = QubitLockClient(properties, mockVaultService, fileRepository)

            // Store client in attributes
            attributes.put(io.ktor.util.AttributeKey<QubitLockClient>("QubitLockClient"), qubitLockClient)
        }

        // Test file upload
        val uploadResponse = client.post("/files") {
            setBody(
                """
                --boundary
                Content-Disposition: form-data; name="file"; filename="test.txt"
                Content-Type: text/plain
                
                Test file content
                --boundary--
                """.trimIndent()
            )
            header(HttpHeaders.ContentType, ContentType.MultiPart.FormData.withParameter("boundary", "boundary").toString())
        }

        assertEquals(HttpStatusCode.OK, uploadResponse.status)

        val uploadJson = Json.parseToJsonElement(uploadResponse.bodyAsText())
        val fileId = uploadJson.jsonObject["id"]?.jsonPrimitive?.content
        assertNotNull(fileId)

        // Test verification
        val verifyResponse = client.get("/files/$fileId/verify")
        assertEquals(HttpStatusCode.OK, verifyResponse.status)

        val verifyJson = Json.parseToJsonElement(verifyResponse.bodyAsText())
        assertEquals(fileId, verifyJson.jsonObject["fileId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should return 404 for non-existent file`() = testApplication {
        val connectionString = mongoServer.bind()

        application {
            val properties = QubitLockProperties(
                vault = QubitLockProperties.VaultConfig(
                    url = "http://localhost:8200",
                    token = "test-token",
                    path = "transit"
                ),
                mongodb = QubitLockProperties.MongoDBConfig(
                    connectionString = "mongodb://$connectionString",
                    database = "test"
                ),
                features = QubitLockProperties.Features()
            )

            QubitLockAutoConfiguration.configure(this)
        }

        val response = client.get("/files/non-existent-id")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should handle malformed requests gracefully`() = testApplication {
        val connectionString = mongoServer.bind()

        application {
            val properties = QubitLockProperties(
                vault = QubitLockProperties.VaultConfig(
                    url = "http://localhost:8200",
                    token = "test-token",
                    path = "transit"
                ),
                mongodb = QubitLockProperties.MongoDBConfig(
                    connectionString = "mongodb://$connectionString",
                    database = "test"
                ),
                features = QubitLockProperties.Features()
            )

            QubitLockAutoConfiguration.configure(this)
        }

        // Test with empty body
        val response = client.post("/files") {
            setBody("")
            header(HttpHeaders.ContentType, ContentType.MultiPart.FormData.toString())
        }

        // Should return 400 or 500 depending on implementation
        assertTrue(response.status == HttpStatusCode.BadRequest ||
                response.status == HttpStatusCode.InternalServerError)
    }
}