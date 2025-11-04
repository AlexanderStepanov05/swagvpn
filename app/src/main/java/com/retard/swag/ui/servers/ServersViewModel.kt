package com.retard.swag.ui.servers

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retard.swag.R
import com.retard.swag.domain.model.Server
import com.retard.swag.domain.repository.SelectedServerRepository
import com.retard.swag.service.ConfigParser
import com.retard.swag.service.XrayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

data class ServersUiState(
    val servers: List<Server> = emptyList(),
    val selectedServerId: String? = null,
    val pingingServerId: String? = null
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selectedServerRepository: SelectedServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServersUiState())
    val uiState: StateFlow<ServersUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _importFileRequest = MutableSharedFlow<Unit>()
    val importFileRequest = _importFileRequest.asSharedFlow()

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

    fun addServerFromClipboard(clipboardContent: String) {
        viewModelScope.launch {
            val server = ConfigParser.parse(clipboardContent)
            if (server != null) {
                addNewServer(server)
                _events.emit(context.getString(R.string.server_added_toast))
            } else {
                _events.emit(context.getString(R.string.invalid_config_toast))
            }
        }
    }

    fun onConfigFileImported(uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    BufferedReader(InputStreamReader(it)).readText()
                }
                if (content == null) {
                    _events.emit("Failed to read file content")
                    return@launch
                }
                val server = ConfigParser.parse(content)
                if (server != null) {
                    addNewServer(server)
                    _events.emit(context.getString(R.string.server_added_toast))
                } else {
                    _events.emit(context.getString(R.string.invalid_config_toast))
                }
            } catch (e: Exception) {
                _events.emit("Error: ${e.message}")
            }
        }
    }

    fun requestImportFromFile() {
        viewModelScope.launch {
            _importFileRequest.emit(Unit)
        }
    }

    private fun addNewServer(server: Server) {
        _uiState.update {
            it.copy(servers = it.servers + server, selectedServerId = server.id)
        }
        selectedServerRepository.selectServer(server)
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