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
    val secretMessage = "Hello QubitLock! My secret: ${System.currentTimeMillis()}"


    println("\n2. 🔐 Зашифровываю через QubitLock...")
    val metadata = client.encryptAndStore(
        fileData = File("C:\\Users\\16227\\Documents\\QubitLock\\test.txt").readBytes(),
        fileName = "secret.txt",
        options = EncryptOptions()
    )

    println("   ✅ Сохранено! ID: ${metadata.id}")

    println("\n3. 🔍 Проверяю целостность...")
    val verified = client.verifyIntegrity(metadata.id)
    println("   ✅ Целостность: $verified")

    println("\n4. 📥 Получаю обратно...")
    val retrieved = String(client.retrieveAndDecrypt(metadata.id))
    println("   ✅ Получено: \"$retrieved\"")

    println("\n" + """
        🎯 ВСЁ РАБОТАЕТ!
        
        Что сделал QubitLock за меня:
        • Шифрование через Vault ✓
        • Сохранение в MongoDB ✓
        • Проверка целостности ✓
        • Автовосстановление ✓
        
        Мой код: всего 10 строк!
        Простота использования: 10/10 ✅
    """.trimIndent())
}