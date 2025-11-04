package com.retard.swag.ui.servers

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retard.swag.R
import com.retard.swag.domain.model.Server
import kotlinx.coroutines.flow.collectLatest

private val PingGood = Color(0xFF388E3C)
private val PingMedium = Color(0xFFF57C00)
private val PingBad = Color(0xFFD32F2F)

@Composable
fun ServersScreen(viewModel: ServersViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<Server?>(null) }
    val context = LocalContext.current

    LaunchedEffect(key1 = Unit) {
        viewModel.events.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.servers_import_config)
                )
            }
        }
    ) { paddingValues ->
        ServerList(
            modifier = Modifier.padding(paddingValues),
            servers = uiState.servers,
            selectedServerId = uiState.selectedServerId,
            pingingServerId = uiState.pingingServerId,
            onServerSelected = viewModel::selectServer,
            onPingClicked = viewModel::pingServer,
            onDeleteClicked = { serverToDelete = it }
        )
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onImportFromFile = { viewModel.requestImportFromFile(); showAddDialog = false },
            onPasteFromClipboard = { viewModel.addServerFromClipboard(it); showAddDialog = false }
        )
    }

    serverToDelete?.let {
        DeleteConfirmationDialog(
            serverName = it.name,
            onConfirm = { viewModel.deleteServer(it.id); serverToDelete = null },
            onDismiss = { serverToDelete = null }
        )
    }
}

@Composable
private fun ServerList(
    servers: List<Server>,
    selectedServerId: String?,
    pingingServerId: String?,
    onServerSelected: (String) -> Unit,
    onPingClicked: (String) -> Unit,
    onDeleteClicked: (Server) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(servers, key = { it.id }) { server ->
            ServerItem(
                server = server,
                isSelected = selectedServerId == server.id,
                isPinging = pingingServerId == server.id,
                onServerSelected = onServerSelected,
                onPingClicked = onPingClicked,
                onDeleteClicked = { onDeleteClicked(server) }
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
    onPingClicked: (String) -> Unit,
    onDeleteClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onServerSelected(server.id) }
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = countryCodeToEmojiFlag(server.countryCode), fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = server.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))

        server.ping?.let {
            Text(
                text = stringResource(R.string.servers_ping_ms, it),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    it < 100 -> PingGood
                    it < 250 -> PingMedium
                    else -> PingBad
                }
            )
        }

        PingButton(isPinging = isPinging, onClick = { onPingClicked(server.id) })
        DeleteButton(onClick = onDeleteClicked)
    }
}

@Composable
private fun PingButton(isPinging: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = !isPinging,
        modifier = Modifier.widthIn(min = 40.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        AnimatedContent(targetState = isPinging, label = "PingButtonAnimation") { pinging ->
            if (pinging) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
            } else {
                Text(stringResource(R.string.servers_ping), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(R.string.delete),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// --- Dialogs and other helpers ---

@Composable
fun DeleteConfirmationDialog(
    serverName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_server_confirmation_title)) },
        text = { Text(stringResource(R.string.delete_server_confirmation_text, serverName)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun AddServerDialog(
    onDismiss: () -> Unit,
    onImportFromFile: () -> Unit,
    onPasteFromClipboard: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_server_dialog_title)) },
        text = { Text(stringResource(R.string.add_server_dialog_text)) },
        confirmButton = {
            TextButton(onClick = onImportFromFile) {
                Text(stringResource(R.string.add_server_dialog_import_file))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                clipboardManager.getText()?.text?.let { onPasteFromClipboard(it) }
                onDismiss()
            }) {
                Text(stringResource(R.string.add_server_dialog_paste))
            }
        }
    )
}

private fun countryCodeToEmojiFlag(countryCode: String): String {
    return countryCode
        .uppercase()
        .map { char -> Character.toChars(char.code + 0x1F1A5) }
        .joinToString("")
}
