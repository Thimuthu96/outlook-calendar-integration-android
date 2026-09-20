package com.example.outlook_calendar_integration_android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw Microsoft Graph `/me/calendarView` response shapes. These mirror only the fields this app
 * needs; unknown fields are ignored by kotlinx.serialization by default configuration below.
 */
@Serializable
data class GraphCalendarViewResponse(
    val value: List<GraphEventDto> = emptyList(),
    /** Present when the 30-day window has more events than fit in one `$top`-sized page. */
    @SerialName("@odata.nextLink") val nextLink: String? = null,
)

@Serializable
data class GraphEventDto(
    val id: String? = null,
    val subject: String? = null,
    val isAllDay: Boolean = false,
    val start: GraphDateTimeDto? = null,
    val end: GraphDateTimeDto? = null,
    val location: GraphLocationDto? = null,
)

@Serializable
data class GraphDateTimeDto(
    val dateTime: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class GraphLocationDto(
    val displayName: String? = null,
)
