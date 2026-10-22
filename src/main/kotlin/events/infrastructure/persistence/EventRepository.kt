package com.example.events.infrastructure.persistence

import com.example.events.domain.Event
import com.example.events.domain.EventStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface EventRepository : JpaRepository<Event, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    fun findForUpdate(id: UUID): Event?

    @Query("select e from Event e where e.status = :status and e.startsAt > :now order by e.startsAt, e.id")
    fun findUpcoming(status: EventStatus, now: Instant, pageable: Pageable): List<Event>

    @Query("""
        select e from Event e
        where e.status = :status and e.startsAt > :now and lower(e.city) = lower(:city)
        order by e.startsAt, e.id
    """)
    fun findUpcomingInCity(status: EventStatus, now: Instant, city: String, pageable: Pageable): List<Event>
}
