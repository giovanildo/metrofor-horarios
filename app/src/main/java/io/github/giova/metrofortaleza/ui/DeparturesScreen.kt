package io.github.giova.metrofortaleza.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.Departure
import io.github.giova.metrofortaleza.data.Direction
import io.github.giova.metrofortaleza.data.Route
import io.github.giova.metrofortaleza.data.Stop
import io.github.giova.metrofortaleza.data.formatTime
import io.github.giova.metrofortaleza.data.minutesUntil
import io.github.giova.metrofortaleza.data.nextDepartures
import io.github.giova.metrofortaleza.data.nowMinutes
import kotlinx.coroutines.delay

private const val NEXT_COUNT = 4
private const val TICK_MILLIS = 15_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeparturesScreen(
    route: Route,
    stop: Stop,
    directions: List<Direction>,
    departuresFor: (Direction) -> List<Int>,
    showSameScheduleWarning: Boolean,
    bike: NearbyBike?,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableIntStateOf(nowMinutes()) }

    // O relógio do app precisa andar sozinho para a contagem regressiva não travar.
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            now = nowMinutes()
        }
    }

    val direction = directions.getOrNull(selectedTab) ?: directions.firstOrNull()
    val all = remember(direction) { direction?.let(departuresFor).orEmpty() }
    val next = remember(all, now) { nextDepartures(all, now, NEXT_COUNT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stop.name)
                        Text(
                            text = route.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(
                                if (isPinned) R.string.unpin_station else R.string.pin_station,
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                directions.forEachIndexed { index, item ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        text = { Text("→ ${item.headsign}") },
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.next_departures),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                if (next.isEmpty()) {
                    item { Text(stringResource(R.string.no_more_today)) }
                } else {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                next.forEachIndexed { index, departure ->
                                    if (index > 0) HorizontalDivider()
                                    DepartureRow(departure, now, highlighted = index == 0)
                                }
                            }
                        }
                    }
                }

                if (bike != null) {
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = stringResource(R.string.bike_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            BikeStationRow(
                                bike = bike,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            Text(
                                text = stringResource(R.string.bike_no_realtime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.all_departures),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item { AllDepartures(all) }

                if (showSameScheduleWarning) {
                    item {
                        Text(
                            text = stringResource(R.string.same_schedule_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure, now: Int, highlighted: Boolean) {
    val remaining = minutesUntil(departure, now)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = formatTime(departure.minutes),
                style = if (highlighted) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            )
            if (departure.tomorrow) {
                Text(
                    text = stringResource(R.string.tomorrow),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = relativeLabel(remaining),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun relativeLabel(remaining: Int): String = when {
    remaining <= 0 -> stringResource(R.string.now)
    remaining < 60 -> stringResource(R.string.in_minutes, remaining)
    else -> stringResource(R.string.in_hours, remaining / 60, remaining % 60)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllDepartures(all: List<Int>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        all.forEach { minutes ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = formatTime(minutes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
