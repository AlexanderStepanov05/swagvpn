package com.retard.swag.ui.servers

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retard.swag.domain.model.Server
import com.retard.swag.domain.repository.SelectedServerRepository
import com.retard.swag.service.XrayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ServersUiState(
    val servers: List<Server> = emptyList(),
    val selectedServerId: String? = null,
    val isLoading: Boolean = false,
    val pingingServerId: String? = null
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val selectedServerRepository: SelectedServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServersUiState())
    val uiState: StateFlow<ServersUiState> = _uiState.asStateFlow()

    private val _importConfigRequest = MutableSharedFlow<Unit>()
    val importConfigRequest = _importConfigRequest.asSharedFlow()

    fun deleteServer(serverId: String) {
        _uiState.update { currentState ->
            val newServers = currentState.servers.filterNot { it.id == serverId }
            if (currentState.selectedServerId == serverId) {
                selectedServerRepository.selectServer(null)
                currentState.copy(servers = newServers, selectedServerId = null)
            } else {
                currentState.copy(servers = newServers)
            }
        }
    }

    fun addServerFromClipboard(uri: String) {
        // In a real app, you would parse the URI and create a server from it
        val newServer = Server(
            id = UUID.randomUUID().toString(),
            name = "Pasted Server",
            country = "Clipboard",
            countryCode = "XX",
            config = uri
        )
        _uiState.update {
            it.copy(servers = it.servers + newServer)
        }
    }

    fun onConfigImported(uri: Uri) {
        val newServer = Server(
            id = UUID.randomUUID().toString(),
            name = "Imported Server",
            country = "Unknown",
            countryCode = "XX",
            config = "{}"
        )
        _uiState.update {
            it.copy(servers = it.servers + newServer)
        }
    }

    fun importConfig() {
        viewModelScope.launch {
            _importConfigRequest.emit(Unit)
        }
    }

    fun selectServer(serverId: String) {
        _uiState.update {
            val currentSelectedId = it.selectedServerId
            if (currentSelectedId == serverId) {
                selectedServerRepository.selectServer(null)
                it.copy(selectedServerId = null)
            } else {
                val serverToSelect = it.servers.find { s -> s.id == serverId }
                selectedServerRepository.selectServer(serverToSelect)
                it.copy(selectedServerId = serverId)
            }
        }
    }

    fun pingServer(serverId: String) {
        viewModelScope.launch {
            val serverToPing = _uiState.value.servers.find { it.id == serverId } ?: return@launch
            _uiState.update { it.copy(pingingServerId = serverId) }

            val newPing = withContext(Dispatchers.IO) {
                XrayManager.measureDelay(serverToPing.config)
            }

            _uiState.update { state ->
                val updatedServers = state.servers.map { server ->
                    if (server.id == serverId) {
                        val validPing = if (newPing >= 0) newPing.toInt() else null
                        server.copy(ping = validPing)
                    } else {
                        server
                    }
                }
                state.copy(servers = updatedServers, pingingServerId = null)
            }
        }
    }
}