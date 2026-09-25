package io.github.giova.metrofortaleza.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Distância em metros entre duas coordenadas, pela fórmula de haversine. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
}

/**
 * A estação mais próxima de ([lat], [lon]), com a distância em metros.
 *
 * Quando a mesma parada é servida por mais de uma linha, vence a primeira da
 * lista — que vem ordenada pela ordem oficial das linhas.
 */
fun List<Station>.nearestTo(lat: Double, lon: Double): Pair<Station, Double>? =
    map { it to distanceMeters(lat, lon, it.lat, it.lon) }
        .minByOrNull { (_, distance) -> distance }

/**
 * Distância a pé que ainda vale a pena para pegar uma bicicleta. Acima disso o
 * app não mostra nada, porque "Bicicletar a 2 km" não ajuda ninguém.
 */
const val BIKE_MAX_METERS = 600.0

/** A estação de bicicleta mais próxima de ([lat], [lon]), dentro de [maxMeters]. */
fun List<BikeStation>.nearestBikeTo(
    lat: Double,
    lon: Double,
    maxMeters: Double = BIKE_MAX_METERS,
): Pair<BikeStation, Double>? =
    map { it to distanceMeters(lat, lon, it.lat, it.lon) }
        .filter { (_, distance) -> distance <= maxMeters }
        .minByOrNull { (_, distance) -> distance }
