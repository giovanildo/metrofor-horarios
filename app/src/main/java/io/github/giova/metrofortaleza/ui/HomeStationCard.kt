package io.github.giova.metrofortaleza.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.Station
import io.github.giova.metrofortaleza.data.formatTime
import io.github.giova.metrofortaleza.data.minutesUntil
import io.github.giova.metrofortaleza.ui.theme.routeColor
import kotlin.math.roundToInt

@Composable
fun HomeStationCard(
    state: HomeStation,
    departures: List<HomeDeparture>,
    bike: NearbyBike?,
    now: Int,
    onUseLocation: () -> Unit,
    onOpenStation: (Station) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        when (state) {
            is HomeStation.Idle -> Invitation(
                message = stringResource(R.string.home_invite),
                actionLabel = stringResource(R.string.home_use_location),
                onAction = onUseLocation,
            )

            is HomeStation.Locating -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.home_locating),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            is HomeStation.Nearby -> StationSummary(
                station = state.station,
                subtitle = stringResource(R.string.home_distance, formatDistance(state.distanceMeters)),
                icon = Icons.Filled.MyLocation,
                departures = departures,
                bike = bike,
                now = now,
                onRefresh = onUseLocation,
                onClick = { onOpenStation(state.station) },
            )

            is HomeStation.Pinned -> StationSummary(
                station = state.station,
                subtitle = stringResource(R.string.home_your_station),
                icon = Icons.Filled.Star,
                departures = departures,
                bike = bike,
                now = now,
                onRefresh = null,
                onClick = { onOpenStation(state.station) },
            )

            is HomeStation.Unavailable -> Invitation(
                message = when (state.reason) {
                    HomeStation.Reason.PERMISSION_DENIED -> stringResource(R.string.home_denied)
                    HomeStation.Reason.NO_FIX -> stringResource(R.string.home_no_fix)
                },
                actionLabel = stringResource(R.string.home_try_again),
                onAction = onUseLocation,
            )
        }
    }
}

@Composable
private fun Invitation(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onAction,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = actionLabel, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun StationSummary(
    station: Station,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    departures: List<HomeDeparture>,
    bike: NearbyBike?,
    now: Int,
    onRefresh: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = station.stopName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(routeColor(station.routeColor)),
                    )
                    Text(text = station.routeName, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRefresh != null) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.home_try_again),
                    )
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            departures.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "→ ${item.headsign}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = item.departure?.let { departure ->
                            val remaining = minutesUntil(departure, now)
                            "${formatTime(departure.minutes)}  ·  ${relativeLabel(remaining)}"
                        } ?: stringResource(R.string.home_no_departures),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (bike != null) {
                BikeStationRow(
                    bike = bike,
                    compact = true,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else "%.1f km".format(meters / 1000)
