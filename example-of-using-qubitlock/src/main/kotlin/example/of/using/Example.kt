package example.of.using

import com.qubitlock.core.QubitLockClient
import com.qubitlock.core.config.QubitLockProperties
import com.qubitlock.core.models.EncryptOptions
import com.qubitlock.core.vault.VaultService
import com.qubitlock.starter.storage.MongoFileRepository
import com.mongodb.client.MongoClients
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() = runBlocking {
    val GREEN = "\u001B[32m"
    val RESET = "\u001B[0m"
    println("""$GREEN
         ██████╗ ██╗   ██╗██████╗ ██╗████████╗██╗      ██████╗  ██████╗██╗  ██╗
        ██╔═══██╗██║   ██║██╔══██╗██║╚══██╔══╝██║     ██╔═══██╗██╔════╝██║ ██╔╝
        ██║   ██║██║   ██║██████╔╝██║   ██║   ██║     ██║   ██║██║     █████╔╝ 
        ██║▄▄ ██║██║   ██║██╔══██╗██║   ██║   ██║     ██║   ██║██║     ██╔═██╗ 
        ╚██████╔╝╚██████╔╝██████╔╝██║   ██║   ███████╗╚██████╔╝╚██████╗██║  ██╗
         ╚══▀▀═╝  ╚═════╝ ╚═════╝ ╚═╝   ╚═╝   ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝

        Hello World! QubitLock SDK Demo
        ================================
        $RESET
    """.trimIndent())

    // Минимальная конфигурация
    val client = QubitLockClient(
        properties = QubitLockProperties(
            vault = QubitLockProperties.VaultConfig(
                url = "http://localhost:8200",
                token = "root-token"
            ),
            mongodb = QubitLockProperties.MongoDBConfig(
                connectionString = "mongodb://localhost:27017/qubitlock",
                database = "qubitlock"
            ),
            features = QubitLockProperties.Features()
        ),
        vaultService = VaultService(
            QubitLockProperties(
                vault = QubitLockProperties.VaultConfig(
                    url = "http://localhost:8200",
                    token = "root-token"
                ),
                mongodb = QubitLockProperties.MongoDBConfig("", ""),
                features = QubitLockProperties.Features()
            )
        ),
        fileRepository = MongoFileRepository(
            MongoClients.create("mongodb://localhost:27017/qubitlock")
                .getDatabase("qubitlock")
        )
    )

    // Простейший пример
    val myFile = File("C:\\Users\\16227\\Documents\\QubitLock\\test.txt")
    println("\n1. Зашифровываю и сохраняю через QubitLock... (команда client.encryptAndStore(...))")
    val metadata = client.encryptAndStore(
        fileData = myFile.readBytes(),
        fileName = myFile.name,
        options = EncryptOptions()
    )

    println("   ✅ Сохранено! ID: ${metadata.id}")

    println("\n2. Проверяю целостность... (команда client.verifyIntegrity(fileId) (это необязательный шаг, потому что при получении всё проверится автоматически))")
    val verified = client.verifyIntegrity(metadata.id)
    println("   ✅ Целостность: $verified")

    println("\n3.  Получаю обратно... (команда client.retrieveAndDecrypt(fileId))")
    val retrieved = String(client.retrieveAndDecrypt(metadata.id))
    println("   ✅ Получено: \"$retrieved\"")

    println("\n" + """
        ВСЁ РАБОТАЕТ!
        
        Что сделал QubitLock за меня:
        • Шифрование через Vault ✓
        • Сохранение в моей базе данных (можно было выбрать облако QubitLock) ✓ 
        • Проверка целостности ✓
        • Автовосстановление ✓
        
        Мой код: всего несколько строк!
        Очень удобно
    """.trimIndent())
}