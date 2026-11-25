package top.ackd7.mchose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.ackd7.mchose.services.AppSettings
import top.ackd7.mchose.services.Autostart
import top.ackd7.mchose.services.BatteryInfo
import top.ackd7.mchose.services.BatteryMonitor
import top.ackd7.mchose.services.ChargeState
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

const val APP_VERSION = "4.2.0"

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
        // 托盘右键菜单（按需挂载的 Compose 小窗口，平时不占用任何 GPU 画布内存）
        var menuVisible by remember { mutableStateOf(false) }
        // 记录本进程内已成功同步过鼠标配置的连接模式（避免每次从托盘重开窗口都重复发 12 67 请求）
        var syncedMouseMode by remember { mutableStateOf<top.ackd7.mchose.services.ConnectionMode?>(null) }
        // 每次「从托盘打开」都自增，强制在 AWT 层把窗口恢复出来
        var showToken by remember { mutableIntStateOf(0) }

        fun syncTrayIcon(info: BatteryInfo) {
            val t = tray ?: return
            javax.swing.SwingUtilities.invokeLater {
                val hasReading = info.success && info.percent >= 0
                val tip = if (hasReading) {
                    val stateText = when {
                        info.stale -> "（休眠，上次读数）"
                        info.isFull -> "  已充满"
                        info.charging -> "  充电中"
                        else -> "  放电中"
                    }
                    "$APP_NAME  ${info.percent}%$stateText"
                } else {
                    "$APP_NAME（休眠 / 未连接）"
                }
                if (settings.showPercentInTray && hasReading && !info.stale) {
                    t.setIcon(info.percent, info.charging, tip)
                } else {
                    // 休眠、未连接或关闭电量数字显示时展示透明 Zzz 图标
                    t.setIcon(null, false, tip)
                }
            }
        }

        // 横向窗口状态放在外层，销毁窗口后再重开仍能保留上次拖动后的坐标与尺寸
        val windowState = rememberWindowState(
            size = if (args.contains("--narrow")) DpSize(460.dp, 700.dp)
            else DpSize(880.dp, 560.dp),
        )
        val menuState = rememberWindowState(size = DpSize(196.dp, 184.dp))

        fun computeTrayMenuPosition(menuW: Int = 196, menuH: Int = 184): Pair<Int, Int> {
            val pointer = java.awt.MouseInfo.getPointerInfo()
            val p = pointer?.location ?: java.awt.Point(100, 100)
            val bounds = pointer?.device?.defaultConfiguration?.bounds
                ?: java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize)
            val minX = bounds.x
            val maxX = (bounds.x + bounds.width - menuW).coerceAtLeast(minX)
            val minY = bounds.y
            val maxY = (bounds.y + bounds.height - menuH).coerceAtLeast(minY)
            return p.x.coerceIn(minX, maxX) to (p.y - menuH - 8).coerceIn(minY, maxY)
        }

        fun openMainWindow() {
            menuVisible = false
            isVisible = true
            showToken++
        }

        fun hideMainWindow() {
            menuVisible = false
            windowState.isMinimized = false
            isVisible = false
            appLog("窗口已销毁并退回托盘（释放 GPU 画布缓存）")
        }

        fun shutdownApp() {
            menuVisible = false
            isVisible = false
            tray?.close()
            monitor.stop()
            singleInstance.close()
            javax.swing.SwingUtilities.invokeLater { exitApplication() }
        }

        // 当主窗口与托盘菜单均关闭（纯后台驻留）时，延迟触发一次 GC 与 Windows 工作集整理
        LaunchedEffect(isVisible, menuVisible) {
            if (!isVisible && !menuVisible) {
                kotlinx.coroutines.delay(900L)
                withContext(Dispatchers.IO) {
                    trimProcessMemory()
                }
            }
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
                t.onMenu = {
                    if (!menuVisible) {
                        val (mx, my) = computeTrayMenuPosition()
                        menuState.position = androidx.compose.ui.window.WindowPosition(mx.dp, my.dp)
                        menuVisible = true
                    } else {
                        menuVisible = false
                    }
                }
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
                // 设备休眠时固件会返回全 0 或 linked=0；此时保留上一次成功读数的快照，避免界面突然变 "--"
                val effective = if (info.success) {
                    val snapshot = info.copy()
                    lastGood = snapshot
                    snapshot
                } else {
                    lastGood?.copy(stale = true, error = info.error, chargeState = ChargeState.Discharging) ?: info.copy()
                }
                val brief = "ok=${info.success} stale=${effective.stale} pct=${effective.percent} rawPct=${effective.rawPercent} chg=${effective.chargeState} err=${info.error}"
                if (brief != lastLogged) {
                    lastLogged = brief
                    appLog("读数: $brief")
                }
                // 低电量提醒（仅在非休眠且放电状态下触发）
                if (settings.lowBatteryAlert && effective.success && !effective.stale && !effective.charging && !effective.isFull) {
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

            if (!isVisible && !menuVisible) {
                withContext(Dispatchers.IO) { trimProcessMemory() }
            }
        }

        // 主窗口按需挂载：关闭隐藏回托盘时直接销毁窗口与 4K DirectX 显存画布，大幅释放后台内存
        if (isVisible) {
            Window(
                onCloseRequest = { hideMainWindow() },
                title = APP_NAME,
                state = windowState,
                undecorated = true,
            ) {
                val win = window

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
                    javax.swing.SwingUtilities.invokeLater {
                        restoreAndFocusWindow(win, false)
                    }
                }

                // 窗口已打开状态下再次点击托盘时置前激活
                LaunchedEffect(showToken) {
                    if (showToken == 0) return@LaunchedEffect
                    val wasIconified = windowState.isMinimized ||
                        (win.extendedState and java.awt.Frame.ICONIFIED) != 0
                    windowState.isMinimized = false
                    javax.swing.SwingUtilities.invokeLater {
                        restoreAndFocusWindow(win, wasIconified)
                        appLog("托盘恢复窗口 #$showToken（wasIconified=$wasIconified, state=${win.extendedState}, visible=${win.isVisible}）")
                    }
                }

                App(
                    settings = settings,
                    monitor = monitor,
                    battery = battery,
                    updateInfo = updateInfo,
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                    onShowPercentChange = { syncTrayIcon(battery) },
                    syncedMouseMode = syncedMouseMode,
                    onMouseSynced = { syncedMouseMode = it },
                    titleBar = {
                        AppTitleBar(
                            window = win,
                            title = APP_NAME,
                            subtitle = "v$APP_VERSION",
                            battery = battery,
                            onClose = { hideMainWindow() },
                        )
                    },
                    onExitRequested = { shutdownApp() },
                    initialPage = initialPage,
                )
            }
        }

        // 托盘右键菜单按需挂载：平时不常驻任何菜单窗口（AWT SystemTray + Compose awaitApplication 保持进程存活）
        if (menuVisible) {
            Window(
                onCloseRequest = { menuVisible = false },
                title = "menu",
                undecorated = true,
                resizable = false,
                alwaysOnTop = true,
                state = menuState,
            ) {
                val win = window
                LaunchedEffect(Unit) {
                    runCatching { applyRoundedCorners(win) }
                    runCatching { win.rootPane.border = null }
                    val (x, y) = computeTrayMenuPosition(win.width.coerceAtLeast(196), win.height.coerceAtLeast(184))
                    win.setLocation(x, y)
                    win.toFront()
                    win.requestFocus()
                    win.addWindowFocusListener(object : java.awt.event.WindowAdapter() {
                        override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                            menuVisible = false
                        }
                    })
                    appLog("托盘菜单已显示 @ $x,$y")
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
}

// ---------------- 融入式无边框标题栏 ----------------

@Composable
private fun AppTitleBar(
    window: java.awt.Window,
    title: String,
    subtitle: String,
    battery: BatteryInfo,
    onClose: () -> Unit,
) {
    val c = rememberMochiColors()
    val canvasBg = if (c.isDark) Color(0xFF0F1218) else Color(0xFFF2F5FA)
    val appIcon = rememberAppIconBitmap()

    val ok = battery.success && battery.percent >= 0
    val sleeping = battery.stale || !battery.success
    val pillColor = when {
        sleeping -> Color(0xFF64748B)
        battery.charging || battery.isFull -> c.success
        battery.percent >= 50 -> c.accent
        battery.percent >= 20 -> c.warning
        else -> c.danger
    }
    val pillText = when {
        sleeping && ok -> "${battery.percent}% · Zzz"
        sleeping -> "休眠 Zzz"
        battery.isFull -> "${battery.percent}% · 已充满"
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
                .dragWindow(window = window),
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

        WindowButton(onClick = onClose, isClose = true) { stroke ->
            strokeLine(stroke, Offset(size.width * 0.22f, size.height * 0.22f), Offset(size.width * 0.78f, size.height * 0.78f), 1.6f)
            strokeLine(stroke, Offset(size.width * 0.78f, size.height * 0.22f), Offset(size.width * 0.22f, size.height * 0.78f), 1.6f)
        }
    }
}

/**
 * 拖动标题栏移动窗口。
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
): Modifier = this
    .pointerInput(window) {
        var startPointerX = 0
        var startPointerY = 0
        var startWindowX = 0
        var startWindowY = 0

        detectDragGestures(
            onDragStart = {
                val p = java.awt.MouseInfo.getPointerInfo()?.location
                if (p != null) {
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
        battery.isFull -> "电量 ${battery.percent}% · 已充满"
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

private interface WorkingSetKernel32 : com.sun.jna.Library {
    fun SetProcessWorkingSetSize(
        hProcess: com.sun.jna.platform.win32.WinNT.HANDLE,
        dwMinimumWorkingSetSize: com.sun.jna.Pointer,
        dwMaximumWorkingSetSize: com.sun.jna.Pointer,
    ): Boolean
}

private val workingSetKernel32: WorkingSetKernel32 by lazy {
    com.sun.jna.Native.load("kernel32", WorkingSetKernel32::class.java)
}

/** 窗口全部卸载退回纯托盘后台时，主动执行 GC 并通知 Windows 回收闲置物理工作集页。 */
private fun trimProcessMemory() {
    runCatching {
        System.gc()
        val trimFlag = com.sun.jna.Pointer.createConstant(-1L)
        workingSetKernel32.SetProcessWorkingSetSize(
            com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentProcess(),
            trimFlag,
            trimFlag,
        )
    }
}

private val dwmApi: DwmApi by lazy {
    com.sun.jna.Native.load("dwmapi", DwmApi::class.java)
}

/** 给无边框窗口加 Windows 11 圆角（失败不影响功能）。 */
private fun applyRoundedCorners(window: java.awt.Window) {
    val hwnd = com.sun.jna.Native.getWindowPointer(window) ?: return
    val pref = com.sun.jna.ptr.IntByReference(2) // DWMWCP_ROUND
    dwmApi.DwmSetWindowAttribute(hwnd, 33, pref, 4) // DWMWA_WINDOW_CORNER_PREFERENCE
}
