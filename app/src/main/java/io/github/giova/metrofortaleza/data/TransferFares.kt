package io.github.giova.metrofortaleza.data

/** Se a integração exige pagar outra passagem. */
enum class TransferFare { FREE, PAID, UNKNOWN }

/** A regra de uma integração, num sentido, com um motivo opcional para mostrar. */
data class TransferRule(val fare: TransferFare, val reason: String? = null)

private const val NORDESTE_FREE = "a Linha Nordeste está gratuita durante a implantação"

/**
 * Regra de passagem de cada integração, **por sentido** (de → para): em
 * Parangaba, ir da Sul para a Nordeste é grátis, mas voltar é pago — entrar
 * na estação da Linha Sul cobra a passagem. Não vem do GTFS (que só tem
 * horários): fica aqui, fixa no código, e quem confirma é quem anda de metrô.
 *
 * A chave é o nome das estações em minúsculas, como no banco. Sem regra
 * confirmada fica [TransferFare.UNKNOWN] — e o app não fala de passagem.
 * Quando a Nordeste começar a cobrar, basta trocar as entradas que a citam.
 */
private val RULES: Map<Pair<String, String>, TransferRule> = mapOf(
    ("chico da silva" to "moura brasil") to TransferRule(TransferFare.PAID),
    ("moura brasil" to "chico da silva") to TransferRule(TransferFare.PAID),
    ("parangaba" to "parangaba - ne") to TransferRule(TransferFare.FREE, NORDESTE_FREE),
    ("parangaba - ne" to "parangaba") to TransferRule(TransferFare.PAID),
    // Expedicionários (Nordeste ↔ VLT Aeroporto): ainda não confirmado.
)

fun transferRule(fromStopName: String, toStopName: String): TransferRule =
    RULES[fromStopName.trim().lowercase() to toStopName.trim().lowercase()]
        ?: TransferRule(TransferFare.UNKNOWN)
