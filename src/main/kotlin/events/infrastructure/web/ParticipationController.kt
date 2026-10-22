package com.example.events.infrastructure.web

import com.example.config.UserPrincipal
import com.example.events.application.EventService
import com.example.events.domain.ParticipationStatus
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class DecisionRequest(val status: ParticipationStatus)

@RestController
@RequestMapping("/api/v1/events/{eventId}/applications")
class ParticipationController(private val service: EventService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun apply(@PathVariable eventId: UUID, @AuthenticationPrincipal actor: UserPrincipal) = service.apply(eventId, actor.id)

    @GetMapping
    fun list(@PathVariable eventId: UUID, @AuthenticationPrincipal actor: UserPrincipal) = service.applications(eventId, actor.id)

    @GetMapping("/me")
    fun mine(@PathVariable eventId: UUID, @AuthenticationPrincipal actor: UserPrincipal) = service.myApplication(eventId, actor.id)

    @PatchMapping("/{id}")
    fun decide(@PathVariable eventId: UUID, @PathVariable id: UUID, @AuthenticationPrincipal actor: UserPrincipal,
               @RequestBody request: DecisionRequest) = service.decide(eventId, id, actor.id, request.status)

    @PostMapping("/me/withdraw")
    fun withdraw(@PathVariable eventId: UUID, @AuthenticationPrincipal actor: UserPrincipal) = service.withdraw(eventId, actor.id)
}
