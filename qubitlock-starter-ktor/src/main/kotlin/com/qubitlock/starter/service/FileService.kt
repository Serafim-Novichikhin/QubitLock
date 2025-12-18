package com.qubitlock.starter.service

import com.qubitlock.core.QubitLockClient
import com.qubitlock.core.models.EncryptOptions
import com.qubitlock.starter.models.UploadResponse
import com.qubitlock.starter.models.VerifyResponse
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class FileService(private val qubitLockClient: QubitLockClient) {
    suspend fun uploadFile(call: ApplicationCall): UploadResponse {
        return withContext(Dispatchers.IO) {
            val multipart = call.receiveMultipart()
            var fileName = ""
            var fileData = ByteArray(0)
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                        fileName = part.originalFileName ?: "unknown"
                        fileData = part.streamProvider().readBytes()
                }
                part.dispose()
            }
            if (fileData.isEmpty()) {
                throw IllegalArgumentException("Файл не загружен")
            }

            val metadata = qubitLockClient.encryptAndStore(
                fileData = fileData,
                fileName = fileName,
                options = EncryptOptions(
                    enableCompression = true,
                    enableMerkleTree = true
                )
            )

            UploadResponse(
                id = metadata.id,
                fileName = metadata.fileName,
                fileSize = metadata.fileSize,
                uploadedAt = metadata.uploadedAt.toString()
            )
        }
    }

    suspend fun downloadFile(call: ApplicationCall, fileId: String) {
        call.respondBytes(
            bytes = qubitLockClient.retrieveAndDecrypt(fileId),
            contentType = ContentType.Application.OctetStream,
            status = HttpStatusCode.OK
        )
    }

    suspend fun verifyFile(fileId: String): VerifyResponse {
        return VerifyResponse(
            fileId = fileId,
            verified = qubitLockClient.verifyIntegrity(fileId),
            timestamp = Date().toString()
        )
    }
}