package com.example.users.infrastructure.web

import com.example.config.UserPrincipal
import com.example.users.application.UserService
import com.example.users.domain.User
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    val email: String,
    val name: String,
    val password: String,
)

data class UserResponse(val id: UUID, val email: String, val name: String, val createdAt: Instant) {
    companion object {
        fun from(user: User) = UserResponse(user.id, user.email, user.name, user.createdAt)
    }
}

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val service: UserService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterRequest): UserResponse =
        UserResponse.from(service.register(request.email, request.name, request.password))

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): UserResponse =
        UserResponse.from(service.get(principal.id))
}
