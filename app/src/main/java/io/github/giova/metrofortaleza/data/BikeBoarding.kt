package io.github.giova.metrofortaleza.data

import java.util.Calendar

/**
 * Linhas onde vale o regulamento "Bike é bem-vinda no metrô" (Metrofor, 2023):
 * as três de Fortaleza com bilhete cobrado. Os VLTs ficam de fora porque o
 * regulamento não fala deles.
 */
val BIKE_BOARDING_ROUTE_IDS = setOf("1", "2", "3")

/**
 * Se dá para embarcar com bicicleta no trem agora.
 *
 * [boundary] é o fim da janela atual quando [allowed], ou o começo da próxima
 * janela de hoje quando não; `null` quer dizer "até o fim da operação" ou
 * "nenhuma janela hoje", respectivamente.
 */
data class BikeBoarding(val allowed: Boolean, val boundary: Int?)

/**
 * Janelas do regulamento: segunda a sexta das 9h às 15h e a partir das 20h;
 * sábado a partir das 15h. Domingo não aparece no regulamento.
 */
fun bikeBoarding(dayOfWeek: Int, minutes: Int): BikeBoarding {
    val windows = when (dayOfWeek) {
        Calendar.SATURDAY -> listOf(15 * 60 to null)
        Calendar.SUNDAY -> emptyList()
        else -> listOf(9 * 60 to 15 * 60, 20 * 60 to null)
    }
    windows.forEach { (start, end) ->
        if (minutes >= start && (end == null || minutes < end)) return BikeBoarding(true, end)
    }
    val next = windows.map { it.first }.firstOrNull { it > minutes }
    return BikeBoarding(false, next)
}
