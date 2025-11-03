package com.retard.swag.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
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
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config != null) {
                    startVpnFlow(config)
                } else {
                    vpnStateInternal.value = VpnState.Error("Config not provided")
                }
            }
            ACTION_STOP -> stopVpnFlow()
        }
        return START_STICKY
    }

    private fun startVpnFlow(config: String) {
        serviceScope.launch {
            vpnStateInternal.value = VpnState.Connecting
            try {
                vpnInterface = createVpnInterface() ?: throw IllegalStateException("Failed to create VPN interface")

                val error = XrayManager.startXray(config)
                if (error != null) {
                    throw Exception("Xray failed to start: $error")
                }
                vpnStateInternal.value = VpnState.Connected
                updateNotification("VPN is running")
            } catch (e: Exception) {
                vpnStateInternal.value = VpnState.Error(e.message ?: "Unknown error")
                stopVpnFlow()
            }
        }
    }

    private fun stopVpnFlow() {
        serviceScope.launch {
            XrayManager.stopXray()
            vpnInterface?.close()
            vpnInterface = null
            vpnStateInternal.value = VpnState.Disconnected
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createVpnInterface(): ParcelFileDescriptor? {
        return Builder()
            .setMtu(1500)
            .addAddress("10.0.0.1", 30)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setSession(getString(R.string.app_name))
            .establish()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
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