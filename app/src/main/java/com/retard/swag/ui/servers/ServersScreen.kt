package com.retard.swag.ui.servers

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retard.swag.R
import com.retard.swag.domain.model.Server

// --- Helper Colors for Ping ---
private val PingGood = Color(0xFF388E3C)
private val PingMedium = Color(0xFFF57C00)
private val PingBad = Color(0xFFD32F2F)

/**
 * The main screen for displaying and managing the list of servers.
 */
@Composable
fun ServersScreen(viewModel: ServersViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::importConfig) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.servers_import_config)
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else {
            ServerList(
                modifier = Modifier.padding(paddingValues),
                servers = uiState.servers,
                selectedServerId = uiState.selectedServerId,
                pingingServerId = uiState.pingingServerId,
                onServerSelected = viewModel::selectServer,
                onPingClicked = viewModel::pingServer
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ServerList(
    modifier: Modifier = Modifier,
    servers: List<Server>,
    selectedServerId: String?,
    pingingServerId: String?,
    onServerSelected: (String) -> Unit,
    onPingClicked: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(servers, key = { it.id }) { server ->
            ServerItem(
                server = server,
                isSelected = selectedServerId == server.id,
                isPinging = pingingServerId == server.id,
                onServerSelected = onServerSelected,
                onPingClicked = onPingClicked
            )
        }
    }
}

@Composable
private fun ServerItem(
    server: Server,
    isSelected: Boolean,
    isPinging: Boolean,
    onServerSelected: (String) -> Unit,
    onPingClicked: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onServerSelected(server.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServerDetails(server = server, modifier = Modifier.weight(1f))
            ServerSelectionIndicator(isSelected = isSelected)
            PingButton(isPinging = isPinging, onClick = { onPingClicked(server.id) })
        }
    }
}

@Composable
private fun ServerDetails(server: Server, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = countryCodeToEmojiFlag(server.countryCode), fontSize = 32.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = server.name, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = server.country, style = MaterialTheme.typography.bodySmall)
                server.ping?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.servers_ping_ms, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            it < 75 -> PingGood
                            it < 150 -> PingMedium
                            else -> PingBad
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerSelectionIndicator(isSelected: Boolean) {
    if (isSelected) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.servers_selected),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun PingButton(isPinging: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !isPinging) {
        AnimatedContent(targetState = isPinging, label = "PingButtonAnimation") { pinging ->
            if (pinging) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.servers_ping))
            }
        }
    }
}

private fun countryCodeToEmojiFlag(countryCode: String): String {
    return countryCode
        .uppercase()
        .map { char -> Character.toChars(char.code + 0x1F1A5) }
        .joinToString("")
}