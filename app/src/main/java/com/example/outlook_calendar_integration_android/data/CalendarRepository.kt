package com.example.outlook_calendar_integration_android.data

import com.example.outlook_calendar_integration_android.auth.AuthResult
import com.example.outlook_calendar_integration_android.auth.ReauthProvider
import com.example.outlook_calendar_integration_android.data.model.CalendarEvent
import com.example.outlook_calendar_integration_android.data.model.GraphCalendarViewResponse
import com.example.outlook_calendar_integration_android.data.model.GraphDateTimeDto
import com.example.outlook_calendar_integration_android.data.model.GraphEventDto
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** How far ahead the agenda looks, per the spec's "next 30 days" requirement. */
private const val AGENDA_WINDOW_DAYS = 30L

/** Safety cap on `nextLink` follows, so a misbehaving/cyclic paging chain can't loop forever. */
private const val MAX_PAGES = 20

/**
 * Fetches the signed-in user's Outlook calendar for the next [AGENDA_WINDOW_DAYS] days and maps
 * Graph's DTOs into UI-ready [CalendarEvent]s. Owns the single-retry-on-401 policy: this covers
 * both the "silent re-auth on relaunch" case (an unauthenticated request 401s if the interceptor
 * had no token) and the "Graph call itself 401s" case (an access token that expired mid-session).
 */
class CalendarRepository(
    private val graphApiService: GraphApiService,
    private val authManager: ReauthProvider,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun fetchUpcomingEvents(allowReauthRetry: Boolean = true): Result<List<CalendarEvent>> {
        return try {
            val now = Instant.now(clock)
            var response = graphApiService.getCalendarView(
                startDateTime = formatInstant(now),
                endDateTime = formatInstant(now.plus(AGENDA_WINDOW_DAYS, java.time.temporal.ChronoUnit.DAYS)),
            )
            val events = response.value.toMutableList()
            var page = 1
            // Graph pages the calendarView when the window has more events than $top; follow
            // nextLink so a busy 30-day window doesn't silently lose events past the first page.
            var nextLink = response.nextLink
            while (nextLink != null && page < MAX_PAGES) {
                response = graphApiService.getCalendarViewPage(nextLink)
                events += response.value
                nextLink = response.nextLink
                page++
            }
            Result.success(mapToCalendarEvents(GraphCalendarViewResponse(value = events)))
        } catch (e: HttpException) {
            if (e.code() == 401 && allowReauthRetry) {
                retryAfterReauth()
            } else {
                Result.failure(e)
            }
        } catch (e: java.io.IOException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            // A response Graph sent back couldn't be decoded (proxy error page, truncated body,
            // etc.) -- surface it as a normal failure so the UI shows Retry instead of crashing.
            Result.failure(e)
        }
    }

    private suspend fun retryAfterReauth(): Result<List<CalendarEvent>> {
        return when (authManager.reauthenticate()) {
            is AuthResult.Success -> fetchUpcomingEvents(allowReauthRetry = false)
            else -> Result.failure(IllegalStateException("Re-authentication failed"))
        }
    }

    private fun formatInstant(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    internal fun mapToCalendarEvents(response: GraphCalendarViewResponse): List<CalendarEvent> =
        response.value.mapNotNull { toCalendarEventOrNull(it) }

    internal fun toCalendarEventOrNull(dto: GraphEventDto): CalendarEvent? {
        val id = dto.id ?: return null
        val startDto = dto.start ?: return null
        val endDto = dto.end ?: return null
        val start = parseDateTime(startDto) ?: return null
        val end = parseDateTime(endDto) ?: return null
        val subject = dto.subject?.takeIf { it.isNotBlank() } ?: "(No subject)"
        return CalendarEvent(
            id = id,
            subject = subject,
            start = start,
            end = end,
            location = dto.location?.displayName?.takeIf { it.isNotBlank() },
            isAllDay = dto.isAllDay,
        )
    }

    private fun parseDateTime(dto: GraphDateTimeDto): java.time.ZonedDateTime? {
        val dateTimeString = dto.dateTime?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val localDateTime = LocalDateTime.parse(dateTimeString)
            val zoneId = try {
                ZoneId.of(dto.timeZone ?: "UTC")
            } catch (e: Exception) {
                ZoneOffset.UTC
            }
            localDateTime.atZone(zoneId)
        } catch (e: DateTimeParseException) {
            // Malformed date from Graph: drop this event rather than crash the agenda.
            null
        }
    }
}
