package top.ackd7.a7battery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.ackd7.a7battery.APP_VERSION
import top.ackd7.a7battery.services.AppSettings
import top.ackd7.a7battery.services.Autostart
import top.ackd7.a7battery.services.BatteryInfo
import top.ackd7.a7battery.services.BatteryMonitor
import top.ackd7.a7battery.services.ConnectionMode
import top.ackd7.a7battery.services.MouseConfigService
import top.ackd7.a7battery.services.UpdateInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val PageTabs = listOf("电量", "鼠标", "设置")

/**
 * 主界面：三个页面（电量 / 鼠标 / 设置），用 TabRow 切换。
 */
@Composable
fun App(
    settings: AppSettings,
    monitor: BatteryMonitor,
    updateInfo: UpdateInfo,
    onExitRequested: () -> Unit,
) {
    AppTheme {
        var battery by remember { mutableStateOf(BatteryInfo()) }
        var page by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            top.ackd7.a7battery.appLog("UI 已组合（页面数=${PageTabs.size}）")
            monitor.onReadingChanged = { info -> battery = info }
        }

        Scaffold(
            topBar = {
                SmallTopAppBar(title = "A7 电量助手", subtitle = "v$APP_VERSION")
            },
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                TabRow(
                    tabs = PageTabs,
                    selectedTabIndex = page,
                    onTabSelected = { page = it },
                )
                when (page) {
                    0 -> BatteryPage(settings, monitor, battery)
                    1 -> MousePage(settings)
                    else -> SettingsPage(settings, updateInfo, onExitRequested)
                }
            }
        }
    }
}

// ---------------- 电量页 ----------------

@Composable
private fun BatteryPage(
    settings: AppSettings,
    monitor: BatteryMonitor,
    battery: BatteryInfo,
) {
    var refreshSeconds by remember { mutableIntStateOf(settings.refreshSeconds) }
    val primary = MiuixTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val percentColor = when {
                        !battery.success || battery.percent < 0 -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                        battery.percent >= 50 -> primary
                        battery.percent >= 20 -> Color(0xFFFFC83D)
                        else -> Color(0xFFFF5C5C)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (battery.success && battery.percent >= 0) "${battery.percent}" else "--",
                            fontSize = 76.sp,
                            color = percentColor,
                        )
                        Text(
                            text = "%",
                            fontSize = 18.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(bottom = 14.dp, start = 2.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)),
                    ) {
                        val frac = if (battery.success && battery.percent > 0) battery.percent / 100f else 0f
                        Box(
                            modifier = Modifier.fillMaxWidth(frac).height(8.dp)
                                .background(percentColor, RoundedCornerShape(4.dp)),
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        InfoColumn(
                            label = "状态",
                            value = when {
                                !battery.success -> "离线 / 休眠"
                                battery.charging -> "正在充电"
                                else -> "放电中"
                            },
                        )
                        InfoColumn(
                            label = "连接",
                            value = when (battery.mode) {
                                ConnectionMode.Usb -> "USB 有线"
                                ConnectionMode.Radio24G -> "2.4G 无线"
                                ConnectionMode.Bluetooth -> "蓝牙"
                                else -> "未知"
                            },
                        )
                        InfoColumn(
                            label = "设备",
                            value = battery.deviceName.ifBlank { "--" },
                        )
                    }

                    if (!battery.success && battery.error.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = battery.error,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            SmallTitle("刷新")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    WindowDropdownPreference(
                        items = listOf("15 秒", "30 秒", "90 秒"),
                        selectedIndex = when (refreshSeconds) {
                            15 -> 0
                            90 -> 2
                            else -> 1
                        },
                        title = "自动刷新间隔",
                        onSelectedIndexChange = { idx ->
                            refreshSeconds = when (idx) {
                                0 -> 15
                                2 -> 90
                                else -> 30
                            }
                            settings.refreshSeconds = refreshSeconds
                            settings.save()
                            monitor.setInterval(refreshSeconds)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        text = "立即刷新",
                        onClick = { monitor.refresh() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
    }
}

// ---------------- 鼠标页 ----------------

@Composable
private fun MousePage(settings: AppSettings) {
    val mouse = remember { MouseConfigService() }
    val dpiText = remember {
        mutableStateListOf(*settings.dpiStages.map { it.toString() }.toTypedArray())
    }
    var activeDpiIndex by remember { mutableIntStateOf(settings.activeDpiIndex) }
    var rateIndex by remember { mutableIntStateOf(rateIndexFromHz(settings.rateHz)) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun readConfig() {
        if (busy) return
        busy = true
        status = "正在读取…"
        CoroutineScope(Dispatchers.IO).launch {
            val cfg = mouse.read()
            withContext(Dispatchers.Main) {
                busy = false
                if (cfg.success) {
                    for (i in 0 until 6) dpiText[i] = cfg.dpiStages[i].toString()
                    if (cfg.activeDpiIndex in 0..5) activeDpiIndex = cfg.activeDpiIndex
                    val hz = when {
                        cfg.usbRateIndex in MouseConfigService.RateHz.indices ->
                            MouseConfigService.RateHz[cfg.usbRateIndex]
                        cfg.wirelessRateIndex in MouseConfigService.RateHz.indices ->
                            MouseConfigService.RateHz[cfg.wirelessRateIndex]
                        else -> -1
                    }
                    if (hz > 0) rateIndex = rateIndexFromHz(hz)
                    status = "已读取：当前档位 ${activeDpiIndex + 1}"
                } else {
                    status = "读取失败：${cfg.error}"
                }
            }
        }
    }

    fun writeDpi() {
        if (busy) return
        val values = IntArray(6)
        for (i in 0 until 6) {
            val v = dpiText[i].trim().toIntOrNull()
            if (v == null || v !in 100..42000) {
                status = "档位 ${i + 1} 的 DPI 需在 100–42000 之间"
                return
            }
            values[i] = v
        }
        busy = true
        status = "正在写入 DPI…"
        CoroutineScope(Dispatchers.IO).launch {
            val (ok, err) = mouse.setDpi(values, activeDpiIndex)
            withContext(Dispatchers.Main) {
                busy = false
                if (ok) {
                    settings.dpiStages = values
                    settings.activeDpiIndex = activeDpiIndex
                    settings.save()
                    status = "DPI 已写入：档位 ${activeDpiIndex + 1} = ${values[activeDpiIndex]}"
                } else {
                    status = "DPI 写入失败：$err"
                }
            }
        }
    }

    fun writeRate() {
        if (busy) return
        busy = true
        status = "正在写入回报率…"
        CoroutineScope(Dispatchers.IO).launch {
            val (ok, err) = mouse.setRate(rateIndex)
            withContext(Dispatchers.Main) {
                busy = false
                if (ok) {
                    settings.rateHz = MouseConfigService.RateHz[rateIndex]
                    settings.save()
                    status = "回报率已写入：${MouseConfigService.RateHz[rateIndex]} Hz"
                } else {
                    status = "回报率写入失败：$err"
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            SmallTitle("设备配置")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ArrowPreference(
                        title = "读取当前配置",
                        summary = "从鼠标回读 DPI 与回报率",
                        onClick = { readConfig() },
                    )
                    if (status.isNotBlank()) {
                        Text(
                            text = status,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            SmallTitle("DPI 档位")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    for (i in 0 until 6) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                text = if (i == activeDpiIndex) "档位 ${i + 1} · 当前" else "档位 ${i + 1}",
                                onClick = { activeDpiIndex = i },
                            )
                            Spacer(Modifier.width(12.dp))
                            TextField(
                                value = dpiText[i],
                                onValueChange = { input ->
                                    dpiText[i] = input.filter { it.isDigit() }.take(5)
                                },
                                modifier = Modifier.weight(1f),
                                label = "DPI",
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        text = "写入 DPI（档位 ${activeDpiIndex + 1}）",
                        onClick = { writeDpi() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            SmallTitle("回报率")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    var sliderValue by remember { mutableFloatStateOf(rateIndex.toFloat()) }
                    Text(
                        text = "${MouseConfigService.RateHz[rateIndex]} Hz",
                        fontSize = 20.sp,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..5f,
                        onValueChangeFinished = {
                            rateIndex = sliderValue.toInt().coerceIn(0, 5)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        text = "写入回报率",
                        onClick = { writeRate() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------- 设置页 ----------------

@Composable
private fun SettingsPage(
    settings: AppSettings,
    updateInfo: UpdateInfo,
    onExitRequested: () -> Unit,
) {
    var autoStart by remember { mutableStateOf(settings.autoStart) }
    var showPercent by remember { mutableStateOf(settings.showPercentInTray) }
    var checkUpdates by remember { mutableStateOf(settings.checkUpdates) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            SmallTitle("常规")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    SwitchPreference(
                        checked = autoStart,
                        onCheckedChange = { checked ->
                            autoStart = checked
                            settings.autoStart = checked
                            settings.save()
                            Autostart.setEnabled(checked)
                        },
                        title = "开机自启",
                        summary = "写入当前用户启动项，无需管理员权限",
                    )
                    SwitchPreference(
                        checked = showPercent,
                        onCheckedChange = { checked ->
                            showPercent = checked
                            settings.showPercentInTray = checked
                            settings.save()
                        },
                        title = "托盘显示电量百分比",
                    )
                    SwitchPreference(
                        checked = checkUpdates,
                        onCheckedChange = { checked ->
                            checkUpdates = checked
                            settings.checkUpdates = checked
                            settings.save()
                        },
                        title = "自动检查更新",
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            SmallTitle("关于")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ArrowPreference(
                        title = "版本",
                        summary = "v$APP_VERSION",
                    )
                    if (updateInfo.available && updateInfo.latestVersion.isNotBlank()) {
                        ArrowPreference(
                            title = "发现新版本 ${updateInfo.latestVersion}",
                            summary = "点击打开下载页面",
                            onClick = {
                                runCatching {
                                    java.awt.Desktop.getDesktop().browse(java.net.URI(updateInfo.htmlUrl))
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "退出程序",
                    onClick = onExitRequested,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun rateIndexFromHz(hz: Int): Int {
    val idx = MouseConfigService.RateHz.indexOf(hz)
    return if (idx >= 0) idx else 2
}
