package com.retard.swag.ui.settings

data class SettingsUiState(
    val isAutoConnectEnabled: Boolean = false,
    val isDarkThemeEnabled: Boolean = false,
    val language: String = "ru"
)