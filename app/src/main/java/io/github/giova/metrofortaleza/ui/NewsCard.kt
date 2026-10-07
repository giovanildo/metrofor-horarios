package io.github.giova.metrofortaleza.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.Headline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val COLLAPSED_COUNT = 3

/** Manchetes recentes sobre o Metrofor, só para bater o olho. */
@Composable
fun NewsCard(
    headlines: List<Headline>,
    syncedAt: Long,
    syncing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) headlines else headlines.take(COLLAPSED_COUNT)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = stringResource(R.string.news_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (syncedAt > 0) {
                            stringResource(R.string.news_synced_at, formatDate(syncedAt, withTime = true))
                        } else {
                            stringResource(R.string.news_never_synced)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.news_refresh))
                    }
                }
            }

            if (headlines.isEmpty() && syncedAt > 0) {
                Text(
                    text = stringResource(R.string.news_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                shown.forEach { headline ->
                    Column {
                        Text(text = headline.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${formatDate(headline.publishedAt, withTime = false)} · ${headline.source}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (headlines.size > COLLAPSED_COUNT) {
                Text(
                    text = stringResource(if (expanded) R.string.news_show_less else R.string.news_show_more),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable { expanded = !expanded },
                )
            }
        }
    }
}

private fun formatDate(millis: Long, withTime: Boolean): String {
    val format = SimpleDateFormat(if (withTime) "dd/MM HH:mm" else "dd/MM", Locale("pt", "BR"))
    format.timeZone = TimeZone.getTimeZone("America/Fortaleza")
    return format.format(Date(millis))
}
