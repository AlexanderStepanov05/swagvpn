package com.retard.swag.ui.home

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retard.swag.R
import com.retard.swag.domain.repository.SelectedServerRepository
import com.retard.swag.service.XrayVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.retard.swag.service.XrayManager
import androidx.core.content.ContextCompat

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selectedServerRepository: SelectedServerRepository
) : ViewModel() {

    val vpnState: StateFlow<VpnState> = XrayVpnService.vpnState
        .stateIn(viewModelScope, SharingStarted.Eagerly, VpnState.Disconnected)

    private val _permissionRequest = MutableSharedFlow<Intent>()
    val permissionRequest = _permissionRequest.asSharedFlow()

    data class Stats(val proxyUp: Long = 0, val proxyDown: Long = 0, val directUp: Long = 0, val directDown: Long = 0)
    private val _stats = MutableStateFlow(Stats())
    val stats = _stats.asStateFlow()

    private val _lastDelayMs = MutableStateFlow<Long?>(null)
    val lastDelayMs = _lastDelayMs.asStateFlow()

    private var statsJob: Job? = null

    fun toggleVpnConnection() {
        viewModelScope.launch {
            if (vpnState.value is VpnState.Connected || vpnState.value is VpnState.Connecting) {
                stopVpn()
                return@launch
            }

            if (selectedServerRepository.selectedServer.value == null) {
                XrayVpnService.vpnStateInternal.value = VpnState.Error(context.getString(R.string.home_no_server_selected))
                return@launch
            }

            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                _permissionRequest.emit(vpnIntent)
            } else {
                onPermissionResult(true)
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        viewModelScope.launch {
            if (isGranted) {
                startVpn()
            } else {
                XrayVpnService.vpnStateInternal.value = VpnState.Error(context.getString(R.string.home_permission_denied))
            }
        }
    }

    private suspend fun startVpn() {
        val selectedServer = selectedServerRepository.selectedServer.first()
        if (selectedServer == null) {
            XrayVpnService.vpnStateInternal.value = VpnState.Error(context.getString(R.string.home_no_server_selected))
            return
        }

        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_START
            putExtra(XrayVpnService.EXTRA_CONFIG, selectedServer.config)
        }
        ContextCompat.startForegroundService(context, intent)
        startStatsLoop()
    }

    private fun stopVpn() {
        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_STOP
        }
        context.startService(intent)
        stopStatsLoop()
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (true) {
                val up = XrayManager.queryStats("proxy", "uplink")
                val down = XrayManager.queryStats("proxy", "downlink")
                val dup = XrayManager.queryStats("direct", "uplink")
                val ddn = XrayManager.queryStats("direct", "downlink")
                _stats.value = Stats(proxyUp = up, proxyDown = down, directUp = dup, directDown = ddn)
                delay(1000)
            }
        }
    }

    private fun stopStatsLoop() { statsJob?.cancel(); statsJob = null }

    fun testConnectivity() {
        viewModelScope.launch {
            val selectedServer = selectedServerRepository.selectedServer.first() ?: return@launch
            val ms = XrayManager.measureDelay(selectedServer.config)
            _lastDelayMs.value = if (ms >= 0) ms else null
        }
    }
}