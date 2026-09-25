package io.github.giova.metrofortaleza.data

import android.content.Context
import android.database.Cursor

/** Consultas de leitura sobre o banco de horários embarcado. */
class ScheduleRepository(context: Context) {

    private val db = MetroforDatabase.open(context)

    fun routes(): List<Route> = db.rawQuery(
        """
        SELECT route_id, name, description, color
        FROM route
        ORDER BY sort_order
        """.trimIndent(),
        null,
    ).map { Route(it.getString(0), it.getString(1), it.getString(2), it.getString(3)) }

    fun route(id: String): Route? = db.rawQuery(
        "SELECT route_id, name, description, color FROM route WHERE route_id = ?",
        arrayOf(id),
    ).map { Route(it.getString(0), it.getString(1), it.getString(2), it.getString(3)) }
        .firstOrNull()

    fun stop(id: String): Stop? = db.rawQuery(
        "SELECT stop_id, name FROM stop WHERE stop_id = ?",
        arrayOf(id),
    ).map { Stop(it.getString(0), it.getString(1), seq = 0) }.firstOrNull()

    fun stationCount(routeId: String): Int = db.rawQuery(
        "SELECT COUNT(DISTINCT stop_id) FROM route_stop WHERE route_id = ?",
        arrayOf(routeId),
    ).map { it.getInt(0) }.firstOrNull() ?: 0

    fun directions(routeId: String): List<Direction> = db.rawQuery(
        """
        SELECT direction_id, headsign
        FROM direction
        WHERE route_id = ?
        ORDER BY direction_id
        """.trimIndent(),
        arrayOf(routeId),
    ).map { Direction(it.getInt(0), it.getString(1)) }

    /** Estações na ordem em que [routeId] as percorre no sentido [directionId]. */
    fun stops(routeId: String, directionId: Int): List<Stop> = db.rawQuery(
        """
        SELECT s.stop_id, s.name, rs.seq
        FROM route_stop rs
        JOIN stop s ON s.stop_id = rs.stop_id
        WHERE rs.route_id = ? AND rs.direction_id = ?
        ORDER BY rs.seq
        """.trimIndent(),
        arrayOf(routeId, directionId.toString()),
    ).map { Stop(it.getString(0), it.getString(1), it.getInt(2)) }

    /** Todas as partidas do dia, em minutos desde a meia-noite, já ordenadas. */
    fun departures(stopId: String, routeId: String, directionId: Int): List<Int> = db.rawQuery(
        """
        SELECT minutes
        FROM departure
        WHERE stop_id = ? AND route_id = ? AND direction_id = ?
        ORDER BY minutes
        """.trimIndent(),
        arrayOf(stopId, routeId, directionId.toString()),
    ).map { it.getInt(0) }

    /** Todas as combinações estação/linha, para a busca da mais próxima. */
    fun stations(): List<Station> = db.rawQuery(
        """
        SELECT s.stop_id, s.name, s.lat, s.lon, r.route_id, r.name, r.color
        FROM route_stop rs
        JOIN stop s ON s.stop_id = rs.stop_id
        JOIN route r ON r.route_id = rs.route_id
        GROUP BY s.stop_id, r.route_id
        ORDER BY r.sort_order
        """.trimIndent(),
        null,
    ).map {
        Station(
            stopId = it.getString(0),
            stopName = it.getString(1),
            lat = it.getDouble(2),
            lon = it.getDouble(3),
            routeId = it.getString(4),
            routeName = it.getString(5),
            routeColor = it.getString(6),
        )
    }

    fun station(stopId: String, routeId: String): Station? =
        stations().firstOrNull { it.stopId == stopId && it.routeId == routeId }

    fun bikeStations(): List<BikeStation> = db.rawQuery(
        "SELECT number, name, district, slots, lat, lon FROM bike_station",
        null,
    ).map {
        BikeStation(
            number = it.getInt(0),
            name = it.getString(1),
            district = it.getString(2),
            slots = it.getInt(3),
            lat = it.getDouble(4),
            lon = it.getDouble(5),
        )
    }

    fun meta(key: String): String? = db.rawQuery(
        "SELECT value FROM meta WHERE key = ?",
        arrayOf(key),
    ).map { it.getString(0) }.firstOrNull()

    private fun <T> Cursor.map(read: (Cursor) -> T): List<T> = use { cursor ->
        buildList(cursor.count) {
            while (cursor.moveToNext()) add(read(cursor))
        }
    }
}
