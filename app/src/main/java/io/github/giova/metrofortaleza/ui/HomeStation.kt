package io.github.giova.metrofortaleza.ui

import io.github.giova.metrofortaleza.data.Departure
import io.github.giova.metrofortaleza.data.Station

/** A próxima partida de um sentido, para o resumo da tela inicial. */
data class HomeDeparture(
    val headsign: String,
    val departure: Departure?,
)

/**
 * Qual estação a tela inicial destaca no topo.
 *
 * As partidas ficam de fora de propósito: elas dependem do relógio, que anda
 * sozinho, então são recalculadas na renderização em vez de viverem no estado.
 */
sealed interface HomeStation {

    /** Ainda não pedimos nada: a pessoa decide se quer usar a localização. */
    data object Idle : HomeStation

    data object Locating : HomeStation

    /** Achada por GPS. */
    data class Nearby(val station: Station, val distanceMeters: Double) : HomeStation

    /** Escolhida à mão pela pessoa. */
    data class Pinned(val station: Station) : HomeStation

    data class Unavailable(val reason: Reason) : HomeStation

    enum class Reason { PERMISSION_DENIED, NO_FIX }
}

/** A estação em destaque, se houver uma. */
val HomeStation.station: Station?
    get() = when (this) {
        is HomeStation.Nearby -> station
        is HomeStation.Pinned -> station
        else -> null
    }
