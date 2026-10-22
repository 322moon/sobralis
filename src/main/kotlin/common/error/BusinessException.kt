package com.example.common.error

sealed class BusinessException(message: String) : RuntimeException(message)
class ValidationException(message: String) : BusinessException(message)
class NotFoundException(message: String) : BusinessException(message)
class ForbiddenException(message: String) : BusinessException(message)
class ConflictException(message: String) : BusinessException(message)
