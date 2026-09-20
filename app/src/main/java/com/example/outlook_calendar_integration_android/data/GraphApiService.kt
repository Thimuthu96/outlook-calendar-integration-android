package com.example.outlook_calendar_integration_android.data

import com.example.outlook_calendar_integration_android.data.model.GraphCalendarViewResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

/** Retrofit interface for the read-only slice of Microsoft Graph this app uses. */
interface GraphApiService {

    /**
     * `GET /me/calendarView` -- unlike `/me/events`, this expands recurring events into concrete
     * occurrences within [startDateTime, endDateTime), which is what a correct agenda needs.
     */
    @GET("me/calendarView")
    suspend fun getCalendarView(
        @Query("startDateTime") startDateTime: String,
        @Query("endDateTime") endDateTime: String,
        @Query("\$orderby") orderBy: String = "start/dateTime",
        @Query("\$top") top: Int = 100,
        @Header("Prefer") preferTimeZone: String = "outlook.timezone=\"UTC\"",
    ): GraphCalendarViewResponse

    /**
     * Follows a `GraphCalendarViewResponse.nextLink` (an absolute URL Graph hands back when the
     * window has more events than one page) -- `@Url` makes Retrofit use it as-is instead of
     * resolving against [BASE_URL].
     */
    @GET
    suspend fun getCalendarViewPage(
        @Url nextLink: String,
        @Header("Prefer") preferTimeZone: String = "outlook.timezone=\"UTC\"",
    ): GraphCalendarViewResponse

    companion object {
        const val BASE_URL = "https://graph.microsoft.com/v1.0/"
    }
}
