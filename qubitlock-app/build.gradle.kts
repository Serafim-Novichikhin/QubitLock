plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.0"
    application
}

dependencies {
    implementation(project(":qubitlock-starter-ktor"))
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-netty:2.3.0")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")
    implementation("io.ktor:ktor-server-call-logging:2.3.0")

    // Для логов
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Для конфигурации (Пока что не работает)
    implementation("com.typesafe:config:1.4.2")
}

application {
    mainClass.set("com.qubitlock.app.ApplicationKt")
}

// Для jar-ников (чтобы быстро потом запускать)
tasks {
    val fatJar = register<Jar>("fatJar") {
        dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources"))
        archiveClassifier.set("standalone")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // На всякий случай
        manifest {
            attributes(mapOf("Main-Class" to application.mainClass))
        }
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) } +
                sourcesMain.output
        from(contents)
    }
    build {
        dependsOn(fatJar)
    }
}

// Ресурсы почему-то не загружаются автоматически, хотя должны
sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
            include("**/*.conf", "**/*.xml", "**/*.properties")
        }
    }
}

// На всякий случай
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from("src/main/resources") {
        include("**/*.conf", "**/*.xml", "**/*.properties")
    }
}