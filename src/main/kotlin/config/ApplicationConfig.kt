package com.example.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ApplicationConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
