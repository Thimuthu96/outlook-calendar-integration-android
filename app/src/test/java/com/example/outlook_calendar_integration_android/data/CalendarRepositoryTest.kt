package com.example.outlook_calendar_integration_android.data

import com.example.outlook_calendar_integration_android.auth.AuthResult
import com.example.outlook_calendar_integration_android.auth.ReauthProvider
import com.example.outlook_calendar_integration_android.data.model.GraphCalendarViewResponse
import com.example.outlook_calendar_integration_android.data.model.GraphDateTimeDto
import com.example.outlook_calendar_integration_android.data.model.GraphEventDto
import com.example.outlook_calendar_integration_android.data.model.GraphLocationDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private fun httpException(code: Int): HttpException {
    val body = "{}".toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(code, body))
}

/** Records every call it receives and plays back a scripted response/throw per call, in order. */
private class ScriptedGraphApiService(
    private val responders: MutableList<() -> GraphCalendarViewResponse>,
) : GraphApiService {
    val capturedStartDateTimes = mutableListOf<String>()
    val capturedEndDateTimes = mutableListOf<String>()
    val capturedPageUrls = mutableListOf<String>()
    val callCount: Int get() = capturedStartDateTimes.size + capturedPageUrls.size

    override suspend fun getCalendarView(
        startDateTime: String,
        endDateTime: String,
        orderBy: String,
        top: Int,
        preferTimeZone: String,
    ): GraphCalendarViewResponse {
        capturedStartDateTimes += startDateTime
        capturedEndDateTimes += endDateTime
        return nextResponse()
    }

    override suspend fun getCalendarViewPage(nextLink: String, preferTimeZone: String): GraphCalendarViewResponse {
        capturedPageUrls += nextLink
        return nextResponse()
    }

    private fun nextResponse(): GraphCalendarViewResponse {
        check(responders.isNotEmpty()) { "ScriptedGraphApiService called more times than scripted" }
        return responders.removeAt(0).invoke()
    }
}

private class ScriptedReauthProvider(
    private val results: MutableList<AuthResult>,
) : ReauthProvider {
    var callCount = 0
        private set

    override suspend fun reauthenticate(): AuthResult {
        callCount++
        check(results.isNotEmpty()) { "ScriptedReauthProvider called more times than scripted" }
        return results.removeAt(0)
    }
}

class CalendarRepositoryTest {

    private val fixedNow = Instant.parse("2026-09-18T10:15:30Z")
    private val fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private fun eventDto(
        id: String? = "event-1",
        subject: String? = "Team sync",
        startDateTime: String? = "2026-09-18T11:00:00.0000000",
        endDateTime: String? = "2026-09-18T11:30:00.0000000",
        timeZone: String? = "UTC",
        location: String? = "Room 1",
        isAllDay: Boolean = false,
    ) = GraphEventDto(
        id = id,
        subject = subject,
        isAllDay = isAllDay,
        start = GraphDateTimeDto(dateTime = startDateTime, timeZone = timeZone),
        end = GraphDateTimeDto(dateTime = endDateTime, timeZone = timeZone),
        location = location?.let { GraphLocationDto(displayName = it) },
    )

    // -- DTO -> UI-model mapping -------------------------------------------------------------

    @Test
    fun `maps a well-formed event`() {
        val repository = CalendarRepository(
            graphApiService = ScriptedGraphApiService(mutableListOf()),
            authManager = ScriptedReauthProvider(mutableListOf()),
            clock = fixedClock,
        )

        val events = repository.mapToCalendarEvents(
            GraphCalendarViewResponse(value = listOf(eventDto())),
        )

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals("event-1", event.id)
        assertEquals("Team sync", event.subject)
        assertEquals("Room 1", event.location)
        assertFalse(event.isAllDay)
        assertEquals(Instant.parse("2026-09-18T11:00:00Z"), event.start.toInstant())
        assertEquals(Instant.parse("2026-09-18T11:30:00Z"), event.end.toInstant())
        assertEquals(ZoneId.of("UTC"), event.start.zone)
    }

    @Test
    fun `blank subject falls back to placeholder`() {
        val repository = CalendarRepository(
            graphApiService = ScriptedGraphApiService(mutableListOf()),
            authManager = ScriptedReauthProvider(mutableListOf()),
            clock = fixedClock,
        )

        val events = repository.mapToCalendarEvents(
            GraphCalendarViewResponse(value = listOf(eventDto(subject = "   "))),
        )

        assertEquals("(No subject)", events.single().subject)
    }

    @Test
    fun `event with malformed start date is dropped, not crashed`() {
        val repository = CalendarRepository(
            graphApiService = ScriptedGraphApiService(mutableListOf()),
            authManager = ScriptedReauthProvider(mutableListOf()),
            clock = fixedClock,
        )

        val events = repository.mapToCalendarEvents(
            GraphCalendarViewResponse(value = listOf(eventDto(startDateTime = "not-a-date"))),
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `event with malformed end date is dropped, not crashed`() {
        val repository = CalendarRepository(
            graphApiService = ScriptedGraphApiService(mutableListOf()),
            authManager = ScriptedReauthProvider(mutableListOf()),
            clock = fixedClock,
        )

        val events = repository.mapToCalendarEvents(
            GraphCalendarViewResponse(value = listOf(eventDto(endDateTime = ""))),
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `empty calendarView value maps to empty list`() {
        val repository = CalendarRepository(
            graphApiService = ScriptedGraphApiService(mutableListOf()),
            authManager = ScriptedReauthProvider(mutableListOf()),
            clock = fixedClock,
        )

        val events = repository.mapToCalendarEvents(GraphCalendarViewResponse(value = emptyList()))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `mixed batch keeps valid events and drops malformed ones`() {
        val repository = CalendarRepository(
            graphApiService = ScriptedGraphApiService(mutableListOf()),
            authManager = ScriptedReauthProvider(mutableListOf()),
            clock = fixedClock,
        )

        val events = repository.mapToCalendarEvents(
            GraphCalendarViewResponse(
                value = listOf(
                    eventDto(id = "good-1"),
                    eventDto(id = "bad-1", startDateTime = "garbage"),
                    eventDto(id = "good-2"),
                    eventDto(id = "bad-2", endDateTime = "garbage"),
                ),
            ),
        )

        assertEquals(listOf("good-1", "good-2"), events.map { it.id })
    }

    // -- date window --------------------------------------------------------------------------

    @Test
    fun `fetch requests a 30-day window starting now`() = runTest {
        val graphApiService = ScriptedGraphApiService(mutableListOf({ GraphCalendarViewResponse() }))
        val repository = CalendarRepository(
            graphApiService = graphApiService,
            authManager = ScriptedReauthProvider(mutableListOf()),
            clock = fixedClock,
        )

        val result = repository.fetchUpcomingEvents()

        assertTrue(result.isSuccess)
        assertEquals(listOf("2026-09-18T10:15:30Z"), graphApiService.capturedStartDateTimes)
        assertEquals(listOf("2026-10-18T10:15:30Z"), graphApiService.capturedEndDateTimes)
    }

    // -- pagination ----------------------------------------------------------------------------

    @Test
    fun `follows nextLink and combines events across pages`() = runTest {
        val page1 = GraphCalendarViewResponse(
            value = listOf(eventDto(id = "page1-event")),
            nextLink = "https://graph.microsoft.com/v1.0/me/calendarView?\$skip=100",
        )
        val page2 = GraphCalendarViewResponse(value = listOf(eventDto(id = "page2-event")))
        val graphApiService = ScriptedGraphApiService(mutableListOf({ page1 }, { page2 }))
        val repository = CalendarRepository(graphApiService, ScriptedReauthProvider(mutableListOf()), fixedClock)

        val result = repository.fetchUpcomingEvents()

        assertTrue(result.isSuccess)
        assertEquals(listOf("page1-event", "page2-event"), result.getOrThrow().map { it.id })
        assertEquals(listOf("https://graph.microsoft.com/v1.0/me/calendarView?\$skip=100"), graphApiService.capturedPageUrls)
    }

    @Test
    fun `a page with no nextLink stops paging after a single call`() = runTest {
        val graphApiService = ScriptedGraphApiService(mutableListOf({ GraphCalendarViewResponse(value = listOf(eventDto())) }))
        val repository = CalendarRepository(graphApiService, ScriptedReauthProvider(mutableListOf()), fixedClock)

        val result = repository.fetchUpcomingEvents()

        assertTrue(result.isSuccess)
        assertEquals(1, graphApiService.callCount)
        assertTrue(graphApiService.capturedPageUrls.isEmpty())
    }

    // -- malformed response body -----------------------------------------------------------------

    @Test
    fun `undecodable response body surfaces as a failure instead of crashing`() = runTest {
        val graphApiService = ScriptedGraphApiService(mutableListOf({ throw SerializationException("unexpected token") }))
        val reauthProvider = ScriptedReauthProvider(mutableListOf())
        val repository = CalendarRepository(graphApiService, reauthProvider, fixedClock)

        val result = repository.fetchUpcomingEvents()

        assertTrue(result.isFailure)
        assertEquals(0, reauthProvider.callCount)
    }

    // -- 401 -> reauth retry --------------------------------------------------------------------

    @Test
    fun `401 followed by successful reauth retries once and succeeds`() = runTest {
        val response = GraphCalendarViewResponse(value = listOf(eventDto()))
        val graphApiService = ScriptedGraphApiService(
            mutableListOf(
                { throw httpException(401) },
                { response },
            ),
        )
        val reauthProvider = ScriptedReauthProvider(mutableListOf(AuthResult.Success("new-token")))
        val repository = CalendarRepository(graphApiService, reauthProvider, fixedClock)

        val result = repository.fetchUpcomingEvents()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(2, graphApiService.callCount)
        assertEquals(1, reauthProvider.callCount)
    }

    @Test
    fun `401 followed by failed reauth surfaces a failure without a second Graph call`() = runTest {
        val graphApiService = ScriptedGraphApiService(mutableListOf({ throw httpException(401) }))
        val reauthProvider = ScriptedReauthProvider(
            mutableListOf(AuthResult.Error("re-auth failed")),
        )
        val repository = CalendarRepository(graphApiService, reauthProvider, fixedClock)

        val result = repository.fetchUpcomingEvents()

        assertTrue(result.isFailure)
        assertEquals(1, graphApiService.callCount)
        assertEquals(1, reauthProvider.callCount)
    }

    @Test
    fun `a second consecutive 401 after a successful reauth is not retried again`() = runTest {
        val graphApiService = ScriptedGraphApiService(
            mutableListOf(
                { throw httpException(401) },
                { throw httpException(401) },
            ),
        )
        val reauthProvider = ScriptedReauthProvider(mutableListOf(AuthResult.Success("new-token")))
        val repository = CalendarRepository(graphApiService, reauthProvider, fixedClock)

        val result = repository.fetchUpcomingEvents()

        assertTrue(result.isFailure)
        // Graph was called twice (original + one retry), but reauth only once -- the retry's own
        // 401 is not retried again.
        assertEquals(2, graphApiService.callCount)
        assertEquals(1, reauthProvider.callCount)
    }

    @Test
    fun `non-401 http error surfaces as failure without triggering reauth`() = runTest {
        val graphApiService = ScriptedGraphApiService(mutableListOf({ throw httpException(500) }))
        val reauthProvider = ScriptedReauthProvider(mutableListOf())
        val repository = CalendarRepository(graphApiService, reauthProvider, fixedClock)

        val result = repository.fetchUpcomingEvents()

        assertTrue(result.isFailure)
        assertEquals(0, reauthProvider.callCount)
    }

    @Test
    fun `network error surfaces as failure without triggering reauth`() = runTest {
        val graphApiService = ScriptedGraphApiService(mutableListOf({ throw IOException("timeout") }))
        val reauthProvider = ScriptedReauthProvider(mutableListOf())
        val repository = CalendarRepository(graphApiService, reauthProvider, fixedClock)

        val result = repository.fetchUpcomingEvents()

        assertTrue(result.isFailure)
        assertEquals(0, reauthProvider.callCount)
    }
}
