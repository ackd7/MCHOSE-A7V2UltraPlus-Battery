package top.ackd7.a7battery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
private val BottomInset = 126.dp

/** 宽屏阈值：超过就左右两列排布。 */
private val WideThreshold = 700.dp

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
        // 鼠标配置状态放在这里：切页不丢，启动即自动读取
        val mouseState = rememberMouseConfigUiState(settings)

        LaunchedEffect(Unit) {
            top.ackd7.a7battery.appLog("UI 已组合（横向自适应布局）")
            mouseState.refresh()
        }

        LaunchedEffect(page) { top.ackd7.a7battery.appLog("切换到页面 $page") }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { titleBar() },
            containerColor = MiuixTheme.colorScheme.background,
        ) { innerPadding ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val wide = maxWidth >= WideThreshold

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(modifier = Modifier.widthIn(max = 1040.dp).fillMaxHeight()) {
                        // 页面切换：横向滑动 + 弹簧回弹 + 轻微景深缩放（mochi 的 tab 切换手感）
                        AnimatedContent(
                            targetState = page,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                val forward = targetState > initialState
                                val dir = if (forward) 1 else -1
                                // 用固定时长的缓动而不是弹簧：
                                // 弹簧在跨页切换时收敛过快，看起来像"没动画"；这里末端带一点过冲，保留弹性手感
                                val slideIn = tween<IntOffset>(
                                    durationMillis = 300,
                                    easing = CubicBezierEasing(0.22f, 1.04f, 0.36f, 1f),
                                )
                                val slideOut = tween<IntOffset>(
                                    durationMillis = 300,
                                    easing = CubicBezierEasing(0.3f, 0.9f, 0.4f, 1f),
                                )
                                (
                                    slideInHorizontally(slideIn) { full -> dir * full }
                                        .togetherWith(
                                            slideOutHorizontally(slideOut) { full -> -dir * full / 3 },
                                        )
                                    )
                            },
                            label = "pageSwitch",
                        ) { p ->
                            // 每页铺不透明底色：滑动时新页盖住旧页，不会互相透出文字
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MiuixTheme.colorScheme.background),
                            ) {
                                when (p) {
                                    0 -> BatteryPage(settings, monitor, battery, wide)
                                    1 -> MousePage(settings, mouseState, wide)
                                    else -> SettingsPage(settings, updateInfo, onExitRequested, wide)
                                }
                            }
                        }
                    }
                }

                // 底部渐变遮罩：内容滚到悬浮栏下方时淡出
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
    wide: Boolean,
) {
    if (wide) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(modifier = Modifier.weight(1.05f).fillMaxHeight()) {
                BatteryCard(
                    battery = battery,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    stretch = true,
                )
                Spacer(Modifier.height(BottomInset))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                RefreshSection(settings, monitor, battery)
                Spacer(Modifier.height(22.dp))
                DetailsSection(battery)
                Spacer(Modifier.height(BottomInset))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = BottomInset),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item { BatteryCard(battery) }
            item { RefreshSection(settings, monitor, battery) }
            item { DetailsSection(battery) }
        }
    }
}

@Composable
private fun BatteryCard(
    battery: BatteryInfo,
    modifier: Modifier = Modifier.fillMaxWidth(),
    stretch: Boolean = false,
) {
    val c = rememberMochiColors()
    val ok = battery.success && battery.percent >= 0

    Box(
        modifier = modifier
            .background(c.card, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Column(
            modifier = if (stretch) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth(),
            verticalArrangement = if (stretch) Arrangement.Center else Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val baseColor = when {
                !ok -> c.secondary
                battery.percent >= 50 -> c.accent
                battery.percent >= 20 -> Color(0xFFFFC83D)
                else -> c.danger
            }
            // 休眠时显示的是上次读数，弱化一点以示区分
            val percentColor = if (battery.stale) baseColor.copy(alpha = 0.55f) else baseColor
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (ok) "${battery.percent}" else "--",
                    fontSize = 72.sp,
                    color = percentColor,
                )
                Text(
                    text = "%",
                    fontSize = 18.sp,
                    color = c.secondary,
                    modifier = Modifier.padding(bottom = 12.dp, start = 2.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
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

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                InfoColumn(
                    label = "状态",
                    value = when {
                        battery.stale || !battery.success -> "休眠中"
                        battery.charging -> "充电中"
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

@Composable
private fun RefreshSection(
    settings: AppSettings,
    monitor: BatteryMonitor,
    battery: BatteryInfo,
) {
    var refreshSeconds by remember { mutableIntStateOf(settings.refreshSeconds) }
    MochiSection(
        header = "刷新",
        footer = when {
            battery.stale ->
                "设备休眠中，以上为 ${formatTime(battery.updatedAt)} 的数据；轻动鼠标即可刷新。"
            !battery.success && battery.error.isNotBlank() -> battery.error
            else -> null
        },
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

@Composable
private fun DetailsSection(battery: BatteryInfo) {
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

@Composable
private fun InfoColumn(label: String, value: String) {
    val c = rememberMochiColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = c.secondary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 14.sp, color = c.text)
    }
}

private fun formatTime(epochMs: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}.getOrDefault("--")

// ---------------- 鼠标页 ----------------

/** DPI 预设档位（自定义时可手填任意 100–42000 的值）。 */
private val DpiPresets = listOf(400, 800, 1200, 1600, 2400, 3200, 6400, 12000, 26000, 32000, 42000)

/**
 * 鼠标配置的 UI 状态（提升到 App 层，切页/重开不丢失）。
 *
 * 说明：A7 V2 Ultra+ 的 `0x12 0x67` 报告里不含回报率字段
 * （实测写入 2000Hz 后回读字节完全不变，低 4 位恒为 0，直接解析永远得到 125Hz），
 * 所以回报率只显示本地记录的值，不回读设备。
 */
@Stable
class MouseConfigUiState(
    private val settings: AppSettings,
    private val mouse: MouseConfigService,
    private val scope: CoroutineScope,
) {
    val dpiText = mutableStateListOf(*settings.dpiStages.map { it.toString() }.toTypedArray())
    var activeDpiIndex by mutableIntStateOf(settings.activeDpiIndex)
    var rateIndex by mutableIntStateOf(rateIndexFromHz(settings.rateHz))
    var status by mutableStateOf("")

    /** 「自定义 DPI」是否处于展开状态。 */
    var customDpi by mutableStateOf(false)

    var busy by mutableStateOf(false)
        private set

    var loaded by mutableStateOf(false)
        private set

    fun stageValue(index: Int): Int = dpiText[index].trim().toIntOrNull() ?: 0

    fun setStageValue(index: Int, value: Int) {
        dpiText[index] = value.coerceIn(100, 42000).toString()
    }

    fun refresh() {
        if (busy) return
        busy = true
        status = "正在读取…"
        scope.launch {
            val cfg = withContext(Dispatchers.IO) { mouse.read() }
            busy = false
            if (cfg.success) {
                for (i in 0 until 6) dpiText[i] = cfg.dpiStages[i].toString()
                if (cfg.activeDpiIndex in 0..5) activeDpiIndex = cfg.activeDpiIndex
                settings.dpiStages = IntArray(6) { cfg.dpiStages[it] }
                settings.activeDpiIndex = activeDpiIndex
                settings.save()
                loaded = true
                customDpi = false
                status = "已读取：当前档位 ${activeDpiIndex + 1}"
            } else {
                status = if (cfg.raw.startsWith("00 00")) {
                    "设备休眠（返回全 0），上方为上次读取的档位；动一下鼠标后点「重新读取配置」"
                } else {
                    "读取失败：${cfg.error}"
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
        scope.launch {
            val (ok, err) = withContext(Dispatchers.IO) { mouse.setDpi(values, activeDpiIndex) }
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

    fun writeRate() {
        if (busy) return
        busy = true
        status = "正在写入回报率…"
        scope.launch {
            val (ok, err) = withContext(Dispatchers.IO) { mouse.setRate(rateIndex) }
            busy = false
            if (ok) {
                settings.rateHz = MouseConfigService.RateHz[rateIndex]
                settings.save()
                status = "回报率已按 ${MouseConfigService.RateHz[rateIndex]} Hz 发送"
            } else {
                status = "回报率写入失败：$err"
            }
        }
    }
}

@Composable
private fun rememberMouseConfigUiState(settings: AppSettings): MouseConfigUiState {
    val scope = rememberCoroutineScope()
    return remember(settings) {
        MouseConfigUiState(settings, MouseConfigService(), scope)
    }
}

@Composable
private fun MousePage(settings: AppSettings, state: MouseConfigUiState, wide: Boolean) {
    val c = rememberMochiColors()

    val stageSummary = (0 until 6).joinToString(" · ") { i ->
        val mark = if (i == state.activeDpiIndex) "*" else ""
        "$mark${i + 1} ${state.dpiText[i]}"
    }

    val deviceSection: @Composable () -> Unit = {
        MochiSection(header = "设备配置", footer = state.status.ifBlank { null }) {
            MochiRow(
                title = "重新读取配置",
                subtitle = "从鼠标回读 DPI 档位（启动时已自动读取）",
                chevron = true,
                divider = false,
                onClick = { state.refresh() },
            )
        }
    }

    val dpiSection: @Composable () -> Unit = {
        MochiSection(
            header = "DPI",
            footer = "各档：$stageSummary（* 当前档）",
        ) {
            // 当前档位
            MochiDropdownRow(
                title = "当前档位",
                options = (1..6).map { "档位 $it" },
                selectedIndex = state.activeDpiIndex,
                onSelect = { state.activeDpiIndex = it },
            )

            // DPI 数值：预设 + 自定义
            val current = state.stageValue(state.activeDpiIndex)
            val presetIndex = DpiPresets.indexOf(current)
            val customSelected = presetIndex < 0 || state.customDpi
            MochiDropdownRow(
                title = "DPI 数值",
                options = DpiPresets.map { "$it" } + "自定义…",
                selectedIndex = if (customSelected) DpiPresets.size else presetIndex,
                divider = !customSelected,
                onSelect = { idx ->
                    if (idx < DpiPresets.size) {
                        state.customDpi = false
                        state.setStageValue(state.activeDpiIndex, DpiPresets[idx])
                    } else {
                        state.customDpi = true
                    }
                },
            )

            if (customSelected) {
                MochiRow(
                    title = "自定义数值",
                    subtitle = "100–42000，直接输入后写入",
                    divider = false,
                    trailing = {
                        BasicTextField(
                            value = state.dpiText[state.activeDpiIndex],
                            onValueChange = { input ->
                                state.dpiText[state.activeDpiIndex] =
                                    input.filter { it.isDigit() }.take(5)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = c.accent,
                                textAlign = TextAlign.End,
                            ),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.width(72.dp),
                        )
                    },
                )
            }

            MochiButtonRow(
                text = "写入 DPI（档位 ${state.activeDpiIndex + 1}）",
                onClick = { state.writeDpi() },
            )
        }
    }

    val rateSection: @Composable () -> Unit = {
        MochiSection(
            header = "回报率",
            footer = "此型号的配置报告不含回报率字段（回读恒为 125 Hz），这里显示本地记录值；写入后以实际手感为准。",
        ) {
            MochiDropdownRow(
                title = "回报率",
                options = MouseConfigService.RateHz.map { "$it Hz" },
                selectedIndex = state.rateIndex,
                onSelect = { state.rateIndex = it },
            )
            MochiButtonRow(text = "写入回报率", onClick = { state.writeRate() })
        }
    }

    if (wide) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                deviceSection()
                Spacer(Modifier.height(22.dp))
                rateSection()
                Spacer(Modifier.height(BottomInset))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                dpiSection()
                Spacer(Modifier.height(BottomInset))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = BottomInset),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item { deviceSection() }
            item { dpiSection() }
            item { rateSection() }
        }
    }
}

private fun rateIndexFromHz(hz: Int): Int {
    val idx = MouseConfigService.RateHz.indexOf(hz)
    return if (idx >= 0) idx else 2
}

// ---------------- 设置页 ----------------

@Composable
private fun SettingsPage(
    settings: AppSettings,
    updateInfo: UpdateInfo,
    onExitRequested: () -> Unit,
    wide: Boolean,
) {
    var autoStart by remember { mutableStateOf(settings.autoStart) }
    var showPercent by remember { mutableStateOf(settings.showPercentInTray) }
    var checkUpdates by remember { mutableStateOf(settings.checkUpdates) }

    val generalSection: @Composable () -> Unit = {
        MochiSection(header = "常规", footer = "开机自启写入当前用户启动项，无需管理员权限") {
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

    val aboutSection: @Composable () -> Unit = {
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

    val exitSection: @Composable () -> Unit = {
        MochiSection {
            MochiButtonRow(text = "退出程序", danger = true, onClick = onExitRequested)
        }
    }

    if (wide) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                generalSection()
                Spacer(Modifier.height(BottomInset))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                aboutSection()
                Spacer(Modifier.height(22.dp))
                exitSection()
                Spacer(Modifier.height(BottomInset))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = BottomInset),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item { generalSection() }
            item { aboutSection() }
            item { exitSection() }
        }
    }
}
