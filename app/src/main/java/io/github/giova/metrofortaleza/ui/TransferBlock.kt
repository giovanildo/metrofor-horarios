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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import io.github.giova.metrofortaleza.data.Departure
import io.github.giova.metrofortaleza.data.TransferFare
import io.github.giova.metrofortaleza.data.TransferTarget
import io.github.giova.metrofortaleza.data.formatTime
import io.github.giova.metrofortaleza.data.minutesUntil
import io.github.giova.metrofortaleza.ui.theme.routeColor

/**
 * Baldeação para outra linha a partir desta estação. Cada sentido mostra o
 * primeiro trem que ainda dá para pegar, contando a caminhada até lá.
 */
@Composable
fun TransferBlock(
    targets: List<TransferTarget>,
    nextCatchable: (TransferTarget) -> Departure?,
    now: Int,
    onOpen: (TransferTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Uma caixa por linha/estação de destino, com os sentidos dentro.
    targets.groupBy { it.routeId to it.stopId }.values.forEach { group ->
        val first = group.first()
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = modifier
                .fillMaxWidth()
                .clickable { onOpen(first) },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SyncAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.transfer_title),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(
                        Modifier
                            .padding(start = 10.dp, end = 6.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(routeColor(first.routeColor)),
                    )
                    Text(
                        text = first.routeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.transfer_walk, first.stopName, first.walkMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                // Passagem: só falamos quando a regra está confirmada.
                when (first.fare.fare) {
                    TransferFare.FREE -> Text(
                        text = stringResource(R.string.transfer_free) +
                            (first.fare.reason?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TransferFare.PAID -> Text(
                        text = stringResource(R.string.transfer_paid) +
                            (first.fare.reason?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TransferFare.UNKNOWN -> Unit
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    group.forEach { target ->
                        val departure = nextCatchable(target)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("→ ${target.direction.headsign}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = departure?.let {
                                    "${formatTime(it.minutes)}  ·  ${relativeLabel(minutesUntil(it, now))}"
                                } ?: stringResource(R.string.home_no_departures),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.transfer_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
