package com.retard.swag.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.retard.swag.R

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SettingItem(
            title = stringResource(R.string.settings_auto_connect),
            checked = uiState.isAutoConnectEnabled,
            onCheckedChange = { viewModel.setAutoConnect(it) }
        )
        SettingItem(
            title = stringResource(R.string.settings_dark_theme),
            checked = uiState.isDarkThemeEnabled,
            onCheckedChange = { viewModel.setDarkTheme(it) }
        )

        LanguageSetting(
            current = uiState.language,
            onChange = viewModel::setLanguage
        )
    }
}

@Composable
fun SettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSetting(current: String, onChange: (String) -> Unit) {
    val options = listOf("ru" to "Русский", "en" to "English")
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.first == current }?.second ?: current

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text = stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            TextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        expanded = false
                        if (code != current) onChange(code)
                    })
                }
            }
        }
    }
}