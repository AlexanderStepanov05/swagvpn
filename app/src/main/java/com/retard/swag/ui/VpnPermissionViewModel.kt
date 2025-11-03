package com.retard.swag.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

@HiltViewModel
class VpnPermissionViewModel @Inject constructor() : ViewModel() {

    private val _permissionRequest = MutableSharedFlow<Unit>()
    val permissionRequest = _permissionRequest.asSharedFlow()

    suspend fun requestPermission() {
        _permissionRequest.emit(Unit)
    }
}