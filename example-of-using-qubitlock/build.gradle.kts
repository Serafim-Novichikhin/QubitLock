plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"

}

group = "example.of.using"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":qubitlock-core"))
    implementation(project(":qubitlock-starter-ktor"))

    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-netty:2.3.0")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")
    implementation("io.ktor:ktor-server-call-logging:2.3.0")

    // MongoDB
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")

    // Корутины
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Логирование
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Конфигурация
    implementation("com.typesafe:config:1.4.2")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8"
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

