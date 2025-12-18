package com.qubitlock.app

import com.qubitlock.starter.QubitLockAutoConfiguration
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    println("Запуск QubitLock Сервера...")

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        QubitLockAutoConfiguration.configure(this)
    }.start(wait = true)
}