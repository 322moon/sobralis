package com.example.events.application

import com.example.common.error.*
import com.example.events.domain.*
import com.example.events.infrastructure.persistence.EventRepository
import com.example.events.infrastructure.persistence.ParticipationRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class EventService(
    private val events: EventRepository,
    private val applications: ParticipationRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(organizerId: UUID, request: CreateEvent): EventView {
        val title = request.title.trim()
        val description = request.description.trim()
        val city = request.city.trim()
        val location = request.publicLocation.trim()
        val address = request.privateAddress?.trim()?.takeIf { it.isNotEmpty() }

        requireValid(title.length in 3..120) { "Название: от 3 до 120 символов" }
        requireValid(description.length in 1..5000) { "Описание: от 1 до 5000 символов" }
        requireValid(city.length in 1..100)  { "Город: от 1 до 100 символов" }
        requireValid(location.length in 1..300) { "Публичное место: от 1 до 300 символов" }
        if (address != null && address.length > 500) throw ValidationException("Адрес: максимум 500 символов")
        requireValid(request.startsAt.isAfter(clock.instant())) { "Начало события должно быть в будущем" }
        requireValid(request.capacity in 1..100) { "Количество мест: от 1 до 100, без организатора" }

        val event = Event(UUID.randomUUID(), organizerId, title, description, city, location, address,
            request.startsAt, request.capacity, EventStatus.ACTIVE, clock.instant())

        events.save(event)
        log.info("Event created: {}, organizer: {}", event.id, organizerId)

        return EventView.from(event, showAddress = true)
    }

    @Transactional(readOnly = true)
    fun list(city: String?, page: Int, size: Int): List<EventView> {
        requireValid(page in 0..10000) { "page: от 0 до 10000" }
        requireValid(size in 1..100) { "size: от 1 до 100" }
        if (city != null && city.length > 100) throw ValidationException("Слишком длинное название города")

        val filter = city?.trim()?.takeIf { it.isNotEmpty() }
        val pageable = PageRequest.of(page, size)
        val rows = if (filter == null) events.findUpcoming(EventStatus.ACTIVE, clock.instant(), pageable)
            else events.findUpcomingInCity(EventStatus.ACTIVE, clock.instant(), filter, pageable)

        return rows.map { EventView.from(it) }
    }

    @Transactional(readOnly = true)
    fun get(id: UUID, viewerId: UUID?): EventView {
        val event = getEvent(id)
        val isOrganizer = viewerId == event.organizerId
        val isApproved = viewerId != null && event.status == EventStatus.ACTIVE &&
            applications.findByEventIdAndUserId(id, viewerId)?.status == ParticipationStatus.APPROVED

        return EventView.from(event, showAddress = isOrganizer || isApproved)
    }

    @Transactional
    fun cancel(id: UUID, actorId: UUID) {
        val event = lockEvent(id)

        requireOrganizer(event, actorId)
        requireOpen(event)

        event.status = EventStatus.CANCELLED
        log.info("Event cancelled: {}", id)
    }

    @Transactional
    fun apply(eventId: UUID, userId: UUID): Participation {
        val event = lockEvent(eventId)

        requireOpen(event)
        if (event.organizerId == userId) throw ConflictException("Организатор не подаёт заявку на своё событие")
        if (applications.findByEventIdAndUserId(eventId, userId) != null) throw ConflictException("Заявка уже существует")

        val application = Participation(UUID.randomUUID(), eventId, userId, ParticipationStatus.PENDING, clock.instant())
        applications.save(application)
        log.info("Application created: {}, event: {}", application.id, eventId)

        return application
    }

    @Transactional(readOnly = true)
    fun applications(eventId: UUID, actorId: UUID): List<Participation> {
        requireOrganizer(getEvent(eventId), actorId)
        return applications.findAllByEventIdOrderByCreatedAtAscIdAsc(eventId)
    }

    @Transactional(readOnly = true)
    fun myApplication(eventId: UUID, userId: UUID): Participation {
        getEvent(eventId)
        return applications.findByEventIdAndUserId(eventId, userId) ?: throw NotFoundException("Ваша заявка не найдена")
    }

    @Transactional
    fun decide(eventId: UUID, applicationId: UUID, actorId: UUID, status: ParticipationStatus): Participation {
        if (status != ParticipationStatus.APPROVED && status != ParticipationStatus.REJECTED) {
            throw ValidationException("Решение: только APPROVED или REJECTED")
        }
        val event = lockEvent(eventId)
        requireOrganizer(event, actorId)
        requireOpen(event)
        val application = applications.findById(applicationId).orElseThrow { NotFoundException("Заявка не найдена") }
        if (application.eventId != eventId) throw NotFoundException("Заявка не найдена в этом событии")
        if (application.status != ParticipationStatus.PENDING) throw ConflictException("Заявка уже обработана")
        if (status == ParticipationStatus.APPROVED && applications.countByEventIdAndStatus(eventId, status) >= event.capacity) {
            throw ConflictException("Свободных мест нет")
        }
        application.status = status
        log.info("Application {}: {}", status, applicationId)
        return application
    }

    @Transactional
    fun withdraw(eventId: UUID, userId: UUID): Participation {
        requireOpen(lockEvent(eventId))
        val application = applications.findByEventIdAndUserId(eventId, userId) ?: throw NotFoundException("Ваша заявка не найдена")
        if (application.status != ParticipationStatus.PENDING && application.status != ParticipationStatus.APPROVED) {
            throw ConflictException("Эту заявку уже нельзя отозвать")
        }
        application.status = ParticipationStatus.WITHDRAWN
        return application
    }

    private fun getEvent(id: UUID) = events.findById(id).orElseThrow { NotFoundException("Событие не найдено") }
    private fun lockEvent(id: UUID) = events.findForUpdate(id) ?: throw NotFoundException("Событие не найдено")

    private fun requireOrganizer(event: Event, actorId: UUID) =
        if (event.organizerId != actorId) throw ForbiddenException("Действие доступно только организатору") else Unit

    private fun requireOpen(event: Event) =
        when {
            event.status != EventStatus.ACTIVE -> throw ConflictException("Событие отменено")
            !event.startsAt.isAfter(clock.instant()) -> throw ConflictException("Событие уже началось")
            else -> Unit
        }

    private fun requireValid(condition: Boolean, message: () -> String){
        if(!condition){
            throw ValidationException(message())
        }
    }
}
