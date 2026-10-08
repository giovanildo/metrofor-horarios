package io.github.giova.metrofortaleza.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.Direction
import io.github.giova.metrofortaleza.data.Station
import io.github.giova.metrofortaleza.data.Stop
import io.github.giova.metrofortaleza.data.TripFix
import io.github.giova.metrofortaleza.data.formatTime
import io.github.giova.metrofortaleza.trip.ActiveTrip
import kotlin.math.roundToInt

/** A viagem em andamento, no topo da tela inicial. */
@Composable
fun TripCard(
    trip: ActiveTrip,
    soundEnabled: Boolean,
    onToggleSound: (Boolean) -> Unit,
    stationAlerts: Boolean,
    onToggleStationAlerts: (Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = trip.plan
    val progress = trip.progress
    val left = progress.stationsLeft(plan)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.trip_card_title, plan.routeName),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "${plan.origin.name} → ${plan.destination.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                FilledTonalIconToggleButton(checked = soundEnabled, onCheckedChange = onToggleSound) {
                    Icon(
                        imageVector = if (soundEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = stringResource(
                            if (soundEnabled) R.string.trip_sound_on else R.string.trip_sound_off,
                        ),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { ((progress.index.coerceAtLeast(0)).toFloat() / plan.stops.lastIndex.coerceAtLeast(1)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )

            Text(
                text = when {
                    trip.arrived -> stringResource(R.string.trip_arrived, plan.destination.name)
                    progress.index < 0 -> stringResource(
                        R.string.trip_waiting,
                        plan.origin.name,
                        formatTime((plan.origin.scheduled + progress.delayMinutes).roundToInt()),
                    )
                    else -> stringResource(
                        R.string.trip_progress_line,
                        plan.stops[progress.index].name,
                        pluralStringResource(R.plurals.trip_stations_left, left, left),
                        formatTime(progress.arrivalMinutes.roundToInt()),
                        stringResource(if (progress.fix == TripFix.GPS) R.string.trip_fix_gps else R.string.trip_fix_schedule),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (trip.alerted) {
                    stringResource(R.string.trip_alert_sent)
                } else {
                    stringResource(R.string.trip_alert_at, plan.stops[plan.alertIndex].name)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FilterChip(
                    selected = stationAlerts,
                    onClick = { onToggleStationAlerts(!stationAlerts) },
                    label = { Text(stringResource(R.string.trip_station_alerts)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (stationAlerts) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            contentDescription = null,
                        )
                    },
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onStop) { Text(stringResource(R.string.trip_stop)) }
            }
        }
    }
}

/** Convite no topo da tela inicial: viajar a partir da estação em destaque. */
@Composable
fun TripStartCard(
    origin: Station?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Filled.Navigation, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = stringResource(R.string.trip_start),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (origin != null) {
                        stringResource(R.string.trip_start_from, origin.stopName, origin.routeName)
                    } else {
                        stringResource(R.string.trip_start_needs_station)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (origin != null) {
                Button(onClick = onStart) { Text(stringResource(R.string.trip_start_button)) }
            }
        }
    }
}

/** Escolha do destino: as estações seguintes, em cada sentido oferecido. */
@Composable
fun DestinationDialog(
    groups: List<Pair<Direction, List<Stop>>>,
    onPick: (Direction, Stop) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_pick_destination)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                groups.filter { it.second.isNotEmpty() }.forEach { (direction, stops) ->
                    if (groups.size > 1) {
                        item(key = "h${direction.id}") {
                            Text(
                                text = "→ ${direction.headsign}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(stops, key = { "${direction.id}-${it.id}" }) { stop ->
                        Text(
                            text = stop.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(direction, stop) }
                                .padding(vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
