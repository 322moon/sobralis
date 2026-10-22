package com.example.events

import com.example.common.error.*
import com.example.events.application.CreateEvent
import com.example.events.application.EventService
import com.example.events.domain.*
import com.example.events.infrastructure.persistence.EventRepository
import com.example.events.infrastructure.persistence.ParticipationRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class EventServiceTest {
    private val now = Instant.parse("2030-01-01T12:00:00Z")
    private val events = mock(EventRepository::class.java)
    private val applications = mock(ParticipationRepository::class.java)
    private val service = EventService(events, applications, Clock.fixed(now, ZoneOffset.UTC))
    private val owner = UUID.randomUUID()
    private val guest = UUID.randomUUID()
    private val event = Event(UUID.randomUUID(), owner, "Настолки", "Играем", "Москва", "Арбат",
        "Квартира 42", now.plusSeconds(3600), 1, EventStatus.ACTIVE, now)
    private val request = CreateEvent("Настолки", "Играем", "Москва", "Арбат", null, event.startsAt, 1)

    private fun availableEvent() {
        `when`(events.findForUpdate(event.id)).thenReturn(event)
        `when`(events.findById(event.id)).thenReturn(Optional.of(event))
    }

    private fun application(userId: UUID = guest): Participation {
        val value = Participation(UUID.randomUUID(), event.id, userId, ParticipationStatus.PENDING, now)
        `when`(applications.findById(value.id)).thenReturn(Optional.of(value))
        `when`(applications.findByEventIdAndUserId(event.id, userId)).thenReturn(value)
        return value
    }

    @Test
    fun `past start and invalid capacity are rejected`() {
        assertThrows(ValidationException::class.java) { service.create(owner, request.copy(startsAt = now)) }
        assertThrows(ValidationException::class.java) { service.create(owner, request.copy(capacity = 0)) }
        assertThrows(ValidationException::class.java) { service.create(owner, request.copy(capacity = 101)) }
    }

    @Test
    fun `blank fields cannot bypass validation with spaces`() {
        assertThrows(ValidationException::class.java) { service.create(owner, request.copy(title = "   ")) }
        assertThrows(ValidationException::class.java) { service.create(owner, request.copy(description = "   ")) }
        assertThrows(ValidationException::class.java) { service.create(owner, request.copy(city = "   ")) }
    }

    @Test
    fun `owner and duplicate applications are rejected`() {
        availableEvent()
        assertThrows(ConflictException::class.java) { service.apply(event.id, owner) }
        application()
        assertThrows(ConflictException::class.java) { service.apply(event.id, guest) }
    }

    @Test
    fun `only owner can decide and only pending application can change`() {
        availableEvent()
        val application = application()
        assertThrows(ForbiddenException::class.java) { service.decide(event.id, application.id, guest, ParticipationStatus.APPROVED) }
        assertThrows(ForbiddenException::class.java) { service.cancel(event.id, guest) }
        service.decide(event.id, application.id, owner, ParticipationStatus.REJECTED)
        assertThrows(ConflictException::class.java) { service.decide(event.id, application.id, owner, ParticipationStatus.APPROVED) }
        assertThrows(ConflictException::class.java) { service.withdraw(event.id, guest) }
    }

    @Test
    fun `approval fills a seat and withdrawal hides the address`() {
        availableEvent()
        val first = application()
        val second = application(UUID.randomUUID())
        assertNull(service.get(event.id, guest).privateAddress)
        service.decide(event.id, first.id, owner, ParticipationStatus.APPROVED)
        assertEquals("Квартира 42", service.get(event.id, guest).privateAddress)
        `when`(applications.countByEventIdAndStatus(event.id, ParticipationStatus.APPROVED)).thenReturn(1)
        assertThrows(ConflictException::class.java) { service.decide(event.id, second.id, owner, ParticipationStatus.APPROVED) }
        service.withdraw(event.id, guest)
        assertEquals(ParticipationStatus.WITHDRAWN, first.status)
        assertNull(service.get(event.id, guest).privateAddress)
    }

    @Test
    fun `cancelled event rejects mutations and hides address from participants`() {
        availableEvent()
        val application = application()
        service.decide(event.id, application.id, owner, ParticipationStatus.APPROVED)
        service.cancel(event.id, owner)
        assertNull(service.get(event.id, guest).privateAddress)
        assertEquals("Квартира 42", service.get(event.id, owner).privateAddress)
        assertThrows(ConflictException::class.java) { service.apply(event.id, UUID.randomUUID()) }
        assertThrows(ConflictException::class.java) { service.withdraw(event.id, guest) }
        assertThrows(ConflictException::class.java) { service.cancel(event.id, owner) }
    }

    @Test
    fun `started event and application from another event are rejected`() {
        availableEvent()
        val foreign = Participation(UUID.randomUUID(), UUID.randomUUID(), guest, ParticipationStatus.PENDING, now)
        `when`(applications.findById(foreign.id)).thenReturn(Optional.of(foreign))
        assertThrows(NotFoundException::class.java) { service.decide(event.id, foreign.id, owner, ParticipationStatus.APPROVED) }
        val started = Event(event.id, owner, event.title, event.description, event.city, event.publicLocation,
            event.privateAddress, now.minusSeconds(1), 1, EventStatus.ACTIVE, now)
        `when`(events.findForUpdate(event.id)).thenReturn(started)
        assertThrows(ConflictException::class.java) { service.apply(event.id, guest) }
    }

    @Test
    fun `invalid pagination is rejected`() {
        assertThrows(ValidationException::class.java) { service.list(null, -1, 20) }
        assertThrows(ValidationException::class.java) { service.list(null, 0, 101) }
    }
}
