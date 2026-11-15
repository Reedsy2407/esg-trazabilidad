package pe.esgtrazabilidad.reciclador.field

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pe.esgtrazabilidad.reciclador.MainActivity
import pe.esgtrazabilidad.reciclador.R
import pe.esgtrazabilidad.reciclador.RecyclerApp
import pe.esgtrazabilidad.reciclador.session.SessionState

/** What the system gives us for GPS right now, asked before opening a workday (R17, R32). */
object LocationAvailability {
    fun permissionGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun gpsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun usable(context: Context): Boolean = permissionGranted(context) && gpsEnabled(context)
}

/**
 * The foreground service of a workday that records GPS: a persistent notification that says what it does, and a
 * platform [LocationManager] listener (no extra dependency) that stores one point every [INTERVAL_MILLIS].
 * It starts and stops with the workday. If the system kills it, the app starts it again when it is opened
 * ([WorkdayManager.shouldRecord]); a service that restarts and finds nothing to record stops by itself.
 * Nothing it does is written to a log: coordinates never reach logcat.
 */
class TrackingService : Service() {

    // A reading that fails to be stored must never take the process down: the points already stored are safe on disk.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    private var listener: LocationListener? = null

    private val workdays get() = (application as RecyclerApp).workdays

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: RuntimeException) {
            // The system refused a foreground start (permission missing, started from the background): nothing to keep.
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            if (!workdays.shouldRecord() || !LocationAvailability.permissionGranted(this@TrackingService)) {
                stopSelf()
                return@launch
            }
            startListening()
        }
        // A logout or a revocation ends the service at once, with or without a GPS fix (the notice would linger otherwise).
        scope.launch {
            (application as RecyclerApp).session.state.collect { if (it !is SessionState.Active) stopSelf() }
        }
        return START_STICKY
    }

    private fun startListening() {
        if (listener != null) return
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                scope.launch { store(location) }
            }

            override fun onProviderDisabled(provider: String) {
                if (provider != LocationManager.GPS_PROVIDER) return
                scope.launch {
                    workdays.stopRecordingGps("El GPS del teléfono se apagó: la jornada sigue sin registrar el recorrido.")
                    stopSelf()
                }
            }

            override fun onProviderEnabled(provider: String) = Unit

            // Required below API 29; the platform never calls it from there on.
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MILLIS, 0f, l, Looper.getMainLooper())
            listener = l
        } catch (e: SecurityException) {
            scope.launch {
                workdays.stopRecordingGps("El permiso de ubicación ya no está activo: la jornada sigue sin registrar el recorrido.")
                stopSelf()
            }
        } catch (e: IllegalArgumentException) {
            // No GPS provider on this device.
            scope.launch {
                workdays.stopRecordingGps("Este teléfono no da ubicación por GPS: la jornada sigue sin registrar el recorrido.")
                stopSelf()
            }
        }
    }

    private suspend fun store(location: Location) {
        // Nothing is stored once the workday is closed or the session ended: the service ends instead.
        if (!workdays.shouldRecord()) {
            stopSelf()
            return
        }
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0
        if (workdays.recordPoint(location.latitude, location.longitude, accuracy)) workdays.onPointStored()
    }

    override fun onDestroy() {
        listener?.let { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(it) }
        listener = null
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.tracking_channel_description)
            },
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gps)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val INTERVAL_MILLIS = 10_000L
        private const val CHANNEL_ID = "workday"
        private const val NOTIFICATION_ID = 1001

        /** Starts the service; the app must be on screen (it is, when the recycler opens the workday or the app). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }
}
