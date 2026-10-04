package io.github.giova.metrofortaleza.data

import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

/**
 * Converte o zip GTFS do Metrofor no mesmo SQLite que `tools/build_db.py` gera.
 *
 * É uma tradução direta do script: se mudar um, mude o outro. As estações do
 * Bicicletar não vêm do GTFS; são copiadas de [bikeSource] (o banco atual).
 */
internal object GtfsImporter {

    private val SCHEMA = listOf(
        "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        """CREATE TABLE route (
            route_id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL,
            color TEXT NOT NULL, text_color TEXT NOT NULL, url TEXT NOT NULL, sort_order INTEGER NOT NULL)""",
        """CREATE TABLE stop (
            stop_id TEXT PRIMARY KEY, code TEXT NOT NULL, name TEXT NOT NULL,
            lat REAL NOT NULL, lon REAL NOT NULL)""",
        """CREATE TABLE direction (
            route_id TEXT NOT NULL, direction_id INTEGER NOT NULL, headsign TEXT NOT NULL,
            PRIMARY KEY (route_id, direction_id))""",
        """CREATE TABLE route_stop (
            route_id TEXT NOT NULL, direction_id INTEGER NOT NULL, seq INTEGER NOT NULL,
            stop_id TEXT NOT NULL, PRIMARY KEY (route_id, direction_id, seq))""",
        """CREATE TABLE departure (
            route_id TEXT NOT NULL, direction_id INTEGER NOT NULL, stop_id TEXT NOT NULL,
            minutes INTEGER NOT NULL, trip_id TEXT NOT NULL)""",
        """CREATE TABLE bike_station (
            number INTEGER PRIMARY KEY, name TEXT NOT NULL, district TEXT NOT NULL,
            slots INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL)""",
        """CREATE TABLE transfer (
            from_stop_id TEXT NOT NULL, to_stop_id TEXT NOT NULL,
            walk_minutes INTEGER NOT NULL, note TEXT NOT NULL,
            PRIMARY KEY (from_stop_id, to_stop_id))""",
        "CREATE INDEX idx_departure_lookup ON departure (stop_id, route_id, direction_id, minutes)",
        "CREATE INDEX idx_route_stop_stop ON route_stop (stop_id)",
    )

    /** As mesmas baldeações de `build_db.py`, declaradas por nome de estação. */
    private val TRANSFERS = listOf(
        Transfer("Parangaba", "Parangaba - Ne", 2, "Mesma estacao"),
        Transfer("Expedicionarios", "Expedicionarios - Ae", 3, "Passarela"),
        Transfer("Chico Da Silva", "Moura Brasil", 5, "A pe, ~200 m"),
    )
    private const val MAX_TRANSFER_METERS = 400.0

    private data class Transfer(val a: String, val b: String, val walk: Int, val note: String)

    /**
     * Gera o banco em [target] a partir de [zip]. Lança exceção se o feed não
     * tiver o mínimo (linhas e estações); zero viagens é aceito, porque num
     * domingo comum pode ser exatamente a verdade.
     */
    fun import(zip: ByteArray, target: File, bikeSource: File?, sourceUrl: String) {
        val files = unzip(zip)
        fun table(name: String) = parseCsv(files[name] ?: error("feed sem $name"))

        val routes = table("routes.txt")
        val stops = table("stops.txt")
        val trips = table("trips.txt")
        val stopTimes = table("stop_times.txt")
        val feedInfo = files["feed_info.txt"]?.let(::parseCsv).orEmpty()
        val calendar = files["calendar.txt"]?.let(::parseCsv).orEmpty()
        check(routes.isNotEmpty()) { "feed sem linhas" }
        check(stops.isNotEmpty()) { "feed sem estações" }

        val tripById = trips.associateBy { it.getValue("trip_id") }
        val byTrip = stopTimes
            .filter { it["trip_id"] in tripById }
            .groupBy { it.getValue("trip_id") }
            .mapValues { (_, rows) -> rows.sortedBy { it["stop_sequence"]?.toIntOrNull() ?: 0 } }

        target.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(target, null)
        try {
            db.beginTransaction()
            SCHEMA.forEach(db::execSQL)

            insert(db, "route", 7, routes.map { r ->
                arrayOf<Any>(
                    r.getValue("route_id"),
                    pretty(r["route_short_name"].orEmpty().ifBlank { r["route_long_name"].orEmpty() }.trim()),
                    pretty(r["route_desc"].orEmpty().trim()),
                    r["route_color"].orEmpty().trim().ifEmpty { "777777" },
                    r["route_text_color"].orEmpty().trim().ifEmpty { "FFFFFF" },
                    r["route_url"].orEmpty().trim(),
                    r.getValue("route_id").toIntOrNull() ?: 999,
                )
            })

            insert(db, "stop", 5, stops.map { s ->
                arrayOf<Any>(
                    s.getValue("stop_id"),
                    s["stop_code"].orEmpty().trim(),
                    pretty(s["stop_name"].orEmpty().trim()),
                    s.getValue("stop_lat").toDouble(),
                    s.getValue("stop_lon").toDouble(),
                )
            })

            // trip_headsign é confiável: bate com a estação final em todas as viagens.
            val directions = linkedMapOf<Pair<String, Int>, String>()
            trips.forEach { t ->
                directions.getOrPut(t.getValue("route_id") to t.getValue("direction_id").toInt()) {
                    pretty(t["trip_headsign"].orEmpty().trim())
                }
            }
            insert(db, "direction", 3, directions.map { (key, headsign) ->
                arrayOf<Any>(key.first, key.second, headsign)
            })

            // A sequência de estações vem da viagem mais longa de cada sentido.
            val longest = mutableMapOf<Pair<String, Int>, List<Map<String, String>>>()
            byTrip.forEach { (tripId, rows) ->
                val t = tripById.getValue(tripId)
                val key = t.getValue("route_id") to t.getValue("direction_id").toInt()
                if (rows.size > (longest[key]?.size ?: -1)) longest[key] = rows
            }
            insert(db, "route_stop", 4, longest.flatMap { (key, rows) ->
                rows.mapIndexed { index, row -> arrayOf<Any>(key.first, key.second, index, row.getValue("stop_id")) }
            })

            insert(db, "departure", 5, byTrip.values.flatten().mapNotNull { row ->
                val t = tripById.getValue(row.getValue("trip_id"))
                val time = row["departure_time"].orEmpty().ifBlank { row["arrival_time"].orEmpty() }
                toMinutes(time)?.let { minutes ->
                    arrayOf<Any>(
                        t.getValue("route_id"),
                        t.getValue("direction_id").toInt(),
                        row.getValue("stop_id"),
                        minutes,
                        row.getValue("trip_id"),
                    )
                }
            })

            val byName = stops.groupBy { it["stop_name"].orEmpty().trim().lowercase() }
                .mapValues { it.value.first() }
            insert(db, "transfer", 4, TRANSFERS.flatMap { transfer ->
                val a = byName[transfer.a.lowercase()]
                val b = byName[transfer.b.lowercase()]
                if (a == null || b == null) return@flatMap emptyList()
                val distance = distanceMeters(
                    a.getValue("stop_lat").toDouble(), a.getValue("stop_lon").toDouble(),
                    b.getValue("stop_lat").toDouble(), b.getValue("stop_lon").toDouble(),
                )
                if (distance > MAX_TRANSFER_METERS) return@flatMap emptyList()
                val idA = a.getValue("stop_id")
                val idB = b.getValue("stop_id")
                listOf(
                    arrayOf<Any>(idA, idB, transfer.walk, transfer.note),
                    arrayOf<Any>(idB, idA, transfer.walk, transfer.note),
                )
            })

            val sameEveryDay = calendar.all { c ->
                listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                    .all { c[it] == "1" }
            }
            val info = feedInfo.firstOrNull().orEmpty()
            val generatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
            insert(db, "meta", 2, listOf(
                arrayOf<Any>("schema_version", "1"),
                arrayOf<Any>("feed_version", info["feed_version"].orEmpty()),
                arrayOf<Any>("feed_start_date", info["feed_start_date"].orEmpty()),
                arrayOf<Any>("feed_end_date", info["feed_end_date"].orEmpty()),
                arrayOf<Any>("source_url", sourceUrl),
                arrayOf<Any>("generated_at", generatedAt),
                arrayOf<Any>("same_schedule_every_day", if (sameEveryDay) "1" else "0"),
            ))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // ATTACH não roda dentro de transação.
        if (bikeSource != null && bikeSource.exists()) {
            db.execSQL("ATTACH DATABASE ? AS src", arrayOf<Any>(bikeSource.path))
            db.execSQL("INSERT INTO bike_station SELECT * FROM src.bike_station")
            db.execSQL("DETACH DATABASE src")
        }
        val bikes = db.rawQuery("SELECT COUNT(*) FROM bike_station", null)
            .use { it.moveToFirst(); it.getInt(0) }
        db.execSQL("INSERT INTO meta VALUES ('bike_station_count', ?)", arrayOf<Any>(bikes.toString()))
        db.close()
    }

    private fun insert(db: SQLiteDatabase, table: String, columns: Int, rows: List<Array<out Any>>) {
        val placeholders = List(columns) { "?" }.joinToString(",")
        val statement = db.compileStatement("INSERT INTO $table VALUES ($placeholders)")
        rows.forEach { row ->
            statement.clearBindings()
            row.forEachIndexed { index, value ->
                when (value) {
                    is Int -> statement.bindLong(index + 1, value.toLong())
                    is Double -> statement.bindDouble(index + 1, value)
                    else -> statement.bindString(index + 1, value.toString())
                }
            }
            statement.executeInsert()
        }
        statement.close()
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    put(entry.name.substringAfterLast('/'), zip.readBytes().decodeToString())
                }
            }
        }
    }

    /** CSV do GTFS (RFC 4180): aspas, vírgulas dentro de aspas, CRLF e BOM. */
    internal fun parseCsv(text: String): List<Map<String, String>> {
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var record = mutableListOf<String>()
        var quoted = false
        var i = if (text.startsWith('﻿')) 1 else 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { record.add(field.toString()); field = StringBuilder() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    record.add(field.toString())
                    field = StringBuilder()
                    if (record.any { it.isNotEmpty() }) records.add(record)
                    record = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        record.add(field.toString())
        if (record.any { it.isNotEmpty() }) records.add(record)

        val header = records.firstOrNull()?.map { it.trim() } ?: return emptyList()
        return records.drop(1).map { values ->
            header.mapIndexed { index, name -> name to values.getOrElse(index) { "" } }.toMap()
        }
    }

    private fun toMinutes(hhmmss: String): Int? {
        val parts = hhmmss.trim().split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        val (h, m, s) = parts
        return h * 60 + m + if (s >= 30) 1 else 0
    }

    // O GTFS do Metrofor vem em Title Case ("Vlt Sobral", "Chico Da Silva").
    private val CONNECTIVES = setOf("de", "da", "do", "das", "dos", "e")
    private val ALWAYS_UPPER = setOf("vlt", "ii", "iii", "iv", "ne", "ae")

    private fun pretty(text: String): String =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }.mapIndexed { index, token ->
            val low = token.lowercase()
            when {
                low in ALWAYS_UPPER -> token.uppercase()
                low in CONNECTIVES && index > 0 -> low
                else -> token
            }
        }.joinToString(" ")
}
