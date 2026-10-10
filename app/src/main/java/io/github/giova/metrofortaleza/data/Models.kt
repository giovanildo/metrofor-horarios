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

/** Um sentido de outra linha que se alcança a pé a partir de uma estação. */
data class TransferTarget(
    val stopId: String,
    val stopName: String,
    val walkMinutes: Int,
    val routeId: String,
    val routeName: String,
    val routeColor: String,
    val direction: Direction,
    /** Se trocar de linha aqui, neste sentido, exige nova passagem. */
    val fare: TransferRule = TransferRule(TransferFare.UNKNOWN),
)

/**
 * Como chamar o veículo da linha no texto: a Linha Sul é **metrô**; as
 * demais (Oeste, Nordeste, Aeroporto, Sobral e Cariri) são **VLT**. O
 * passageiro não fala "trem". Os dois são masculinos ("o metrô", "o VLT").
 */
fun vehicleName(routeName: String): String =
    if (routeName.trim().equals("Linha Sul", ignoreCase = true)) "metrô" else "VLT"
