package com.neuroserve.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.neuroserve.R
import com.neuroserve.data.HardwareAccel
import com.neuroserve.utils.restartApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Dialog States
    var showServerNameDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var pendingLanguage by remember { mutableStateOf<String?>(null) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showHardwareDialog by remember { mutableStateOf(false) }
    var showContextWindowDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // -- General Section --
            SettingsGroup(title = stringResource(R.string.section_general)) {
                SettingsListItem(
                    icon = Icons.Outlined.Dns,
                    title = stringResource(R.string.pref_server_name),
                    subtitle = uiState.serverName,
                    onClick = { showServerNameDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                
                val langSystem = stringResource(R.string.lang_system)
                val langEn = stringResource(R.string.lang_en)
                val langZh = stringResource(R.string.lang_zh)
                val languageOptions = mapOf(
                    "system" to langSystem,
                    "en" to langEn,
                    "zh" to langZh
                )
                
                SettingsListItem(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.pref_language),
                    subtitle = languageOptions[uiState.language] ?: uiState.language,
                    onClick = { showLanguageDialog = true }
                )
            }

            // -- Network Section --
            SettingsGroup(title = stringResource(R.string.section_network)) {
                SettingsListItem(
                    icon = Icons.Outlined.Lan,
                    title = stringResource(R.string.pref_http_port),
                    subtitle = uiState.httpPort.toString(),
                    onClick = { showPortDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                SettingsSwitchItem(
                    icon = Icons.Outlined.Public,
                    title = stringResource(R.string.pref_lan_access),
                    subtitle = stringResource(R.string.pref_lan_access_desc),
                    checked = uiState.allowLanAccess,
                    onCheckedChange = viewModel::toggleLanAccess
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                SettingsSwitchItem(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.pref_api_auth),
                    subtitle = stringResource(R.string.pref_api_auth_desc),
                    checked = uiState.apiAuthEnabled,
                    onCheckedChange = viewModel::toggleApiAuth
                )
                
                AnimatedVisibility(
                    visible = uiState.apiAuthEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        SettingsListItem(
                            icon = Icons.Outlined.Key,
                            title = stringResource(R.string.pref_api_key),
                            subtitle = uiState.apiKey.take(8) + "...",
                            trailingContent = {
                                Row {
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(uiState.apiKey))
                                        Toast.makeText(context, context.getString(R.string.toast_api_key_copied), Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, stringResource(R.string.action_copy), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { viewModel.regenerateApiKey() }) {
                                        Icon(Icons.Default.Refresh, stringResource(R.string.action_regenerate), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            onClick = { }
                        )
                    }
                }
            }

            // -- Inference Section --
            SettingsGroup(title = stringResource(R.string.section_inference)) {
                SettingsListItem(
                    icon = Icons.Outlined.Memory,
                    title = stringResource(R.string.pref_hardware),
                    subtitle = uiState.hardwareAccel.name,
                    onClick = { showHardwareDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${stringResource(R.string.pref_threads)}: ${uiState.cpuThreads}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = uiState.cpuThreads.toFloat(),
                        onValueChange = { viewModel.setCpuThreads(it.toInt()) },
                        valueRange = 1f..viewModel.maxSupportedThreads.toFloat(),
                        steps = viewModel.maxSupportedThreads - 2
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                SettingsListItem(
                    icon = Icons.Outlined.TextFormat,
                    title = stringResource(R.string.pref_context),
                    subtitle = uiState.contextWindow.toString(),
                    onClick = { showContextWindowDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                SettingsListItem(
                    icon = Icons.Outlined.SmartToy,
                    title = stringResource(R.string.pref_model_path),
                    subtitle = uiState.defaultModelPath ?: stringResource(R.string.pref_model_none),
                    onClick = { }
                )
            }

            // -- System Section --
            SettingsGroup(title = stringResource(R.string.section_system)) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.PowerSettingsNew,
                    title = stringResource(R.string.pref_wakelock),
                    subtitle = stringResource(R.string.pref_wakelock_desc),
                    checked = uiState.keepCpuAwake,
                    onCheckedChange = viewModel::toggleKeepCpuAwake
                )
                
                val isIgnoringOptimizations = remember(context) { 
                    viewModel.isIgnoringBatteryOptimizations(context) 
                }
                if (!isIgnoringOptimizations) {
                     HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                     SettingsListItem(
                        icon = Icons.Outlined.BatteryAlert,
                        title = stringResource(R.string.pref_battery_opt),
                        subtitle = stringResource(R.string.pref_battery_opt_desc),
                        onClick = {
                            val intent = viewModel.requestIgnoreBatteryOptimization(context)
                            intent?.let { context.startActivity(it) }
                        }
                    )
                }
            }
            
            // -- About Section --
            SettingsGroup(title = stringResource(R.string.section_about)) {
                 SettingsListItem(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.pref_version),
                    subtitle = stringResource(R.string.app_version),
                    trailingContent = {}
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }

        // -- Dialogs --

        if (showServerNameDialog) {
            EditTextDialog(
                title = stringResource(R.string.pref_server_name),
                initialValue = uiState.serverName,
                onDismiss = { showServerNameDialog = false },
                onConfirm = { viewModel.setServerName(it) }
            )
        }
        
        if (showLanguageDialog) {
            val langSystem = stringResource(R.string.lang_system)
            val langEn = stringResource(R.string.lang_en)
            val langZh = stringResource(R.string.lang_zh)
            val languageOptions = mapOf(
                "system" to langSystem,
                "en" to langEn,
                "zh" to langZh
            )
             SingleChoiceDialog(
                title = stringResource(R.string.dialog_language_title),
                options = languageOptions.values.toList(),
                selectedOption = languageOptions[uiState.language] ?: langSystem,
                onDismiss = { showLanguageDialog = false },
                onConfirm = { name -> 
                    val code = languageOptions.entries.find { it.value == name }?.key ?: "system"
                    if (code != uiState.language) {
                        pendingLanguage = code
                        showLanguageDialog = false
                        showRestartDialog = true
                    } else {
                        showLanguageDialog = false
                    }
                }
            )
        }
        
        if (showRestartDialog && pendingLanguage != null) {
            AlertDialog(
                onDismissRequest = { 
                    pendingLanguage = null
                    showRestartDialog = false
                },
                title = { Text(stringResource(R.string.dialog_restart_title)) },
                text = { Text(stringResource(R.string.dialog_restart_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingLanguage?.let { lang ->
                                scope.launch(Dispatchers.IO) {
                                    viewModel.setLanguageSync(lang)
                                    context.restartApp()
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.btn_restart_now))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingLanguage = null
                            showRestartDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showPortDialog) {
            EditTextDialog(
                title = stringResource(R.string.pref_http_port),
                initialValue = uiState.httpPort.toString(),
                keyboardType = KeyboardType.Number,
                validator = { (it.toIntOrNull() ?: 0) in 1024..65535 },
                onDismiss = { showPortDialog = false },
                onConfirm = { it.toIntOrNull()?.let { port -> viewModel.setHttpPort(port) } }
            )
        }
        
        if (showContextWindowDialog) {
            EditTextDialog(
                title = stringResource(R.string.pref_context),
                initialValue = uiState.contextWindow.toString(),
                keyboardType = KeyboardType.Number,
                onDismiss = { showContextWindowDialog = false },
                onConfirm = { it.toIntOrNull()?.let { size -> viewModel.setContextWindow(size) } }
            )
        }

        if (showHardwareDialog) {
            SingleChoiceDialog(
                title = stringResource(R.string.pref_hardware),
                options = HardwareAccel.values().map { it.name },
                selectedOption = uiState.hardwareAccel.name,
                onDismiss = { showHardwareDialog = false },
                onConfirm = { name -> 
                    HardwareAccel.values().find { it.name == name }?.let { viewModel.setHardwareAccel(it) }
                }
            )
        }
    }
}

// --- Components ---

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsListItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    },
    onClick: (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    )
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    )
}

@Composable
fun EditTextDialog(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    validator: (String) -> Boolean = { true },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { 
                    text = it
                    isError = !validator(it)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                isError = isError,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { 
                     if (validator(text)) {
                         onConfirm(text)
                         onDismiss()
                     } else {
                         isError = true
                     }
                },
                enabled = !isError
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selected by remember(selectedOption) { mutableStateOf(selectedOption) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().fillMaxWidth()) {
                if (options.isEmpty()) {
                    Text(stringResource(R.string.dialog_no_options), style = MaterialTheme.typography.bodyMedium)
                } else {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (option == selected),
                                    onClick = { selected = option },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selected),
                                onClick = null 
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onConfirm(selected)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
             TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
