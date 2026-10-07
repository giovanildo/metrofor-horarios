package io.github.giova.metrofortaleza.trip

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
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
        job = scope.launch {
            fun update() {
                val progress = estimator.progress(nowMinutes())
                val arrived = progress.index >= plan.stops.lastIndex
                var alerted = trip.alerted
                // Partiu da origem e chegou na estação de aviso (ou já passou dela).
                if (!alerted && progress.index >= plan.alertIndex) {
                    alerted = true
                    trip = trip.copy(progress = progress)
                    TripNotifications.alert(this@TripService, trip, TripTracker.isSoundEnabled(this@TripService))
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

    private fun finish() {
        job?.cancel()
        TripTracker.publish(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
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
