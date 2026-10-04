package io.github.giova.metrofortaleza.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R

/**
 * O Bicicletar em destaque na tela inicial. [fromYou] diz de onde a distância
 * foi medida: da posição do GPS, ou da estação fixada quando não há GPS.
 */
data class HomeBike(
    val bike: NearbyBike,
    val fromYou: Boolean,
)

@Composable
fun HomeBikeCard(
    homeBike: HomeBike,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    if (homeBike.fromYou) R.string.home_bike_from_you else R.string.home_bike_from_station,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            BikeStationRow(
                bike = homeBike.bike,
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
