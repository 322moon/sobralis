package com.example.config

import com.example.common.error.problem
import org.springframework.http.HttpStatus
import com.example.users.infrastructure.persistence.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User as SecurityUser
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import java.util.Locale
import java.util.UUID

class UserPrincipal(val id: UUID, email: String, hash: String) : SecurityUser(email, hash, emptyList())

@Configuration
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(users: UserRepository) = UserDetailsService { email ->
        val user = users.findByEmail(email.trim().lowercase(Locale.ROOT))
            ?: throw UsernameNotFoundException("Неверные учётные данные")
        UserPrincipal(user.id, user.email, user.passwordHash)
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity, mapper: ObjectMapper): SecurityFilterChain {
        val unauthorized = org.springframework.security.web.AuthenticationEntryPoint { _, response, _ ->
            response.setHeader("WWW-Authenticate", "Basic realm=\"sobralis\"")
            writeError(response, mapper, HttpStatus.UNAUTHORIZED, "Требуется корректный email и пароль")
        }
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*").permitAll()
                it.requestMatchers("/error").permitAll()
                it.anyRequest().authenticated()
            }
            .httpBasic { it.authenticationEntryPoint(unauthorized) }
            .exceptionHandling {
                it.authenticationEntryPoint(unauthorized)
                it.accessDeniedHandler { _, response, _ ->
                    writeError(response, mapper, HttpStatus.FORBIDDEN, "Недостаточно прав")
                }
            }
        return http.build()
    }

    private fun writeError(response: HttpServletResponse, mapper: ObjectMapper, status: HttpStatus, message: String) {
        response.status = status.value()
        response.contentType = "application/problem+json"
        response.characterEncoding = "UTF-8"
        mapper.writeValue(response.writer, problem(status, message))
    }
}
