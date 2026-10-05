package io.github.giova.metrofortaleza.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import java.io.File

/**
 * Abre o banco de horários: a grade baixada hoje (ou a última do mesmo tipo de
 * dia) quando existe, e a embarcada em `assets/metrofor.db` como reserva.
 *
 * A embarcada é gerada por `tools/build_db.py` a partir do GTFS oficial; aqui
 * ela só é copiada para um diretório gravável e aberta somente para leitura. A
 * cópia é refeita quando o carimbo de geração gravado junto ao asset difere do
 * que já está instalado.
 */
internal object MetroforDatabase {

    private const val ASSET_NAME = "metrofor.db"
    private const val VERSION_ASSET_NAME = "metrofor.db.version"
    private const val PREFS_NAME = "metrofor_db"
    private const val KEY_INSTALLED_VERSION = "installed_data_version"

    private val opened = mutableMapOf<String, SQLiteDatabase>()

    /**
     * O banco a usar agora: a grade baixada para o tipo de dia de hoje, se houver
     * ([ScheduleStore]), senão a que veio no APK.
     */
    fun open(context: Context): Pair<SQLiteDatabase, ScheduleSource> {
        val app = context.applicationContext
        val (file, source) = ScheduleStore.current(app)
            ?: (bundledFile(app) to ScheduleSource(ScheduleSource.Kind.BUNDLED, date = null, syncedAt = 0L))
        return openFile(file) to source
    }

    /**
     * O banco com a grade mais provável de amanhã: a última baixada do tipo de
     * dia de amanhã, ou a do APK se amanhã for dia útil (é a grade dela).
     * `null` quando não há como saber — sábado ou domingo nunca baixados.
     */
    fun openTomorrow(context: Context): SQLiteDatabase? {
        val app = context.applicationContext
        val type = ScheduleStore.tomorrowType()
        val file = ScheduleStore.stored(app, type)
            ?: bundledFile(app).takeIf { type == DayType.WEEKDAY }
            ?: return null
        return openFile(file)
    }

    /** O banco do APK já copiado para o disco; serve também de fonte do Bicicletar. */
    fun bundledFile(context: Context): File = synchronized(this) {
        val app = context.applicationContext
        val file = File(app.noBackupFilesDir, ASSET_NAME)
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Comparar a versao dos DADOS, e nao o versionCode do app, faz o
        // banco se atualizar sempre que tools/build_db.py rodar de novo --
        // inclusive entre builds de debug com o mesmo versionCode.
        val assetVersion = app.assets.open(VERSION_ASSET_NAME).use {
            it.readBytes().decodeToString().trim()
        }
        if (!file.exists() || prefs.getString(KEY_INSTALLED_VERSION, null) != assetVersion) {
            copyAsset(app, file)
            prefs.edit { putString(KEY_INSTALLED_VERSION, assetVersion) }
        }
        file
    }

    /**
     * Abre (ou reaproveita) um banco somente-leitura. A chave inclui a data de
     * modificação, então uma grade recém-baixada abre um handle novo. O antigo
     * fica aberto de propósito: alguma tela ainda pode estar lendo dele, e um
     * handle esquecido por dia não custa nada.
     */
    private fun openFile(file: File): SQLiteDatabase = synchronized(this) {
        val key = "${file.path}@${file.lastModified()}"
        opened.getOrPut(key) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        }
    }

    private fun copyAsset(context: Context, target: File) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$ASSET_NAME.tmp")
        context.assets.open(ASSET_NAME).use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        // Renomear no fim evita deixar um banco truncado se o processo morrer aqui.
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "não foi possível instalar $ASSET_NAME" }
    }
}
