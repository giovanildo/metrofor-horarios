package io.github.giova.metrofortaleza.data

import org.json.JSONArray
import org.json.JSONObject

/** Uma estação no caminho da viagem, com o horário programado nela. */
data class TripStop(
    val stopId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Minutos desde a meia-noite, pela grade do dia. */
    val scheduled: Int,
)

/**
 * O que a pessoa escolheu: uma viagem de [stops].first() até [stops].last(),
 * na linha [routeName] rumo a [headsign].
 */
data class TripPlan(
    val routeId: String,
    val routeName: String,
    val headsign: String,
    val stops: List<TripStop>,
    /** Quantas estações antes do destino vem o aviso de descida. */
    val alertStationsBefore: Int = 2,
    /**
     * Viagem "sentado pelo terminal": índice da chegada ao terminal, onde o
     * trem volta (a próxima parada é o mesmo terminal, já como partida).
     * -1 = viagem direta.
     */
    val turnaroundIndex: Int = -1,
) {
    val viaTerminal get() = turnaroundIndex in 0 until stops.lastIndex
    val terminal get() = stops.getOrNull(turnaroundIndex)

    val origin get() = stops.first()
    val destination get() = stops.last()

    /** A estação em que o aviso dispara: N antes do destino, ou a origem se a viagem é curta. */
    val alertIndex get() = (stops.lastIndex - alertStationsBefore).coerceAtLeast(0)

    fun toJson(): String = JSONObject()
        .put("routeId", routeId)
        .put("routeName", routeName)
        .put("headsign", headsign)
        .put("alert", alertStationsBefore)
        .put("turn", turnaroundIndex)
        .put("stops", JSONArray().apply {
            stops.forEach {
                put(JSONObject().put("id", it.stopId).put("name", it.name)
                    .put("lat", it.lat).put("lon", it.lon).put("t", it.scheduled))
            }
        })
        .toString()

    companion object {
        fun fromJson(json: String): TripPlan? = runCatching {
            val o = JSONObject(json)
            val array = o.getJSONArray("stops")
            TripPlan(
                routeId = o.getString("routeId"),
                routeName = o.getString("routeName"),
                headsign = o.getString("headsign"),
                stops = (0 until array.length()).map { i ->
                    val s = array.getJSONObject(i)
                    TripStop(s.getString("id"), s.getString("name"), s.getDouble("lat"), s.getDouble("lon"), s.getInt("t"))
                },
                alertStationsBefore = o.optInt("alert", 2),
                turnaroundIndex = o.optInt("turn", -1),
            )
        }.getOrNull()
    }
}

/** Como a posição atual foi descoberta. */
enum class TripFix { GPS, SCHEDULE }

/** Onde a viagem está agora. */
data class TripProgress(
    /** Índice em [TripPlan.stops] da última estação alcançada (-1 = ainda não partiu). */
    val index: Int,
    val fix: TripFix,
    /** Atraso medido pelo GPS em relação à grade, em minutos (positivo = atrasado). */
    val delayMinutes: Double,
    /** Previsão de chegada ao destino, em minutos desde a meia-noite. */
    val arrivalMinutes: Double,
    /**
     * Há quantos minutos não chega posição de GPS boa, quando passou de
     * [TripEstimator.GPS_LOST_MINUTES]; `null` = GPS respondendo.
     */
    val gpsSilentMinutes: Double? = null,
) {
    /** A posição é presumida pela tabela de horários, não vista pelo GPS. */
    val presumed: Boolean get() = fix == TripFix.SCHEDULE
    fun stationsLeft(plan: TripPlan): Int = (plan.stops.lastIndex - index.coerceAtLeast(0)).coerceAtLeast(0)
}

/**
 * Acompanha a viagem. O GPS manda quando é confiável e está perto de uma
 * estação; quando ele some (dentro do trem, no trecho subterrâneo), a posição
 * sai da grade de horários, corrigida pelo atraso que o GPS mediu da última vez.
 * A posição nunca anda para trás.
 */
class TripEstimator(
    private val plan: TripPlan,
    /** Sem posição boa por este tempo, o GPS é dado como perdido (configurável). */
    private val gpsLostMinutes: Double = GPS_LOST_MINUTES,
) {

    private var index = -1
    private var delay = 0.0
    private var lastFix = TripFix.SCHEDULE
    private var startedAt: Double? = null
    private var lastGoodGps: Double? = null

    /** Uma posição de GPS. Só conta se for precisa e estiver perto de uma estação da viagem. */
    fun onLocation(lat: Double, lon: Double, accuracyMeters: Float, nowMinutes: Double) {
        if (accuracyMeters > MAX_ACCURACY_METERS) return
        // Posição boa, mesmo longe de estação: o GPS está alcançável.
        lastGoodGps = nowMinutes
        // Numa viagem pelo terminal a mesma estação aparece duas vezes (ida e
        // volta): das que estão no raio, vale a próxima à frente; só sem
        // nenhuma à frente olhamos para trás.
        val inRange = plan.stops.withIndex()
            .map { (i, stop) -> i to distanceMeters(lat, lon, stop.lat, stop.lon) }
            .filter { it.second <= STATION_RADIUS_METERS }
        if (inRange.isEmpty()) return
        val (nearest, distance) = inRange.filter { it.first >= index }.minByOrNull { it.first }
            ?: inRange.maxBy { it.first }
        // Voltar só com GPS muito bom: é o caso de quem ainda espera na estação
        // um trem atrasado, enquanto a grade já "andou" sozinha.
        val confident = accuracyMeters <= CONFIDENT_ACCURACY_METERS && distance <= CONFIDENT_RADIUS_METERS
        if (nearest < index && !confident) return
        index = nearest
        lastFix = TripFix.GPS
        // Estar na estação i agora quer dizer atraso de (agora − horário em i).
        // Na origem, chegar cedo não é adiantamento: o trem ainda vai passar.
        val measured = nowMinutes - plan.stops[nearest].scheduled
        val accepted = if (nearest == 0) measured.coerceAtLeast(0.0) else measured
        if (kotlin.math.abs(accepted) <= MAX_DELAY_MINUTES) delay = accepted
    }

    /** O estado agora, avançando pela grade quando o GPS não diz nada novo. */
    fun progress(nowMinutes: Double): TripProgress {
        // No terminal o trem espera o horário de saída: passado dele, um
        // adiantamento medido na ida não vale mais.
        if (plan.viaTerminal && index >= plan.turnaroundIndex && delay < 0) delay = 0.0
        val bySchedule = plan.stops.indexOfLast { it.scheduled + delay <= nowMinutes }
        if (bySchedule > index) {
            index = bySchedule
            lastFix = TripFix.SCHEDULE
        }
        val since = lastGoodGps ?: startedAt ?: nowMinutes.also { startedAt = it }
        val silent = nowMinutes - since
        return TripProgress(
            index = index,
            fix = lastFix,
            delayMinutes = delay,
            arrivalMinutes = plan.destination.scheduled + delay,
            gpsSilentMinutes = silent.takeIf { it >= gpsLostMinutes },
        )
    }

    companion object {
        /**
         * Padrão: sem posição boa por 1 minuto (12 tentativas, uma a cada 5 s),
         * o GPS é dado como inalcançável. Ajustável nas configurações. É menos que o tempo entre duas estações
         * (~2 min), então o aviso aparece antes de a estimativa errar de estação.
         */
        const val GPS_LOST_MINUTES = 1.0

        private const val MAX_ACCURACY_METERS = 150f
        private const val STATION_RADIUS_METERS = 300.0
        private const val CONFIDENT_ACCURACY_METERS = 40f
        private const val CONFIDENT_RADIUS_METERS = 150.0
        private const val MAX_DELAY_MINUTES = 30.0
    }
}
