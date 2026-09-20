package com.example.outlook_calendar_integration_android.ui.calendar

import com.example.outlook_calendar_integration_android.auth.AuthProvider
import com.example.outlook_calendar_integration_android.auth.AuthResult
import com.example.outlook_calendar_integration_android.auth.ReauthProvider
import com.example.outlook_calendar_integration_android.data.CalendarRepository
import com.example.outlook_calendar_integration_android.data.GraphApiService
import com.example.outlook_calendar_integration_android.data.model.GraphCalendarViewResponse
import com.example.outlook_calendar_integration_android.data.model.GraphDateTimeDto
import com.example.outlook_calendar_integration_android.data.model.GraphEventDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private class EmptyGraphApiService : GraphApiService {
    override suspend fun getCalendarView(
        startDateTime: String,
        endDateTime: String,
        orderBy: String,
        top: Int,
        preferTimeZone: String,
    ): GraphCalendarViewResponse = GraphCalendarViewResponse(value = emptyList())

    override suspend fun getCalendarViewPage(nextLink: String, preferTimeZone: String): GraphCalendarViewResponse =
        GraphCalendarViewResponse(value = emptyList())
}

/** Always returns a single UTC-timed event, so tests can assert on the ViewModel's zone handling. */
private class SingleEventGraphApiService(private val utcDateTime: String) : GraphApiService {
    override suspend fun getCalendarView(
        startDateTime: String,
        endDateTime: String,
        orderBy: String,
        top: Int,
        preferTimeZone: String,
    ): GraphCalendarViewResponse = GraphCalendarViewResponse(
        value = listOf(
            GraphEventDto(
                id = "event-1",
                subject = "Team sync",
                start = GraphDateTimeDto(dateTime = utcDateTime, timeZone = "UTC"),
                end = GraphDateTimeDto(dateTime = utcDateTime, timeZone = "UTC"),
            ),
        ),
    )

    override suspend fun getCalendarViewPage(nextLink: String, preferTimeZone: String): GraphCalendarViewResponse =
        GraphCalendarViewResponse(value = emptyList())
}

private object NoopReauthProvider : ReauthProvider {
    override suspend fun reauthenticate(): AuthResult = AuthResult.Error("not exercised")
}

private class FakeAuthProvider(private val signOutResult: Boolean = true) : AuthProvider {
    var signOutCallCount = 0
        private set

    override suspend fun signIn(): AuthResult = AuthResult.Error("not exercised")
    override suspend fun acquireTokenSilent(): AuthResult = AuthResult.Error("not exercised")
    override suspend fun signOut(): Boolean {
        signOutCallCount++
        return signOutResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private lateinit var collectorScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collectorScope = CoroutineScope(Dispatchers.Main)
    }

    @After
    fun tearDown() {
        collectorScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `signing out removes the account and signals sign-out`() = runTest {
        val authProvider = FakeAuthProvider(signOutResult = true)
        val repository = CalendarRepository(EmptyGraphApiService(), NoopReauthProvider)
        val viewModel = CalendarViewModel(repository, authProvider)

        var signedOut = false
        collectorScope.launch { viewModel.signedOut.collect { signedOut = true } }

        viewModel.onSignOutClicked()
        advanceUntilIdle()

        assertTrue(signedOut)
        assertEquals(1, authProvider.signOutCallCount)
    }

    @Test
    fun `failed sign-out does not navigate away and surfaces an error`() = runTest {
        val authProvider = FakeAuthProvider(signOutResult = false)
        val repository = CalendarRepository(EmptyGraphApiService(), NoopReauthProvider)
        val viewModel = CalendarViewModel(repository, authProvider)

        var signedOut = false
        collectorScope.launch { viewModel.signedOut.collect { signedOut = true } }

        viewModel.onSignOutClicked()
        advanceUntilIdle()

        assertEquals(false, signedOut)
        assertTrue(viewModel.uiState.value is CalendarUiState.Error)
    }

    @Test
    fun `empty calendar renders an empty agenda`() = runTest {
        val authProvider = FakeAuthProvider()
        val repository = CalendarRepository(EmptyGraphApiService(), NoopReauthProvider)
        val viewModel = CalendarViewModel(repository, authProvider)

        val state = viewModel.uiState.value
        assertTrue(state is CalendarUiState.Success)
        assertTrue((state as CalendarUiState.Success).eventsByDay.isEmpty())
    }

    @Test
    fun `events are converted from Graph's UTC zone to the device's local zone`() = runTest {
        val utcDateTime = "2026-09-18T23:30:00.0000000"
        val authProvider = FakeAuthProvider()
        val repository = CalendarRepository(SingleEventGraphApiService(utcDateTime), NoopReauthProvider)
        val viewModel = CalendarViewModel(repository, authProvider)

        val state = viewModel.uiState.value
        assertTrue(state is CalendarUiState.Success)
        val event = (state as CalendarUiState.Success).eventsByDay.values.single().single()

        val expectedInstant = Instant.parse("2026-09-18T23:30:00Z")
        assertEquals(ZoneId.systemDefault(), event.start.zone)
        assertEquals(expectedInstant, event.start.toInstant())
    }
}
