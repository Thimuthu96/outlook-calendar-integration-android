package com.example.outlook_calendar_integration_android.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outlook_calendar_integration_android.auth.AuthProvider
import com.example.outlook_calendar_integration_android.data.CalendarRepository
import com.example.outlook_calendar_integration_android.data.model.CalendarEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Success(val eventsByDay: Map<LocalDate, List<CalendarEvent>>) : CalendarUiState
    data class Error(val message: String) : CalendarUiState
}

class CalendarViewModel(
    private val calendarRepository: CalendarRepository,
    private val authManager: AuthProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _signedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signedOut: SharedFlow<Unit> = _signedOut.asSharedFlow()

    init {
        loadEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            _uiState.value = CalendarUiState.Loading
            fetchAndPublish()
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchAndPublish()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchAndPublish() {
        calendarRepository.fetchUpcomingEvents().fold(
            onSuccess = { events ->
                // Graph is queried in UTC (see CalendarRepository); convert to the device's own
                // zone here so both the displayed times and the day grouping match the user's
                // actual calendar day, not the UTC one.
                val grouped = events
                    .map { it.toLocalZone() }
                    .groupBy { it.start.toLocalDate() }
                    .toSortedMap()
                _uiState.value = CalendarUiState.Success(grouped)
            },
            onFailure = {
                _uiState.value = CalendarUiState.Error("Couldn't load your calendar. Please try again.")
            },
        )
    }

    private fun CalendarEvent.toLocalZone(): CalendarEvent {
        val zone = ZoneId.systemDefault()
        return copy(start = start.withZoneSameInstant(zone), end = end.withZoneSameInstant(zone))
    }

    fun onSignOutClicked() {
        viewModelScope.launch {
            if (authManager.signOut()) {
                _signedOut.emit(Unit)
            } else {
                _uiState.value = CalendarUiState.Error("Couldn't sign out. Please try again.")
            }
        }
    }
}
