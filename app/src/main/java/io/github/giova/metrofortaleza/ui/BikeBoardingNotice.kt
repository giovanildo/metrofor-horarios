package io.github.giova.metrofortaleza.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.BikeBoarding
import io.github.giova.metrofortaleza.data.formatTime

/** Uma linha dizendo se dá para levar a bicicleta no trem agora. */
@Composable
fun BikeBoardingNotice(rule: BikeBoarding, modifier: Modifier = Modifier) {
    val text = when {
        rule.allowed && rule.boundary != null ->
            stringResource(R.string.bike_boarding_until, formatTime(rule.boundary))
        rule.allowed -> stringResource(R.string.bike_boarding_until_close)
        rule.boundary != null ->
            stringResource(R.string.bike_boarding_from, formatTime(rule.boundary))
        else -> stringResource(R.string.bike_boarding_not_today)
    }
    val color = if (rule.allowed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (rule.allowed) FontWeight.Medium else FontWeight.Normal,
            color = color,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
