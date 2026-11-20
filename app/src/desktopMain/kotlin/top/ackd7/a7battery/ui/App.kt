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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
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
import top.ackd7.a7battery.ui.mochi.Check
import top.ackd7.a7battery.ui.mochi.MochiButtonRow
import top.ackd7.a7battery.ui.mochi.MochiDropdownRow
import top.ackd7.a7battery.ui.mochi.MochiRow
import top.ackd7.a7battery.ui.mochi.MochiSection
import top.ackd7.a7battery.ui.mochi.MochiSwitch
import top.ackd7.a7battery.ui.mochi.MochiTab
import top.ackd7.a7battery.ui.mochi.MochiTabBar
import top.ackd7.a7battery.ui.mochi.rememberMochiColors
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 内容区底部留白，避免被悬浮标签栏挡住。 */
private val BottomInset = 152.dp

private val Tabs = listOf(
    MochiTab("电量", MiuixIcons.Home),
    MochiTab("鼠标", MiuixIcons.Tune),
    MochiTab("设置", MiuixIcons.Settings),
)

@Composable
fun App(
    settings: AppSettings,
    monitor: BatteryMonitor,
    battery: BatteryInfo,
    updateInfo: UpdateInfo,
    titleBar: @Composable () -> Unit,
    onExitRequested: () -> Unit,
    initialPage: Int = 0,
) {
    AppTheme {
        var page by remember { mutableIntStateOf(initialPage.coerceIn(0, 2)) }

        LaunchedEffect(Unit) { top.ackd7.a7battery.appLog("UI 已组合（mochi 列表 + 悬浮标签栏）") }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { titleBar() },
            containerColor = MiuixTheme.colorScheme.background,
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (page) {
                    0 -> BatteryPage(settings, monitor, battery)
                    1 -> MousePage(settings)
                    else -> SettingsPage(settings, updateInfo, onExitRequested)
                }

                // 底部渐变遮罩：内容滚到悬浮栏下方时淡出，不露出半截控件
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to MiuixTheme.colorScheme.background.copy(alpha = 0.92f),
                                1f to MiuixTheme.colorScheme.background,
                            ),
                        ),
                )

                MochiTabBar(
                    tabs = Tabs,
                    selectedIndex = page,
                    onSelect = { page = it },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                )
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
    val c = rememberMochiColors()
    var refreshSeconds by remember { mutableIntStateOf(settings.refreshSeconds) }
    val ok = battery.success && battery.percent >= 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = BottomInset),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            // 信息卡：电量数字 + 进度条 + 三列状态
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.card, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val percentColor = when {
                        !ok -> c.secondary
                        battery.percent >= 50 -> c.accent
                        battery.percent >= 20 -> Color(0xFFFFC83D)
                        else -> c.danger
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (ok) "${battery.percent}" else "--",
                            fontSize = 76.sp,
                            color = percentColor,
                        )
                        Text(
                            text = "%",
                            fontSize = 18.sp,
                            color = c.secondary,
                            modifier = Modifier.padding(bottom = 14.dp, start = 2.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(c.fill, RoundedCornerShape(4.dp)),
                    ) {
                        val frac = if (ok && battery.percent > 0) battery.percent / 100f else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .height(8.dp)
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
                        InfoColumn(label = "设备", value = battery.deviceName.ifBlank { "--" })
                    }
                }
            }
        }

        item {
            MochiSection(
                header = "刷新",
                footer = if (!battery.success && battery.error.isNotBlank()) battery.error else null,
            ) {
                MochiDropdownRow(
                    title = "自动刷新间隔",
                    options = listOf("15 秒", "30 秒", "90 秒"),
                    selectedIndex = when (refreshSeconds) {
                        15 -> 0
                        90 -> 2
                        else -> 1
                    },
                    onSelect = { idx ->
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
                MochiButtonRow(text = "立即刷新", onClick = { monitor.refresh() })
            }
        }

        item {
            MochiSection(header = "详情") {
                MochiRow(
                    title = "上次更新",
                    value = if (battery.updatedAt > 0) formatTime(battery.updatedAt) else "--",
                )
                MochiRow(
                    title = "设备编号",
                    value = if (battery.vendorId.isNotBlank()) "${battery.vendorId}:${battery.productId}" else "--",
                    divider = false,
                )
            }
        }
    }
}

private fun formatTime(epochMs: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}.getOrDefault("--")

@Composable
private fun InfoColumn(label: String, value: String) {
    val c = rememberMochiColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = c.secondary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 14.sp, color = c.text)
    }
}

// ---------------- 鼠标页 ----------------

@Composable
private fun MousePage(settings: AppSettings) {
    val c = rememberMochiColors()
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
                    status = "已读取：当前档位 ${activeDpiIndex + 1} · ${MouseConfigService.RateHz[rateIndex]} Hz"
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
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = BottomInset),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            MochiSection(
                header = "设备配置",
                footer = status.ifBlank { null },
            ) {
                MochiRow(
                    title = "读取当前配置",
                    subtitle = "从鼠标回读 DPI 与回报率",
                    chevron = true,
                    divider = false,
                    onClick = { readConfig() },
                )
            }
        }

        item {
            MochiSection(
                header = "DPI 档位",
                footer = "点档位名设为当前档，数值可直接编辑",
            ) {
                for (i in 0 until 6) {
                    val active = i == activeDpiIndex
                    MochiRow(
                        title = "档位 ${i + 1}",
                        divider = i != 5,
                        accent = active,
                        onClick = { activeDpiIndex = i },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = dpiText[i],
                                    onValueChange = { input ->
                                        dpiText[i] = input.filter { it.isDigit() }.take(5)
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        color = if (active) c.accent else c.secondary,
                                        textAlign = TextAlign.End,
                                    ),
                                    cursorBrush = SolidColor(c.accent),
                                    modifier = Modifier.width(64.dp),
                                )
                                if (active) {
                                    Spacer(Modifier.width(6.dp))
                                    Check(color = c.accent)
                                }
                            }
                        },
                    )
                }
                MochiButtonRow(
                    text = "写入 DPI（档位 ${activeDpiIndex + 1}）",
                    onClick = { writeDpi() },
                )
            }
        }

        item {
            MochiSection(header = "回报率") {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    var sliderValue by remember { mutableFloatStateOf(rateIndex.toFloat()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("回报率", fontSize = 15.sp, color = c.text)
                        Text(
                            "${MouseConfigService.RateHz[rateIndex]} Hz",
                            fontSize = 15.sp,
                            color = c.accent,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..5f,
                        onValueChangeFinished = {
                            rateIndex = sliderValue.toInt().coerceIn(0, 5)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MochiButtonRow(text = "写入回报率", onClick = { writeRate() })
            }
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
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = BottomInset),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            MochiSection(
                header = "常规",
                footer = "开机自启写入当前用户启动项，无需管理员权限",
            ) {
                MochiRow(
                    title = "开机自启",
                    divider = true,
                    trailing = {
                        MochiSwitch(
                            checked = autoStart,
                            onCheckedChange = { checked ->
                                autoStart = checked
                                settings.autoStart = checked
                                settings.save()
                                Autostart.setEnabled(checked)
                            },
                        )
                    },
                )
                MochiRow(
                    title = "托盘显示电量百分比",
                    divider = true,
                    trailing = {
                        MochiSwitch(
                            checked = showPercent,
                            onCheckedChange = { checked ->
                                showPercent = checked
                                settings.showPercentInTray = checked
                                settings.save()
                            },
                        )
                    },
                )
                MochiRow(
                    title = "自动检查更新",
                    divider = false,
                    trailing = {
                        MochiSwitch(
                            checked = checkUpdates,
                            onCheckedChange = { checked ->
                                checkUpdates = checked
                                settings.checkUpdates = checked
                                settings.save()
                            },
                        )
                    },
                )
            }
        }

        item {
            MochiSection(header = "关于") {
                MochiRow(
                    title = "版本",
                    value = "v$APP_VERSION",
                    divider = updateInfo.available && updateInfo.latestVersion.isNotBlank(),
                )
                if (updateInfo.available && updateInfo.latestVersion.isNotBlank()) {
                    MochiRow(
                        title = "发现新版本 ${updateInfo.latestVersion}",
                        accent = true,
                        chevron = true,
                        divider = false,
                        onClick = {
                            runCatching {
                                java.awt.Desktop.getDesktop().browse(java.net.URI(updateInfo.htmlUrl))
                            }
                        },
                    )
                }
            }
        }

        item {
            MochiSection {
                MochiButtonRow(text = "退出程序", danger = true, onClick = onExitRequested)
            }
        }
    }
}

private fun rateIndexFromHz(hz: Int): Int {
    val idx = MouseConfigService.RateHz.indexOf(hz)
    return if (idx >= 0) idx else 2
}
