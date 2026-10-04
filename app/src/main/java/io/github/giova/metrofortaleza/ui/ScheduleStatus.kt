package io.github.giova.metrofortaleza.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.ScheduleSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Diz de onde vêm os horários na tela: de hoje, de outro dia, ou do APK. */
@Composable
fun scheduleStatusText(source: ScheduleSource, syncing: Boolean, failed: Boolean): String = when {
    syncing -> stringResource(R.string.schedule_syncing)
    source.kind == ScheduleSource.Kind.TODAY ->
        stringResource(R.string.schedule_today, formatClock(source.syncedAt))
    source.kind == ScheduleSource.Kind.SAME_DAY_TYPE -> stringResource(
        if (failed) R.string.schedule_same_type_failed else R.string.schedule_same_type,
        source.date.orEmpty().let { if (it.length == 10) "${it.substring(8)}/${it.substring(5, 7)}" else it },
    )
    else -> stringResource(R.string.schedule_bundled)
}

/** Linha de status dos horários, com botão para baixar de novo. */
@Composable
fun ScheduleStatus(
    source: ScheduleSource,
    syncing: Boolean,
    failed: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = scheduleStatusText(source, syncing, failed),
            style = MaterialTheme.typography.bodySmall,
            color = if (source.kind == ScheduleSource.Kind.TODAY || syncing) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.weight(1f),
        )
        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.padding(12.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.schedule_refresh))
            }
        }
    }
}

private fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("America/Fortaleza") }
        .format(Date(millis))
