package io.github.giova.metrofortaleza.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.BikeStation

/** A estação do Bicicletar mais próxima, com a distância até ela. */
data class NearbyBike(
    val station: BikeStation,
    val distanceMeters: Double,
)

/**
 * Linha com o Bicicletar mais próximo.
 *
 * Mostra a capacidade de vagas, e diz explicitamente que não é
 * disponibilidade: quem chega na estação e não acha bicicleta com o app tendo
 * dito "20 vagas" precisa entender por quê.
 */
@Composable
fun BikeStationRow(
    bike: NearbyBike,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
            contentDescription = stringResource(R.string.bike_title),
            modifier = Modifier.size(if (compact) 16.dp else 20.dp),
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bike.station.name,
                    style = if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.bike_number, bike.station.number),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = stringResource(
                    R.string.bike_details,
                    formatWalk(bike.distanceMeters),
                    pluralStringResource(
                        R.plurals.bike_slots,
                        bike.station.slots,
                        bike.station.slots,
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatWalk(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000)
