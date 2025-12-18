package com.qubitlock.core

import com.qubitlock.core.config.QubitLockProperties
import com.qubitlock.core.models.*
import com.qubitlock.core.storage.FileRepository
import com.qubitlock.core.utils.MerkleTree
import com.qubitlock.core.vault.VaultService
import kotlinx.datetime.Clock
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.IllegalArgumentException
import java.util.*

class QubitLockClient(
    private val properties: QubitLockProperties,
    private val vaultService: VaultService,
    private val fileRepository: FileRepository,
    private val merkleTree: MerkleTree = MerkleTree()
) {

    suspend fun encryptAndStore(
        fileData: ByteArray,
        fileName: String,
        options: EncryptOptions = EncryptOptions()
    ): FileMetadata {
        if (fileData.isEmpty()) {
            println("Нет данных для сохранения")
            throw IllegalArgumentException("Нельзя сохранять пустые файлы")
        }

        // Сжатие
        var compressionAlgorithm: String? = null
        var processedData = fileData
        if (properties.features.compression && options.enableCompression) {
            compressionAlgorithm = detectCompressionAlgorithm(fileName)
            val compressed = compressData(fileData, compressionAlgorithm)
            if (compressed.size > fileData.size) {
                println("Файл маленький, поэтому сжатие не требуется.")
                compressionAlgorithm = null
            }
            else if (compressed.isNotEmpty()) {
                println("Сжато: ${fileData.size} → ${compressed.size} байт. Эффективность: ${(fileData.size - compressed.size) * 100 / fileData.size}%")
                processedData = compressed
            }
            else {
                println("⚠️ Странно: размер сжатого файла равен нулю. Поэтому сохраняем исходный.")
            }
        }


        // Шифрование
        val ciphertext = vaultService.encryptData(processedData)

        // Дерево Меркла
        val merkleRoot = if (properties.features.merkleTree && options.enableMerkleTree) {
            val root = merkleTree.calculateRoot(ciphertext.toByteArray())
            println("Дерево Меркла сформировано успешно")
            root
        } else null

        // Создание пакета

        val encryptedPackage = EncryptedPackage(
            id = UUID.randomUUID().toString(),
            encryptedData = ciphertext,
            merkleRoot = merkleRoot,
            compressionAlgorithm = compressionAlgorithm,
            originalSize = fileData.size,
            processedSize = processedData.size
        )

        println("Пакет создан: ${encryptedPackage.id}")
        println("   Алгоритм сжатия: $compressionAlgorithm")


        return fileRepository.save(
            encryptedPackage = encryptedPackage,
            metadata = FileMetadata(
                id = encryptedPackage.id,
                fileName = fileName,
                uploadedAt = Clock.System.now(),
                fileSize = fileData.size,
                merkleRoot = merkleRoot,
                compressionEnabled = properties.features.compression && options.enableCompression,
                integrityVerified = properties.features.merkleTree && options.enableMerkleTree,
                originalName = fileName,
                contentType = detectContentType(fileName)
            )
        )
    }

    suspend fun retrieveAndDecrypt(fileId: String): ByteArray {
        val encryptedPackage = fileRepository.retrieve(fileId)
        val metadata = fileRepository.getMetadata(fileId)
        val decryptedData = vaultService.decryptData(
            ciphertext = encryptedPackage.encryptedData
        )

        // Распаковка
        val finalData = if (encryptedPackage.compressionAlgorithm != null && metadata.compressionEnabled) {
            println("Распаковка с алгоритмом ${encryptedPackage.compressionAlgorithm}...")
            try {
                val decompressed = decompressData(decryptedData, encryptedPackage.compressionAlgorithm)
                println("✅ Распаковано: ${decryptedData.size} → ${decompressed.size} байт")
                decompressed
            } catch (e: Exception) {
                println("⚠️  Не получилось распаковать: ${e.message}.")
                decryptedData
            }
        } else {
            println("Распаковка не требуется")
            decryptedData
        }

        // Проверка целостности
        if (metadata.integrityVerified && encryptedPackage.merkleRoot != null) {
            val currentRoot = merkleTree.calculateRoot(encryptedPackage.encryptedData.toByteArray())
            if (currentRoot != encryptedPackage.merkleRoot) {
                throw SecurityException("⚠️  Целостность нарушена! (Корень Меркла не совпадает)")
            }
            println("✅ Целостность не нарушена")
        }
        return finalData
    }
    suspend fun verifyIntegrity(fileId: String): Boolean {
        val encryptedPackage = fileRepository.retrieve(fileId)
        if (encryptedPackage.merkleRoot == null) {
            throw IllegalStateException("Проверка деревом Меркла отключена")
        }
        return merkleTree.calculateRoot(encryptedPackage.encryptedData.toByteArray()) == encryptedPackage.merkleRoot
    }

    private fun compressData(data: ByteArray, algorithm: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        println("Сжатие алгоритмом $algorithm...")

        try {
            val compressor = CompressorStreamFactory().createCompressorOutputStream(algorithm, outputStream)
            compressor.use { it.write(data) }
            return outputStream.toByteArray()
        } catch (e: Exception) {
            println("⚠️  Не получилось сжать: ${e.message}. Возвращаем исходные данные.")
            return data
        }
    }

    private fun decompressData(data: ByteArray, algorithm: String): ByteArray {
        val inputStream = ByteArrayInputStream(data)
        try {
            val decompressor = CompressorStreamFactory().createCompressorInputStream(algorithm, inputStream)
            return decompressor.use { it.readBytes() }
        } catch (e: Exception) {
            println("❌ Ошибка разжатия с алгоритмом $algorithm: ${e.message}!")
            throw RuntimeException("Не получилось разжать, используя алгоритм $algorithm", e)
        }
    }

    private fun detectCompressionAlgorithm(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            in listOf("txt", "csv", "json", "xml", "log", "pdf") -> CompressorStreamFactory.GZIP
            in listOf("jpg", "jpeg", "png", "bmp", "gif") -> CompressorStreamFactory.BZIP2
            else -> CompressorStreamFactory.DEFLATE
        }
    }

    private fun detectContentType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}