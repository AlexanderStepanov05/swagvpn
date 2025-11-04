package com.retard.swag.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.retard.swag.R
import com.retard.swag.ui.home.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class XrayVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "XrayVpnService"
        const val ACTION_START = "com.retard.swag.service.START"
        const val ACTION_STOP = "com.retard.swag.service.STOP"
        const val EXTRA_CONFIG = "config"

        internal val vpnStateInternal = MutableStateFlow<VpnState>(VpnState.Disconnected)
        val vpnState = vpnStateInternal.asStateFlow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("XrayVpnService", "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config != null) {
                    startVpnFlow(config)
                } else {
                    Log.e("XrayVpnService", "Config not provided")
                    vpnStateInternal.value = VpnState.Error("Config not provided")
                }
            }
            ACTION_STOP -> stopVpnFlow()
        }
        return START_NOT_STICKY
    }

    private fun startVpnFlow(config: String) {
        serviceScope.launch {
            vpnStateInternal.value = VpnState.Connecting
            try {
                Log.d("XrayVpnService", "Creating VPN interface")
                val pfd = createVpnInterface() ?: throw IllegalStateException("Failed to create VPN interface")
                vpnInterface = pfd
                val tunFd = pfd.detachFd()

                Log.d("XrayVpnService", "Starting Xray with tunFd=$tunFd")
                val error = XrayManager.startXray(config, tunFd)
                if (error != null) {
                    Log.e("XrayVpnService", "Xray failed to start: $error")
                    throw Exception("Xray failed to start: $error")
                }

                vpnStateInternal.value = VpnState.Connected
                updateNotification("VPN is running")
                Log.d("XrayVpnService", "VPN Connected")
            } catch (e: Exception) {
                Log.e("XrayVpnService", "startVpnFlow error", e)
                vpnStateInternal.value = VpnState.Error(e.message ?: "Unknown error")
                stopVpnFlow()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopVpnFlow() {
        serviceScope.launch {
            Log.d("XrayVpnService", "Stopping VPN flow")
            XrayManager.stopXray()
            vpnInterface?.close()
            vpnInterface = null
            vpnStateInternal.value = VpnState.Disconnected
            stopForeground(true)
            stopSelf()
            Log.d("XrayVpnService", "VPN Disconnected")
        }
    }

    private fun createVpnInterface(): ParcelFileDescriptor? {
        return Builder()
            .setMtu(1400)
            .addAddress("172.19.0.1", 30)
            .addAddress("fdfe:dcba:9876::1", 126)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setSession(getString(R.string.app_name))
            .establish()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d("XrayVpnService", "Service created, starting foreground")
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String): Notification {
        val stopIntent = Intent(this, XrayVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this,
            0,
            stopIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(0, getString(android.R.string.cancel), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Xray VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnFlow()
        serviceScope.cancel()
    }
}