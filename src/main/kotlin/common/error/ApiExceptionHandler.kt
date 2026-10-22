package com.example.common.error

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

fun problem(status: HttpStatus, message: String): ProblemDetail =
    ProblemDetail.forStatusAndDetail(status, message).apply { setProperty("code", status.name) }

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun business(error: BusinessException): ProblemDetail {
        val status = when (error) {
            is ValidationException -> HttpStatus.BAD_REQUEST
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ForbiddenException -> HttpStatus.FORBIDDEN
            is ConflictException -> HttpStatus.CONFLICT
        }
        return problem(status, error.message ?: status.reasonPhrase)
    }

    @ExceptionHandler(DataIntegrityViolationException::class, PessimisticLockingFailureException::class)
    fun conflict() = problem(HttpStatus.CONFLICT, "Конфликт данных. Проверьте состояние и повторите запрос")

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ProblemDetail {
        log.error("Unexpected request failure", error)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера")
    }
}
