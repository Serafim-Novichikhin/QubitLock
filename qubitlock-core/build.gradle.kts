plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Сериализация @Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    implementation("io.ktor:ktor-client-core:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")

    // Vault (Сервис для хранения ключей)
    implementation("io.github.jopenlibs:vault-java-driver:5.2.0")
    // Сжатие
    implementation("org.apache.commons:commons-compress:1.24.0")
    implementation("com.github.luben:zstd-jni:1.5.5-4")
    // Хеширование
    implementation("org.bouncycastle:bcprov-jdk18on:1.76")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    // Тестирование
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")

    // Логи
    implementation("org.slf4j:slf4j-api:2.0.9")
    // Конфигурация
    implementation("com.typesafe:config:1.4.2")
}
// Конфигурация публикации
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.qubitlock"
            artifactId = "qubitlock-core"
            version = "1.0.0"

            from(components["java"])

            // Информация о лицензии
            pom {
                name.set("QubitLock Core")
                description.set("Secure data storage SDK with encryption, Merkle trees, and compression")
                url.set("https://github.com/Serafim-Novichikhin/qubitlock")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("Serafim-Novichikhin")
                        name.set("Serafim")
                        email.set("ne_hochy.ykazivat@example.com")
                    }
                    // Подставьте сюда свои данные:
                    developer {
                        id.set("yourusername")
                        name.set("Your Name")
                        email.set("your.email@example.com")
                    }
                    developer {
                        id.set("yourusername")
                        name.set("Your Name")
                        email.set("your.email@example.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Serafim-Novichikhin/qubitlock.git")
                    developerConnection.set("scm:git:ssh://github.com/Serafim-Novichikhin/qubitlock.git")
                    url.set("https://github.com/Serafim-Novichikhin/qubitlock")
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}