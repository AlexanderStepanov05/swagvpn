package com.retard.swag.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.retard.swag.R
import com.retard.swag.service.XrayManager

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val vpnState by viewModel.vpnState.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val lastDelay by viewModel.lastDelayMs.collectAsState()
    val logs by XrayManager.recentLogs.collectAsState()

    val buttonColor by animateColorAsState(
        targetValue = when (vpnState) {
            is VpnState.Connected -> Color(0xFF388E3C)
            is VpnState.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(500), label = "ButtonColorAnimation"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = vpnState,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                    .togetherWith(fadeOut(animationSpec = tween(90)))
            },
            label = "VpnStateAnimation"
        ) { targetState ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { viewModel.toggleVpnConnection() },
                    modifier = Modifier.size(150.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                ) {
                    if (targetState is VpnState.Connecting) {
                        CircularProgressIndicator()
                    } else {
                        Icon(
                            imageVector = Icons.Default.Power,
                            contentDescription = stringResource(R.string.home_tap_to_connect),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = when (targetState) {
                        is VpnState.Disconnected -> stringResource(R.string.home_tap_to_connect)
                        is VpnState.Connecting -> stringResource(R.string.home_connecting)
                        is VpnState.Connected -> stringResource(R.string.home_connected)
                        is VpnState.Error -> stringResource(R.string.home_error, targetState.message)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        DiagnosticsCard(
            stats = stats,
            lastDelay = lastDelay,
            logs = logs,
            onPing = { viewModel.testConnectivity() },
            onClearLogs = { XrayManager.clearLogs() }
        )
    }
}

@Composable
private fun DiagnosticsCard(
    stats: HomeViewModel.Stats,
    lastDelay: Long?,
    logs: List<String>,
    onPing: () -> Unit,
    onClearLogs: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.home_connected), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Proxy ↑ ${stats.proxyUp} B / ↓ ${stats.proxyDown} B")
                Text("Direct ↑ ${stats.directUp} B / ↓ ${stats.directDown} B")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ping: ${lastDelay?.let { "$it ms" } ?: "—"}")
                Button(onClick = onPing) { Text("Ping") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Logs: ${logs.size}")
                Row {
                    IconButton(onClick = {
                        val text = logs.joinToString("\n")
                        clipboard.setText(buildAnnotatedString { append(text) })
                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs") }
                    Button(onClick = onClearLogs) { Text("Clear") }
                }
            }
            logs.takeLast(10).forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
