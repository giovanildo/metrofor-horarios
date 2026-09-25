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
     * provedor ativo, ou se nada chegar dentro de [timeoutMillis].
     */
    @SuppressLint("MissingPermission") // hasPermission() barra o caminho antes
    suspend fun current(timeoutMillis: Long = 10_000): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = runCatching { manager.getProviders(true) }.getOrNull().orEmpty()
        if (providers.isEmpty()) return null

        // Uma posição recente já serve e é instantânea; só pedimos uma nova se
        // não houver nada fresco em cache.
        val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
        providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.time >= cutoff }
            .minByOrNull { it.accuracy }
            ?.let { return it }

        val provider = providers.firstOrNull { it == LocationManager.GPS_PROVIDER }
            ?: providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
            ?: providers.first()

        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    // Depreciado, mas continua abstrato nos aparelhos anteriores
                    // ao Android 11: sem este override o app quebraria lá com
                    // AbstractMethodError.
                    @Deprecated("Exigido pela interface em APIs anteriores a 30")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                runCatching {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }
    }

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        private const val MAX_AGE_MILLIS = 5 * 60 * 1000L
    }
}
