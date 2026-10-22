package com.example.events.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ParticipationStatus { PENDING, APPROVED, REJECTED, WITHDRAWN }

@Entity
@Table(name = "participations")
class Participation(
    @Id val id: UUID,
    @Column(nullable = false) val eventId: UUID,
    @Column(nullable = false) val userId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var status: ParticipationStatus,
    @Column(nullable = false) val createdAt: Instant,
)
