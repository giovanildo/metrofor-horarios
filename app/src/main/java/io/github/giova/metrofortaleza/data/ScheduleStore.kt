package io.github.giova.metrofortaleza.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Tipo de dia, para escolher a grade guardada mais parecida com hoje. */
enum class DayType { WEEKDAY, SATURDAY, SUNDAY }

/** De onde vêm os horários que o app está mostrando. */
data class ScheduleSource(
    val kind: Kind,
    /** Dia em que a grade foi baixada (`yyyy-MM-dd`), ou `null` para a do APK. */
    val date: String?,
    /** Quando foi baixada, em millis, ou 0 para a do APK. */
    val syncedAt: Long,
) {
    enum class Kind {
        /** Baixada hoje: é a grade do dia. */
        TODAY,

        /** Baixada num dia anterior do mesmo tipo (dia útil, sábado ou domingo). */
        SAME_DAY_TYPE,

        /** A que veio dentro do APK (gerada num dia útil). */
        BUNDLED,
    }
}

/**
 * Grades de horário baixadas no próprio celular.
 *
 * O GTFS do Metrofor é gerado a cada requisição e traz a grade **do dia**:
 * dia útil, sábado, ou a operação especial de um domingo de eleição — mesmo
 * com o `calendar.txt` dizendo que vale para todos os dias. Por isso o app
 * baixa o feed uma vez por dia e guarda a última grade de cada [DayType],
 * para ter algo parecido quando estiver sem internet.
 */
object ScheduleStore {

    const val FEED_URL = "https://info.metrofor.ce.gov.br/gtfs_file"
    private const val FEED_HOST = "info.metrofor.ce.gov.br"
    private const val RETRY_MILLIS = 15L * 60 * 1000

    private val zone: TimeZone = TimeZone.getTimeZone("America/Fortaleza")

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = zone }
        .format(Date())

    fun todayType(): DayType = when (Calendar.getInstance(zone).get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> DayType.SATURDAY
        Calendar.SUNDAY -> DayType.SUNDAY
        else -> DayType.WEEKDAY
    }

    /** O arquivo da grade a usar agora e de onde ele veio. `null` = usar a do APK. */
    fun current(context: Context): Pair<File, ScheduleSource>? {
        val type = todayType()
        val file = file(context, type)
        val date = prefs(context).getString(keyDate(type), null)
        if (!file.exists() || date == null) return null
        val kind = if (date == today()) ScheduleSource.Kind.TODAY else ScheduleSource.Kind.SAME_DAY_TYPE
        return file to ScheduleSource(kind, date, prefs(context).getLong(keySyncedAt(type), 0L))
    }

    /** Se ainda não há grade de hoje e não tentamos há pouco. */
    fun needsSync(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.getString(keyDate(todayType()), null) == today()) return false
        return System.currentTimeMillis() - prefs.getLong(KEY_LAST_ATTEMPT, 0L) > RETRY_MILLIS
    }

    /**
     * Baixa o feed, monta o banco e troca a grade de hoje. Bloqueia: chamar
     * fora da main. Qualquer falha lança exceção e mantém a grade anterior.
     */
    fun sync(context: Context, bikeSource: File?) {
        prefs(context).edit { putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()) }
        val type = todayType()
        val date = today()
        val zip = download()
        val target = file(context, type)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            GtfsImporter.import(zip, temp, bikeSource, FEED_URL)
            // Renomear no fim: um banco pela metade nunca fica no lugar do bom.
            check(temp.renameTo(target)) { "não foi possível instalar ${target.name}" }
        } finally {
            temp.delete()
            File(temp.path + "-journal").delete()
        }
        prefs(context).edit {
            putString(keyDate(type), date)
            putLong(keySyncedAt(type), System.currentTimeMillis())
        }
    }

    private fun download(): ByteArray {
        val connection = URL(FEED_URL).openConnection() as HttpsURLConnection
        // O certificado de *.metrofor.ce.gov.br costuma estar vencido. Aceitamos
        // qualquer certificado só nesta conexão: são dados públicos de horário, e
        // o pior que um intermediário faria é mostrar uma grade errada.
        connection.sslSocketFactory = lenientTls.socketFactory
        connection.hostnameVerifier = HostnameVerifier { host, _ -> host == FEED_HOST }
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "metrofor-horarios/1.0")
        return try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private val lenientTls: SSLContext by lazy {
        @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
    }

    private fun file(context: Context, type: DayType) =
        File(context.applicationContext.noBackupFilesDir, "schedules/${type.name.lowercase()}.db")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("metrofor_schedules", Context.MODE_PRIVATE)

    private fun keyDate(type: DayType) = "date_${type.name.lowercase()}"
    private fun keySyncedAt(type: DayType) = "synced_at_${type.name.lowercase()}"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
}
