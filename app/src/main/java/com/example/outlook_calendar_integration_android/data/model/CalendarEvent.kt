package com.example.outlook_calendar_integration_android.data.model

import java.time.ZonedDateTime

/** UI-facing calendar event, already decoupled from the Graph API's DTO shape. */
data class CalendarEvent(
    val id: String,
    val subject: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val location: String?,
    val isAllDay: Boolean,
)
