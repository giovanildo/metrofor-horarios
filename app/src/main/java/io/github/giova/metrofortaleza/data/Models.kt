package io.github.giova.metrofortaleza.data

/** Uma linha do sistema Metrofor. */
data class Route(
    val id: String,
    val name: String,
    val description: String,
    val colorHex: String,
)

/** Um sentido de uma linha, identificado pelo destino final. */
data class Direction(
    val id: Int,
    val headsign: String,
)

/** Uma estação na ordem em que a linha a percorre. */
data class Stop(
    val id: String,
    val name: String,
    val seq: Int,
)

/**
 * Uma partida programada.
 *
 * [minutes] são minutos desde a meia-noite do dia da partida, e [tomorrow] marca
 * as partidas que já pertencem ao dia seguinte (usado quando o serviço do dia
 * corrente acabou).
 */
data class Departure(
    val minutes: Int,
    val tomorrow: Boolean,
)

/**
 * Uma estação junto da linha que a serve.
 *
 * Uma parada pode pertencer a mais de uma linha (Campo dos Velhos, em Sobral),
 * então o par é o que identifica de forma não ambígua o que mostrar.
 */
data class Station(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val routeId: String,
    val routeName: String,
    val routeColor: String,
)

/**
 * Uma estação do Bicicletar, o sistema de bicicletas compartilhadas de
 * Fortaleza.
 *
 * [slots] é a capacidade de vagas da estação, não quantas bicicletas estão lá
 * agora: não existe fonte pública de disponibilidade em tempo real.
 */
data class BikeStation(
    val number: Int,
    val name: String,
    val district: String,
    val slots: Int,
    val lat: Double,
    val lon: Double,
)
