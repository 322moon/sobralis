package com.example.users.application

import com.example.common.error.ConflictException
import com.example.common.error.NotFoundException
import com.example.common.error.ValidationException
import com.example.users.domain.User
import com.example.users.infrastructure.persistence.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Locale
import java.util.UUID

@Service
class UserService(
    private val users: UserRepository,
    private val passwords: PasswordEncoder,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun register(email: String, name: String, password: String): User {
        val normalizedEmail = normalizeAndValidateEmail(email)
        val normalizedName = normalizeAndValidateName(name)
        validatePassword(password)

        if (users.findByEmail(normalizedEmail) != null) {
            throw ConflictException("Этот email уже зарегистрирован")
        }

        val user = users.save(
            User(
                UUID.randomUUID(),
                normalizedEmail,
                normalizedName,
                passwords.encode(password),
                clock.instant()
            )
        )

        log.info("User registered: {}", user.id)
        return user
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): User = users.findById(id).orElseThrow { NotFoundException("Пользователь не найден") }

    companion object {
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }

    private fun normalizeAndValidateEmail(email: String): String {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)

        if (normalizedEmail.length > 254 || !EMAIL.matches(normalizedEmail)) {
            throw ValidationException("Некорректный email")
        }

        return normalizedEmail
    }

    private fun normalizeAndValidateName(name: String): String {
        val normalizedName = name.trim()

        if (normalizedName.length !in 2..80) {
            throw ValidationException("Имя должно содержать от 2 до 80 символов")
        }

        return normalizedName
    }

    private fun validatePassword(password: String) {
        if (password.length < 8 || password.toByteArray(Charsets.UTF_8).size > 72) {
            throw ValidationException("Пароль: минимум 8 символов и максимум 72 байта UTF-8")
        }
    }
}
