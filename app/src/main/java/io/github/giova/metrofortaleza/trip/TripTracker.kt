package io.github.giova.metrofortaleza.trip

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.giova.metrofortaleza.data.TripPlan
import io.github.giova.metrofortaleza.data.TripProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uma viagem em andamento, como a tela e a notificação a mostram. */
data class ActiveTrip(
    val plan: TripPlan,
    val progress: TripProgress,
    val alerted: Boolean,
    val arrived: Boolean,
)

/**
 * Ponte entre o [TripService] e a interface: o serviço publica o estado aqui,
 * as telas observam. Também guarda a preferência do aviso sonoro.
 */
object TripTracker {

    private val _active = MutableStateFlow<ActiveTrip?>(null)
    val active: StateFlow<ActiveTrip?> = _active.asStateFlow()

    private val _soundEnabled = MutableStateFlow<Boolean?>(null)
    private val _stationAlerts = MutableStateFlow<Boolean?>(null)

    internal fun publish(trip: ActiveTrip?) {
        _active.value = trip
    }

    fun start(context: Context, plan: TripPlan) {
        val intent = Intent(context, TripService::class.java)
            .setAction(TripService.ACTION_START)
            .putExtra(TripService.EXTRA_PLAN, plan.toJson())
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, TripService::class.java).setAction(TripService.ACTION_STOP))
    }

    /** Se o aviso de chegada toca som (o padrão) ou só aparece e vibra. */
    fun soundEnabled(context: Context): StateFlow<Boolean?> {
        if (_soundEnabled.value == null) _soundEnabled.value = prefs(context).getBoolean(KEY_SOUND, true)
        return _soundEnabled
    }

    fun isSoundEnabled(context: Context): Boolean = soundEnabled(context).value ?: true

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_SOUND, enabled) }
        _soundEnabled.value = enabled
    }

    /** Se cada estação alcançada gera um aviso curto com som (desligado por padrão). */
    fun stationAlerts(context: Context): StateFlow<Boolean?> {
        if (_stationAlerts.value == null) _stationAlerts.value = prefs(context).getBoolean(KEY_STATION_ALERTS, false)
        return _stationAlerts
    }

    fun isStationAlertsEnabled(context: Context): Boolean = stationAlerts(context).value ?: false

    fun setStationAlerts(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_STATION_ALERTS, enabled) }
        _stationAlerts.value = enabled
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("metrofor_prefs", Context.MODE_PRIVATE)

    private const val KEY_SOUND = "trip_alert_sound"
    private const val KEY_STATION_ALERTS = "trip_station_alerts"
}
