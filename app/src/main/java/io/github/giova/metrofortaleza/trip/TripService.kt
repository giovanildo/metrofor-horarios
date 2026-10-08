package io.github.giova.metrofortaleza.trip

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.LocationSource
import io.github.giova.metrofortaleza.data.TripEstimator
import io.github.giova.metrofortaleza.data.TripPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * Serviço em primeiro plano do modo viagem: segue o GPS com a tela desligada,
 * cai para a grade de horários quando o sinal some, e avisa duas estações
 * antes do destino.
 */
class TripService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var voice: TripVoice? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        val plan = intent?.getStringExtra(EXTRA_PLAN)?.let(TripPlan::fromJson)
        if (plan == null) {
            finish()
            return START_NOT_STICKY
        }
        TripNotifications.createChannels(this)
        val estimator = TripEstimator(plan)
        var trip = ActiveTrip(plan, estimator.progress(nowMinutes()), alerted = false, arrived = false)
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                TripNotifications.ONGOING_ID,
                TripNotifications.ongoing(this, trip),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }
        if (started.isFailure) {
            // Sem permissão de localização o Android recusa o serviço; não há viagem.
            finish()
            return START_NOT_STICKY
        }
        TripTracker.publish(trip)

        job?.cancel()
        if (voice == null) voice = TripVoice(this)
        job = scope.launch {
            // A origem não é anunciada: a pessoa já está nela.
            var announced = 0
            fun update() {
                val progress = estimator.progress(nowMinutes())
                val arrived = progress.index >= plan.stops.lastIndex
                var alerted = trip.alerted
                var alertedNow = false
                // Partiu da origem e chegou na estação de aviso (ou já passou dela).
                if (!alerted && progress.index >= plan.alertIndex) {
                    alerted = true
                    alertedNow = true
                    trip = trip.copy(progress = progress)
                    val sound = TripTracker.isSoundEnabled(this@TripService)
                    TripNotifications.alert(this@TripService, trip, sound)
                    if (sound) voice?.speak(spokenAlert(trip))
                }
                // Estação nova alcançada. Na estação do aviso principal, ele já basta.
                if (progress.index > announced) {
                    announced = progress.index
                    if (!alertedNow && TripTracker.isStationAlertsEnabled(this@TripService)) {
                        val reached = trip.copy(progress = progress)
                        TripNotifications.station(this@TripService, reached)
                        voice?.speak(spokenStation(reached))
                    }
                }
                trip = trip.copy(progress = progress, alerted = alerted, arrived = arrived)
                TripTracker.publish(trip)
                runCatching {
                    getSystemService(NotificationManager::class.java)
                        .notify(TripNotifications.ONGOING_ID, TripNotifications.ongoing(this@TripService, trip))
                }
            }

            launch {
                LocationSource(this@TripService).updates(GPS_INTERVAL_MILLIS, 0f).collect { location ->
                    estimator.onLocation(location.latitude, location.longitude, location.accuracy, nowMinutes())
                    update()
                }
            }
            while (true) {
                update()
                if (trip.arrived) {
                    // Deixa a notificação de chegada um tempo e encerra sozinho.
                    delay(ARRIVED_LINGER_MILLIS)
                    finish()
                    break
                }
                if (nowMinutes() > plan.destination.scheduled + trip.progress.delayMinutes + GIVE_UP_MINUTES) {
                    finish()
                    break
                }
                delay(TICK_MILLIS)
            }
        }
        return START_NOT_STICKY
    }

    /** "Parangaba. Faltam 5 estações para Benfica." — ou a chegada. */
    private fun spokenStation(trip: ActiveTrip): String {
        val plan = trip.plan
        val index = trip.progress.index.coerceIn(0, plan.stops.lastIndex)
        if (index == plan.stops.lastIndex) return getString(R.string.trip_arrived, plan.destination.name)
        val left = trip.progress.stationsLeft(plan)
        return plan.stops[index].name + ". " + getString(
            R.string.trip_station_text,
            resources.getQuantityString(R.plurals.trip_stations_left, left, left),
            plan.destination.name,
        )
    }

    /** "Prepare-se para descer. Faltam 2 estações para Benfica." */
    private fun spokenAlert(trip: ActiveTrip): String {
        val left = trip.progress.stationsLeft(trip.plan)
        return getString(R.string.trip_alert_title) + ". " + getString(
            R.string.trip_alert_text,
            resources.getQuantityString(R.plurals.trip_stations_left, left, left),
            trip.plan.destination.name,
        )
    }

    private fun finish() {
        job?.cancel()
        TripTracker.publish(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        voice?.shutdown()
        voice = null
        TripTracker.publish(null)
        super.onDestroy()
    }

    private fun nowMinutes(): Double {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Fortaleza"))
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE) +
            calendar.get(Calendar.SECOND) / 60.0
    }

    companion object {
        const val ACTION_START = "io.github.giova.metrofortaleza.trip.START"
        const val ACTION_STOP = "io.github.giova.metrofortaleza.trip.STOP"
        const val EXTRA_PLAN = "plan"

        private const val GPS_INTERVAL_MILLIS = 5_000L
        private const val TICK_MILLIS = 15_000L
        private const val ARRIVED_LINGER_MILLIS = 60_000L
        /** Se a viagem passou tanto do horário previsto, algo deu errado: encerra. */
        private const val GIVE_UP_MINUTES = 30
    }
}
