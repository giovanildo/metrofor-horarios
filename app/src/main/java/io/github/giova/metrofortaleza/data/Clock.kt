package io.github.giova.metrofortaleza.data

import java.util.Calendar
import java.util.TimeZone

private const val MINUTES_PER_DAY = 24 * 60

/** Minutos decorridos desde a meia-noite em Fortaleza (o sistema não tem horário de verão). */
fun nowMinutes(): Int {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Fortaleza"))
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

/** Formata minutos desde a meia-noite como `HH:mm`. */
fun formatTime(minutes: Int): String {
    val normalized = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}

/**
 * As próximas [count] partidas a partir de [now].
 *
 * Se o serviço do dia já terminou, volta ao começo da lista e marca as partidas
 * como sendo de amanhã — o feed do Metrofor usa o mesmo quadro todos os dias,
 * então a primeira viagem de amanhã é a mesma de hoje.
 */
fun nextDepartures(all: List<Int>, now: Int, count: Int): List<Departure> {
    if (all.isEmpty()) return emptyList()
    val today = all.asSequence()
        .filter { it >= now }
        .take(count)
        .map { Departure(it, tomorrow = false) }
        .toList()
    if (today.size >= count) return today
    val tomorrow = all.asSequence()
        .take(count - today.size)
        .map { Departure(it, tomorrow = true) }
    return today + tomorrow
}

/** Quantos minutos faltam para [departure], considerando a virada do dia. */
fun minutesUntil(departure: Departure, now: Int): Int =
    departure.minutes - now + if (departure.tomorrow) MINUTES_PER_DAY else 0
