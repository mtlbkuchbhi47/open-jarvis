package com.openjarvis.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.openjarvis.accessibility.JarvisAccessibilityService
import com.openjarvis.agent.AgentCore
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.graphify.nodes.TaskNode
import com.openjarvis.ui.dashboard.DashboardScreen
import com.openjarvis.ui.theme.OpenJarvisTheme

class MainActivity : ComponentActivity() {
    private lateinit var graphifyRepo: GraphifyRepository
    private lateinit var agentCore: AgentCore
    private var refreshCounter by mutableIntStateOf(0)

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshCounter++ }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshCounter++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        graphifyRepo = GraphifyRepository(this)
        agentCore = AgentCore(this)

        setContent {
            OpenJarvisTheme {
                var recentTasks by remember { mutableStateOf<List<TaskNode>>(emptyList()) }
                LaunchedEffect(refreshCounter) { recentTasks = graphifyRepo.getRecentTasks(10) }

                val accessibilityEnabled = remember(refreshCounter) { PermissionManager.hasAccessibility(this) }
                val overlayEnabled = remember(refreshCounter) { PermissionManager.hasOverlay(this) }
                val microphoneEnabled = remember(refreshCounter) { PermissionManager.hasMicrophone(this) }
                val notificationsEnabled = remember(refreshCounter) { PermissionManager.hasNotifications(this) }
                val notificationAccessEnabled = remember(refreshCounter) { PermissionManager.hasNotificationAccess(this) }
                agentCore.state.collectAsState()

                if (!accessibilityEnabled || !overlayEnabled || !microphoneEnabled || !notificationsEnabled || !notificationAccessEnabled) {
                    PermissionScreen(
                        accessibilityEnabled = accessibilityEnabled,
                        overlayEnabled = overlayEnabled,
                        microphoneEnabled = microphoneEnabled,
                        notificationsEnabled = notificationsEnabled,
                        notificationAccessEnabled = notificationAccessEnabled,
                        onEnableAccessibility = { openSafely(PermissionManager.accessibilitySettings()) },
                        onEnableOverlay = { openSafely(PermissionManager.overlaySettings(this)) },
                        onEnableMicrophone = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                        onEnableNotifications = {
                            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else refreshCounter++
                        },
                        onEnableNotificationAccess = { openSafely(PermissionManager.notificationAccessSettings()) },
                        onOpenAppSettings = { openSafely(PermissionManager.appDetailsSettings(this)) }
                    )
                } else {
                    DashboardScreen(
                        onStartOverlay = { startOverlayService() },
                        onOpenSettings = { openSettings() },
                        graphifyRepo = graphifyRepo,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCounter++
    }

    private fun openSafely(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "This settings page is not available on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOverlayService() {
        if (!PermissionManager.hasOverlay(this)) {
            openSafely(PermissionManager.overlaySettings(this))
            return
        }
        runCatching { startService(Intent(this, OverlayService::class.java)) }
            .onSuccess { Toast.makeText(this, "Jarvis is active", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "Unable to start Jarvis overlay", Toast.LENGTH_SHORT).show() }
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))
}

@Composable
fun PermissionScreen(
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    microphoneEnabled: Boolean,
    notificationsEnabled: Boolean,
    notificationAccessEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onEnableMicrophone: () -> Unit,
    onEnableNotifications: () -> Unit,
    onEnableNotificationAccess: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val items = listOf(
        PermissionItem("Accessibility Service", "Tap, type, scroll and control other apps", accessibilityEnabled, onEnableAccessibility),
        PermissionItem("Display over other apps", "Show Jarvis's floating controls", overlayEnabled, onEnableOverlay),
        PermissionItem("Microphone", "Use voice commands when you choose voice input", microphoneEnabled, onEnableMicrophone),
        PermissionItem("Notifications", "Allow Jarvis to show task/status notifications", notificationsEnabled, onEnableNotifications),
        PermissionItem("Notification Access", "Read supported notifications for automation", notificationAccessEnabled, onEnableNotificationAccess)
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Set up Open Jarvis", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "Enable only the access you want. Android controls the final permission dialogs and settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        items.forEach { item ->
            PermissionCard(item)
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null)
            Spacer(Modifier.width(8.dp))
            Text("Open App Settings")
        }
        Spacer(Modifier.height(32.dp))
    }
}

private data class PermissionItem(
    val title: String,
    val description: String,
    val enabled: Boolean,
    val action: () -> Unit
)

@Composable
private fun PermissionCard(item: PermissionItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (item.enabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (item.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.description, style = MaterialTheme.typography.bodySmall)
                Text(if (item.enabled) "Enabled" else "Not enabled", style = MaterialTheme.typography.labelSmall)
            }
            if (!item.enabled) {
                TextButton(onClick = item.action) { Text("Enable") }
            }
        }
    }
}
