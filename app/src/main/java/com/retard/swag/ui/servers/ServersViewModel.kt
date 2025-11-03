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
    val isLoading: Boolean = true,
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

    init {
        loadSampleServers()
    }

    private fun loadSampleServers() {
        // In a real app, these would be loaded from a database.
        val sampleServers = listOf(
            Server(id = "1", name = "USA - New York", country = "USA", countryCode = "US", config = "{}"),
            Server(id = "2", name = "Germany - Frankfurt", country = "Germany", countryCode = "DE", config = "{}"),
            Server(id = "3", name = "Japan - Tokyo", country = "Japan", countryCode = "JP", config = "{}"),
            Server(id = "4", name = "UK - London", country = "UK", countryCode = "GB", config = "{}"),
            Server(id = "5", name = "Singapore", country = "Singapore", countryCode = "SG", config = "{}")
        )
        _uiState.update {
            it.copy(servers = sampleServers, isLoading = false)
        }
    }

    fun onConfigImported(uri: Uri) {
        // In a real app, you would read and parse the config file from the URI here.
        val newServer = Server(
            id = UUID.randomUUID().toString(),
            name = "Imported Server",
            country = "Unknown",
            countryCode = "XX", // Represents an unknown country
            config = "{\"v\": \"2\", \"ps\": \"Imported\", \"add\": \"127.0.0.1\", \"port\": \"1080\", \"id\": \"${UUID.randomUUID()}\", \"aid\": \"0\", \"net\": \"tcp\", \"type\": \"none\", \"host\": \"\", \"path\": \"\", \"tls\": \"\"}" // Example Vmess config
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
                        // A negative ping value from the core indicates an error.
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