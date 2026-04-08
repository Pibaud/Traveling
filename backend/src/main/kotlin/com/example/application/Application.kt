package com.example.application

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()
    configureHTTP()
    configureMonitoring()
    configureFrameworks()
    configureSerialization()
    configureSecurity()
    configureRouting()
}
