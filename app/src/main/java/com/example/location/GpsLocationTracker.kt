package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LiveGpsLocation(
    val latitude: Double = 41.0082,
    val longitude: Double = 28.9784,
    val accuracy: Float = 5f,
    val addressName: String = "İstanbul, Türkiye",
    val timestamp: Long = System.currentTimeMillis()
)

class GpsLocationTracker(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationState = MutableStateFlow(LiveGpsLocation())
    val locationState: StateFlow<LiveGpsLocation> = _locationState

    private val scope = CoroutineScope(Dispatchers.IO)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val lastLocation: Location? = result.lastLocation
            if (lastLocation != null) {
                val newGps = LiveGpsLocation(
                    latitude = lastLocation.latitude,
                    longitude = lastLocation.longitude,
                    accuracy = lastLocation.accuracy,
                    addressName = "Enlem: %.4f, Boylam: %.4f".format(lastLocation.latitude, lastLocation.longitude),
                    timestamp = System.currentTimeMillis()
                )
                _locationState.value = newGps
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val fineLocation = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fineLocation != PackageManager.PERMISSION_GRANTED && coarseLocation != PackageManager.PERMISSION_GRANTED) {
            Log.w("GpsLocationTracker", "Location permissions not granted yet. Skipping location updates until permission is granted.")
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e("GpsLocationTracker", "Error requesting location updates: ${e.message}")
        }
    }

    fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e("GpsLocationTracker", "Error removing location updates: ${e.message}")
        }
    }
}
