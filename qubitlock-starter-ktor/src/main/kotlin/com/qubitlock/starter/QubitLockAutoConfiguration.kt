package com.qubitlock.starter

import com.qubitlock.core.QubitLockClient
import com.qubitlock.core.config.QubitLockProperties
import com.qubitlock.core.vault.VaultService
import com.qubitlock.starter.models.*
import com.qubitlock.starter.service.FileService
import com.qubitlock.starter.storage.MongoFileRepository
import com.mongodb.client.MongoClients
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.respond
import io.ktor.util.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.time.Instant
import java.util.*

class QubitLockAutoConfiguration {

    companion object {
        fun configure(application: Application) {
            application.install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            // По-другому не получается настроить конфигурацию (с помощью .conf)
            val properties = QubitLockProperties(
                vault = QubitLockProperties.VaultConfig(
                    url = "http://localhost:8200",
                    token = "root-token",
                    path = "transit"
                ),
                mongodb = QubitLockProperties.MongoDBConfig(
                    connectionString = "mongodb://localhost:27017/qubitlock",
                    database = "qubitlock"
                ),
                features = QubitLockProperties.Features(
                    compression = true,
                    merkleTree = true
                )
            )
            try {
                println("Подключение к MongoDB...")
                val mongoClient = MongoClients.create(properties.mongodb.connectionString)
                val database = mongoClient.getDatabase(properties.mongodb.database)

                database.runCommand(org.bson.Document("ping", 1))
                println("✅ MongoDB успешно подключена")

                val vaultService = VaultService(properties)
                val fileRepository = MongoFileRepository(database)
                val qubitLockClient = QubitLockClient(properties, vaultService, fileRepository)
                val fileService = FileService(qubitLockClient)

                // Регистрация в Application
                application.attributes.put(QubitLockClientKey, qubitLockClient)
                application.attributes.put(FileServiceKey, fileService)

                configureRoutes(application, fileService)

            } catch (e: Exception) {
                println("❌ Не удалось выполнить инициализацию: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }

        private fun configureRoutes(application: Application, fileService: FileService) {
            application.routing {
                get("/health") {
                    call.respond(HealthResponse(status = "UP"))
                }
                post("/files") {
                    try {
                        val result = fileService.uploadFile(call)
                        println("✅ Файл успешно загружен. id: ${result.id}")
                        call.respond(result)
                    } catch (e: Exception) {
                        println("❌ Не удалось загрузить: ${e.message}")
                        e.printStackTrace()
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(error = e.message ?: "Unknown error")
                        )
                    }
                }

                get("/files/{id}") {
                    val fileId = call.parameters["id"] ?: throw IllegalArgumentException("Нет параметра \"id\"")
                    try {
                        fileService.downloadFile(call, fileId)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse(error = "Файл не найден: ${e.message}")
                        )
                    }
                }

                get("/files/{id}/verify") {
                    val fileId = call.parameters["id"] ?: throw IllegalArgumentException("Нет параметра \"id\"")
                    try {
                        call.respond(fileService.verifyFile(fileId))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(error = e.message ?: "Не удалось выполнить проверку")
                        )
                    }
                }

                get("/test/vault") {
                    try {
                        val properties = QubitLockProperties(
                            vault = QubitLockProperties.VaultConfig(
                                url = "http://localhost:8200",
                                token = "root-token",
                                path = "transit"
                            ),
                            mongodb = QubitLockProperties.MongoDBConfig(
                                connectionString = "",
                                database = ""
                            ),
                            features = QubitLockProperties.Features()
                        )
                        val vaultService = VaultService(properties)
                        val plaintext = "Какой-то текст для проверки vault".toByteArray()
                        val ciphertext = vaultService.encryptData(plaintext)
                        val decrypted = vaultService.decryptData(ciphertext)
                        call.respond(
                            VaultTestResponse(
                                status = "success",
                                original = String(plaintext),
                                decrypted = String(decrypted),
                                match = String(plaintext) == String(decrypted)
                            )
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            VaultTestResponse(
                                status = "failed",
                                error = e.message
                            )
                        )
                    }
                }

                get("/test/mongo") {
                    try {
                        val mongoClient = MongoClients.create("mongodb://localhost:27017/qubitlock")
                        val collections = mongoClient.getDatabase("qubitlock").listCollectionNames().into(mutableListOf())
                        mongoClient.close()
                        call.respond(
                            MongoTestResponse(
                                status = "connected",
                                collections = collections
                            )
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            MongoTestResponse(
                                status = "error",
                                error = e.message
                            )
                        )
                    }
                }
            }
        }
    }
}

val QubitLockClientKey = AttributeKey<QubitLockClient>("QubitLockClient")
val FileServiceKey = AttributeKey<FileService>("FileService")