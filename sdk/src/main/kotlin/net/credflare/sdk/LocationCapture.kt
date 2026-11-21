package net.credflare.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocationCaptureException(val copyKey: String, val best: Location? = null): Exception(copyKey)

internal object LocationCapture {
    fun ageSeconds(location: Location) = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000_000.0

    fun acceptable(location: Location, settings: LocationSettings) =
        location.hasAccuracy() && location.accuracy <= settings.maxAccuracyM && ageSeconds(location) <= settings.maxAgeS

    @Suppress("DEPRECATION")
    fun isMock(location: Location): Boolean? = if(Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider

    fun toFix(location: Location): LocationFix = LocationFix(
        location.latitude, location.longitude, location.accuracy.toDouble(),
        if(location.hasAltitude()) location.altitude else null,
        IsoTime.format(location.time),
        isMock(location),
        location.provider?.takeIf { it in listOf("gps", "network", "fused") },
        IsoTime.now())

    @SuppressLint("MissingPermission")
    fun acquire(context: Context, settings: LocationSettings, onAttempt: (Int) -> Unit): Location {
        val manager = context.getSystemService(LocationManager::class.java) ?: throw LocationCaptureException(CredflareSpec.CopyKey.LOCATION_UNAVAILABLE)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if(providers.isEmpty()) throw LocationCaptureException(CredflareSpec.CopyKey.LOCATION_UNAVAILABLE)
        var best: Location? = null
        fun consider(location: Location?) {
            if(location == null || !location.hasAccuracy() || ageSeconds(location) > settings.maxAgeS) return
            if(best == null || location.accuracy < best!!.accuracy) best = location
        }
        providers.forEach { consider(runCatching { manager.getLastKnownLocation(it) }.getOrNull()) }
        best?.let { if(acceptable(it, settings)) return it }
        val thread = HandlerThread("credflare-location").apply { start() }
        try {
            for(attempt in 1..CredflareSpec.Location.RETRY_ATTEMPTS) {
                onAttempt(attempt)
                val done = CountDownLatch(1)
                val listener = object: LocationListener {
                    override fun onLocationChanged(location: Location) {
                        synchronized(this@LocationCapture) { consider(location) }
                        if(acceptable(location, settings)) done.countDown()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, thread.looper) }
                try { done.await(CredflareSpec.Location.FIX_TIMEOUT_S.toLong(), TimeUnit.SECONDS) }
                finally { manager.removeUpdates(listener) }
                synchronized(this) { best?.let { if(acceptable(it, settings)) return it } }
            }
        } finally { thread.quitSafely() }
        val last = best
        throw if(last == null) LocationCaptureException(CredflareSpec.CopyKey.LOCATION_TIMEOUT) else LocationCaptureException(CredflareSpec.CopyKey.LOCATION_LOW_ACCURACY, last)
    }
}
