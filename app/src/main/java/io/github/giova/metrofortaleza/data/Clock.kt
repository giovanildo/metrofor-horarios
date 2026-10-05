package io.github.giova.metrofortaleza.data

import java.util.Calendar
import java.util.TimeZone

private const val MINUTES_PER_DAY = 24 * 60

/** Minutos decorridos desde a meia-noite em Fortaleza (o sistema não tem horário de verão). */
fun nowMinutes(): Int {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Fortaleza"))
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

/** Dia da semana em Fortaleza, como as constantes de [Calendar] (`Calendar.SUNDAY`…). */
fun nowDayOfWeek(): Int =
    Calendar.getInstance(TimeZone.getTimeZone("America/Fortaleza")).get(Calendar.DAY_OF_WEEK)

/**
 * Domingo em Fortaleza. Metrô e VLTs normalmente não circulam aos domingos,
 * só em operação especial (eleição, ENEM, eventos) — mas o GTFS publica o
 * mesmo quadro para todos os dias, então o app precisa avisar por conta própria.
 */
fun isSundayToday(): Boolean = nowDayOfWeek() == Calendar.SUNDAY

/** Formata minutos desde a meia-noite como `HH:mm`. */
fun formatTime(minutes: Int): String {
    val normalized = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}

/**
 * As próximas [count] partidas a partir de [now].
 *
 * Se o serviço do dia acabar antes de completar [count], continua pelas
 * partidas de amanhã ([tomorrow]), marcadas como tal. Com [tomorrow] `null`
 * (não sabemos a grade de amanhã), devolve só as de hoje.
 */
fun nextDepartures(all: List<Int>, now: Int, count: Int, tomorrow: List<Int>?): List<Departure> {
    val today = all.asSequence()
        .filter { it >= now }
        .take(count)
        .map { Departure(it, tomorrow = false) }
        .toList()
    if (today.size >= count || tomorrow == null) return today
    val next = tomorrow.asSequence()
        .take(count - today.size)
        .map { Departure(it, tomorrow = true) }
    return today + next
}

/** Quantos minutos faltam para [departure], considerando a virada do dia. */
fun minutesUntil(departure: Departure, now: Int): Int =
    departure.minutes - now + if (departure.tomorrow) MINUTES_PER_DAY else 0
