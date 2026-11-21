package top.ackd7.mchose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.ackd7.mchose.services.AppSettings
import top.ackd7.mchose.services.Autostart
import top.ackd7.mchose.services.BatteryInfo
import top.ackd7.mchose.services.BatteryMonitor
import top.ackd7.mchose.services.NativeTrayIcon
import top.ackd7.mchose.services.SingleInstance
import top.ackd7.mchose.services.UpdateChecker
import top.ackd7.mchose.services.UpdateInfo
import top.ackd7.mchose.ui.App
import top.ackd7.mchose.ui.AppTheme
import top.ackd7.mchose.ui.mochi.G2RoundedShape
import top.ackd7.mchose.ui.mochi.MochiStatusPill
import top.ackd7.mchose.ui.mochi.mochiPressScale
import top.ackd7.mchose.ui.mochi.rememberMochiColors
import top.ackd7.mchose.ui.rememberAppIconBitmap
import top.ackd7.mchose.ui.themeModeOf
import top.yukonga.miuix.kmp.basic.Text

const val APP_VERSION = "4.0.0"

/** 软件显示名称（窗口标题 / 托盘 / 关于页 / 安装包统一使用）。 */
const val APP_NAME = "MCHOSE A7 V2 Ultra+ Battery"

/** 内部标识：exe / 配置目录 / 注册表项统一使用（不含空格与加号）。 */
const val APP_ID = "MCHOSE-A7V2UltraPlus-Battery"

/** 启动参数（供 UI 层读取开发开关）。 */
private var startupArgs: Array<String> = emptyArray()

internal fun appArgsContains(arg: String): Boolean = startupArgs.contains(arg)

private val logLock = Any()
private const val MAX_LOG_BYTES = 1024L * 1024L

/** 轻量日志：写入 %LOCALAPPDATA%\APP_ID\app.log（超过 1 MB 自动轮转，防止无限增长）。 */
internal fun appLog(msg: String) {
    synchronized(logLock) {
        runCatching {
            val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            val dir = java.io.File(base, APP_ID).apply { mkdirs() }
            val file = java.io.File(dir, "app.log")
            if (file.exists() && file.length() > MAX_LOG_BYTES) {
                val backup = java.io.File(dir, "app.log.old")
                backup.delete()
                file.renameTo(backup)
            }
            file.appendText("[${java.time.LocalDateTime.now()}] $msg\n", Charsets.UTF_8)
        }
    }
}

/** 把 stdout/stderr 也写进 app.log —— 打包后的程序是 WinExe，控制台输出否则完全看不到。 */
private fun installStdStreamTee() {
    runCatching {
        val tee = java.io.PrintStream(
            object : java.io.OutputStream() {
                private val buf = java.io.ByteArrayOutputStream()

                @Synchronized
                private fun flushLine() {
                    if (buf.size() == 0) return
                    val text = buf.toString(Charsets.UTF_8).trimEnd('\r')
                    buf.reset()
                    if (text.isNotBlank()) appLog("OUT: $text")
                }

                @Synchronized
                override fun write(b: Int) {
                    if (b == '\n'.code) {
                        flushLine()
                    } else {
                        buf.write(b)
                        if (buf.size() >= 4096) flushLine()
                    }
                }

                @Synchronized
                override fun write(b: ByteArray, off: Int, len: Int) {
                    var start = off
                    val end = off + len
                    for (i in off until end) {
                        if (b[i].toInt() == '\n'.code) {
                            if (i > start) buf.write(b, start, i - start)
                            flushLine()
                            start = i + 1
                        }
                    }
                    if (start < end) {
                        buf.write(b, start, end - start)
                        if (buf.size() >= 4096) flushLine()
                    }
                }
            },
            true,
            Charsets.UTF_8,
        )
        System.setErr(tee)
        System.setOut(tee)
    }
}

fun main(args: Array<String>) {
    startupArgs = args
    val startMinimized = args.contains("--minimized")
    installStdStreamTee()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        appLog("UNCAUGHT in ${t.name}: ${e.stackTraceToString()}")
    }

    // 单实例锁：若已有实例在托盘运行，非静默启动时自动唤醒旧窗口并退出
    val singleInstance = SingleInstance.tryAcquire(notifyIfRunning = !startMinimized)
    if (singleInstance == null) {
        appLog("检测到已有运行中实例，已发送唤醒信号并退出")
        return
    }

    appLog("=== 启动 v$APP_VERSION args=${args.joinToString(" ")} ===")

    val settings = AppSettings.load()
    // 与真实注册表自启状态双向同步：
    // 1) 若设置里开启了自启，刷新注册表为新 exe 路径并清理旧 A7Battery 值
    // 2) 若安装向导勾选了开机自启写入了注册表，但 settings.json 仍为 false，则同步为 true
    runCatching {
        if (settings.autoStart) {
            Autostart.setEnabled(true)
            appLog("开机自启已刷新为新路径")
        } else if (Autostart.isEnabled()) {
            settings.autoStart = true
            settings.save()
            appLog("检测到安装向导已配置开机自启，已同步至设置")
        }
    }

    val monitor = BatteryMonitor()
    val initialPage = args.firstOrNull { it.startsWith("--page=") }
        ?.substringAfter("=")?.toIntOrNull() ?: 0

    application {
        var tray by remember { mutableStateOf<NativeTrayIcon?>(null) }
        var updateInfo by remember { mutableStateOf(UpdateInfo()) }
        // 读数状态集中在这里，再传给 UI（避免多处覆盖 onReadingChanged 导致 UI 收不到）
        var battery by remember { mutableStateOf(BatteryInfo()) }
        var themeMode by remember { mutableStateOf(settings.themeMode) }
        var isVisible by remember { mutableStateOf(!startMinimized) }
        // 托盘右键菜单（Compose 自绘窗口，规避 AWT 菜单中文乱码）
        var menuVisible by remember { mutableStateOf(false) }
        // 每次「从托盘打开」都自增，强制在 AWT 层把窗口恢复出来
        // （只靠 isVisible 布尔值不够：窗口被最小化时 isVisible 仍是 true，置 true 是空操作）
        var showToken by remember { mutableIntStateOf(0) }

        fun syncTrayIcon(info: BatteryInfo) {
            val t = tray ?: return
            javax.swing.SwingUtilities.invokeLater {
                val hasReading = info.success && info.percent >= 0
                val tip = if (hasReading) {
                    APP_NAME + "  ${info.percent}%" +
                        (if (info.stale) "（休眠，上次读数）" else if (info.charging) "  充电中" else "  放电中")
                } else {
                    "$APP_NAME（休眠 / 未连接）"
                }
                if (settings.showPercentInTray && hasReading && !info.stale) {
                    t.setIcon(info.percent, info.charging, tip)
                } else {
                    // 休眠、未连接或关闭电量数字显示时展示透明 Zzz 胶囊
                    t.setIcon(null, false, tip)
                }
            }
        }

        // 横向窗口；--narrow 为开发用（验证窄屏单列布局）
        val windowState = rememberWindowState(
            size = if (args.contains("--narrow")) DpSize(460.dp, 700.dp)
            else DpSize(880.dp, 560.dp),
        )
        val menuState = rememberWindowState(size = DpSize(196.dp, 184.dp))

        fun openMainWindow() {
            menuVisible = false
            isVisible = true
            showToken++
        }

        fun hideMainWindow() {
            menuVisible = false
            windowState.isMinimized = false
            isVisible = false
            appLog("窗口已隐藏到托盘")
        }

        fun shutdownApp() {
            menuVisible = false
            tray?.close()
            monitor.stop()
            singleInstance.close()
            javax.swing.SwingUtilities.invokeLater { exitApplication() }
        }

        // 所有副作用只做一次
        LaunchedEffect(Unit) {
            singleInstance.setWakeHandler {
                javax.swing.SwingUtilities.invokeLater { openMainWindow() }
            }
            try {
                val t = NativeTrayIcon()
                t.accent = java.awt.Color(0x34, 0x82, 0xFF)
                t.onOpen = { openMainWindow() }
                t.onMenu = { menuVisible = !menuVisible }
                t.onRefresh = { monitor.requestRefresh() }
                t.onExit = { shutdownApp() }
                tray = t
                t.setIcon(null, false, APP_NAME)
                appLog("托盘已创建")
            } catch (e: Exception) {
                appLog("托盘创建失败: ${e.message}")
            }

            var lastLogged = ""
            var lastGood: BatteryInfo? = null
            // 低电量提醒：降到阈值提醒一次，电量回升（阈值 +5）后重新待命
            var alertArmed = true
            monitor.onReadingChanged = { info ->
                // 设备休眠时固件会返回全 0；此时保留上一次成功读数的快照，避免界面突然变 "--"
                val effective = if (info.success) {
                    val snapshot = info.copy()
                    lastGood = snapshot
                    snapshot
                } else {
                    lastGood?.copy(stale = true, error = info.error, charging = false) ?: info.copy()
                }
                val brief = "ok=${info.success} stale=${effective.stale} pct=${effective.percent} err=${info.error}"
                if (brief != lastLogged) {
                    lastLogged = brief
                    appLog("读数: $brief")
                }
                // 低电量提醒（仅在非休眠且未充电时触发）
                if (settings.lowBatteryAlert && effective.success && !effective.stale && !effective.charging) {
                    val threshold = settings.alertThreshold
                    if (effective.percent in 0..100) {
                        if (effective.percent <= threshold && alertArmed) {
                            alertArmed = false
                            tray?.notify(
                                "鼠标电量不足",
                                "剩余 ${effective.percent}%，建议及时充电。",
                            )
                            appLog("低电量提醒：${effective.percent}%（阈值 $threshold%）")
                        } else if (effective.percent > threshold + 5) {
                            alertArmed = true
                        }
                    }
                }
                // 切到 EDT 更新 Compose State 与托盘图标，避免 IO 线程并发修改 UI 状态
                javax.swing.SwingUtilities.invokeLater {
                    battery = effective
                    syncTrayIcon(effective)
                }
            }
            monitor.start(settings.refreshSeconds)
            appLog("监控已启动")

            // 每次启动都检查一次更新（网络开销很小，用户也期望"打开就检查"）
            if (settings.checkUpdates) {
                val info = withContext(Dispatchers.IO) {
                    runCatching { UpdateChecker().check(APP_VERSION) }.getOrDefault(UpdateInfo())
                }
                settings.lastUpdateCheck = java.time.LocalDate.now().toString()
                settings.save()
                updateInfo = info
                appLog("检查更新：latest=${info.latestVersion} available=${info.available} err=${info.error}")
            }
        }

        Window(
            onCloseRequest = { hideMainWindow() },
            title = APP_NAME,
            state = windowState,
            undecorated = true,
            visible = isVisible,
        ) {
            val win = window
            val uiScope = rememberCoroutineScope()
            var isMax by remember { mutableStateOf(false) }
            var normalBounds by remember { mutableStateOf<java.awt.Rectangle?>(null) }

            // 窗口过渡动画状态（打开 / 关闭 / 缩小 / 放大）
            val winScale = remember { Animatable(1f) }
            val winAlpha = remember { Animatable(1f) }
            val winOffsetY = remember { Animatable(0f) }
            var transitionJob by remember { mutableStateOf<Job?>(null) }
            var boundsAnimJob by remember { mutableStateOf<Job?>(null) }

            fun playEnterAnimation() {
                transitionJob?.cancel()
                transitionJob = uiScope.launch {
                    val ease = CubicBezierEasing(0.18f, 0.9f, 0.28f, 1f)
                    winScale.snapTo(0.94f)
                    winAlpha.snapTo(0f)
                    winOffsetY.snapTo(14f)
                    coroutineScope {
                        launch { winScale.animateTo(1f, tween(190, easing = ease)) }
                        launch { winAlpha.animateTo(1f, tween(160, easing = ease)) }
                        launch { winOffsetY.animateTo(0f, tween(190, easing = ease)) }
                    }
                }
            }

            fun playCloseAnimation() {
                transitionJob?.cancel()
                transitionJob = uiScope.launch {
                    val ease = CubicBezierEasing(0.4f, 0f, 0.85f, 0.7f)
                    coroutineScope {
                        launch { winScale.animateTo(0.92f, tween(145, easing = ease)) }
                        launch { winAlpha.animateTo(0f, tween(140, easing = ease)) }
                        launch { winOffsetY.animateTo(18f, tween(145, easing = ease)) }
                    }
                    hideMainWindow()
                    winScale.snapTo(1f)
                    winAlpha.snapTo(1f)
                    winOffsetY.snapTo(0f)
                }
            }

            fun playMinimizeAnimation() {
                transitionJob?.cancel()
                transitionJob = uiScope.launch {
                    val ease = CubicBezierEasing(0.4f, 0f, 0.8f, 0.8f)
                    coroutineScope {
                        launch { winScale.animateTo(0.88f, tween(155, easing = ease)) }
                        launch { winAlpha.animateTo(0f, tween(145, easing = ease)) }
                        launch { winOffsetY.animateTo(36f, tween(155, easing = ease)) }
                    }
                    windowState.isMinimized = true
                }
            }

            fun animateWindowBounds(from: java.awt.Rectangle, to: java.awt.Rectangle, onDone: () -> Unit = {}) {
                boundsAnimJob?.cancel()
                boundsAnimJob = uiScope.launch {
                    val steps = 14
                    val frameMs = 12L
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps.toFloat()
                        // Cubic ease-out 顺滑减速曲线
                        val e = 1f - (1f - t) * (1f - t) * (1f - t)
                        val x = (from.x + (to.x - from.x) * e).toInt()
                        val y = (from.y + (to.y - from.y) * e).toInt()
                        val w = (from.width + (to.width - from.width) * e).toInt()
                        val h = (from.height + (to.height - from.height) * e).toInt()
                        win.setBounds(x, y, w, h)
                        delay(frameMs)
                    }
                    win.setBounds(to)
                    onDone()
                }
            }

            fun toggleMaximizeAnimated() {
                if (!isMax) {
                    val current = win.bounds
                    normalBounds = java.awt.Rectangle(current)
                    val target = currentScreenWorkArea(win)
                    isMax = true
                    appLog("切换最大化：普通 -> 放大工作区 $target")
                    animateWindowBounds(current, target)
                } else {
                    val current = win.bounds
                    val target = normalBounds ?: defaultCenteredBounds(win)
                    isMax = false
                    appLog("切换最大化：放大 -> 还原 $target")
                    animateWindowBounds(current, target)
                }
            }

            fun unmaximizeForDrag(pointerScreenX: Int, pointerScreenY: Int) {
                if (!isMax) return
                boundsAnimJob?.cancel()
                val oldBounds = win.bounds
                val restore = normalBounds ?: defaultCenteredBounds(win)
                val ratioX = ((pointerScreenX - oldBounds.x).toDouble() / oldBounds.width.coerceAtLeast(1))
                    .coerceIn(0.15, 0.85)
                val nx = (pointerScreenX - restore.width * ratioX).toInt()
                val ny = (pointerScreenY - 22).coerceAtLeast(currentScreenWorkArea(win).y)
                isMax = false
                win.setBounds(nx, ny, restore.width, restore.height)
            }

            LaunchedEffect(Unit) {
                runCatching {
                    val iconStream = ClassLoader.getSystemResourceAsStream("app.png")
                    if (iconStream != null) {
                        win.iconImage = iconStream.use { javax.imageio.ImageIO.read(it) }
                    }
                }
                runCatching { win.minimumSize = java.awt.Dimension(620, 420) }
                runCatching { applyRoundedCorners(win) }
                runCatching { win.rootPane.border = null }

                win.addWindowStateListener { e ->
                    appLog("窗口状态变化: ${e.oldState} -> ${e.newState}（isMax=$isMax）")
                }
                win.addWindowListener(object : java.awt.event.WindowAdapter() {
                    override fun windowIconified(e: java.awt.event.WindowEvent?) {
                        appLog("窗口被最小化（iconified）")
                    }

                    override fun windowDeiconified(e: java.awt.event.WindowEvent?) {
                        appLog("窗口已还原（deiconified）")
                        javax.swing.SwingUtilities.invokeLater { playEnterAnimation() }
                    }
                })
            }

            // 窗口每次由隐藏变为可见时：套用 Win11 圆角并播放丝滑进入动画
            LaunchedEffect(isVisible) {
                if (isVisible) {
                    runCatching { applyRoundedCorners(win) }
                    playEnterAnimation()
                }
            }

            // 从托盘打开时强制恢复（含取消最小化 / Win32 还原置前 / 抢焦点 + 进入动画）
            LaunchedEffect(showToken) {
                if (showToken == 0) return@LaunchedEffect
                val wasIconified = windowState.isMinimized ||
                    (win.extendedState and java.awt.Frame.ICONIFIED) != 0
                windowState.isMinimized = false
                javax.swing.SwingUtilities.invokeLater {
                    restoreAndFocusWindow(win, wasIconified)
                    playEnterAnimation()
                    appLog("托盘恢复窗口 #$showToken（wasIconified=$wasIconified, state=${win.extendedState}, visible=${win.isVisible}）")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = winScale.value
                        scaleY = winScale.value
                        alpha = winAlpha.value
                        translationY = winOffsetY.value
                    },
            ) {
                App(
                    settings = settings,
                    monitor = monitor,
                    battery = battery,
                    updateInfo = updateInfo,
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                    onShowPercentChange = { syncTrayIcon(battery) },
                    titleBar = {
                        AppTitleBar(
                            window = win,
                            title = APP_NAME,
                            subtitle = "v$APP_VERSION",
                            battery = battery,
                            isMaximized = isMax,
                            onDragStartFromMaximized = { px, py -> unmaximizeForDrag(px, py) },
                            onMinimize = {
                                appLog("点击最小化 → 缩小动画并最小化到任务栏")
                                playMinimizeAnimation()
                            },
                            onToggleMaximize = { toggleMaximizeAnimated() },
                            onClose = {
                                appLog("点击关闭 → 关闭动画并隐藏到托盘")
                                playCloseAnimation()
                            },
                        )
                    },
                    onExitRequested = { shutdownApp() },
                    initialPage = initialPage,
                )
            }
        }

        // 托盘右键菜单：常驻的 Compose 小窗口（只切 visible，避免销毁窗口导致应用退出）
        Window(
            onCloseRequest = { menuVisible = false },
            title = "menu",
            undecorated = true,
            resizable = false,
            alwaysOnTop = true,
            visible = menuVisible,
            state = menuState,
        ) {
            val win = window
            LaunchedEffect(Unit) {
                appLog("托盘菜单已创建")
                runCatching { applyRoundedCorners(win) }
                runCatching { win.rootPane.border = null }
                win.addWindowFocusListener(object : java.awt.event.WindowAdapter() {
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                        menuVisible = false
                    }
                })
            }
            // 每次弹出时移动到鼠标所在屏幕的指针上方（兼容多显示器与副屏任务栏）
            LaunchedEffect(menuVisible) {
                if (menuVisible) {
                    val pointer = java.awt.MouseInfo.getPointerInfo()
                    val p = pointer?.location ?: java.awt.Point(100, 100)
                    val bounds = pointer?.device?.defaultConfiguration?.bounds
                        ?: java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize)
                    val minX = bounds.x
                    val maxX = (bounds.x + bounds.width - win.width).coerceAtLeast(minX)
                    val minY = bounds.y
                    val maxY = (bounds.y + bounds.height - win.height).coerceAtLeast(minY)
                    val x = p.x.coerceIn(minX, maxX)
                    val y = (p.y - win.height - 8).coerceIn(minY, maxY)
                    win.setLocation(x, y)
                    win.toFront()
                    win.requestFocus()
                    appLog("托盘菜单已显示 @ $x,$y")
                }
            }
            AppTheme(themeModeOf(themeMode)) {
                TrayMenu(
                    battery = battery,
                    onOpen = { openMainWindow() },
                    onRefresh = { menuVisible = false; monitor.requestRefresh() },
                    onExit = { shutdownApp() },
                )
            }
        }
    }
}

// ---------------- 融入式无边框标题栏 ----------------

@Composable
private fun AppTitleBar(
    window: java.awt.Window,
    title: String,
    subtitle: String,
    battery: BatteryInfo,
    isMaximized: Boolean,
    onDragStartFromMaximized: (Int, Int) -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    val c = rememberMochiColors()
    val canvasBg = if (c.isDark) Color(0xFF0F1218) else Color(0xFFF2F5FA)
    val appIcon = rememberAppIconBitmap()

    val ok = battery.success && battery.percent >= 0
    val sleeping = battery.stale || !battery.success
    val pillColor = when {
        sleeping -> Color(0xFF64748B)
        battery.charging -> c.success
        battery.percent >= 50 -> c.accent
        battery.percent >= 20 -> c.warning
        else -> c.danger
    }
    val pillText = when {
        sleeping && ok -> "${battery.percent}% · Zzz"
        sleeping -> "休眠 Zzz"
        battery.charging -> "${battery.percent}% · 充电中"
        else -> "${battery.percent}%"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(canvasBg)
            .padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .dragWindow(
                    window = window,
                    onBeforeDrag = onDragStartFromMaximized,
                    onDoubleClick = onToggleMaximize,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = title,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(G2RoundedShape(7.dp)),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.text,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = c.secondary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            MochiStatusPill(
                text = pillText,
                dotColor = pillColor,
                emphasized = !sleeping,
            )
        }

        WindowButton(onClick = onMinimize) { stroke ->
            strokeLine(stroke, Offset(size.width * 0.15f, size.height / 2f), Offset(size.width * 0.85f, size.height / 2f), 1.6f)
        }
        WindowButton(onClick = onToggleMaximize) { stroke ->
            if (isMaximized) {
                drawRect(stroke, Offset(size.width * 0.08f, size.height * 0.3f), Size(size.width * 0.58f, size.height * 0.58f), style = Stroke(1.5f))
                drawRect(stroke, Offset(size.width * 0.3f, size.height * 0.08f), Size(size.width * 0.58f, size.height * 0.58f), style = Stroke(1.5f))
            } else {
                drawRect(stroke, Offset(size.width * 0.15f, size.height * 0.15f), Size(size.width * 0.7f, size.height * 0.7f), style = Stroke(1.5f))
            }
        }
        WindowButton(onClick = onClose, isClose = true) { stroke ->
            strokeLine(stroke, Offset(size.width * 0.22f, size.height * 0.22f), Offset(size.width * 0.78f, size.height * 0.78f), 1.6f)
            strokeLine(stroke, Offset(size.width * 0.78f, size.height * 0.22f), Offset(size.width * 0.22f, size.height * 0.78f), 1.6f)
        }
    }
}

/**
 * 拖动标题栏移动窗口；双击标题栏切换最大化。
 *
 * 抖动原因：早期实现用「窗口位置 + 节点偏移 + 事件局部坐标」反推屏幕指针。
 * 事件里的局部坐标是按"事件产生时"的窗口位置算的，而我们处理时窗口可能已经移动，
 * 两者错开一点就会来回修正 → 拖动时文字/图标发抖。
 *
 * 现在只认「按下那一刻的屏幕指针位置」和「那一刻的窗口位置」：
 *     新窗口位置 = 起始窗口位置 + (当前指针 - 起始指针)
 * 与窗口当前状态无关，不存在反馈，也不会抖。
 */
private fun Modifier.dragWindow(
    window: java.awt.Window,
    onBeforeDrag: (Int, Int) -> Unit,
    onDoubleClick: () -> Unit,
): Modifier = this
    .pointerInput(window) {
        detectTapGestures(onDoubleTap = { onDoubleClick() })
    }
    .pointerInput(window) {
        var startPointerX = 0
        var startPointerY = 0
        var startWindowX = 0
        var startWindowY = 0

        detectDragGestures(
            onDragStart = {
                val p = java.awt.MouseInfo.getPointerInfo()?.location
                if (p != null) {
                    onBeforeDrag(p.x, p.y)
                    startPointerX = p.x
                    startPointerY = p.y
                }
                startWindowX = window.x
                startWindowY = window.y
            },
            onDrag = { change, _ ->
                change.consume()
                val p = java.awt.MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                val (nx, ny) = clampToScreen(
                    window,
                    startWindowX + (p.x - startPointerX),
                    startWindowY + (p.y - startPointerY),
                )
                if (nx != window.x || ny != window.y) window.setLocation(nx, ny)
            },
        )
    }

/** 获取窗口当前所在显示器的可用工作区（扣除 Windows 任务栏区域，放大时不遮挡任务栏）。 */
private fun currentScreenWorkArea(window: java.awt.Window): java.awt.Rectangle {
    val gc = window.graphicsConfiguration
        ?: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val b = gc.bounds
    val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
    return java.awt.Rectangle(
        b.x + insets.left,
        b.y + insets.top,
        (b.width - insets.left - insets.right).coerceAtLeast(620),
        (b.height - insets.top - insets.bottom).coerceAtLeast(420),
    )
}

private fun defaultCenteredBounds(window: java.awt.Window): java.awt.Rectangle {
    val work = currentScreenWorkArea(window)
    val w = 880.coerceAtMost(work.width)
    val h = 560.coerceAtMost(work.height)
    return java.awt.Rectangle(work.x + (work.width - w) / 2, work.y + (work.height - h) / 2, w, h)
}

/** 保证窗口至少有一块留在某个屏幕内。 */
private fun clampToScreen(window: java.awt.Window, x: Int, y: Int): Pair<Int, Int> {
    val screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        .screenDevices.map { it.defaultConfiguration.bounds }
    if (screens.isEmpty()) return x to y
    val keepVisible = 120
    val minX = screens.minOf { it.x } - window.width + keepVisible
    val maxX = screens.maxOf { it.x + it.width } - keepVisible
    val minY = screens.minOf { it.y }
    val maxY = screens.maxOf { it.y + it.height } - 60
    return x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY)
}

@Composable
private fun WindowButton(
    onClick: () -> Unit,
    isClose: Boolean = false,
    draw: DrawScope.(Color) -> Unit,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        hovered && isClose -> c.danger
        hovered -> c.fill
        else -> Color.Transparent
    }
    val stroke = if (hovered && isClose) Color.White else c.text.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .size(30.dp)
            .mochiPressScale(interaction, pressedScale = 0.90f)
            .clip(G2RoundedShape(9.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(10.5.dp)) {
            draw(stroke)
        }
    }
}

/** 线条两端圆头。 */
private fun DrawScope.strokeLine(color: Color, start: Offset, end: Offset, width: Float) {
    drawLine(color = color, start = start, end = end, strokeWidth = width, cap = StrokeCap.Round)
}

// ---------------- Liquid Glass 托盘右键菜单 ----------------

@Composable
private fun TrayMenu(
    battery: BatteryInfo,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
) {
    val c = rememberMochiColors()
    val ok = battery.success && battery.percent >= 0
    val sleeping = battery.stale || !battery.success
    val statusSummary = when {
        sleeping && ok -> "电量 ${battery.percent}% · 休眠中"
        sleeping -> "设备休眠 / 未连接"
        battery.charging -> "电量 ${battery.percent}% · 充电中"
        else -> "电量 ${battery.percent}% · 放电中"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp)
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = statusSummary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = c.secondary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .height(1.dp)
                .background(c.divider),
        )
        TrayMenuItem("打开面板", onOpen)
        TrayMenuItem("立即刷新", onRefresh)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(1.dp)
                .background(c.divider),
        )
        TrayMenuItem("退出程序", onExit, danger = true)
    }
}

@Composable
private fun TrayMenuItem(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val itemShape = G2RoundedShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .mochiPressScale(interaction, pressedScale = 0.96f)
            .clip(itemShape)
            .background(
                when {
                    hovered && danger -> c.danger.copy(alpha = 0.14f)
                    hovered -> c.cardHover
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = if (hovered) FontWeight.Medium else FontWeight.Normal,
            color = if (danger) c.danger else c.text,
        )
    }
}

// ---------------- Windows 圆角 ----------------

private interface DwmApi : com.sun.jna.Library {
    fun DwmSetWindowAttribute(
        hwnd: com.sun.jna.Pointer,
        dwAttribute: Int,
        pvAttribute: com.sun.jna.ptr.IntByReference,
        cbAttribute: Int,
    ): Int
}

/** 若窗口被拖到所有屏幕之外，拉回主屏居中（否则「打开面板」看起来像没反应）。 */
private fun ensureOnScreen(w: java.awt.Window) {
    runCatching {
        val screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
            .screenDevices.map { it.defaultConfiguration.bounds }
        if (screens.any { it.intersects(w.bounds) }) return
        val p = screens.firstOrNull() ?: return
        w.setLocation(
            p.x + (p.width - w.width) / 2,
            p.y + (p.height - w.height) / 2,
        )
    }
}

/** 从隐藏/最小化状态强制还原并置前激活窗口（AWT + Win32 双保险）。 */
private fun restoreAndFocusWindow(win: java.awt.Frame, wasIconified: Boolean) {
    runCatching {
        val iconified = wasIconified || (win.extendedState and java.awt.Frame.ICONIFIED) != 0
        win.extendedState = win.extendedState and java.awt.Frame.ICONIFIED.inv()
        ensureOnScreen(win)
        if (!win.isVisible) win.isVisible = true

        val ptr = com.sun.jna.Native.getWindowPointer(win)
        if (ptr != null) {
            val hwnd = com.sun.jna.platform.win32.WinDef.HWND(ptr)
            val user32 = com.sun.jna.platform.win32.User32.INSTANCE
            // SW_RESTORE(9) 还原最小化窗口；SW_SHOW(5) 保持最大化/正常尺寸不变
            user32.ShowWindow(hwnd, if (iconified) 9 else 5)
            user32.SetForegroundWindow(hwnd)
        }

        // 临时置顶脉冲，确保窗口从其他应用后面浮出并获取焦点
        win.isAlwaysOnTop = true
        win.toFront()
        win.requestFocus()
        win.isAlwaysOnTop = false
    }.onFailure { appLog("拉起窗口异常: ${it.message}") }
}

/** 给无边框窗口加 Windows 11 圆角（失败不影响功能）。 */
private fun applyRoundedCorners(window: java.awt.Window) {
    val hwnd = com.sun.jna.Native.getWindowPointer(window) ?: return
    val dwm = com.sun.jna.Native.load("dwmapi", DwmApi::class.java)
    val pref = com.sun.jna.ptr.IntByReference(2) // DWMWCP_ROUND
    dwm.DwmSetWindowAttribute(hwnd, 33, pref, 4) // DWMWA_WINDOW_CORNER_PREFERENCE
}
