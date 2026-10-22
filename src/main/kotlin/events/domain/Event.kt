package com.example.events.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class EventStatus { ACTIVE, CANCELLED }

@Entity
@Table(name = "events")
class Event(
    @Id val id: UUID,
    @Column(nullable = false) val organizerId: UUID,
    @Column(nullable = false, length = 120) val title: String,
    @Column(nullable = false, length = 5000) val description: String,
    @Column(nullable = false, length = 100) val city: String,
    @Column(nullable = false, length = 300) val publicLocation: String,
    @Column(length = 500) val privateAddress: String?,
    @Column(nullable = false) val startsAt: Instant,
    @Column(nullable = false) val capacity: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var status: EventStatus,
    @Column(nullable = false) val createdAt: Instant,
)
