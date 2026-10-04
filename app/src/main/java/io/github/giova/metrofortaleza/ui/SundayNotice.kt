package io.github.giova.metrofortaleza.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R

/**
 * Aviso de domingo. As partidas continuam visíveis porque, em dia de operação
 * especial, ainda servem de referência aproximada.
 */
@Composable
fun SundayNotice(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.sunday_warning),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
