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

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selectedServerRepository: SelectedServerRepository
) : ViewModel() {

    val vpnState: StateFlow<VpnState> = XrayVpnService.vpnState
        .stateIn(viewModelScope, SharingStarted.Eagerly, VpnState.Disconnected)

    private val _permissionRequest = MutableSharedFlow<Intent>()
    val permissionRequest = _permissionRequest.asSharedFlow()

    fun toggleVpnConnection() {
        viewModelScope.launch {
            if (vpnState.value is VpnState.Connected) {
                stopVpn()
            } else {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    _permissionRequest.emit(vpnIntent)
                } else {
                    onPermissionResult(true)
                }
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
            XrayVpnService.vpnStateInternal.value = VpnState.Error("No server selected")
            return
        }

        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_START
            putExtra(XrayVpnService.EXTRA_CONFIG, selectedServer.config)
        }
        context.startService(intent)
    }

    private fun stopVpn() {
        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}