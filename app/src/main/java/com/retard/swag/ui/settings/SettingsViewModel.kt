package com.retard.swag.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retard.swag.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.isAutoConnectEnabled,
                settingsRepository.isDarkThemeEnabled
            ) { isAutoConnectEnabled, isDarkThemeEnabled ->
                SettingsUiState(
                    isAutoConnectEnabled = isAutoConnectEnabled,
                    isDarkThemeEnabled = isDarkThemeEnabled
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoConnect(enabled)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkTheme(enabled)
        }
    }
}