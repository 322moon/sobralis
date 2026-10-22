package com.example.events.infrastructure.web

import com.example.config.UserPrincipal
import com.example.events.application.CreateEvent
import com.example.events.application.EventService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/events")
class EventController(private val service: EventService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal actor: UserPrincipal, @RequestBody request: CreateEvent) =
        service.create(actor.id, request)

    @GetMapping
    fun list(@RequestParam(required = false) city: String?, @RequestParam(defaultValue = "0") page: Int,
             @RequestParam(defaultValue = "20") size: Int) = service.list(city, page, size)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID, @AuthenticationPrincipal actor: UserPrincipal?) = service.get(id, actor?.id)

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@PathVariable id: UUID, @AuthenticationPrincipal actor: UserPrincipal) = service.cancel(id, actor.id)
}
