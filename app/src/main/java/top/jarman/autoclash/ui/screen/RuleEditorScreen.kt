package top.jarman.autoclash.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
                allProxies = uiState.allProxies,
                hasShownIspWarning = uiState.hasShownIspWarning,
                onIspWarningShown = viewModel::markIspWarningAsShown,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { type, condition, proxy, negate ->
                    if (uiState.editingRule != null) {
                        viewModel.updateRule(type, condition, proxy, negate)
                    } else {
                        viewModel.addRule(type, condition, proxy, negate)
                    }
                }
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
    val prefix = if (rule.negate) "非 " else ""
    return when (rule.ruleType) {
        RuleType.WLAN -> "WiFi: ${prefix}${rule.condition}"
        RuleType.CARRIER -> "ISP: ${prefix}${rule.condition}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(
    editingRule: AutomationRule?,
    allProxies: List<String>,
    hasShownIspWarning: Boolean,
    onIspWarningShown: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (RuleType, String, String, Boolean) -> Unit
) {
    val isEditing = editingRule != null
    var selectedType by remember { mutableStateOf(editingRule?.ruleType ?: RuleType.WLAN) }
    var condition by remember { mutableStateOf(editingRule?.condition ?: "") }
    var selectedProxy by remember { mutableStateOf(editingRule?.targetProxy ?: allProxies.firstOrNull() ?: "") }
    var proxyDropdownExpanded by remember { mutableStateOf(false) }
    var negate by remember { mutableStateOf(editingRule?.negate ?: false) }
    var showIspWarning by remember { mutableStateOf(false) }

    if (showIspWarning) {
        AlertDialog(
            onDismissRequest = { showIspWarning = false },
            title = { Text("注意事项", fontWeight = FontWeight.Bold) },
            text = { Text("获取ISP依赖ip-api接口，请确保该接口(ip-api.com)走**直连**，否则可能导致ISP识别错误。") },
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
                                    },
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (type) {
                                        RuleType.WLAN -> Icons.Default.Wifi
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
                    // ISP (CARRIER) — dropdown selector
                    val ispOptions = listOf("中国电信", "中国联通", "中国移动")
                    var ispExpanded by remember { mutableStateOf(false) }
                    // Pre-select first option if condition is empty
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

                // Negate checkbox
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
                onClick = { onConfirm(selectedType, condition, selectedProxy, negate) },
                enabled = condition.isNotBlank() && selectedProxy.isNotBlank()
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
