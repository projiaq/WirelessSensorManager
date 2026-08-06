@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.wirelesssensormanager

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.wirelesssensormanager.core.common.*
import com.example.wirelesssensormanager.core.design.WirelessSensorTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private enum class Route(val path: String, val title: String) {
    START("start", "环境检查"), HOME("home", "首页"), SCAN("scan", "设备扫描"),
    GATEWAY("gateway", "接收器详情"), GATEWAY_CONFIG("gateway_config", "接收器参数"),
    SLOTS("slots", "绑定槽位"), SENSOR("sensor", "传感器详情"), SENSOR_CONFIG("sensor_config", "传感器参数"),
    BIND("bind", "绑定向导"), UNBIND("unbind", "解绑确认"), DIAGNOSTICS("diagnostics", "通信诊断"),
    HISTORY("history", "历史记录"), OTA("ota", "固件升级"), SETTINGS("settings", "设置与关于")
}

@Composable private fun App(vm: MainViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    WirelessSensorTheme(mineMode = state.mineMode) {
    Scaffold(
        topBar = { AppTopBar(nav, state) },
        bottomBar = { AppBottomBar(nav) },
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            NavHost(nav, startDestination = Route.START.path) {
                composable(Route.START.path) { StartScreen(nav, state) }
                composable(Route.HOME.path) { HomeScreen(nav, state) }
                composable(Route.SCAN.path) { ScanScreen(nav, state, vm) }
                composable(Route.GATEWAY.path) { GatewayScreen(nav, state, vm) }
                composable(Route.GATEWAY_CONFIG.path) { GatewayConfigScreen(state, vm) }
                composable(Route.SLOTS.path) { SlotsScreen(nav, state, vm) }
                composable(Route.SENSOR.path) { SensorScreen(nav, state, vm) }
                composable(Route.SENSOR_CONFIG.path) { SensorConfigScreen(state, vm) }
                composable(Route.BIND.path) { BindScreen(state, vm) }
                composable(Route.UNBIND.path) { UnbindScreen(state, vm) }
                composable(Route.DIAGNOSTICS.path) { DiagnosticsScreen(state, vm) }
                composable(Route.HISTORY.path) { HistoryScreen(state) }
                composable(Route.OTA.path) { OtaScreen(state, vm) }
                composable(Route.SETTINGS.path) { SettingsScreen(state, vm) }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            state.message?.let { MessageDialog(it, vm::clearMessage) }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppTopBar(nav: NavHostController, state: MainUiState) {
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val title = Route.entries.firstOrNull { it.path == route }?.title ?: "无线传感器配置工具"
    TopAppBar(title = { Column { Text(title); Text(state.connection.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) } }, navigationIcon = {
        if (route != Route.START.path && route != Route.HOME.path) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "返回") }
    })
}

@Composable private fun AppBottomBar(nav: NavHostController) {
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    if (route == Route.START.path) return
    NavigationBar {
        listOf(Route.HOME to Icons.Default.Home, Route.SCAN to Icons.Default.BluetoothSearching, Route.DIAGNOSTICS to Icons.Default.Troubleshoot, Route.SETTINGS to Icons.Default.Settings).forEach { (r, icon) ->
            NavigationBarItem(selected = route == r.path, onClick = { nav.navigate(r.path) { launchSingleTop = true; popUpTo(Route.HOME.path) { saveState = true } } }, icon = { Icon(icon, r.title) }, label = { Text(r.title) })
        }
    }
}

@Composable private fun StartScreen(nav: NavHostController, state: MainUiState) {
    val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val context = LocalContext.current
    var granted by remember { mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it.values.all { value -> value } }
    CenterColumn {
        Icon(Icons.Default.BluetoothConnected, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text("无线传感器配置工具", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Android 8.0 及以上 · 本地离线运行", color = MaterialTheme.colorScheme.outline)
        StatusRow("BLE 支持", if (state.connection != BleConnectionState.BLUETOOTH_UNAVAILABLE) "待检查/可用" else "不可用")
        StatusRow("蓝牙权限", if (granted) "已授权" else "需要授权")
        Button(onClick = { if (granted) nav.navigate(Route.HOME.path) { popUpTo(Route.START.path) { inclusive = true } } else launcher.launch(permissions) }) { Icon(Icons.Default.Security, null); Spacer(Modifier.width(8.dp)); Text(if (granted) "进入工具" else "授权蓝牙权限") }
    }
}

@Composable private fun HomeScreen(nav: NavHostController, state: MainUiState) = Page {
    Text("现场设备", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(state.selected?.let { "当前：${it.name ?: it.address}" } ?: "当前没有活动连接", color = MaterialTheme.colorScheme.outline)
    ActionTile(Icons.Default.BluetoothSearching, "扫描接收器和传感器", "按固件 Service UUID 识别") { nav.navigate(Route.SCAN.path) }
    ActionTile(Icons.Default.Router, "接收器与 8 槽绑定", "查看设备真实绑定表") { nav.navigate(Route.GATEWAY.path) }
    ActionTile(Icons.Default.Sensors, "传感器配置", "偏移、速率和功耗模式") { nav.navigate(Route.SENSOR.path) }
    ActionTile(Icons.Default.History, "操作记录", "绑定、解绑与参数修改") { nav.navigate(Route.HISTORY.path) }
    ActionTile(Icons.Default.SystemUpdate, "固件升级 OTA", "选择本地 .gbl 文件并按 DFU 流程传输") { nav.navigate(Route.OTA.path) }
}

@Composable private fun OtaScreen(state: MainUiState, vm: MainViewModel) = Page {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.startOta(context, it) } }
    Text("Silicon Labs GBL 固件升级", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text("升级会自动进入 DFU、重新扫描、传输并回连读取版本。数据块失败后立即终止，不会重发。", color = MaterialTheme.colorScheme.outline)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { pick.launch("application/octet-stream") }, enabled = state.connection == BleConnectionState.READY && state.maintainerMode) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("本地 .gbl") }
        OutlinedButton(onClick = vm::cancelOta) { Icon(Icons.Default.Cancel, null); Spacer(Modifier.width(6.dp)); Text("取消") }
    }
    OutlinedTextField(url, { url = it.trim() }, label = { Text("固件 URL（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { vm.startOtaUrl(url) }, enabled = url.startsWith("http") && state.maintainerMode) { Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(6.dp)); Text("下载并升级") }
    val progress by vm.ota.collectAsStateWithLifecycle()
    progress?.let { p -> LinearProgressIndicator({ p.fraction }, Modifier.fillMaxWidth()); Text("${p.state}  ${(p.fraction * 100).toInt()}%"); p.logs.takeLast(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }; TextButton(onClick = { clipboard.setText(AnnotatedString(p.logs.joinToString("\n"))) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("复制升级日志") } }
}

@Composable private fun ScanScreen(nav: NavHostController, state: MainUiState, vm: MainViewModel) = Page {
    val qr = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(vm::setScanQuery) }
    OutlinedTextField(state.scanQuery, vm::setScanQuery, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { qr.launch(ScanOptions().setPrompt("扫描设备二维码").setBeepEnabled(false)) }) { Icon(Icons.Default.QrCodeScanner, "扫描二维码") } }, label = { Text("搜索名称或 MAC") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("最低信号 ${state.rssiThreshold} dBm", style = MaterialTheme.typography.labelLarge)
    Slider(state.rssiThreshold.toFloat(), { vm.setRssiThreshold(it.toInt()) }, valueRange = -100f..-30f, steps = 13)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::scan, enabled = state.connection != BleConnectionState.SCANNING) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("开始扫描") }
        OutlinedButton(onClick = vm::stopScan) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("停止") }
        IconButton(onClick = vm::toggleScanSort) { Icon(if (state.sortDescending) Icons.Default.South else Icons.Default.North, "切换 RSSI 排序") }
    }
    val shown = state.devices.filter { it.rssi >= state.rssiThreshold }.filter { state.scanQuery.isBlank() || it.name.orEmpty().contains(state.scanQuery, true) || it.address.contains(state.scanQuery, true) }.let { if (state.sortDescending) it.sortedByDescending(DiscoveredDevice::rssi) else it.sortedBy(DiscoveredDevice::rssi) }
    val recent = listOfNotNull(state.lastReceiver, state.lastSensor).mapNotNull { saved -> val parts = saved.split('|'); shown.firstOrNull { it.address.equals(parts.firstOrNull(), true) } }
    if (recent.isNotEmpty()) { Text("最近设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); recent.distinctBy { it.address }.forEach { device -> DeviceRow(device) { vm.connect(device); nav.navigate(if (device.type == DeviceType.RECEIVER) Route.GATEWAY.path else Route.SENSOR.path) } } }
    shown.groupBy { if (it.type == DeviceType.RECEIVER) "接收器" else "传感器" }.forEach { (group, devices) ->
        Text("$group · ${devices.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        devices.forEach { device ->
        DeviceRow(device) { vm.connect(device); nav.navigate(if (device.type == DeviceType.RECEIVER) Route.GATEWAY.path else Route.SENSOR.path) }
        }
    }
    if (shown.isEmpty()) EmptyState("没有符合筛选条件的设备")
}

@Composable private fun GatewayScreen(nav: NavHostController, state: MainUiState, vm: MainViewModel) = Page {
    DeviceHeader(state.selected, DeviceType.RECEIVER)
    val s = state.receiverStatus
    InfoRow("接收器编号", s?.receiverId?.toString() ?: "--")
    InfoRow("槽位数量", s?.slotCount?.toString() ?: "8")
    InfoRow("固件 / 硬件", "${state.deviceVersion?.firmware ?: "--"} / ${state.deviceVersion?.hardware ?: "--"}")
    InfoRow("已绑定 / 在线", "${state.slots.count { it.occupied }} / ${state.slots.count { it.online }}")
    Button(onClick = vm::refreshReceiverAction) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("刷新设备状态") }
    Text("实时数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    state.slots.forEach { slot ->
        val sample = state.samples[slot.index]
        if (sample != null) ListItem(headlineContent = { Text("槽位 ${slot.index + 1} · ${if (slot.sensorType == 2) "倾角" else "压力"}") }, supportingContent = { Text(if (slot.sensorType == 2) "X ${(sample.xAngle / 10f)}°  Y ${(sample.yAngle / 10f)}°" else "压力 ${sample.pressure} · 原始值 ${sample.raw}") }, trailingContent = { Text("${sample.temperature}°C") })
    }
    ActionTile(Icons.Default.Tune, "接收器参数", "设置真实 Receiver ID") { nav.navigate(Route.GATEWAY_CONFIG.path) }
    ActionTile(Icons.Default.GridView, "1～8 号绑定槽位", "设备绑定表为最终依据") { nav.navigate(Route.SLOTS.path) }
    ActionTile(Icons.Default.Link, "绑定向导", "写入后自动回读核验") { nav.navigate(Route.BIND.path) }
    ActionTile(Icons.Default.LinkOff, "解绑传感器", "二次确认并回读核验") { nav.navigate(Route.UNBIND.path) }
    var clearConfirm by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { clearConfirm = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("清空全部绑定") }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text("确认清空全部绑定？") }, text = { Text("8 个槽位将全部清除，完成后以设备回读结果为准。") }, confirmButton = { TextButton(onClick = { clearConfirm = false; vm.clearAllBindings() }) { Text("确认") } }, dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("取消") } })
}

@Composable private fun GatewayConfigScreen(state: MainUiState, vm: MainViewModel) = Page {
    var text by remember(state.receiverStatus?.receiverId) { mutableStateOf(state.receiverStatus?.receiverId?.toString().orEmpty()) }
    OutlinedTextField(text, { text = it.filter(Char::isDigit).take(5) }, label = { Text("接收器编号（0～65535）") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { text.toIntOrNull()?.let(vm::setReceiverId) }, enabled = text.toIntOrNull() in 0..65535) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("写入并回读验证") }
}

@Composable private fun SlotsScreen(nav: NavHostController, state: MainUiState, vm: MainViewModel) = Page {
    state.slots.forEach { slot ->
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${slot.index + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(38.dp))
                Column(Modifier.weight(1f)) { Text(if (slot.occupied) SensorPositions.label(slot.sensorType, slot.sensorId) else "空闲槽位", fontWeight = FontWeight.Medium); Text(if (slot.occupied) slot.macText() else "可绑定", color = MaterialTheme.colorScheme.outline); state.slotInfos[slot.index]?.let { Text("${it.voltageMv / 1000f} V · RSSI ${it.rssi ?: 0} dBm · 错误 ${it.errors}", style = MaterialTheme.typography.bodySmall) } }
                Icon(if (slot.online) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (slot.online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
            }
        }
        if (slot.occupied) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { vm.setSlotRate(slot.index, false) }, label = { Text("慢速") })
            AssistChip(onClick = { vm.setSlotRate(slot.index, true) }, label = { Text("快速") })
            AssistChip(onClick = { vm.readSlotInfo(slot.index) }, label = { Text("传感器信息") })
        }
    }
    TextButton(onClick = { nav.navigate(Route.BIND.path) }) { Text("打开绑定向导") }
}

@Composable private fun SensorScreen(nav: NavHostController, state: MainUiState, vm: MainViewModel) = Page {
    DeviceHeader(state.selected, DeviceType.SENSOR)
    val info = state.sensorInfo
    InfoRow("传感器类型", when (info?.sensorType) { 1 -> "压力"; 2 -> "倾角"; else -> "--" })
    InfoRow("协议版本", info?.protocolVersion?.toString() ?: "--")
    InfoRow("固件 / 硬件", "${state.deviceVersion?.firmware ?: "--"} / ${state.deviceVersion?.hardware ?: "--"}")
    InfoRow("电压", info?.let { "%.2f V".format(it.voltageMv / 1000.0) } ?: "--")
    InfoRow("累计运行", info?.workSeconds?.let { "$it 秒" } ?: "--")
    InfoRow("传感器状态", if (info?.online == true) "在线" else "未确认")
    Button(onClick = vm::refreshSensorAction) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("重新读取") }
    state.samples[-1]?.let { sample ->
        Text("实时采样", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        InfoRow("采样状态", state.sampleState); InfoRow("压力", sample.pressure.toString()); InfoRow("倾角（已校零）", "X ${((sample.xAngle - state.zeroX) / 10f)}°  Y ${((sample.yAngle - state.zeroY) / 10f)}°"); InfoRow("原始倾角", "X ${sample.xRaw} / Y ${sample.yRaw} / Z ${sample.zRaw}"); InfoRow("电压", "${sample.voltageVolts ?: 0f} V")
        OutlinedButton(onClick = vm::markZero) { Icon(Icons.Default.CenterFocusStrong, null); Spacer(Modifier.width(6.dp)); Text("当前位置记为 0") }
    }
    ActionTile(Icons.Default.Tune, "传感器参数", "写入后等待确认并回读") { nav.navigate(Route.SENSOR_CONFIG.path) }
}

@Composable private fun SensorConfigScreen(state: MainUiState, vm: MainViewModel) = Page {
    var mac by remember { mutableStateOf("") }
    OutlinedTextField(mac, { mac = it.uppercase().take(17) }, label = { Text("传感器 MAC") }, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { vm.writeSensorMac(mac) }, enabled = mac.count { it == ':' } == 5) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("写入 MAC 并回读") }
    var offset by remember(state.sensorOffset) { mutableStateOf(state.sensorOffset?.toString().orEmpty()) }
    OutlinedTextField(offset, { offset = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("偏移（固件 int32 原始单位）") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { offset.toIntOrNull()?.let(vm::writeOffset) }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("写入偏移并回读") }
    Text("上报速率", style = MaterialTheme.typography.titleMedium)
    SingleChoiceSegmentedButtonRow { listOf(0 to "慢速", 1 to "快速").forEachIndexed { i, p -> SegmentedButton(selected = state.sensorRate == p.first, onClick = { vm.writeRate(p.first) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(p.second) } } }
    Text("功耗模式", style = MaterialTheme.typography.titleMedium)
    SingleChoiceSegmentedButtonRow { listOf(0 to "普通", 1 to "低功耗").forEachIndexed { i, p -> SegmentedButton(selected = state.powerMode == p.first, onClick = { vm.writePower(p.first) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(p.second) } } }
    Button(onClick = vm::enterEm4, enabled = state.maintainerMode, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.PowerSettingsNew, null); Spacer(Modifier.width(6.dp)); Text("进入 EM4 深度休眠") }
}

@Composable private fun BindScreen(state: MainUiState, vm: MainViewModel) = Page {
    Text("1. 连接传感器并读取真实类型与 MAC", fontWeight = FontWeight.Medium)
    var sensorId by remember { mutableStateOf("1") }
    var sensorType by remember(state.sensorInfo?.sensorType) { mutableIntStateOf(state.sensorInfo?.sensorType ?: 1) }
    var macSource by remember { mutableStateOf("") }
    val qr = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let { macSource = it } }
    var positionsOpen by remember { mutableStateOf(false) }
    SingleChoiceSegmentedButtonRow { listOf(1 to "压力", 2 to "倾角").forEachIndexed { i, item -> SegmentedButton(sensorType == item.first, { sensorType = item.first }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(item.second) } } }
    val positions = SensorPositions.all.filter { it.type == sensorType }
    Box { OutlinedButton(onClick = { positionsOpen = true }, enabled = positions.isNotEmpty()) { Text(positions.firstOrNull { it.sensorId.toString() == sensorId }?.label ?: "选择现场点位") }; DropdownMenu(positionsOpen, { positionsOpen = false }) { positions.forEach { position -> DropdownMenuItem(text = { Text(position.label) }, onClick = { sensorId = position.sensorId.toString(); positionsOpen = false }) } } }
    OutlinedTextField(sensorId, { sensorId = it.filter(Char::isDigit).take(3) }, label = { Text("测点编号（1～255）") }, supportingText = { Text("该编号不由传感器固件提供，需按现场定义选择") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { sensorId.toIntOrNull()?.let(vm::stageSensor) }, enabled = state.sensorInfo != null) { Icon(Icons.Default.Inventory2, null); Spacer(Modifier.width(6.dp)); Text("暂存传感器身份") }
    OutlinedTextField(macSource, { macSource = it }, label = { Text("MAC 或二维码内容") }, trailingIcon = { IconButton(onClick = { qr.launch(ScanOptions().setPrompt("扫描传感器二维码").setBeepEnabled(false)) }) { Icon(Icons.Default.QrCodeScanner, "扫描") } }, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { sensorId.toIntOrNull()?.let { vm.stageManual(sensorType, it, macSource) } }) { Icon(Icons.Default.AddLink, null); Spacer(Modifier.width(6.dp)); Text("从 MAC/二维码暂存") }
    Text("2. 连接接收器，选择空槽写入", fontWeight = FontWeight.Medium)
    var slot by remember { mutableIntStateOf(state.slots.indexOfFirst { !it.occupied }.coerceAtLeast(0)) }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { (0..7).forEach { i -> SegmentedButton(selected = slot == i, onClick = { slot = i }, enabled = !state.slots[i].occupied, shape = SegmentedButtonDefaults.itemShape(i, 8)) { Text("${i + 1}") } } }
    Button(onClick = { vm.bind(slot) }, enabled = state.stagedSensor != null && state.selected?.type == DeviceType.RECEIVER) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(6.dp)); Text("绑定并回读验证") }
    state.stagedSensor?.let { InfoRow("已暂存", "类型 ${it.type} / 编号 ${it.sensorId} / ${it.displayMac}") }
    HorizontalDivider()
    Text("8 槽绑定编辑器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    var draft by remember(state.slots) { mutableStateOf(state.slots.map { slot -> if (slot.occupied) SensorIdentity(slot.sensorType, slot.sensorId, slot.mac) else null }) }
    state.slots.indices.forEach { index ->
        val target = draft[index]
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
            Text(target?.let { SensorPositions.label(it.type, it.sensorId) } ?: "空槽", modifier = Modifier.weight(1f))
            IconButton(onClick = { state.stagedSensor?.let { staged -> draft = draft.toMutableList().also { it[index] = staged } } }, enabled = state.stagedSensor != null) { Icon(Icons.Default.ContentPaste, "填入暂存设备") }
            IconButton(onClick = { draft = draft.toMutableList().also { it[index] = null } }) { Icon(Icons.Default.Clear, "清空槽位") }
        }
    }
    Button(onClick = { vm.applyBindingDraft(draft) }, enabled = state.maintainerMode && state.selected?.type == DeviceType.RECEIVER) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(6.dp)); Text("核对差异并下发") }
}

@Composable private fun UnbindScreen(state: MainUiState, vm: MainViewModel) = Page {
    var selected by remember { mutableIntStateOf(-1) }
    var confirm by remember { mutableStateOf(false) }
    state.slots.filter { it.occupied }.forEach { slot -> FilterChip(selected = selected == slot.index, onClick = { selected = slot.index }, label = { Text("槽位 ${slot.index + 1} · ${slot.typeLabel()} ${slot.sensorId}") }, leadingIcon = { Icon(Icons.Default.Sensors, null) }) }
    Button(onClick = { confirm = true }, enabled = selected >= 0, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.LinkOff, null); Spacer(Modifier.width(6.dp)); Text("解绑所选传感器") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, icon = { Icon(Icons.Default.Warning, null) }, title = { Text("确认解绑槽位 ${selected + 1}？") }, text = { Text("解绑后接收器将断开该传感器。操作完成后 App 会重新读取设备绑定表。") }, confirmButton = { TextButton(onClick = { confirm = false; vm.unbind(selected) }) { Text("确认解绑") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}

@Composable private fun DiagnosticsScreen(state: MainUiState, vm: MainViewModel) = Page {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("最近 ${state.logs.size} 条通信记录"); IconButton(onClick = { vm.exportLogs(context) }) { Icon(Icons.Default.FileDownload, "导出") } }
    state.logs.take(100).forEach { l ->
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text("${l.direction}  ${l.commandName}  ${l.result}", fontWeight = FontWeight.Medium); if (l.hexData.isNotBlank()) Text(l.hexData, style = MaterialTheme.typography.bodySmall); l.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }; HorizontalDivider() }
    }
    if (state.logs.isEmpty()) EmptyState("暂无诊断日志")
}

@Composable private fun HistoryScreen(state: MainUiState) = Page {
    state.operations.forEach { o -> ListItem(headlineContent = { Text(o.action) }, supportingContent = { Text(o.detail) }, trailingContent = { Icon(if (o.success) Icons.Default.Check else Icons.Default.Error, null, tint = if (o.success) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error) }) }
    if (state.operations.isEmpty()) EmptyState("暂无操作记录")
}

@Composable private fun SettingsScreen(state: MainUiState, vm: MainViewModel) = Page {
    var diagnostics by remember { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    ListItem(headlineContent = { Text(if (state.maintainerMode) "维护模式" else "客户模式") }, supportingContent = { Text(if (state.maintainerMode) "允许设备配置、绑定、休眠和 OTA" else "仅开放扫描、查看和实时数据") }, trailingContent = { Switch(state.maintainerMode, { if (it) showPassword = true else vm.exitMaintainer() }) })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("井下高对比模式", fontWeight = FontWeight.Medium); Text("提升暗光现场的文字与状态辨识度", color = MaterialTheme.colorScheme.outline) }; Switch(state.mineMode, vm::setMineMode) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("记录诊断日志", fontWeight = FontWeight.Medium); Text("仅保存在本机，可手动导出", color = MaterialTheme.colorScheme.outline) }; Switch(diagnostics, { diagnostics = it; vm.setDiagnostics(it) }) }
    HorizontalDivider()
    InfoRow("应用版本", BuildConfig.VERSION_NAME)
    InfoRow("最低 Android", "8.0 / API 26")
    InfoRow("临时包名", BuildConfig.APPLICATION_ID)
    Text("核心功能完全离线运行，维护模式仅保存在本机。", color = MaterialTheme.colorScheme.outline)
    if (showPassword) AlertDialog(onDismissRequest = { showPassword = false }, title = { Text("进入维护模式") }, text = { OutlinedTextField(password, { password = it }, label = { Text("维护密码") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()) }, confirmButton = { TextButton(onClick = { showPassword = false; vm.enterMaintainer(password); password = "" }) { Text("确认") } }, dismissButton = { TextButton(onClick = { showPassword = false }) { Text("取消") } })
}

@Composable private fun Page(content: @Composable ColumnScope.() -> Unit) = LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
@Composable private fun CenterColumn(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically), content = content)
@Composable private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) = Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Default.ChevronRight, null) } }
@Composable private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) = Surface(onClick = onClick, shape = MaterialTheme.shapes.small, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (device.type == DeviceType.RECEIVER) Icons.Default.Router else Icons.Default.Sensors, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(device.name ?: "未命名设备", fontWeight = FontWeight.Medium); Text("${device.address} · RSSI ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }; Text("连接", color = MaterialTheme.colorScheme.primary) } }
@Composable private fun DeviceHeader(device: DiscoveredDevice?, expected: DeviceType) { Text(device?.name ?: if (expected == DeviceType.RECEIVER) "未连接接收器" else "未连接传感器", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(device?.address ?: "请先到扫描页连接设备", color = MaterialTheme.colorScheme.outline) }
@Composable private fun InfoRow(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.outline); Text(value, fontWeight = FontWeight.Medium) }
@Composable private fun StatusRow(label: String, value: String) = Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, color = MaterialTheme.colorScheme.primary) } }
@Composable private fun EmptyState(text: String) = Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.outline) }
@Composable private fun MessageDialog(message: String, close: () -> Unit) = AlertDialog(onDismissRequest = close, title = { Text(if (message.contains("成功") || message.contains("一致")) "操作结果" else "提示") }, text = { Text(message) }, confirmButton = { TextButton(onClick = close) { Text("确定") } })
private fun BindingSlot.typeLabel() = when (sensorType) { 1 -> "压力"; 2 -> "倾角"; else -> "未知类型 $sensorType" }
private fun BindingSlot.macText() = mac.joinToString(":") { "%02X".format(it) }
private fun BleConnectionState.label() = when (this) { BleConnectionState.BLUETOOTH_UNAVAILABLE -> "蓝牙不可用"; BleConnectionState.PERMISSION_REQUIRED -> "权限未授权"; BleConnectionState.IDLE -> "未开始扫描"; BleConnectionState.SCANNING -> "扫描中"; BleConnectionState.DEVICE_FOUND -> "已发现设备"; BleConnectionState.CONNECTING -> "正在连接"; BleConnectionState.CONNECTED -> "已连接"; BleConnectionState.DISCOVERING_SERVICES -> "正在发现服务"; BleConnectionState.NEGOTIATING_MTU -> "正在协商 MTU"; BleConnectionState.SUBSCRIBING_NOTIFICATIONS -> "正在订阅通知"; BleConnectionState.PROTOCOL_HANDSHAKE -> "正在协议握手"; BleConnectionState.READY -> "设备就绪"; BleConnectionState.EXECUTING -> "正在执行命令"; BleConnectionState.DISCONNECTING -> "正在断开"; BleConnectionState.DISCONNECTED -> "已断开"; BleConnectionState.TIMEOUT -> "超时"; BleConnectionState.ERROR -> "通信错误" }
