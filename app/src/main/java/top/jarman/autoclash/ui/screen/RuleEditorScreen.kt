package top.jarman.autoclash.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import top.jarman.autoclash.data.model.AutomationRule
import top.jarman.autoclash.data.model.RuleType
import top.jarman.autoclash.ui.viewmodel.RuleDialogResult
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

                // Reorderable rules list
                item {
                    var workingRules by remember(uiState.rules) { mutableStateOf(uiState.rules) }
                    var draggedIndex by remember { mutableIntStateOf(-1) }
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    val itemHeights = remember { mutableStateMapOf<Int, Float>() }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        workingRules.forEachIndexed { index, rule ->
                            key(rule.id) {
                                val isDragged = index == draggedIndex
                                val currentIndex by rememberUpdatedState(index)

                                Box(
                                    modifier = Modifier
                                        .zIndex(if (isDragged) 1f else 0f)
                                        .onGloballyPositioned { coordinates ->
                                            itemHeights[currentIndex] = coordinates.size.height.toFloat()
                                        }
                                        .graphicsLayer {
                                            translationY = if (isDragged) dragOffset else 0f
                                            scaleX = if (isDragged) 1.03f else 1f
                                            scaleY = if (isDragged) 1.03f else 1f
                                            shadowElevation = if (isDragged) 16f else 0f
                                            alpha = if (isDragged) 0.9f else 1f
                                        }
                                ) {
                                    RuleCard(
                                        rule = rule,
                                        index = index,
                                        onToggle = { viewModel.toggleRule(rule) },
                                        onDelete = { viewModel.deleteRule(rule.id) },
                                        onEdit = { viewModel.showEditDialog(rule) },
                                        dragHandleModifier = Modifier.pointerInput(Unit) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedIndex = currentIndex
                                                    dragOffset = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffset += dragAmount.y

                                                    // Check if we should swap with neighbor
                                                    val currentHeight = itemHeights[draggedIndex] ?: return@detectDragGesturesAfterLongPress
                                                    val threshold = currentHeight / 2

                                                    if (dragOffset > threshold && draggedIndex < workingRules.size - 1) {
                                                        val nextHeight = itemHeights[draggedIndex + 1] ?: currentHeight
                                                        workingRules = workingRules.toMutableList().apply {
                                                            val temp = this[draggedIndex]
                                                            this[draggedIndex] = this[draggedIndex + 1]
                                                            this[draggedIndex + 1] = temp
                                                        }
                                                        dragOffset -= nextHeight + 12f
                                                        draggedIndex++
                                                    } else if (dragOffset < -threshold && draggedIndex > 0) {
                                                        val prevHeight = itemHeights[draggedIndex - 1] ?: currentHeight
                                                        workingRules = workingRules.toMutableList().apply {
                                                            val temp = this[draggedIndex]
                                                            this[draggedIndex] = this[draggedIndex - 1]
                                                            this[draggedIndex - 1] = temp
                                                        }
                                                        dragOffset += prevHeight + 12f
                                                        draggedIndex--
                                                    }
                                                },
                                                onDragEnd = {
                                                    viewModel.reorderRules(workingRules)
                                                    draggedIndex = -1
                                                    dragOffset = 0f
                                                },
                                                onDragCancel = {
                                                    workingRules = uiState.rules
                                                    draggedIndex = -1
                                                    dragOffset = 0f
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                }
            }
        }

        // Add / Edit Rule Dialog
        if (uiState.showAddDialog) {
            RuleDialog(
                editingRule = uiState.editingRule,
                existingRules = uiState.rules,
                allProxies = uiState.allProxies,
                hasShownIspWarning = uiState.hasShownIspWarning,
                onIspWarningShown = viewModel::markIspWarningAsShown,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { result ->
                    if (uiState.editingRule != null) {
                        viewModel.updateRule(result)
                    } else {
                        viewModel.addRule(result)
                    }
                },
                onCheckConflict = { type -> viewModel.checkConflict(type, uiState.rules, uiState.editingRule?.id) }
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: AutomationRule,
    index: Int,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val (icon, color) = when (rule.ruleType) {
        RuleType.WLAN -> Icons.Default.Wifi to MaterialTheme.colorScheme.secondary
        RuleType.CARRIER -> Icons.Default.SimCard to MaterialTheme.colorScheme.primary
        RuleType.FALLBACK -> Icons.Default.Loop to MaterialTheme.colorScheme.tertiary
    }

    Card(
        onClick = onEdit,
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
                .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle
            Column(
                modifier = dragHandleModifier
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "拖拽排序",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${index + 1}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

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
                if (rule.ruleType == RuleType.FALLBACK) {
                    Text(
                        "每 ${rule.checkIntervalSecs}s · 失败重试 ${rule.retryCount} 次",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = color.copy(alpha = if (rule.enabled) 1f else 0.5f)
                    )
                } else {
                    Text(
                        "→ ${rule.targetProxy}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = color.copy(alpha = if (rule.enabled) 1f else 0.5f)
                    )
                }
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
    val prefix = if (rule.negate) "非 " else ""
    return when (rule.ruleType) {
        RuleType.WLAN -> "WiFi: ${prefix}${rule.condition}"
        RuleType.CARRIER -> "ISP: ${prefix}${rule.condition}"
        RuleType.FALLBACK -> "检测: ${rule.testUrl.ifBlank { "(未设置)" }}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(
    editingRule: AutomationRule?,
    existingRules: List<AutomationRule>,
    allProxies: List<String>,
    hasShownIspWarning: Boolean,
    onIspWarningShown: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (RuleDialogResult) -> Unit,
    onCheckConflict: (RuleType) -> String?
) {
    val isEditing = editingRule != null
    var selectedType by remember { mutableStateOf(editingRule?.ruleType ?: RuleType.WLAN) }
    var condition by remember { mutableStateOf(editingRule?.condition ?: "") }
    var selectedProxy by remember { mutableStateOf(editingRule?.targetProxy ?: allProxies.firstOrNull() ?: "") }
    var proxyDropdownExpanded by remember { mutableStateOf(false) }
    var negate by remember { mutableStateOf(editingRule?.negate ?: false) }
    var showIspWarning by remember { mutableStateOf(false) }

    // Fallback-specific fields
    var testUrl by remember { mutableStateOf(editingRule?.testUrl?.ifBlank { "http://www.gstatic.com/generate_204" } ?: "http://www.gstatic.com/generate_204") }
    var checkIntervalStr by remember { mutableStateOf((editingRule?.checkIntervalSecs ?: 60).toString()) }
    var retryCountStr by remember { mutableStateOf((editingRule?.retryCount ?: 1).toString()) }
    var retryIntervalStr by remember { mutableStateOf((editingRule?.retryIntervalSecs ?: 5).toString()) }

    // Live conflict check
    val conflictMessage = remember(selectedType) { onCheckConflict(selectedType) }

    if (showIspWarning) {
        AlertDialog(
            onDismissRequest = { showIspWarning = false },
            title = { Text("注意事项", fontWeight = FontWeight.Bold) },
            text = { Text("获取ISP依赖api.ip.sb接口，请确保该接口(api.ip.sb)走**直连**，否则可能导致ISP识别错误。") },
            confirmButton = {
                TextButton(onClick = {
                    showIspWarning = false
                    onIspWarningShown()
                }) {
                    Text("知道了")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "编辑自动化规则" else "添加自动化规则", fontWeight = FontWeight.Bold)
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
                                if (type == RuleType.CARRIER && !hasShownIspWarning && selectedType != RuleType.CARRIER) {
                                    showIspWarning = true
                                }
                                selectedType = type
                                condition = ""
                            },
                            label = {
                                Text(
                                    when (type) {
                                        RuleType.WLAN -> "WiFi"
                                        RuleType.CARRIER -> "ISP"
                                        RuleType.FALLBACK -> "Fallback"
                                    },
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (type) {
                                        RuleType.WLAN -> Icons.Default.Wifi
                                        RuleType.CARRIER -> Icons.Default.SimCard
                                        RuleType.FALLBACK -> Icons.Default.Loop
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                // Conflict warning
                if (conflictMessage != null) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                conflictMessage,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (selectedType == RuleType.FALLBACK) {
                    // Fallback: test URL
                    OutlinedTextField(
                        value = testUrl,
                        onValueChange = { testUrl = it },
                        label = { Text("检测地址 (test-url)") },
                        placeholder = { Text("https://www.google.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    // Check interval
                    OutlinedTextField(
                        value = checkIntervalStr,
                        onValueChange = { checkIntervalStr = it.filter { c -> c.isDigit() } },
                        label = { Text("检测间隔（秒，默认 60）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    // Retry count
                    OutlinedTextField(
                        value = retryCountStr,
                        onValueChange = { retryCountStr = it.filter { c -> c.isDigit() } },
                        label = { Text("重试次数（0 为不重试，默认 1）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    // Retry interval
                    OutlinedTextField(
                        value = retryIntervalStr,
                        onValueChange = { retryIntervalStr = it.filter { c -> c.isDigit() } },
                        label = { Text("重试间隔（秒，默认 5）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    // WLAN / CARRIER condition input
                    if (selectedType == RuleType.WLAN) {
                        OutlinedTextField(
                            value = condition,
                            onValueChange = { condition = it },
                            label = { Text("WiFi 名称 (SSID)") },
                            placeholder = { Text("MyWiFi") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        val ispOptions = listOf("中国电信", "中国联通", "中国移动")
                        var ispExpanded by remember { mutableStateOf(false) }
                        if (condition.isEmpty()) condition = ispOptions[0]

                        ExposedDropdownMenuBox(
                            expanded = ispExpanded,
                            onExpandedChange = { ispExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = condition,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("ISP") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ispExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = ispExpanded,
                                onDismissRequest = { ispExpanded = false }
                            ) {
                                ispOptions.forEach { isp ->
                                    DropdownMenuItem(
                                        text = { Text(isp) },
                                        onClick = {
                                            condition = isp
                                            ispExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Negate checkbox (WLAN/CARRIER only)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = negate,
                            onCheckedChange = { negate = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "取反匹配（不满足条件时触发）",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Target proxy dropdown (WLAN/CARRIER only)
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedType == RuleType.FALLBACK) {
                        onConfirm(
                            RuleDialogResult(
                                ruleType = RuleType.FALLBACK,
                                condition = "",
                                targetProxy = "",
                                negate = false,
                                testUrl = testUrl,
                                checkIntervalSecs = checkIntervalStr.toIntOrNull()?.coerceAtLeast(1) ?: 60,
                                retryCount = retryCountStr.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                                retryIntervalSecs = retryIntervalStr.toIntOrNull()?.coerceAtLeast(1) ?: 5
                            )
                        )
                    } else {
                        onConfirm(
                            RuleDialogResult(
                                ruleType = selectedType,
                                condition = condition,
                                targetProxy = selectedProxy,
                                negate = negate
                            )
                        )
                    }
                },
                enabled = conflictMessage == null && if (selectedType == RuleType.FALLBACK) {
                    testUrl.isNotBlank()
                } else {
                    condition.isNotBlank() && selectedProxy.isNotBlank()
                }
            ) {
                Text(if (isEditing) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
