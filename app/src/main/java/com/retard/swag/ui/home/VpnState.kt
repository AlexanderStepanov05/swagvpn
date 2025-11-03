package com.retard.swag.ui.home

sealed class VpnState {
    object Disconnected : VpnState()
    object Connecting : VpnState()
    object Connected : VpnState()
    data class Error(val message: String) : VpnState()
}