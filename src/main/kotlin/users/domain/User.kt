package com.example.users.domain

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id val id: UUID,
    @Column(nullable = false, unique = true, length = 254) val email: String,
    @Column(nullable = false, length = 80) val name: String,
    @get:JsonIgnore
    @Column(nullable = false, length = 100) val passwordHash: String,
    @Column(nullable = false) val createdAt: Instant,
)
