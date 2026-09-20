package com.example.outlook_calendar_integration_android.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.outlook_calendar_integration_android.data.model.CalendarEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Agenda") },
                actions = {
                    IconButton(onClick = onSignOutClick) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState) {
                is CalendarUiState.Loading -> LoadingContent()
                is CalendarUiState.Error -> ErrorContent(message = uiState.message, onRetry = onRetry)
                is CalendarUiState.Success -> {
                    if (uiState.eventsByDay.isEmpty()) {
                        EmptyContent()
                    } else {
                        AgendaList(eventsByDay = uiState.eventsByDay)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun EmptyContent() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "No events in the next 30 days.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun AgendaList(eventsByDay: Map<LocalDate, List<CalendarEvent>>) {
    val dayFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        eventsByDay.forEach { (day, events) ->
            item(key = "header_$day") {
                Text(
                    text = day.format(dayFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(events, key = { it.id }) { event ->
                EventRow(event = event, timeFormatter = timeFormatter)
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, timeFormatter: DateTimeFormatter) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = event.subject, style = MaterialTheme.typography.bodyLarge)
            val timeText = if (event.isAllDay) {
                "All day"
            } else {
                "${event.start.format(timeFormatter)} - ${event.end.format(timeFormatter)}"
            }
            Text(text = timeText, style = MaterialTheme.typography.bodySmall)
            event.location?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
