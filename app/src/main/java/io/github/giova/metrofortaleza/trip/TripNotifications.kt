package io.github.giova.metrofortaleza.trip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.giova.metrofortaleza.MainActivity
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.TripFix
import io.github.giova.metrofortaleza.data.formatTime
import kotlin.math.roundToInt

/**
 * Canais e notificações do modo viagem.
 *
 * O som de um canal não pode ser mudado depois de criado, então o aviso tem
 * dois canais: um com som de alarme (toca mesmo no modo vibrar) e um só com
 * vibração. O botão de som da tela escolhe qual usar.
 */
internal object TripNotifications {

    const val ONGOING_ID = 1001
    private const val ALERT_ID = 1002
    private const val STATION_ID = 1003

    private const val CHANNEL_ONGOING = "trip_ongoing"
    private const val CHANNEL_ALERT_SOUND = "trip_alert_sound"
    private const val CHANNEL_ALERT_SILENT = "trip_alert_silent"
    private const val CHANNEL_STATION = "trip_station"

    private val VIBRATION = longArrayOf(0, 600, 300, 600, 300, 600)
    private const val STATION_TIMEOUT_MILLIS = 60_000L

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                context.getString(R.string.trip_channel_ongoing),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT_SOUND,
                context.getString(R.string.trip_channel_alert_sound),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(
                    alarmUri(),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = VIBRATION
            },
        )
        // Aviso de cada estação: som curto de notificação, não o de alarme.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATION,
                context.getString(R.string.trip_channel_station),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT_SILENT,
                context.getString(R.string.trip_channel_alert_silent),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = VIBRATION
            },
        )
    }

    /** Aviso curto ao alcançar cada estação, quando a pessoa ligou essa opção. */
    fun station(context: Context, trip: ActiveTrip) {
        val plan = trip.plan
        val index = trip.progress.index.coerceIn(0, plan.stops.lastIndex)
        val left = trip.progress.stationsLeft(plan)
        val text = if (index == plan.stops.lastIndex) {
            context.getString(R.string.trip_arrived, plan.destination.name)
        } else {
            context.getString(
                R.string.trip_station_text,
                context.resources.getQuantityString(R.plurals.trip_stations_left, left, left),
                plan.destination.name,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_STATION)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(plan.stops[index].name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setTimeoutAfter(STATION_TIMEOUT_MILLIS)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(STATION_ID, notification)
        }
    }

    /** A notificação fixa enquanto a viagem dura. */
    fun ongoing(context: Context, trip: ActiveTrip): android.app.Notification {
        val plan = trip.plan
        val progress = trip.progress
        val left = progress.stationsLeft(plan)
        val title = context.getString(R.string.trip_notification_title, plan.destination.name)
        val text = when {
            trip.arrived -> context.getString(R.string.trip_arrived, plan.destination.name)
            progress.index < 0 -> context.getString(
                R.string.trip_waiting, plan.origin.name, formatTime((plan.origin.scheduled + progress.delayMinutes).roundToInt()),
            )
            else -> context.getString(
                R.string.trip_progress_line,
                plan.stops[progress.index].name,
                context.resources.getQuantityString(R.plurals.trip_stations_left, left, left),
                formatTime(progress.arrivalMinutes.roundToInt()),
                context.getString(if (progress.fix == TripFix.GPS) R.string.trip_fix_gps else R.string.trip_fix_schedule),
            )
        }
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .addAction(0, context.getString(R.string.trip_stop), stopIntent(context))
            .build()
    }

    /** O aviso de que o destino está chegando. */
    fun alert(context: Context, trip: ActiveTrip, sound: Boolean) {
        val left = trip.progress.stationsLeft(trip.plan)
        val text = context.getString(
            R.string.trip_alert_text,
            context.resources.getQuantityString(R.plurals.trip_stations_left, left, left),
            trip.plan.destination.name,
        )
        val notification = NotificationCompat.Builder(context, if (sound) CHANNEL_ALERT_SOUND else CHANNEL_ALERT_SILENT)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.trip_alert_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(VIBRATION)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            // Antes do Android 8 não há canais: o som vai na própria notificação.
            .apply { if (sound) setSound(alarmUri(), AudioManager.STREAM_ALARM) }
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(ALERT_ID, notification)
        }
    }

    private fun alarmUri() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, TripService::class.java).setAction(TripService.ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
