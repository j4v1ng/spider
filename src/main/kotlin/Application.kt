package com.javing

import com.fasterxml.jackson.databind.SerializationFeature
import com.javing.controllers.configureControllers
import com.javing.services.SpiderService
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.thymeleaf.*
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Configure Thymeleaf
    install(Thymeleaf) {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            characterEncoding = "utf-8"
        })
    }

    // Configure Content Negotiation
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

            // Handle circular references
            findAndRegisterModules()
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

            // Custom serializer for LocalDateTime
            registerModule(com.fasterxml.jackson.databind.module.SimpleModule().apply {
                addSerializer(java.time.LocalDateTime::class.java, object : com.fasterxml.jackson.databind.JsonSerializer<java.time.LocalDateTime>() {
                    override fun serialize(value: java.time.LocalDateTime, gen: com.fasterxml.jackson.core.JsonGenerator, serializers: com.fasterxml.jackson.databind.SerializerProvider) {
                        gen.writeString(value.toString())
                    }
                })
            })
        }
    }

    // Create services
    val spiderService = SpiderService()

    // Configure controllers
    configureControllers(spiderService)
}
