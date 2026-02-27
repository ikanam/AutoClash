package top.jarman.autoclash.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.jarman.autoclash.data.model.AutomationRule
import top.jarman.autoclash.data.model.RuleType
import top.jarman.autoclash.ui.viewmodel.RuleEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    groupName: String,
    onNavigateBack: () -> Unit,
    viewModel: RuleEditorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(groupName) {
        viewModel.loadGroup(groupName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(groupName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (uiState.currentProxy.isNotBlank()) {
                            Text(
                                "当前: ${uiState.currentProxy}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::showAddDialog,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加规则") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Group info header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "共 ${uiState.allProxies.size} 个可选节点，${uiState.rules.size} 条规则",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (uiState.rules.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "暂无自动化规则",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                            Text(
                                "点击下方按钮添加第一条规则",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                items(uiState.rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleRule(rule) },
                        onDelete = { viewModel.deleteRule(rule.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                }
            }
        }

        // Add Rule Dialog
        if (uiState.showAddDialog) {
            AddRuleDialog(
                allProxies = uiState.allProxies,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { type, condition, proxy ->
                    viewModel.addRule(type, condition, proxy)
                }
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: AutomationRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, color) = when (rule.ruleType) {
        RuleType.WLAN -> Icons.Default.Wifi to MaterialTheme.colorScheme.secondary
        RuleType.TIME -> Icons.Default.Schedule to MaterialTheme.colorScheme.tertiary
        RuleType.CARRIER -> Icons.Default.SimCard to MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (rule.enabled) 0.5f else 0.2f
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rule.ruleType.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (rule.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    conditionDescription(rule),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (rule.enabled) 1f else 0.5f
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "→ ${rule.targetProxy}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = color.copy(alpha = if (rule.enabled) 1f else 0.5f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

private fun conditionDescription(rule: AutomationRule): String {
    return when (rule.ruleType) {
        RuleType.WLAN -> "WiFi: ${rule.condition}"
        RuleType.TIME -> "时间: ${rule.condition}"
        RuleType.CARRIER -> "运营商: ${rule.condition}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    allProxies: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (RuleType, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(RuleType.WLAN) }
    var condition by remember { mutableStateOf("") }
    var selectedProxy by remember { mutableStateOf(allProxies.firstOrNull() ?: "") }
    var proxyDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("添加自动化规则", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Rule type selector
                Text(
                    "规则类型",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RuleType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                condition = ""
                            },
                            label = {
                                Text(
                                    when (type) {
                                        RuleType.WLAN -> "WiFi"
                                        RuleType.TIME -> "定时"
                                        RuleType.CARRIER -> "运营商"
                                    },
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (type) {
                                        RuleType.WLAN -> Icons.Default.Wifi
                                        RuleType.TIME -> Icons.Default.Schedule
                                        RuleType.CARRIER -> Icons.Default.SimCard
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                // Condition input
                OutlinedTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = {
                        Text(
                            when (selectedType) {
                                RuleType.WLAN -> "WiFi 名称 (SSID)"
                                RuleType.TIME -> "时间范围 (例: 08:00-18:00)"
                                RuleType.CARRIER -> "运营商 (中国电信/中国联通/中国移动)"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                RuleType.WLAN -> "MyWiFi"
                                RuleType.TIME -> "08:00-18:00"
                                RuleType.CARRIER -> "中国电信"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Target proxy dropdown
                ExposedDropdownMenuBox(
                    expanded = proxyDropdownExpanded,
                    onExpandedChange = { proxyDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProxy,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("目标节点") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = proxyDropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = proxyDropdownExpanded,
                        onDismissRequest = { proxyDropdownExpanded = false }
                    ) {
                        allProxies.forEach { proxy ->
                            DropdownMenuItem(
                                text = { Text(proxy) },
                                onClick = {
                                    selectedProxy = proxy
                                    proxyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedType, condition, selectedProxy) },
                enabled = condition.isNotBlank() && selectedProxy.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
