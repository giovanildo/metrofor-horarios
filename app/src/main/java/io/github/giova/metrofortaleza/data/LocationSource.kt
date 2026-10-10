package io.github.giova.metrofortaleza.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Posição atual do aparelho, via [LocationManager] do próprio Android.
 *
 * Não usamos o Play Services de propósito: o app inteiro tem três dependências
 * e não vale trocar isso por uma precisão que aqui não faz diferença — as
 * estações estão a mais de um quilômetro umas das outras.
 */
class LocationSource(private val context: Context) {

    fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * A melhor posição que der para obter, ou `null` se não houver permissão,
     * provedor ativo, nem posição guardada recente.
     *
     * Pede a todos os provedores ao mesmo tempo — o do Google ("fused", o mesmo
     * do Maps), a rede (Wi-Fi/antenas) e o GPS — e fica com a primeira posição
     * boa. Pedir só ao GPS fazia o botão falhar em quem acabou de instalar: sem
     * posição guardada, o GPS frio não acha satélites em poucos segundos dentro
     * de casa ou do trem, e só abrir o Maps (que usa o "fused") destravava.
     */
    @SuppressLint("MissingPermission") // hasPermission() barra o caminho antes
    suspend fun current(timeoutMillis: Long = 30_000): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = runCatching { manager.getProviders(true) }.getOrNull().orEmpty()

        // Uma posição recente já serve e é instantânea.
        val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        val now = System.currentTimeMillis()
        cached.filter { it.time >= now - MAX_AGE_MILLIS }
            .minByOrNull { it.accuracy }
            ?.let { return it }

        var best: Location? = null
        val fresh = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (best.let { it == null || location.accuracy < it.accuracy }) best = location
                        // As estações ficam a mais de 1 km umas das outras: 500 m já acerta.
                        if (location.accuracy <= GOOD_ENOUGH_METERS) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }
                    }

                    // Depreciado, mas continua abstrato nos aparelhos anteriores
                    // ao Android 11: sem este override o app quebraria lá com
                    // AbstractMethodError.
                    @Deprecated("Exigido pela interface em APIs anteriores a 30")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                val asked = providers.filter { it in WANTED_PROVIDERS }.count { provider ->
                    runCatching {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    }.isSuccess
                }
                if (asked == 0 && continuation.isActive) continuation.resume(null)
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }
        // Nada bom a tempo: a melhor que chegou, ou a última guardada de até 30 min.
        return fresh ?: best ?: cached
            .filter { it.time >= now - FALLBACK_AGE_MILLIS }
            .maxByOrNull { it.time }
    }

    /** A posição mais precisa já guardada no aparelho, de até [maxAgeMillis] atrás. Não liga o GPS. */
    @SuppressLint("MissingPermission") // hasPermission() barra o caminho antes
    fun lastKnown(maxAgeMillis: Long): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        return runCatching { manager.getProviders(true) }.getOrNull().orEmpty()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.time >= cutoff }
            .minByOrNull { it.accuracy }
    }

    /**
     * Posições contínuas enquanto o Flow for coletado, de GPS e rede ao mesmo
     * tempo — dentro do trem o GPS some e a rede ainda ajuda. Sem permissão ou
     * provedor, o Flow simplesmente não emite.
     */
    @SuppressLint("MissingPermission") // hasPermission() barra o caminho antes
    fun updates(intervalMillis: Long, minMeters: Float): Flow<Location> = callbackFlow {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (!hasPermission() || manager == null) {
            awaitClose()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Exigido pela interface em APIs anteriores a 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        val providers = runCatching { manager.getProviders(true) }.getOrNull().orEmpty()
            .filter { it in WANTED_PROVIDERS }
        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(provider, intervalMillis, minMeters, listener, Looper.getMainLooper())
            }
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        private const val MAX_AGE_MILLIS = 60 * 1000L
        private const val FALLBACK_AGE_MILLIS = 30 * 60 * 1000L
        private const val GOOD_ENOUGH_METERS = 500f

        /** "fused" é o provedor do Google (o do Maps); só existe em aparelhos com ele. */
        private val WANTED_PROVIDERS = setOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
