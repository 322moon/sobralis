package com.example

import com.example.common.error.ConflictException
import com.example.events.application.EventService
import com.example.events.application.CreateEvent
import com.example.events.domain.EventStatus
import com.example.events.domain.ParticipationStatus
import com.example.users.application.UserService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.security.crypto.password.PasswordEncoder
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ServerTest {
    @Autowired lateinit var http: TestRestTemplate
    @Autowired lateinit var users: UserService
    @Autowired lateinit var events: EventService
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    @Autowired lateinit var flyway: Flyway
    @Autowired lateinit var encoder: PasswordEncoder
    @Autowired lateinit var mapper: ObjectMapper

    private val password = "test-password-123"

    @Test
    fun `full HTTP scenario checks identity privacy seats withdrawal and cancellation`() {
        val ownerEmail = email()
        val guestEmail = email()
        for (address in listOf(ownerEmail, guestEmail)) {
            val registration = request(HttpMethod.POST, "/api/v1/users", mapOf("email" to address, "name" to "Алексей", "password" to password))
            assertEquals(201, registration.statusCode.value())
            assertFalse(registration.body!!.has("passwordHash"))
        }
        assertEquals(200, request(HttpMethod.GET, "/api/v1/users/me", email = ownerEmail).statusCode.value())
        val created = request(HttpMethod.POST, "/api/v1/events", createBody(), ownerEmail)
        assertEquals(201, created.statusCode.value())
        val id = created.body!!["id"].asText()
        assertEquals("Секретная квартира 42", created.body!!["privateAddress"].asText())
        assertTrue(request(HttpMethod.GET, "/api/v1/events/$id").body!!["privateAddress"].isNull)
        val feed = request(HttpMethod.GET, "/api/v1/events?city=москва&size=100")
        assertEquals(200, feed.statusCode.value())
        assertTrue(feed.body!!.any { it["id"].asText() == id })
        assertTrue(feed.body!!.all { it["privateAddress"].isNull })
        assertEquals(403, request(HttpMethod.POST, "/api/v1/events/$id/cancel", email = guestEmail).statusCode.value())
        assertEquals(409, request(HttpMethod.POST, "/api/v1/events/$id/applications", email = ownerEmail).statusCode.value())
        val applied = request(HttpMethod.POST, "/api/v1/events/$id/applications", email = guestEmail)
        assertEquals(201, applied.statusCode.value())
        val applicationId = applied.body!!["id"].asText()
        assertEquals(409, request(HttpMethod.POST, "/api/v1/events/$id/applications", email = guestEmail).statusCode.value())
        assertEquals(403, request(HttpMethod.GET, "/api/v1/events/$id/applications", email = guestEmail).statusCode.value())
        assertTrue(request(HttpMethod.GET, "/api/v1/events/$id", email = guestEmail).body!!["privateAddress"].isNull)
        val approved = request(HttpMethod.PATCH, "/api/v1/events/$id/applications/$applicationId", mapOf("status" to "APPROVED"), email = ownerEmail)
        assertEquals(200, approved.statusCode.value())
        assertEquals("APPROVED", approved.body!!["status"].asText())
        assertEquals("Секретная квартира 42", request(HttpMethod.GET, "/api/v1/events/$id", email = guestEmail).body!!["privateAddress"].asText())
        assertEquals("APPROVED", request(HttpMethod.GET, "/api/v1/events/$id/applications/me", email = guestEmail).body!!["status"].asText())
        val withdrawn = request(HttpMethod.POST, "/api/v1/events/$id/applications/me/withdraw", email = guestEmail)
        assertEquals("WITHDRAWN", withdrawn.body!!["status"].asText())
        assertTrue(request(HttpMethod.GET, "/api/v1/events/$id", email = guestEmail).body!!["privateAddress"].isNull)
        assertEquals(204, request(HttpMethod.POST, "/api/v1/events/$id/cancel", email = ownerEmail).statusCode.value())
        assertEquals("CANCELLED", request(HttpMethod.GET, "/api/v1/events/$id").body!!["status"].asText())
        assertEquals(409, request(HttpMethod.POST, "/api/v1/events/$id/applications", email = account().email).statusCode.value())
    }

    @Test
    fun `passwords are salted hashes and cannot be serialized`() {
        val first = account()
        val second = account()
        assertNotEquals(password, first.passwordHash)
        assertNotEquals(first.passwordHash, second.passwordHash)
        assertTrue(encoder.matches(password, first.passwordHash))
        assertFalse(encoder.matches("incorrect", first.passwordHash))
        val serialized = mapper.valueToTree<JsonNode>(first)
        assertFalse(serialized.has("password"))
        assertFalse(serialized.has("passwordHash"))
        val profile = request(HttpMethod.GET, "/api/v1/users/me", email = first.email)
        assertEquals(200, profile.statusCode.value())
        assertFalse(profile.body!!.has("password"))
        assertFalse(profile.body!!.has("passwordHash"))
    }

    @Test
    fun `feed works without a city and with empty or unknown city`() {
        val owner = account()
        val event = event(owner.id)
        val all = request(HttpMethod.GET, "/api/v1/events?size=100")
        assertEquals(200, all.statusCode.value())
        assertTrue(all.body!!.any { it["id"].asText() == event.id.toString() })
        assertTrue(all.body!!.all { it["privateAddress"].isNull })
        assertEquals(200, request(HttpMethod.GET, "/api/v1/events?city=").statusCode.value())
        val missing = request(HttpMethod.GET, "/api/v1/events?city=Unknown-${UUID.randomUUID()}")
        assertEquals(200, missing.statusCode.value())
        assertEquals(0, missing.body!!.size())
    }

    @Test
    fun `HTTP validation authentication and errors are structured`() {
        val owner = account()
        assertEquals(401, request(HttpMethod.GET, "/api/v1/users/me").statusCode.value())
        val wrongPassword = request(HttpMethod.GET, "/api/v1/users/me", email = owner.email, pass = "incorrect")
        assertEquals(401, wrongPassword.statusCode.value())
        assertEquals("UNAUTHORIZED", wrongPassword.body!!["code"].asText())
        assertEquals(400, request(HttpMethod.POST, "/api/v1/users", mapOf("email" to "bad", "name" to "A", "password" to "short")).statusCode.value())
        assertEquals(409, request(HttpMethod.POST, "/api/v1/users", mapOf("email" to owner.email.uppercase(), "name" to "Участник", "password" to password)).statusCode.value())
        assertEquals(400, request(HttpMethod.POST, "/api/v1/events", createBody() + ("capacity" to 0), owner.email).statusCode.value())
        assertEquals(400, request(HttpMethod.POST, "/api/v1/events", createBody() + ("startsAt" to "yesterday"), owner.email).statusCode.value())
        assertEquals(400, request(HttpMethod.POST, "/api/v1/events", createBody() + ("startsAt" to "2000-01-01T00:00:00Z"), owner.email).statusCode.value())
        assertEquals(400, request(HttpMethod.POST, "/api/v1/events", createBody() + ("organizerId" to owner.id), owner.email).statusCode.value())
        val malformed = request(HttpMethod.GET, "/api/v1/events/not-a-uuid")
        assertEquals(400, malformed.statusCode.value())
        assertEquals(400, malformed.body!!["status"].asInt())
        assertTrue(malformed.body!!["detail"].asText().isNotBlank())
        assertEquals(400, request(HttpMethod.GET, "/api/v1/events?page=-1").statusCode.value())
        val missing = request(HttpMethod.GET, "/api/v1/events/${UUID.randomUUID()}")
        assertEquals(404, missing.statusCode.value())
        assertEquals("NOT_FOUND", missing.body!!["code"].asText())
        assertFalse(missing.body!!.has("trace"))
    }

    @Test
    fun `application cannot be processed through another event and rejection is final`() {
        val owner = account()
        val guest = account()
        val first = event(owner.id)
        val second = event(owner.id)
        val application = events.apply(first.id, guest.id)
        assertEquals(404, request(HttpMethod.PATCH, "/api/v1/events/${second.id}/applications/${application.id}", mapOf("status" to "APPROVED"), email = owner.email).statusCode.value())
        assertEquals(400, request(HttpMethod.PATCH, "/api/v1/events/${first.id}/applications/${application.id}", mapOf("status" to "WITHDRAWN"), email = owner.email).statusCode.value())
        val rejected = request(HttpMethod.PATCH, "/api/v1/events/${first.id}/applications/${application.id}", mapOf("status" to "REJECTED"), email = owner.email)
        assertEquals("REJECTED", rejected.body!!["status"].asText())
        assertEquals(409, request(HttpMethod.PATCH, "/api/v1/events/${first.id}/applications/${application.id}", mapOf("status" to "APPROVED"), email = owner.email).statusCode.value())
    }

    @Test
    fun `concurrent approvals cannot take the same final seat and withdrawal frees it`() {
        val owner = account()
        val first = account()
        val second = account()
        val event = event(owner.id)
        val a = events.apply(event.id, first.id)
        val b = events.apply(event.id, second.id)
        val outcomes = race(
            { try { events.decide(event.id, a.id, owner.id, ParticipationStatus.APPROVED); "approved" } catch (_: ConflictException) { "full" } },
            { try { events.decide(event.id, b.id, owner.id, ParticipationStatus.APPROVED); "approved" } catch (_: ConflictException) { "full" } },
        )
        assertEquals(listOf("approved", "full"), outcomes.sorted())
        val applications = events.applications(event.id, owner.id)
        val approved = applications.single { it.status == ParticipationStatus.APPROVED }
        val pending = applications.single { it.status == ParticipationStatus.PENDING }
        events.withdraw(event.id, approved.userId)
        assertEquals(ParticipationStatus.APPROVED, events.decide(event.id, pending.id, owner.id, ParticipationStatus.APPROVED).status)
    }

    @Test
    fun `concurrent cancellation and approval end in cancelled event with hidden address`() {
        val owner = account()
        val guest = account()
        val event = event(owner.id)
        val application = events.apply(event.id, guest.id)
        race(
            { events.cancel(event.id, owner.id); "cancelled" },
            { try { events.decide(event.id, application.id, owner.id, ParticipationStatus.APPROVED); "approved" } catch (_: ConflictException) { "closed" } },
        )
        assertEquals(EventStatus.CANCELLED, events.get(event.id, guest.id).status)
        assertNull(events.get(event.id, guest.id).privateAddress)
        assertThrows(ConflictException::class.java) { events.decide(event.id, application.id, owner.id, ParticipationStatus.APPROVED) }
    }

    @Test
    fun `concurrent duplicate applications create one record`() {
        val owner = account()
        val guest = account()
        val event = event(owner.id)
        fun apply(): String = try { events.apply(event.id, guest.id); "created" } catch (_: ConflictException) { "duplicate" }
        assertEquals(listOf("created", "duplicate"), race({ apply() }, { apply() }).sorted())
        assertEquals(1, events.applications(event.id, owner.id).size)
    }

    @Test
    fun `transaction rollback leaves no partial event`() {
        val owner = account()
        var id: UUID? = null
        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(transactionManager).executeWithoutResult {
                id = event(owner.id).id
                throw IllegalStateException("Force rollback")
            }
        }
        assertThrows(com.example.common.error.NotFoundException::class.java) { events.get(id!!, owner.id) }
    }

    @Test
    fun `migration validates and repeat migration changes nothing`() {
        flyway.validate()
        assertEquals(0, flyway.migrate().migrationsExecuted)
    }

    private fun race(first: () -> String, second: () -> String): List<String> {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(first, second).map { action ->
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    action()
                })
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    class TestPostgres : PostgreSQLContainer<TestPostgres>("postgres:16.6-alpine")

    companion object {
        @Container
        @JvmField
        val postgres = TestPostgres().withDatabaseName("sobralis_test").withUsername("test").withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    private fun email() = "${UUID.randomUUID()}@example.com"

    private fun account() = users.register(email(), "Участник", password)

    private fun event(owner: UUID, capacity: Int = 1) = events.create(owner, CreateEvent("Настолки", "Играем вместе", "Москва",
        "Арбат", "Секретная квартира 42", Instant.now().plusSeconds(86400), capacity))

    private fun request(method: HttpMethod, path: String, body: Any? = null, email: String? = null, pass: String = password): ResponseEntity<JsonNode> {
        val client = if (email == null) http else http.withBasicAuth(email, pass)
        return client.exchange(path, method, HttpEntity(body, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }), JsonNode::class.java)
    }

    private fun createBody() = mapOf("title" to "Встреча", "description" to "Играем в настолки", "city" to "Москва",
        "publicLocation" to "Арбат", "privateAddress" to "Секретная квартира 42", "startsAt" to Instant.now().plusSeconds(86400).toString(), "capacity" to 1)

}
