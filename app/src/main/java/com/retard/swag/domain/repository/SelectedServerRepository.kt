package com.retard.swag.domain.repository

import com.retard.swag.domain.model.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelectedServerRepository @Inject constructor() {
    private val _selectedServer = MutableStateFlow<Server?>(null)
    val selectedServer = _selectedServer.asStateFlow()

    fun selectServer(server: Server?) {
        _selectedServer.value = server
    }
}
