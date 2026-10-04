package io.github.giova.metrofortaleza.data

import android.content.Context
import android.util.Xml
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/** Uma manchete sobre o Metrofor. Só texto: a ideia é leitura rápida. */
data class Headline(
    val title: String,
    val source: String,
    val publishedAt: Long,
)

/**
 * Manchetes via busca RSS do Google Notícias.
 *
 * Nem o site do Metrofor nem o do Governo do Ceará têm RSS ou API; os avisos
 * de operação especial (eleição, ENEM, obras) aparecem é na imprensa. Não é
 * uma API oficial, então qualquer falha aqui só deixa a lista antiga na tela.
 */
object NewsFeed {

    private const val QUERY = "metrofor OR \"metrô de fortaleza\" OR \"VLT\" Ceará"
    private const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_ITEMS = 20

    private val url = "https://news.google.com/rss/search?q=" +
        URLEncoder.encode(QUERY, "UTF-8") + "&hl=pt-BR&gl=BR&ceid=BR:pt-419"

    /** Baixa as manchetes dos últimos 30 dias, mais novas primeiro. Bloqueia: chamar fora da main. */
    fun fetch(now: Long = System.currentTimeMillis()): List<Headline> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "metrofor-horarios/1.0")
        return try {
            connection.inputStream.use { parse(it) }
                .filter { now - it.publishedAt <= MAX_AGE_MILLIS }
                .distinctBy { it.title }
                .sortedByDescending { it.publishedAt }
                .take(MAX_ITEMS)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(input: java.io.InputStream): List<Headline> {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        val result = mutableListOf<Headline>()
        var title = ""
        var source = ""
        var date = 0L
        var inItem = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = ""
                        source = ""
                        date = 0L
                    }
                    "title" -> if (inItem) title = parser.nextText()
                    "source" -> if (inItem) source = parser.nextText()
                    "pubDate" -> if (inItem) {
                        date = runCatching { dateFormat.parse(parser.nextText())?.time }
                            .getOrNull() ?: 0L
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    inItem = false
                    // O Google põe " - Fonte" no fim do título; a fonte já vai à parte.
                    val clean = if (source.isNotEmpty()) title.removeSuffix(" - $source") else title
                    if (clean.isNotBlank() && date > 0) result += Headline(clean.trim(), source, date)
                }
            }
        }
        return result
    }
}

/** Última lista baixada, para as manchetes aparecerem mesmo sem internet. */
class NewsCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("metrofor_prefs", Context.MODE_PRIVATE)

    val syncedAt: Long get() = prefs.getLong(KEY_SYNCED_AT, 0L)

    fun load(): List<Headline> = runCatching {
        val array = JSONArray(prefs.getString(KEY_HEADLINES, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Headline(item.getString("t"), item.getString("s"), item.getLong("d"))
        }
    }.getOrDefault(emptyList())

    fun save(headlines: List<Headline>, now: Long = System.currentTimeMillis()) {
        val array = JSONArray()
        headlines.forEach {
            array.put(JSONObject().put("t", it.title).put("s", it.source).put("d", it.publishedAt))
        }
        prefs.edit {
            putString(KEY_HEADLINES, array.toString())
            putLong(KEY_SYNCED_AT, now)
        }
    }

    private companion object {
        const val KEY_HEADLINES = "news_headlines"
        const val KEY_SYNCED_AT = "news_synced_at"
    }
}
