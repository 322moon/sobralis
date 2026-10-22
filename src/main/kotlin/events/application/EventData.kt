package com.example.events.application

import com.example.events.domain.Event
import com.example.events.domain.EventStatus
import java.time.Instant
import java.util.UUID

data class CreateEvent(
    val title: String,
    val description: String,
    val city: String,
    val publicLocation: String,
    val privateAddress: String? = null,
    val startsAt: Instant,
    val capacity: Int,
)

data class EventView(
    val id: UUID,
    val organizerId: UUID,
    val title: String,
    val description: String,
    val city: String,
    val publicLocation: String,
    val privateAddress: String?,
    val startsAt: Instant,
    val capacity: Int,
    val status: EventStatus,
    val createdAt: Instant,
) {
    companion object {
        fun from(event: Event, showAddress: Boolean = false) = EventView(
            event.id, event.organizerId, event.title, event.description, event.city,
            event.publicLocation, if (showAddress) event.privateAddress else null,
            event.startsAt, event.capacity, event.status, event.createdAt,
        )
    }
}
