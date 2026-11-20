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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主界面（对应原 WPF MainWindow）。
 * 使用 miuix 组件重建，配色与控件完全遵循 miuix 设计 token。
 */
@Composable
fun App(
    settings: AppSettings,
    monitor: BatteryMonitor,
    updateInfo: UpdateInfo,
    onExitRequested: () -> Unit,
) {
    AppTheme {
        // 电量状态
        var battery by remember { mutableStateOf(BatteryInfo()) }

        // 鼠标配置
        val mouse = remember { MouseConfigService() }
        var dpiStages by remember { mutableStateOf(settings.dpiStages.copyOf()) }
        var activeDpiIndex by remember { mutableIntStateOf(settings.activeDpiIndex) }
        var rateIndex by remember { mutableIntStateOf(rateIndexFromHz(settings.rateHz)) }
        var mouseStatus by remember { mutableStateOf("") }
        var mouseBusy by remember { mutableStateOf(false) }

        // 设置开关状态
        var autoStart by remember { mutableStateOf(settings.autoStart) }
        var showPercent by remember { mutableStateOf(settings.showPercentInTray) }
        var checkUpdates by remember { mutableStateOf(settings.checkUpdates) }
        var refreshSeconds by remember { mutableStateOf(settings.refreshSeconds) }

        // 挂载监控回调
        LaunchedEffect(Unit) {
            monitor.onReadingChanged = { info -> battery = info }
        }

        val primary = MiuixTheme.colorScheme.primary

        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = "A7 电量助手",
                    subtitle = "v$APP_VERSION",
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // ---------- 电量卡片 ----------
                item {
                    SmallTitle("电量")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val percentColor = when {
                                !battery.success || battery.percent < 0 -> Color(0xFF9AA5B4)
                                battery.percent >= 50 -> primary
                                battery.percent >= 20 -> Color(0xFFFFC83D)
                                else -> Color(0xFFFF5C5C)
                            }
                            Text(
                                text = if (battery.success && battery.percent >= 0) "${battery.percent}" else "--",
                                fontSize = 72.sp,
                                color = percentColor,
                            )
                            Text(
                                text = "%",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(12.dp))

                            // 电量条
                            Box(
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                                    .background(
                                        MiuixTheme.colorScheme.surfaceContainerHigh,
                                        RoundedCornerShape(4.dp),
                                    ),
                            ) {
                                val frac = if (battery.success && battery.percent > 0) battery.percent / 100f else 0f
                                Box(
                                    modifier = Modifier.fillMaxWidth(frac).height(8.dp)
                                        .background(percentColor, RoundedCornerShape(4.dp)),
                                )
                            }
                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("状态", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    Text(
                                        if (battery.success) (if (battery.charging) "正在充电" else "放电中") else "离线/休眠",
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                Column {
                                    Text("连接", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    Text(
                                        when (battery.mode) {
                                            ConnectionMode.Usb -> "USB 有线"
                                            ConnectionMode.Radio24G -> "2.4G 无线"
                                            ConnectionMode.Bluetooth -> "蓝牙"
                                            else -> "未知"
                                        },
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                Column {
                                    Text("设备", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    Text(
                                        battery.deviceName.ifBlank { "--" },
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ---------- 刷新间隔 ----------
                item {
                    SmallTitle("刷新间隔")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        WindowDropdownPreference(
                            items = listOf("15 秒", "30 秒", "90 秒"),
                            selectedIndex = when (refreshSeconds) {
                                15 -> 0
                                90 -> 2
                                else -> 1
                            },
                            title = "间隔",
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
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ---------- 鼠标配置 ----------
                item {
                    SmallTitle("鼠标配置")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            ArrowPreference(
                                title = "读取当前配置",
                                summary = mouseStatus.ifBlank { "回读设备当前档位值" },
                                onClick = {
                                    if (mouseBusy) return@ArrowPreference
                                    mouseBusy = true
                                    mouseStatus = "正在读取…"
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val cfg = mouse.read()
                                        withContext(Dispatchers.Main) {
                                            mouseBusy = false
                                            if (cfg.success) {
                                                dpiStages = cfg.dpiStages.copyOf()
                                                if (cfg.activeDpiIndex in 0..5) activeDpiIndex = cfg.activeDpiIndex
                                                val hz = when {
                                                    cfg.usbRateIndex in MouseConfigService.RateHz.indices ->
                                                        MouseConfigService.RateHz[cfg.usbRateIndex]
                                                    cfg.wirelessRateIndex in MouseConfigService.RateHz.indices ->
                                                        MouseConfigService.RateHz[cfg.wirelessRateIndex]
                                                    else -> -1
                                                }
                                                if (hz > 0) rateIndex = rateIndexFromHz(hz)
                                                mouseStatus = "已读取 · 当前档位 ${activeDpiIndex + 1}"
                                            } else {
                                                mouseStatus = "读取失败：${cfg.error}"
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ---------- DPI 档位 ----------
                item {
                    SmallTitle("DPI 档位")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            for (i in 0 until 6) {
                                val active = i == activeDpiIndex
                                ArrowPreference(
                                    title = "档位 ${i + 1}",
                                    summary = "${dpiStages[i]} DPI${if (active) "（当前）" else ""}",
                                    onClick = { activeDpiIndex = i },
                                )
                            }
                            TextButton(
                                text = "写入 DPI（档位 ${activeDpiIndex + 1} = ${dpiStages[activeDpiIndex]}）",
                                onClick = {
                                    if (mouseBusy) return@TextButton
                                    mouseBusy = true
                                    mouseStatus = "正在写入 DPI…"
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val (ok, err) = mouse.setDpi(dpiStages, activeDpiIndex)
                                        withContext(Dispatchers.Main) {
                                            mouseBusy = false
                                            if (ok) {
                                                settings.dpiStages = dpiStages.copyOf()
                                                settings.activeDpiIndex = activeDpiIndex
                                                settings.save()
                                                mouseStatus = "DPI 已写入"
                                            } else {
                                                mouseStatus = "写入失败：$err"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ---------- 回报率 ----------
                item {
                    SmallTitle("回报率")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            var sliderValue by remember {
                                mutableFloatStateOf(rateIndex.toFloat())
                            }
                            Text(
                                text = "${MouseConfigService.RateHz[rateIndex]} Hz",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                valueRange = 0f..5f,
                                onValueChangeFinished = {
                                    rateIndex = sliderValue.toInt().coerceIn(0, 5)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                text = "写入回报率",
                                onClick = {
                                    if (mouseBusy) return@TextButton
                                    mouseBusy = true
                                    mouseStatus = "正在写入回报率…"
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val (ok, err) = mouse.setRate(rateIndex)
                                        withContext(Dispatchers.Main) {
                                            mouseBusy = false
                                            if (ok) {
                                                settings.rateHz = MouseConfigService.RateHz[rateIndex]
                                                settings.save()
                                                mouseStatus = "回报率已写入 · ${MouseConfigService.RateHz[rateIndex]} Hz"
                                            } else {
                                                mouseStatus = "写入失败：$err"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ---------- 设置 ----------
                item {
                    SmallTitle("设置")
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
                    Spacer(Modifier.height(12.dp))
                }

                // ---------- 更新提示 ----------
                if (updateInfo.available && updateInfo.latestVersion.isNotBlank()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = "有新版本 ${updateInfo.latestVersion}",
                                summary = "点击查看/下载",
                                onClick = {
                                    try {
                                        java.awt.Desktop.getDesktop().browse(java.net.URI(updateInfo.htmlUrl))
                                    } catch (_: Exception) {
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // ---------- 退出 ----------
                item {
                    TextButton(
                        text = "退出",
                        onClick = onExitRequested,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun rateIndexFromHz(hz: Int): Int {
    val idx = MouseConfigService.RateHz.indexOf(hz)
    return if (idx >= 0) idx else 2
}
