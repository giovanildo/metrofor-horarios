package io.github.giova.metrofortaleza.data

/** Se a baldeação exige pagar outra passagem. */
enum class TransferFare { FREE, PAID, UNKNOWN }

/**
 * Regra de passagem de cada baldeação. Não vem do GTFS (que só tem horários):
 * fica aqui, fixa no código, como as baldeações em `tools/build_db.py`.
 *
 * A chave é o par de estações, sem ordem, pelo nome em minúsculas. Enquanto a
 * regra não for confirmada, fica [TransferFare.UNKNOWN] — e o app não diz
 * nada sobre passagem, para não informar errado.
 */
private val FARES: Map<Set<String>, TransferFare> = mapOf(
    setOf("parangaba", "parangaba - ne") to TransferFare.UNKNOWN,
    setOf("expedicionarios", "expedicionarios - ae") to TransferFare.UNKNOWN,
    setOf("chico da silva", "moura brasil") to TransferFare.UNKNOWN,
)

fun transferFare(fromStopName: String, toStopName: String): TransferFare =
    FARES[setOf(fromStopName.trim().lowercase(), toStopName.trim().lowercase())] ?: TransferFare.UNKNOWN
