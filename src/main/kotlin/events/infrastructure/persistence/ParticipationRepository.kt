package com.example.events.infrastructure.persistence

import com.example.events.domain.Participation
import com.example.events.domain.ParticipationStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ParticipationRepository : JpaRepository<Participation, UUID> {
    fun findByEventIdAndUserId(eventId: UUID, userId: UUID): Participation?
    fun findAllByEventIdOrderByCreatedAtAscIdAsc(eventId: UUID): List<Participation>
    fun countByEventIdAndStatus(eventId: UUID, status: ParticipationStatus): Long
}
