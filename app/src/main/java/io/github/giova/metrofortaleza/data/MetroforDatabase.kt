package io.github.giova.metrofortaleza.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import java.io.File

/**
 * Abre o banco de horários que vem embarcado em `assets/metrofor.db`.
 *
 * O arquivo é gerado por `tools/build_db.py` a partir do GTFS oficial do
 * Metrofor, então aqui só precisamos copiá-lo para um diretório gravável e
 * abri-lo somente para leitura. A cópia é refeita quando o carimbo de geração
 * gravado junto ao asset difere do que já está instalado.
 */
internal object MetroforDatabase {

    private const val ASSET_NAME = "metrofor.db"
    private const val VERSION_ASSET_NAME = "metrofor.db.version"
    private const val PREFS_NAME = "metrofor_db"
    private const val KEY_INSTALLED_VERSION = "installed_data_version"

    @Volatile
    private var database: SQLiteDatabase? = null

    fun open(context: Context): SQLiteDatabase {
        database?.let { return it }
        synchronized(this) {
            database?.let { return it }
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
            val opened = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            database = opened
            return opened
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
