package com.neuroserve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.neuroserve.R
import com.neuroserve.service.InferenceService

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val inferenceSpeed by viewModel.inferenceSpeed.collectAsState()
    val lanIp by viewModel.lanIp.collectAsState()
    val uptime by viewModel.uptime.collectAsState()
    val activeModelName by viewModel.activeModelName.collectAsState()
    
    // Real system metrics from SystemMonitor
    val appMemoryInfo by viewModel.systemMonitor.appMemoryInfo.collectAsState()
    val batteryTemperature by viewModel.systemMonitor.batteryTemperature.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.updateLanIp(context)
        viewModel.initSystemMonitor(context)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanupSystemMonitor(context)
        }
    }

    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
            // Removed verticalScroll to make layout fixed
    ) {
        // -- Header --
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                color = onSurface,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.dashboard_settings),
                    tint = onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // -- Status Card --
        Card(
            colors = CardDefaults.cardColors(containerColor = surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Top Row: Online Status & IP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Online Badge
                    Surface(
                        color = Color(0xFFDCFCE7), // green-100
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)) // green-200
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF16A34A), CircleShape) // green-600
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isServiceRunning) stringResource(R.string.status_online) else stringResource(R.string.status_offline),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isServiceRunning) Color(0xFF166534) else Color.Gray
                            )
                        }
                    }

                    // IP Badge
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3F4F6)) // gray-100
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isServiceRunning) lanIp else "N/A",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF374151) // gray-700
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Grid: Uptime & Active Clients
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusItem(
                        label = stringResource(R.string.label_uptime),
                        value = uptime,
                        modifier = Modifier.weight(1f)
                    )
                    StatusItem(
                        label = stringResource(R.string.label_active_clients),
                        value = "0",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Flexible spacer to push Power Button to center-ish
        Spacer(modifier = Modifier.weight(1f))

        // -- Power Button (Large FAB / Circular) --
        Spacer(modifier = Modifier.weight(0.5f))
        
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Button
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                
                val buttonColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isServiceRunning) 
                        Color(0xFF4CAF50) // Green 500
                    else 
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    label = "ButtonColor"
                )
                
                val iconColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isServiceRunning) 
                        Color.White
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "IconColor"
                )
                
                // Shadow / Glow
                val shadowElevation by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isServiceRunning) 8.dp else 4.dp,
                    label = "ShadowElevation"
                )

                Surface(
                    onClick = {
                         haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                         if (isServiceRunning) {
                            InferenceService.stop(context)
                            viewModel.toggleService(false)
                        } else {
                            InferenceService.start(context)
                            viewModel.toggleService(true)
                        }
                    },
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(elevation = shadowElevation, shape = CircleShape, spotColor = buttonColor),
                    shape = CircleShape,
                    color = buttonColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = stringResource(R.string.desc_power_button),
                            tint = iconColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Status Label below (Redesigned)
                AnimatedContent(
                    targetState = isServiceRunning,
                    label = "StatusLabel"
                ) { running ->
                    Surface(
                        color = if (running) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                             Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (running) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant, 
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (running) stringResource(R.string.server_on) else stringResource(R.string.server_off),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (running) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Flexible spacer to push System Status to bottom
        Spacer(modifier = Modifier.weight(1f))

        // -- System Status Grid --
        Text(
            text = stringResource(R.string.label_system_status),
            style = MaterialTheme.typography.titleSmall,
            color = onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                label = stringResource(R.string.label_speed),
                value = if (inferenceSpeed.isNotEmpty()) inferenceSpeed else "--",
                unit = stringResource(R.string.unit_tok_s),
                color = Color(0xFF4F46E5),
                progress = 0f,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = stringResource(R.string.label_app_ram),
                value = appMemoryInfo.displayText,
                unit = "",
                color = Color(0xFF0D9488),
                progress = 0f,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = stringResource(R.string.label_temp),
                value = "%.0f".format(batteryTemperature),
                unit = stringResource(R.string.unit_celsius),
                color = Color(0xFFF97316),
                progress = ((batteryTemperature - 20f) / 40f).coerceIn(0f, 1f),
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp)) // Fixed bottom padding for Nav Bar clearance
    }
}

@Composable
fun StatusItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String,
    color: Color,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3F4F6)), // Light border
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp)
            ) {
                 CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), // Background track
                    strokeWidth = 3.dp,
                )
                
                CircularProgressIndicator(
                    progress = { if (progress <= 0f) 0.01f else progress }, // Minimal visibility
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                         color = MaterialTheme.colorScheme.onSurface
                    )
                    if (unit.isNotEmpty()) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
        }
    }
}
