plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

dependencies {
    implementation(project(":qubitlock-core"))

    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-netty:2.3.0")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")
    implementation("io.ktor:ktor-server-cors:2.3.0")
    implementation("io.ktor:ktor-server-call-logging:2.3.0")

    // MongoDB
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    // Тестирование
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")

    // Логи
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Конфигурация
    implementation("com.typesafe:config:1.4.2")
}

// Конфигурация публикации
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.qubitlock"
            artifactId = "qubitlock-starter-ktor"
            version = "1.0.0"

            from(components["java"])

            pom {
                name.set("QubitLock Starter Ktor")
                description.set("Ktor integration for QubitLock SDK")
                url.set("https://github.com/Serafim-Novichikhin/QubitLock")

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
                    // Подставьте сюда свои данные или я могу подставить:
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
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}