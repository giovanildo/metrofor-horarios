package io.github.giova.metrofortaleza.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Distâncias e tempos que a pessoa pode ajustar na tela de configurações.
 * Os padrões são os valores que o app usava fixos até a v1.9.
 */
data class AppSettings(
    /** Outras linhas ganham horários na tela inicial até esta distância. */
    val nearbyLinesMeters: Int = 3_000,
    /** Bicicletar mais perto de você, pelo GPS. */
    val bikeNearYouMeters: Int = 3_000,
    /** Bicicletar perto da estação (fixada ou na tela de horários). */
    val bikeNearStationMeters: Int = 600,
    /** Acima disso, o modo viagem pergunta antes de começar. */
    val tripWarnMeters: Int = 1_000,
    /** Acima disso, o modo viagem não aparece. */
    val tripMaxMeters: Int = 3_000,
    /** Quantas estações antes do destino vem o aviso de descida. */
    val alertStationsBefore: Int = 2,
    /** Sem posição boa por este tempo, o GPS é dado como perdido. */
    val gpsLostSeconds: Int = 60,
    /** De quanto em quanto tempo a tela inicial pede posição ao GPS. */
    val homeGpsSeconds: Int = 10,
    /** O modo viagem ainda pega o trem que saiu há até este tempo. */
    val lateBoardingMinutes: Int = 2,
    /** A viagem encerra sozinha se passar tanto assim da chegada prevista. */
    val tripGiveUpMinutes: Int = 30,
    /**
     * Até quantas estações de um terminal o modo viagem oferece ir até ele e
     * voltar sentado. 0 = não oferecer.
     */
    val seatedMaxStations: Int = 3,
    /** Tema: [THEME_AUTO] (como o celular), [THEME_LIGHT] ou [THEME_DARK]. */
    val themeMode: Int = THEME_AUTO,
    /** Cores tiradas do papel de parede (Android 12+), em vez das cores do app. */
    val wallpaperColors: Boolean = true,
) {
    companion object {
        const val THEME_AUTO = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}

/** Guarda as [AppSettings] no aparelho e avisa a interface quando mudam. */
object SettingsStore {

    private val state = MutableStateFlow<AppSettings?>(null)

    fun flow(context: Context): StateFlow<AppSettings?> {
        if (state.value == null) state.value = load(context)
        return state.asStateFlow()
    }

    fun get(context: Context): AppSettings = flow(context).value ?: AppSettings()

    fun save(context: Context, settings: AppSettings) {
        prefs(context).edit {
            putInt("nearby_lines_m", settings.nearbyLinesMeters)
            putInt("bike_near_you_m", settings.bikeNearYouMeters)
            putInt("bike_near_station_m", settings.bikeNearStationMeters)
            putInt("trip_warn_m", settings.tripWarnMeters)
            putInt("trip_max_m", settings.tripMaxMeters)
            putInt("alert_stations_before", settings.alertStationsBefore)
            putInt("gps_lost_s", settings.gpsLostSeconds)
            putInt("home_gps_s", settings.homeGpsSeconds)
            putInt("late_boarding_min", settings.lateBoardingMinutes)
            putInt("trip_give_up_min", settings.tripGiveUpMinutes)
            putInt("seated_max_stations", settings.seatedMaxStations)
            putInt("theme_mode", settings.themeMode)
            putBoolean("wallpaper_colors", settings.wallpaperColors)
        }
        state.value = settings
    }

    fun reset(context: Context) {
        prefs(context).edit { clear() }
        state.value = AppSettings()
    }

    private fun load(context: Context): AppSettings {
        val p = prefs(context)
        val d = AppSettings()
        return AppSettings(
            nearbyLinesMeters = p.getInt("nearby_lines_m", d.nearbyLinesMeters),
            bikeNearYouMeters = p.getInt("bike_near_you_m", d.bikeNearYouMeters),
            bikeNearStationMeters = p.getInt("bike_near_station_m", d.bikeNearStationMeters),
            tripWarnMeters = p.getInt("trip_warn_m", d.tripWarnMeters),
            tripMaxMeters = p.getInt("trip_max_m", d.tripMaxMeters),
            alertStationsBefore = p.getInt("alert_stations_before", d.alertStationsBefore),
            gpsLostSeconds = p.getInt("gps_lost_s", d.gpsLostSeconds),
            homeGpsSeconds = p.getInt("home_gps_s", d.homeGpsSeconds),
            lateBoardingMinutes = p.getInt("late_boarding_min", d.lateBoardingMinutes),
            tripGiveUpMinutes = p.getInt("trip_give_up_min", d.tripGiveUpMinutes),
            seatedMaxStations = p.getInt("seated_max_stations", d.seatedMaxStations),
            themeMode = p.getInt("theme_mode", d.themeMode),
            wallpaperColors = p.getBoolean("wallpaper_colors", d.wallpaperColors),
        )
    }

    // Arquivo próprio: "Restaurar padrões" apaga só as configurações.
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("metrofor_settings", Context.MODE_PRIVATE)
}
