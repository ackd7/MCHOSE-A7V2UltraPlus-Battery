package top.ackd7.mchose.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.ackd7.mchose.APP_NAME
import top.ackd7.mchose.APP_VERSION
import top.ackd7.mchose.services.AppSettings
import top.ackd7.mchose.services.Autostart
import top.ackd7.mchose.services.BatteryInfo
import top.ackd7.mchose.services.BatteryMonitor
import top.ackd7.mchose.services.ConnectionMode
import top.ackd7.mchose.services.MouseConfigService
import top.ackd7.mchose.services.UpdateInfo
import top.ackd7.mchose.ui.mochi.Check
import top.ackd7.mchose.ui.mochi.G2RoundedShape
import top.ackd7.mchose.ui.mochi.MochiButtonRow
import top.ackd7.mchose.ui.mochi.MochiDropdownRow
import top.ackd7.mchose.ui.mochi.MochiPrimaryButton
import top.ackd7.mchose.ui.mochi.MochiRow
import top.ackd7.mchose.ui.mochi.MochiSection
import top.ackd7.mchose.ui.mochi.MochiSegmentedControl
import top.ackd7.mchose.ui.mochi.MochiStatusPill
import top.ackd7.mchose.ui.mochi.MochiSwitch
import top.ackd7.mchose.ui.mochi.MochiTab
import top.ackd7.mchose.ui.mochi.MochiTabBar
import top.ackd7.mchose.ui.mochi.mochiGlassSurface
import top.ackd7.mchose.ui.mochi.mochiPressScale
import top.ackd7.mchose.ui.mochi.rememberMochiColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune

/** 内容区底部留白，避免被悬浮液态玻璃标签栏挡住。 */
private val BottomInset = 112.dp

/** 宽屏阈值：超过就左右两列排布。 */
private val WideThreshold = 700.dp

private val Tabs = listOf(
    MochiTab("电量", MiuixIcons.Home),
    MochiTab("鼠标", MiuixIcons.Tune),
    MochiTab("设置", MiuixIcons.Settings),
)

@Composable
internal fun rememberAppIconBitmap(): ImageBitmap? = remember {
    runCatching {
        ClassLoader.getSystemResourceAsStream("app.png")?.use {
            javax.imageio.ImageIO.read(it)?.toComposeImageBitmap()
        }
    }.getOrNull()
}

@Composable
fun App(
    settings: AppSettings,
    monitor: BatteryMonitor,
    battery: BatteryInfo,
    updateInfo: UpdateInfo,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onShowPercentChange: () -> Unit,
    titleBar: @Composable () -> Unit,
    onExitRequested: () -> Unit,
    initialPage: Int = 0,
) {
    var showAbout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (top.ackd7.mchose.appArgsContains("--about")) showAbout = true
    }

    AppTheme(themeModeOf(themeMode)) {
        val c = rememberMochiColors()
        val canvasBg = if (c.isDark) Color(0xFF0F1218) else Color(0xFFF2F5FA)

        var page by remember { mutableIntStateOf(initialPage.coerceIn(0, 2)) }
        val mouseState = rememberMouseConfigUiState(settings)

        LaunchedEffect(Unit) {
            top.ackd7.mchose.appLog("UI 已组合（Miuix × Mochi）")
            mouseState.refresh(battery.mode)
        }

        // 开机时鼠标可能处于休眠导致首次读取失败：鼠标唤醒上线或有线/无线模式切换时自动补读一次配置
        var syncedMode by remember { mutableStateOf(ConnectionMode.Unknown) }
        LaunchedEffect(battery.success, battery.stale, battery.mode) {
            if (battery.success && !battery.stale) {
                val modeChanged = syncedMode != ConnectionMode.Unknown &&
                    battery.mode != ConnectionMode.Unknown &&
                    battery.mode != syncedMode
                if ((!mouseState.loaded || modeChanged) && !mouseState.busy) {
                    syncedMode = battery.mode
                    mouseState.refresh(battery.mode)
                } else if (mouseState.loaded && syncedMode == ConnectionMode.Unknown && battery.mode != ConnectionMode.Unknown) {
                    syncedMode = battery.mode
                }
            }
        }

        LaunchedEffect(page) {
            top.ackd7.mchose.appLog("切换到页面 $page")
            if (page == 1 && !mouseState.loaded && !mouseState.busy) {
                mouseState.refresh(battery.mode)
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { titleBar() },
            containerColor = canvasBg,
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvasBg)
                    .padding(innerPadding),
            ) {
                val wide = maxWidth >= WideThreshold

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(modifier = Modifier.widthIn(max = 1040.dp).fillMaxHeight()) {
                        AnimatedContent(
                            targetState = page,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                val forward = targetState > initialState
                                val dir = if (forward) 1 else -1
                                val slideIn = tween<IntOffset>(
                                    durationMillis = 280,
                                    easing = CubicBezierEasing(0.22f, 1.04f, 0.36f, 1f),
                                )
                                val slideOut = tween<IntOffset>(
                                    durationMillis = 260,
                                    easing = CubicBezierEasing(0.3f, 0.9f, 0.4f, 1f),
                                )
                                (slideInHorizontally(slideIn) { full -> dir * (full * 2 / 3) } +
                                    fadeIn(tween(200)))
                                    .togetherWith(
                                        slideOutHorizontally(slideOut) { full -> -dir * full / 4 } +
                                            fadeOut(tween(150)),
                                    )
                            },
                            label = "pageSwitch",
                        ) { p ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(canvasBg),
                            ) {
                                when (p) {
                                    0 -> BatteryPage(settings, monitor, battery, wide)
                                    1 -> MousePage(mouseState, battery.mode, wide)
                                    else -> SettingsPage(
                                        settings = settings,
                                        updateInfo = updateInfo,
                                        onExitRequested = onExitRequested,
                                        wide = wide,
                                        themeMode = themeMode,
                                        onThemeModeChange = onThemeModeChange,
                                        onShowPercentChange = onShowPercentChange,
                                        onShowAbout = { showAbout = true },
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部柔雾渐隐遮罩：让滚动内容柔和淡出在液态玻璃胶囊栏下方
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(124.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to canvasBg.copy(alpha = 0.88f),
                                1f to canvasBg,
                            ),
                        ),
                )

                MochiTabBar(
                    tabs = Tabs,
                    selectedIndex = page,
                    onSelect = { page = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )

                if (showAbout) {
                    AboutOverlay(onClose = { showAbout = false })
                }
            }
        }
    }
}

// ---------------- 页面 1：电量主页（Battery Dashboard） ----------------

@Composable
private fun BatteryPage(
    settings: AppSettings,
    monitor: BatteryMonitor,
    battery: BatteryInfo,
    wide: Boolean,
) {
    if (wide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(modifier = Modifier.weight(1.08f).fillMaxHeight()) {
                BatteryHeroCard(
                    battery = battery,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    stretch = true,
                )
                Spacer(Modifier.height(BottomInset))
            }
            Column(
                modifier = Modifier
                    .weight(0.96f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                RefreshSection(settings, monitor, battery)
                Spacer(Modifier.height(18.dp))
                DetailsSection(settings, battery)
                Spacer(Modifier.height(BottomInset))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = BottomInset),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { BatteryHeroCard(battery) }
            item { RefreshSection(settings, monitor, battery) }
            item { DetailsSection(settings, battery) }
        }
    }
}

@Composable
private fun BatteryHeroCard(
    battery: BatteryInfo,
    modifier: Modifier = Modifier.fillMaxWidth(),
    stretch: Boolean = false,
) {
    val c = rememberMochiColors()
    val ok = battery.success && battery.percent >= 0
    val sleeping = battery.stale || !battery.success

    val targetTone = when {
        !ok -> c.secondary
        battery.stale -> Color(0xFF64748B)
        battery.charging || battery.isFull -> c.success
        battery.percent >= 50 -> c.accent
        battery.percent >= 20 -> c.warning
        else -> c.danger
    }
    val tone by animateColorAsState(
        targetValue = targetTone,
        animationSpec = tween(durationMillis = 360),
        label = "heroTone",
    )
    val animatedPercent by animateIntAsState(
        targetValue = if (ok) battery.percent.coerceIn(0, 100) else 0,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "heroPct",
    )
    val animatedFrac by animateFloatAsState(
        targetValue = if (ok) battery.percent.coerceIn(0, 100) / 100f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "heroFrac",
    )

    Box(
        modifier = modifier.mochiGlassSurface(
            colors = c,
            cornerRadius = 26.dp,
            elevation = if (c.isDark) 6.dp else 10.dp,
        ),
    ) {
        Column(
            modifier = (if (stretch) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = if (stretch) Arrangement.SpaceBetween else Arrangement.spacedBy(20.dp),
        ) {
            // 顶部：设备名 + 实时状态胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = battery.deviceName.ifBlank { "MCHOSE A7 V2 Ultra+" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.text,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = connectionLabel(battery.mode),
                        fontSize = 12.sp,
                        color = c.secondary,
                    )
                }

                MochiStatusPill(
                    text = when {
                        sleeping -> "休眠中 Zzz"
                        battery.isFull -> "已充满"
                        battery.charging -> "充电中"
                        else -> "放电中"
                    },
                    dotColor = tone,
                    emphasized = true,
                )
            }

            // 中央：超大号数字 + HyperOS 胶囊电量条
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val numberColor = if (battery.stale) tone.copy(alpha = 0.68f) else tone
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (ok) "$animatedPercent" else "--",
                        fontSize = 84.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = numberColor,
                    )
                    Text(
                        text = "%",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.secondary,
                        modifier = Modifier.padding(bottom = 14.dp, start = 4.dp),
                    )
                }

                when {
                    battery.stale && ok -> {
                        Text(
                            text = "保留 ${formatTime(battery.updatedAt)} 的读数 · 轻动鼠标刷新",
                            fontSize = 11.5.sp,
                            color = c.secondary,
                        )
                    }
                    battery.smoothed && battery.charging -> {
                        Text(
                            text = "充电电压平滑中 · 固件原始值 ${battery.rawPercent}%",
                            fontSize = 11.5.sp,
                            color = c.secondary,
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Spacer(Modifier.height(18.dp))

                // HyperOS 纯色宽幅胶囊电量条
                HyperOsCapsuleMeter(
                    fraction = animatedFrac,
                    fillColor = tone,
                )
            }

            // 底部三列紧凑指标条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(G2RoundedShape(16.dp))
                    .background(c.segmentTrack.copy(alpha = if (c.isDark) 0.72f else 0.75f))
                    .border(1.dp, c.cardBorder, G2RoundedShape(16.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricCell(
                    label = "电源状态",
                    value = when {
                        sleeping -> "休眠中"
                        battery.isFull -> "已充满"
                        battery.charging -> "充电中"
                        else -> "正常放电"
                    },
                    modifier = Modifier.weight(1f),
                )
                MetricDivider()
                MetricCell(
                    label = "连接通道",
                    value = connectionShortLabel(battery.mode),
                    modifier = Modifier.weight(1f),
                )
                MetricDivider()
                MetricCell(
                    label = "上次同步",
                    value = if (battery.updatedAt > 0) formatTime(battery.updatedAt) else "--",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** HyperOS 纯色胶囊电量条。 */
@Composable
private fun HyperOsCapsuleMeter(
    fraction: Float,
    fillColor: Color,
) {
    val c = rememberMochiColors()
    val shape = RoundedCornerShape(999.dp)
    val clamped = fraction.coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .clip(shape)
                .background(c.segmentTrack)
                .border(1.2.dp, c.cardBorder, shape)
                .padding(3.dp),
        ) {
            if (clamped > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clamped)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(fillColor),
                )
            }
        }
        Spacer(Modifier.width(3.dp))
        // 右侧极简电池正极小凸起
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.secondary.copy(alpha = 0.45f)),
        )
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    val c = rememberMochiColors()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, fontSize = 11.sp, color = c.secondary)
        Spacer(Modifier.height(3.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.text)
    }
}

@Composable
private fun MetricDivider() {
    val c = rememberMochiColors()
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(c.divider),
    )
}

@Composable
private fun RefreshSection(
    settings: AppSettings,
    monitor: BatteryMonitor,
    battery: BatteryInfo,
) {
    val c = rememberMochiColors()
    var refreshSeconds by remember { mutableIntStateOf(settings.refreshSeconds) }
    val intervals = listOf(15, 30, 90)
    val selectedIdx = intervals.indexOf(refreshSeconds).let { if (it >= 0) it else 1 }

    MochiSection(
        header = "轮询与刷新",
        footer = when {
            battery.stale -> "鼠标休眠中，轻动鼠标或点击上方「立即刷新」可唤醒读数。"
            !battery.success && battery.error.isNotBlank() -> battery.error
            else -> null
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("自动刷新间隔", fontSize = 14.sp, color = c.text)
                Text("每 $refreshSeconds 秒", fontSize = 12.sp, color = c.secondary)
            }
            Spacer(Modifier.height(10.dp))
            MochiSegmentedControl(
                options = listOf("15 秒", "30 秒", "90 秒"),
                selectedIndex = selectedIdx,
                onSelect = { idx ->
                    refreshSeconds = intervals[idx]
                    settings.refreshSeconds = refreshSeconds
                    settings.save()
                    monitor.setInterval(refreshSeconds)
                },
            )
            Spacer(Modifier.height(12.dp))
            MochiPrimaryButton(
                text = "立即刷新电量",
                onClick = { monitor.requestRefresh() },
            )
        }
    }
}

@Composable
private fun DetailsSection(settings: AppSettings, battery: BatteryInfo) {
    MochiSection(header = "设备与提醒") {
        MochiRow(
            title = "设备编号",
            value = if (battery.vendorId.isNotBlank()) "${battery.vendorId}:${battery.productId}" else "--",
        )
        MochiRow(
            title = "低电量提醒",
            value = if (settings.lowBatteryAlert) "已开启 · ≤ ${settings.alertThreshold}%" else "已关闭",
            divider = false,
        )
    }
}

private fun connectionLabel(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.Usb -> "USB 有线直连"
    ConnectionMode.Radio24G -> "2.4GHz 无线接收器"
    ConnectionMode.Bluetooth -> "蓝牙连接"
    else -> "等待设备连接"
}

private fun connectionShortLabel(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.Usb -> "USB 有线"
    ConnectionMode.Radio24G -> "2.4G 无线"
    ConnectionMode.Bluetooth -> "蓝牙"
    else -> "未知"
}

private fun formatTime(epochMs: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}.getOrDefault("--")

// ---------------- 页面 2：鼠标调校（Mouse Studio） ----------------

private val DpiQuickPresets = listOf(400, 800, 1600, 3200, 6400)
private val DpiPresets = listOf(400, 800, 1200, 1600, 2400, 3200, 6400, 12000, 26000, 32000, 42000)

@Stable
class MouseConfigUiState(
    private val settings: AppSettings,
    private val mouse: MouseConfigService,
    private val scope: CoroutineScope,
) {
    val dpiText = mutableStateListOf(
        *Array(6) { i -> settings.dpiStages.getOrElse(i) { 800 }.toString() },
    )
    var activeDpiIndex by mutableIntStateOf(settings.activeDpiIndex.coerceIn(0, 5))
    var rateIndex by mutableIntStateOf(rateIndexFromHz(settings.rateHz))
    var status by mutableStateOf("")
    var statusIsError by mutableStateOf(false)
    var customDpi by mutableStateOf(false)

    var busy by mutableStateOf(false)
        private set

    var loaded by mutableStateOf(false)
        private set

    fun stageValue(index: Int): Int = dpiText[index.coerceIn(0, 5)].trim().toIntOrNull() ?: 800

    fun setStageValue(index: Int, value: Int) {
        val aligned = ((value.coerceIn(100, 42000) + 5) / 10) * 10
        dpiText[index.coerceIn(0, 5)] = aligned.coerceIn(100, 42000).toString()
    }

    fun stepStageValue(index: Int, delta: Int) {
        setStageValue(index, stageValue(index) + delta)
    }

    fun refresh(mode: ConnectionMode = ConnectionMode.Unknown) {
        if (busy) return
        busy = true
        statusIsError = false
        status = "正在从鼠标读取配置…"
        scope.launch {
            val cfg = withContext(Dispatchers.IO) { mouse.read() }
            busy = false
            if (cfg.success) {
                for (i in 0 until 6) dpiText[i] = cfg.dpiStages[i].toString()
                val readDpiIdx = cfg.activeDpiIndexFor(mode)
                if (readDpiIdx in 0..5) activeDpiIndex = readDpiIdx
                val readRate = cfg.activeRateIndex(mode)
                if (readRate >= 0) {
                    rateIndex = readRate
                    settings.rateHz = MouseConfigService.RateHz[readRate]
                }
                settings.dpiStages = IntArray(6) { cfg.dpiStages[it] }
                settings.activeDpiIndex = activeDpiIndex
                settings.save()
                loaded = true
                customDpi = false
                statusIsError = false
                val rateDesc = if (readRate >= 0) " · ${MouseConfigService.RateHz[readRate]} Hz" else ""
                status = "已同步鼠标配置 · 档位 ${activeDpiIndex + 1}$rateDesc"
            } else {
                statusIsError = true
                status = if (cfg.raw.startsWith("00 00")) {
                    "设备休眠中（返回全 0），轻动鼠标后点「重新读取」"
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
            if (v == null || v !in 100..42000 || v % 10 != 0) {
                statusIsError = true
                status = "档位 ${i + 1} 的 DPI 需在 100–42000 之间且为 10 的整数倍"
                return
            }
            values[i] = v
        }
        busy = true
        statusIsError = false
        status = "正在写入 DPI…"
        scope.launch {
            val (ok, err) = withContext(Dispatchers.IO) { mouse.setDpi(values, activeDpiIndex) }
            busy = false
            if (ok) {
                settings.dpiStages = values
                settings.activeDpiIndex = activeDpiIndex
                settings.save()
                statusIsError = false
                status = "DPI 已写入 · 档位 ${activeDpiIndex + 1} = ${values[activeDpiIndex]}"
            } else {
                statusIsError = true
                status = "DPI 写入失败：$err"
            }
        }
    }

    fun writeRate() {
        if (busy) return
        busy = true
        statusIsError = false
        status = "正在写入回报率…"
        scope.launch {
            val (ok, err) = withContext(Dispatchers.IO) { mouse.setRate(rateIndex) }
            busy = false
            if (ok) {
                settings.rateHz = MouseConfigService.RateHz[rateIndex]
                settings.save()
                statusIsError = false
                status = "回报率已写入 · ${MouseConfigService.RateHz[rateIndex]} Hz"
            } else {
                statusIsError = true
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
private fun MousePage(state: MouseConfigUiState, mode: ConnectionMode, wide: Boolean) {
    val c = rememberMochiColors()

    val syncSection: @Composable () -> Unit = {
        MochiSection(
            header = "设备同步",
            footer = state.status.ifBlank { null },
        ) {
            MochiRow(
                title = "重新读取鼠标配置",
                subtitle = "从板载内存同步 6 档 DPI、当前档位与回报率",
                chevron = true,
                divider = false,
                enabled = !state.busy,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(G2RoundedShape(10.dp))
                            .background(c.accent.copy(alpha = if (c.isDark) 0.18f else 0.11f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "刷新",
                            tint = c.accent,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                },
                onClick = { state.refresh(mode) },
            )
        }
    }

    val dpiSection: @Composable () -> Unit = {
        MochiSection(header = "DPI 档位（点击切换激活档）") {
            Column(modifier = Modifier.padding(14.dp)) {
                // 3×2 可视化 DPI 超椭圆瓦片矩阵
                for (rowIdx in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (colIdx in 0..2) {
                            val stageIdx = rowIdx * 3 + colIdx
                            val active = stageIdx == state.activeDpiIndex
                            DpiStageTile(
                                index = stageIdx,
                                valueText = state.dpiText[stageIdx],
                                selected = active,
                                onClick = {
                                    state.activeDpiIndex = stageIdx
                                    state.customDpi = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (rowIdx == 0) Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Spacer(Modifier.height(12.dp))

                // 当前选中档位快捷预设 Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "档位 ${state.activeDpiIndex + 1} 快捷数值",
                        fontSize = 12.5.sp,
                        color = c.secondary,
                    )
                    Text(
                        text = "${state.stageValue(state.activeDpiIndex)} DPI",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.accent,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val cur = state.stageValue(state.activeDpiIndex)
                    DpiQuickPresets.forEach { preset ->
                        QuickChip(
                            label = "$preset",
                            selected = cur == preset && !state.customDpi,
                            onClick = {
                                state.customDpi = false
                                state.setStageValue(state.activeDpiIndex, preset)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 完整下拉预设 + 自定义输入
            val current = state.stageValue(state.activeDpiIndex)
            val presetIndex = DpiPresets.indexOf(current)
            val customSelected = presetIndex < 0 || state.customDpi

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(c.divider))

            MochiDropdownRow(
                title = "更多预设 / 自定义",
                options = DpiPresets.map { "$it" } + "自定义…",
                selectedIndex = if (customSelected) DpiPresets.size else presetIndex,
                divider = customSelected,
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
                    title = "自定义 DPI",
                    subtitle = "100–42000（10 的倍数）",
                    divider = false,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MiniStepButton("-100") { state.stepStageValue(state.activeDpiIndex, -100) }
                            val currentText = state.dpiText[state.activeDpiIndex]
                            val fieldState = rememberTextFieldState(initialText = currentText)
                            LaunchedEffect(state.activeDpiIndex, currentText) {
                                if (fieldState.text.toString() != currentText) {
                                    fieldState.setTextAndPlaceCursorAtEnd(currentText)
                                }
                            }
                            LaunchedEffect(state.activeDpiIndex) {
                                snapshotFlow { fieldState.text.toString() }.collect { input ->
                                    val filtered = input.filter { it.isDigit() }.take(5)
                                    if (filtered != state.dpiText[state.activeDpiIndex]) {
                                        state.dpiText[state.activeDpiIndex] = filtered
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .width(74.dp)
                                    .clip(G2RoundedShape(10.dp))
                                    .background(c.segmentTrack)
                                    .border(1.dp, c.accent.copy(alpha = 0.45f), G2RoundedShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                BasicTextField(
                                    state = fieldState,
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    inputTransformation = InputTransformation {
                                        val clean = asCharSequence().filter { it.isDigit() }.take(5)
                                        if (clean.toString() != asCharSequence().toString()) {
                                            replace(0, length, clean)
                                        }
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.accent,
                                        textAlign = TextAlign.End,
                                    ),
                                    cursorBrush = SolidColor(c.accent),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            MiniStepButton("+100") { state.stepStageValue(state.activeDpiIndex, 100) }
                        }
                    },
                )
            }

            Box(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)) {
                MochiPrimaryButton(
                    text = if (state.busy) "正在同步…" else "写入 DPI（激活档位 ${state.activeDpiIndex + 1}）",
                    enabled = !state.busy,
                    onClick = { state.writeDpi() },
                )
            }
        }
    }

    val rateSection: @Composable () -> Unit = {
        MochiSection(
            header = "回报率（Polling Rate）",
            footer = "支持启动自动回读当前回报率，写入后掉电不丢（有线与 2.4G 同步生效）。",
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val rates = MouseConfigService.RateHz
                for (rowIdx in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (colIdx in 0..2) {
                            val idx = rowIdx * 3 + colIdx
                            val hz = rates[idx]
                            val selected = idx == state.rateIndex
                            RateTile(
                                hz = hz,
                                selected = selected,
                                onClick = { state.rateIndex = idx },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (rowIdx == 0) Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(14.dp))
                MochiPrimaryButton(
                    text = if (state.busy) "正在写入…" else "写入回报率（${MouseConfigService.RateHz[state.rateIndex]} Hz）",
                    enabled = !state.busy,
                    onClick = { state.writeRate() },
                )
            }
        }
    }

    if (wide) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                syncSection()
                Spacer(Modifier.height(18.dp))
                rateSection()
                Spacer(Modifier.height(BottomInset))
            }
            Column(
                modifier = Modifier
                    .weight(1.08f)
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = BottomInset),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { syncSection() }
            item { dpiSection() }
            item { rateSection() }
        }
    }
}

@Composable
private fun DpiStageTile(
    index: Int,
    valueText: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = G2RoundedShape(15.dp)

    val bg by animateColorAsState(
        targetValue = when {
            selected -> c.accent.copy(alpha = if (c.isDark) 0.22f else 0.12f)
            hovered -> c.cardHover
            else -> c.segmentTrack.copy(alpha = if (c.isDark) 0.65f else 0.72f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dpiTileBg",
    )
    val borderCol by animateColorAsState(
        targetValue = if (selected) c.accent.copy(alpha = 0.75f) else c.cardBorder,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dpiTileBorder",
    )

    Column(
        modifier = modifier
            .mochiPressScale(interaction, pressedScale = 0.95f)
            .clip(shape)
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, borderCol, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "档位 ${index + 1}",
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) c.accent else c.secondary,
            )
            if (selected) {
                Check(color = c.accent, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = valueText.ifBlank { "--" },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) c.text else c.text.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun RateTile(
    hz: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = G2RoundedShape(14.dp)

    val bg by animateColorAsState(
        targetValue = when {
            selected -> c.accent.copy(alpha = if (c.isDark) 0.22f else 0.12f)
            hovered -> c.cardHover
            else -> c.segmentTrack.copy(alpha = if (c.isDark) 0.65f else 0.72f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rateTileBg",
    )
    val borderCol by animateColorAsState(
        targetValue = if (selected) c.accent.copy(alpha = 0.75f) else c.cardBorder,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rateTileBorder",
    )

    Column(
        modifier = modifier
            .mochiPressScale(interaction, pressedScale = 0.95f)
            .clip(shape)
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, borderCol, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hz >= 1000) "${hz / 1000}K" else "$hz",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) c.accent else c.text,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = "$hz Hz",
            fontSize = 10.5.sp,
            color = if (selected) c.accent.copy(alpha = 0.85f) else c.secondary,
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = G2RoundedShape(10.dp)

    Box(
        modifier = modifier
            .height(30.dp)
            .mochiPressScale(interaction, pressedScale = 0.94f)
            .clip(shape)
            .background(
                when {
                    selected -> c.accent
                    hovered -> c.cardHover
                    else -> c.segmentTrack
                },
            )
            .border(
                1.dp,
                if (selected) c.accent else c.cardBorder,
                shape,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Color.White else c.text.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun MiniStepButton(label: String, onClick: () -> Unit) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = G2RoundedShape(9.dp)
    Box(
        modifier = Modifier
            .mochiPressScale(interaction, pressedScale = 0.92f)
            .clip(shape)
            .background(if (hovered) c.cardHover else c.segmentTrack)
            .border(1.dp, c.cardBorder, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = c.text)
    }
}

private fun rateIndexFromHz(hz: Int): Int {
    val idx = MouseConfigService.RateHz.indexOf(hz)
    return if (idx >= 0) idx else 2
}

// ---------------- 页面 3：设置页（Settings） ----------------

@Composable
private fun SettingsPage(
    settings: AppSettings,
    updateInfo: UpdateInfo,
    onExitRequested: () -> Unit,
    wide: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onShowPercentChange: () -> Unit,
    onShowAbout: () -> Unit,
) {
    val c = rememberMochiColors()
    val appIcon = rememberAppIconBitmap()
    var autoStart by remember { mutableStateOf(settings.autoStart) }
    var showPercent by remember { mutableStateOf(settings.showPercentInTray) }
    var checkUpdates by remember { mutableStateOf(settings.checkUpdates) }
    var lowAlert by remember { mutableStateOf(settings.lowBatteryAlert) }
    var alertThreshold by remember { mutableIntStateOf(settings.alertThreshold) }

    val appearanceSection: @Composable () -> Unit = {
        MochiSection(header = "外观与常规") {
            val themeIndex = when (themeMode) {
                "light" -> 1
                "dark" -> 2
                else -> 0
            }
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
                Text("界面外观", fontSize = 14.sp, color = c.text)
                Spacer(Modifier.height(10.dp))
                MochiSegmentedControl(
                    options = listOf("跟随系统", "浅色", "深色"),
                    selectedIndex = themeIndex,
                    onSelect = { idx ->
                        val mode = when (idx) {
                            1 -> "light"
                            2 -> "dark"
                            else -> "system"
                        }
                        onThemeModeChange(mode)
                        settings.themeMode = mode
                        settings.save()
                    },
                )
            }
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.divider))
            MochiRow(
                title = "开机静默自启",
                subtitle = "开机自动驻留托盘，不弹出主窗口",
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
                title = "托盘显示电量数字",
                subtitle = "关闭后托盘隐藏实时电量数字",
                divider = false,
                trailing = {
                    MochiSwitch(
                        checked = showPercent,
                        onCheckedChange = { checked ->
                            showPercent = checked
                            settings.showPercentInTray = checked
                            settings.save()
                            onShowPercentChange()
                        },
                    )
                },
            )
        }
    }

    val alertSection: @Composable () -> Unit = {
        val thresholds = listOf(10, 15, 20, 25, 30, 40, 50)
        MochiSection(
            header = "低电量提醒",
            footer = if (lowAlert) {
                "电量降到 $alertThreshold% 或以下时发送一次托盘气泡提醒，回升后自动重新待命。"
            } else {
                null
            },
        ) {
            MochiRow(
                title = "开启低电量提醒",
                divider = lowAlert,
                trailing = {
                    MochiSwitch(
                        checked = lowAlert,
                        onCheckedChange = { checked ->
                            lowAlert = checked
                            settings.lowBatteryAlert = checked
                            settings.save()
                        },
                    )
                },
            )
            if (lowAlert) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("提醒阈值", fontSize = 13.5.sp, color = c.text)
                        Text("$alertThreshold%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        thresholds.forEach { t ->
                            QuickChip(
                                label = "$t%",
                                selected = alertThreshold == t,
                                onClick = {
                                    alertThreshold = t
                                    settings.alertThreshold = t
                                    settings.save()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    val aboutAndUpdateSection: @Composable () -> Unit = {
        MochiSection(header = "更新与关于") {
            MochiRow(
                title = "自动检查更新",
                subtitle = "启动时检查 GitHub Releases 新版本",
                divider = true,
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
            MochiRow(
                title = APP_NAME,
                subtitle = "点击查看版本更新日志",
                value = "v$APP_VERSION",
                chevron = true,
                divider = updateInfo.available && updateInfo.latestVersion.isNotBlank(),
                leading = if (appIcon != null) {
                    {
                        Image(
                            bitmap = appIcon,
                            contentDescription = APP_NAME,
                            modifier = Modifier.size(30.dp).clip(G2RoundedShape(8.dp)),
                        )
                    }
                } else {
                    null
                },
                onClick = onShowAbout,
            )
            if (updateInfo.available && updateInfo.latestVersion.isNotBlank()) {
                MochiRow(
                    title = "发现新版本 ${updateInfo.latestVersion}",
                    subtitle = "前往 GitHub Releases 下载更新",
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
            modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                appearanceSection()
                Spacer(Modifier.height(BottomInset))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                alertSection()
                Spacer(Modifier.height(18.dp))
                aboutAndUpdateSection()
                Spacer(Modifier.height(18.dp))
                exitSection()
                Spacer(Modifier.height(BottomInset))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = BottomInset),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { appearanceSection() }
            item { alertSection() }
            item { aboutAndUpdateSection() }
            item { exitSection() }
        }
    }
}

// ---------------- Liquid Glass 关于弹窗 ----------------

private val RecentChanges = listOf(
    "4.1.1" to listOf(
        "修复 12 67 全量配置报告 byte[1]（有线 USB）与 byte[2]（2.4G 无线）错位导致 2000Hz 等无线回报率误读为 1000Hz 的问题",
        "按当前实际连接方式（2.4G 无线 / USB 有线）精准匹配回报率与 DPI 档位，模式切换时自动同步",
        "修复开机静默启动时鼠标休眠导致配置读取失败后不会自动补读的问题：鼠标唤醒上线或打开鼠标页时自动同步",
    ),
    "4.1.0" to listOf(
        "新增充电电压虚高平滑滤波，解决插电瞬间电量从 87% 暴涨到 100% 的假满电问题，并透明显示固件原始值",
        "融合开源社区协议成果：双采样防抖确认、无线在线标志位校验、区分「充电中 / 已充满」、监听 Report 0x13 主动推送",
        "修复 12 67 全量配置报告高低半字节解析反转，正式支持启动自动回读真实回报率",
    ),
    "4.0.0" to listOf(
        "全新纯色超椭圆 UI（参考 miuix × mochi-compose）：G2 连续圆角、3×2 可视化 DPI 与回报率矩阵、分段外观与刷新切换",
        "全新扁平化无线鼠标应用图标，安装包显式绑定独立图标并自动刷新 Windows 桌面图标缓存",
        "全新 4K 高清 1:1 托盘小图标：圆角正方形透明边框 + 大号 Segoe UI 粗体数字 + 充电闪电角标 + 休眠 Zzz",
        "新增缩小、放大/还原、关闭与打开窗口的流畅过渡动画（放大精准贴合工作区不遮挡任务栏）",
        "精简仓库无用文件与冗余依赖，全量代码审查修复线程同步、单实例唤醒、日志轮转等细节",
    ),
    "3.2.4" to listOf(
        "新增单实例限制：重复打开时自动唤醒已在托盘运行的窗口",
        "下拉选项面板支持滚动；日志超 1 MB 自动轮转并修复标准输出中文乱码",
        "同步安装向导写入的开机自启状态，优化自定义 DPI 校验与多屏托盘菜单定位",
    ),
    "3.2.3" to listOf(
        "修复开机自启静默启动、点击关闭按钮隐藏与最小化后托盘拉起问题",
        "项目主页升级为中英双语文档",
    ),
    "3.2.2" to listOf(
        "修复休眠保留上次读数失效、提醒阈值低于 30% 错位与手动刷新卡顿",
        "为 HID 读写加互斥锁；托盘右键菜单同步跟随深浅色外观",
    ),
    "3.1.0" to listOf(
        "新增浅色 / 深色 / 跟随系统三种外观、低电量提醒与自定义 DPI",
    ),
)

@Composable
private fun AboutOverlay(onClose: () -> Unit) {
    val c = rememberMochiColors()
    val appIcon = rememberAppIconBitmap()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.90f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "aboutScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "aboutAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f * alpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight(0.82f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .mochiGlassSurface(
                    colors = c,
                    cornerRadius = 26.dp,
                    elevation = 18.dp,
                    backgroundColor = c.card,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = APP_NAME,
                        modifier = Modifier.size(44.dp).clip(G2RoundedShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(APP_NAME, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Spacer(Modifier.height(2.dp))
                    Text("v$APP_VERSION", fontSize = 12.5.sp, color = c.accent, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            Spacer(Modifier.height(12.dp))

            Text("更新日志", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.secondary)
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                RecentChanges.forEach { (ver, items) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(c.accent),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("v$ver", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                    }
                    Spacer(Modifier.height(4.dp))
                    items.forEach { line ->
                        Text(
                            text = "· $line",
                            fontSize = 12.5.sp,
                            color = c.text.copy(alpha = 0.88f),
                            modifier = Modifier.padding(start = 14.dp, bottom = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MochiPrimaryButton(
                    text = "项目主页",
                    onClick = {
                        runCatching {
                            java.awt.Desktop.getDesktop().browse(
                                java.net.URI("https://github.com/ackd7/MCHOSE-A7V2UltraPlus-Battery"),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .weight(0.75f)
                        .height(42.dp)
                        .clip(G2RoundedShape(14.dp))
                        .background(c.segmentTrack)
                        .border(1.dp, c.cardBorder, G2RoundedShape(14.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("关闭", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.text)
                }
            }
        }
    }
}
