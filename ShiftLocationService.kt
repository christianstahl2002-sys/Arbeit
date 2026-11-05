package de.example.aussendienst

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class ShiftLocationService : Service() {
    private lateinit var store: TrackingStore
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var callback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        store = TrackingStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (store.activeShift() == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startOngoingNotification()
                startLocationUpdates()
            }
        }
        return START_STICKY
    }

    private fun startOngoingNotification() {
        val openApp = PendingIntent.getActivity(
            this, 701, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Schichtaufzeichnung aktiv")
            .setContentText("Route und Aufenthalte werden lokal auf diesem Gerät gespeichert.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        if (callback != null) return
        val intervalSeconds = getSharedPreferences("settings", MODE_PRIVATE).getInt("interval_seconds", 15).coerceIn(10, 60)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalSeconds * 1000L)
            .setMinUpdateIntervalMillis((intervalSeconds * 1000L) / 2)
            .setMinUpdateDistanceMeters(0f)
            .build()
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { store.recordLocation(it) }
                sendBroadcast(Intent(ACTION_DATA_UPDATED).setPackage(packageName))
            }
        }
        try {
            fused.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
        } catch (_: SecurityException) {
            callback = null
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Aktive Schichtaufzeichnung", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Zeigt an, dass die manuell gestartete Schichtaufzeichnung aktiv ist."
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "de.example.aussendienst.action.START"
        const val ACTION_STOP = "de.example.aussendienst.action.STOP"
        const val ACTION_DATA_UPDATED = "de.example.aussendienst.action.DATA_UPDATED"
        private const val CHANNEL_ID = "shift_tracking"
        private const val NOTIFICATION_ID = 801
    }
}
