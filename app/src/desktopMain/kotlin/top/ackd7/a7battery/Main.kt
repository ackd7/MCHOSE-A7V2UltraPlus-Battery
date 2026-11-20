package top.ackd7.a7battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.ackd7.a7battery.services.AppSettings
import top.ackd7.a7battery.services.BatteryInfo
import top.ackd7.a7battery.services.BatteryMonitor
import top.ackd7.a7battery.services.NativeTrayIcon
import top.ackd7.a7battery.services.UpdateChecker
import top.ackd7.a7battery.services.UpdateInfo
import top.ackd7.a7battery.ui.App
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

const val APP_VERSION = "3.0.0"

/** 轻量日志：写入 %LOCALAPPDATA%\A7Battery\app.log，便于排查启动异常。 */
internal fun appLog(msg: String) {
    runCatching {
        val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        val dir = java.io.File(base, "A7Battery").apply { mkdirs() }
        java.io.File(dir, "app.log").appendText("[${java.time.LocalDateTime.now()}] $msg\n")
    }
}

/** 把 stdout/stderr 也写进 app.log —— 打包后的程序是 WinExe，控制台输出否则完全看不到。 */
private fun installStdStreamTee() {
    runCatching {
        val tee = java.io.PrintStream(
            object : java.io.OutputStream() {
                private val line = StringBuilder()
                override fun write(b: Int) {
                    if (b == '\n'.code) {
                        if (line.isNotBlank()) appLog("OUT: $line")
                        line.setLength(0)
                    } else {
                        line.append(b.toChar())
                        if (line.length > 4000) {
                            appLog("OUT: $line")
                            line.setLength(0)
                        }
                    }
                }
            },
            true,
            "UTF-8",
        )
        System.setErr(tee)
        System.setOut(tee)
    }
}

fun main(args: Array<String>) {
    installStdStreamTee()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        appLog("UNCAUGHT in ${t.name}: ${e.stackTraceToString()}")
    }
    appLog("=== 启动 v$APP_VERSION args=${args.joinToString(" ")} ===")

    val settings = AppSettings.load()
    val monitor = BatteryMonitor()
    val startMinimized = args.contains("--minimized")
    val initialPage = args.firstOrNull { it.startsWith("--page=") }
        ?.substringAfter("=")?.toIntOrNull() ?: 0

    application {
        var tray by remember { mutableStateOf<NativeTrayIcon?>(null) }
        var updateInfo by remember { mutableStateOf(UpdateInfo()) }
        // 读数状态集中在这里，再传给 UI（避免多处覆盖 onReadingChanged 导致 UI 收不到）
        var battery by remember { mutableStateOf(BatteryInfo()) }
        var isVisible by remember { mutableStateOf(!startMinimized) }
        // 托盘右键菜单（Compose 自绘窗口，规避 AWT 菜单中文乱码）
        var menuVisible by remember { mutableStateOf(false) }
        // 每次「从托盘打开」都自增，强制在 AWT 层把窗口恢复出来
        // （只靠 isVisible 布尔值不够：窗口被最小化时 isVisible 仍是 true，置 true 是空操作）
        var showToken by remember { mutableIntStateOf(0) }

        // 横向窗口；--narrow 为开发用（验证窄屏单列布局）
        val windowState = rememberWindowState(
            size = if (args.contains("--narrow")) DpSize(460.dp, 700.dp)
            else DpSize(880.dp, 560.dp),
        )
        val menuState = rememberWindowState(size = DpSize(180.dp, 148.dp))

        // 所有副作用只做一次
        LaunchedEffect(Unit) {
            try {
                val t = NativeTrayIcon()
                t.accent = java.awt.Color(0x34, 0x82, 0xFF)
                t.onOpen = { menuVisible = false; isVisible = true; showToken++ }
                t.onMenu = { menuVisible = !menuVisible }
                t.onRefresh = { monitor.refresh() }
                t.onExit = {
                    monitor.stop()
                    javax.swing.SwingUtilities.invokeLater { exitApplication() }
                }
                tray = t
                t.setIcon(null, false, "A7 电量助手")
                appLog("托盘已创建")
            } catch (e: Exception) {
                appLog("托盘创建失败: ${e.message}")
            }

            var lastLogged = ""
            var lastGood: BatteryInfo? = null
            monitor.onReadingChanged = { info ->
                // 设备休眠时固件会返回全 0；此时保留上一次成功读数，避免界面突然变 "--"
                val effective = if (info.success) {
                    lastGood = info
                    info
                } else {
                    lastGood?.copy(stale = true, error = info.error, charging = false) ?: info
                }
                battery = effective
                val brief = "ok=${info.success} stale=${effective.stale} pct=${effective.percent} err=${info.error}"
                if (brief != lastLogged) {
                    lastLogged = brief
                    appLog("读数: $brief")
                }
                val t = tray
                if (t != null) {
                    javax.swing.SwingUtilities.invokeLater {
                        if (settings.showPercentInTray && effective.success && effective.percent >= 0) {
                            t.setIcon(
                                effective.percent,
                                effective.charging,
                                "迈从 A7 V2 Ultra+  ${effective.percent}%" +
                                    (if (effective.stale) "（休眠，上次读数）" else if (effective.charging) "  充电中" else "  放电中"),
                            )
                        } else {
                            t.setIcon(null, false, "A7 电量助手（设备休眠/未连接）")
                        }
                    }
                }
            }
            monitor.start(settings.refreshSeconds)
            appLog("监控已启动")

            if (args.contains("--test-menu")) {
                kotlinx.coroutines.delay(1500)
                menuVisible = true
            }

            if (settings.checkUpdates) {
                val today = java.time.LocalDate.now().toString()
                if (today != settings.lastUpdateCheck) {
                    val info = withContext(Dispatchers.IO) {
                        runCatching { UpdateChecker().check(APP_VERSION) }.getOrDefault(UpdateInfo())
                    }
                    settings.lastUpdateCheck = today
                    settings.save()
                    updateInfo = info
                }
            }
        }

        Window(
            onCloseRequest = { isVisible = false },
            title = "A7 电量助手",
            state = windowState,
            undecorated = true,
        ) {
            val win = window
            var isMax by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                runCatching { win.minimumSize = java.awt.Dimension(620, 420) }
                runCatching { applyRoundedCorners(win) }
                // 去掉 Swing 默认边框，避免无边框窗口右/下缘出现一条细线
                runCatching { win.rootPane.border = null }

                // 记录窗口状态变化，便于排查「界面消失」类问题
                win.addWindowStateListener { e ->
                    isMax = (e.newState and java.awt.Frame.MAXIMIZED_BOTH) != 0
                    appLog("窗口状态变化: ${e.oldState} -> ${e.newState}（isMax=$isMax）")
                }
                win.addWindowListener(object : java.awt.event.WindowAdapter() {
                    override fun windowIconified(e: java.awt.event.WindowEvent?) {
                        appLog("窗口被最小化（iconified）")
                    }

                    override fun windowDeiconified(e: java.awt.event.WindowEvent?) {
                        appLog("窗口已还原（deiconified）")
                    }
                })
            }

            // 开发用：点开「自动刷新间隔」下拉，验证弹出动画与面板位置
            LaunchedEffect(Unit) {
                if (args.contains("--test-dropdown")) {
                    kotlinx.coroutines.delay(3000)
                    win.isAlwaysOnTop = true
                    win.toFront()
                    win.requestFocus()
                    kotlinx.coroutines.delay(700)
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val robot = java.awt.Robot()
                            // 电量页右列「自动刷新间隔」这一行
                            val x = win.x + win.width * 3 / 4
                            val y = win.y + 122
                            robot.mouseMove(x, y)
                            Thread.sleep(150)
                            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                            Thread.sleep(60)
                            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                            Thread.sleep(500)
                            val img = robot.createScreenCapture(win.bounds)
                            javax.imageio.ImageIO.write(
                                img,
                                "png",
                                java.io.File(System.getProperty("java.io.tmpdir"), "a7-dropdown.png"),
                            )
                        }.onFailure { appLog("下拉测试失败: " + it.message) }
                    }
                    appLog("下拉测试：已点开选项并截图")
                    win.isAlwaysOnTop = false
                }
            }

            // 开发用：Robot 点击底部「鼠标」标签，并在动画中途截图（验证滑动切换）
            LaunchedEffect(Unit) {
                val switchArg = args.firstOrNull { it.startsWith("--test-switch") }
                if (switchArg != null) {
                    val tabIndex = switchArg.substringAfter("=", "2").toIntOrNull() ?: 2
                    kotlinx.coroutines.delay(3000)
                    win.isAlwaysOnTop = true
                    win.toFront()
                    win.requestFocus()
                    kotlinx.coroutines.delay(700)
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val robot = java.awt.Robot()
                            val x = win.x + win.width / 2 + (tabIndex - 2) * 84  // 标签项宽 84dp
                            val y = win.y + win.height - 44    // 悬浮标签栏位置
                            robot.mouseMove(x, y)
                            Thread.sleep(120)
                            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                            Thread.sleep(60)
                            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                            // 连拍 10 帧（25ms 间隔），捕捉滑动过程
                            for (i in 1..10) {
                                Thread.sleep(25)
                                val img = robot.createScreenCapture(win.bounds)
                                javax.imageio.ImageIO.write(
                                    img,
                                    "png",
                                    java.io.File(
                                        System.getProperty("java.io.tmpdir"),
                                        "a7-sw-$i.png",
                                    ),
                                )
                            }
                        }.onFailure { appLog("切换测试失败: " + it.message) }
                    }
                    appLog("测试切换：已点击「鼠标」标签并截图")
                    win.isAlwaysOnTop = false
                }
            }

            // 开发用：Robot 拖拽标题栏，验证窗口跟随鼠标且不会飞出屏幕
            // 注意 Robot 操作必须放在 IO 线程，否则 Thread.sleep 阻塞 EDT 会让指针事件滞后
            LaunchedEffect(Unit) {
                if (args.contains("--test-drag")) {
                    kotlinx.coroutines.delay(3000)
                    win.isAlwaysOnTop = true
                    win.toFront()
                    win.requestFocus()
                    kotlinx.coroutines.delay(800)
                    val before = win.bounds
                    val moved = withContext(Dispatchers.IO) {
                        runCatching {
                            val robot = java.awt.Robot()
                            val startX = before.x + 120
                            val startY = before.y + 24
                            robot.mouseMove(startX, startY)
                            Thread.sleep(250)
                            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                            Thread.sleep(150)
                            for (step in 1..20) {
                                robot.mouseMove(startX + step * 10, startY + step * 6)
                                Thread.sleep(30)
                            }
                            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                            Thread.sleep(600)
                            true
                        }.getOrElse {
                            appLog("测试拖拽异常: " + it.message)
                            false
                        }
                    }
                    kotlinx.coroutines.delay(400)
                    val after = win.bounds
                    appLog(
                        "测试拖拽(after=$moved)：before=(${before.x},${before.y}) " +
                            "after=(${after.x},${after.y}) 期望≈(200,120) " +
                            "实际=(${after.x - before.x},${after.y - before.y}) " +
                            "visible=${win.isVisible} state=${win.extendedState}",
                    )
                    runCatching {
                        val img = java.awt.Robot().createScreenCapture(win.bounds)
                        val out = java.io.File(System.getProperty("java.io.tmpdir"), "a7-drag.png")
                        javax.imageio.ImageIO.write(img, "png", out)
                        appLog("测试拖拽：截图 = " + out.absolutePath)
                    }
                    win.isAlwaysOnTop = false
                }
            }

            // 开发用：用 Robot 点击标题栏空白处（标题右侧、按钮左侧），复现「点空白就消失」
            LaunchedEffect(Unit) {
                if (args.contains("--test-titlebar")) {
                    kotlinx.coroutines.delay(3000)
                    runCatching {
                        val b = win.bounds
                        val robot = java.awt.Robot()
                        // 标题栏同一行、按钮左侧的空白区域，横向点三处
                        val offsets = intArrayOf(-420, -300, -180)
                        for (dx in offsets) {
                            val x = b.x + b.width + dx
                            val y = b.y + 24
                            robot.mouseMove(x, y)
                            Thread.sleep(150)
                            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                            Thread.sleep(90)
                            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                            Thread.sleep(600)
                            appLog(
                                "测试：点击($x,$y) 后 visible=${win.isVisible} " +
                                    "state=${win.extendedState} maximized=" +
                                    ((win.extendedState and java.awt.Frame.MAXIMIZED_BOTH) != 0),
                            )
                        }
                        val img = java.awt.Robot().createScreenCapture(win.bounds)
                        val out = java.io.File(System.getProperty("java.io.tmpdir"), "a7-titlebar.png")
                        javax.imageio.ImageIO.write(img, "png", out)
                        appLog("测试：点击后截图 = " + out.absolutePath)
                    }.onFailure { appLog("测试失败: " + it.message) }
                }
            }

            // 开发用：连续切换最大化 4 次，验证不会出现异常最小化
            LaunchedEffect(Unit) {
                if (args.contains("--test-max")) {
                    kotlinx.coroutines.delay(2500)
                    repeat(4) { i ->
                        val maximized =
                            (win.extendedState and java.awt.Frame.MAXIMIZED_BOTH) != 0
                        win.extendedState =
                            if (maximized) java.awt.Frame.NORMAL else java.awt.Frame.MAXIMIZED_BOTH
                        appLog("测试：第 ${i + 1} 次切换最大化（原状态=${if (maximized) "最大化" else "普通"}）")
                        kotlinx.coroutines.delay(1200)
                    }
                }
            }

            // 开发用：模拟「窗口被最小化后从托盘打开」，验证恢复逻辑
            LaunchedEffect(Unit) {
                if (args.contains("--test-restore")) {
                    kotlinx.coroutines.delay(2500)
                    appLog("测试：手动最小化窗口")
                    win.isMinimized = true
                    kotlinx.coroutines.delay(1500)
                    appLog("测试：模拟点击托盘打开")
                    isVisible = true
                    showToken++
                }
            }

            // 显隐：只在这里改 AWT 状态，避免 Compose 的 visible 参数与手动操作互相打架
            LaunchedEffect(isVisible) {
                if (isVisible) {
                    win.isVisible = true
                } else {
                    win.isVisible = false
                    appLog("窗口已隐藏")
                }
            }

            // 从托盘打开时强制恢复（含取消最小化 / 置前 / 抢焦点）
            LaunchedEffect(showToken) {
                if (showToken == 0) return@LaunchedEffect
                win.isMinimized = false
                ensureOnScreen(win)
                win.isVisible = true
                win.toFront()
                win.requestFocus()
                appLog("托盘恢复窗口 #$showToken（minimized=${win.isMinimized}, visible=${win.isVisible}）")
            }

            // 开发用：--screenshot 时把本窗口截图存到临时目录（便于无交互验证 UI）
            LaunchedEffect(Unit) {
                if (args.contains("--screenshot")) {
                    kotlinx.coroutines.delay(3000)
                    runCatching {
                        win.isAlwaysOnTop = true
                        win.toFront()
                        win.requestFocus()
                        Thread.sleep(900)
                        val img = java.awt.Robot().createScreenCapture(win.bounds)
                        val out = java.io.File(System.getProperty("java.io.tmpdir"), "a7-window.png")
                        javax.imageio.ImageIO.write(img, "png", out)
                        appLog("截图已保存: " + out.absolutePath)
                        win.isAlwaysOnTop = false
                    }.onFailure { appLog("截图失败: " + it.message) }
                }
            }

            App(
                settings = settings,
                monitor = monitor,
                battery = battery,
                updateInfo = updateInfo,
                titleBar = {
                    AppTitleBar(
                        window = win,
                        title = "A7 电量助手",
                        subtitle = "v$APP_VERSION",
                        isMaximized = isMax,
                        onMinimize = {
                            // 真正最小化：任务栏保留按钮；托盘「打开面板」会强制还原
                            appLog("点击最小化 → 最小化到任务栏")
                            win.isMinimized = true
                        },
                        onToggleMaximize = {
                            // 必须以窗口真实状态为依据，不能用自己维护的布尔值（会与系统状态错位）
                            val maximized =
                                (win.extendedState and java.awt.Frame.MAXIMIZED_BOTH) != 0
                            win.extendedState =
                                if (maximized) java.awt.Frame.NORMAL
                                else java.awt.Frame.MAXIMIZED_BOTH
                            appLog("切换最大化：当前=${if (maximized) "最大化" else "普通"} → ${if (maximized) "普通" else "最大化"}")
                        },
                        onClose = {
                            appLog("点击关闭 → 隐藏窗口")
                            isVisible = false
                        },
                    )
                },
                onExitRequested = {
                    tray?.close()
                    monitor.stop()
                    exitApplication()
                },
                initialPage = initialPage,
            )
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
                win.addWindowFocusListener(object : java.awt.event.WindowAdapter() {
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                        menuVisible = false
                    }
                })
            }
            // 每次弹出时移动到鼠标位置（托盘在屏幕底部，所以往上弹）
            LaunchedEffect(menuVisible) {
                if (menuVisible) {
                    val p = java.awt.MouseInfo.getPointerInfo()?.location ?: java.awt.Point(100, 100)
                    val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
                    val x = p.x.coerceIn(0, (screen.width - win.width).coerceAtLeast(0))
                    val y = (p.y - win.height - 8).coerceAtLeast(0)
                    win.setLocation(x, y)
                    win.toFront()
                    win.requestFocus()
                    appLog("托盘菜单已显示 @ $x,$y")
                }
            }
            TrayMenu(
                onOpen = { menuVisible = false; isVisible = true },
                onRefresh = { menuVisible = false; monitor.refresh() },
                onExit = {
                    menuVisible = false
                    monitor.stop()
                    javax.swing.SwingUtilities.invokeLater { exitApplication() }
                },
            )
        }
    }
}

// ---------------- 自绘标题栏 ----------------

@Composable
private fun AppTitleBar(
    window: java.awt.Window,
    title: String,
    subtitle: String,
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MiuixTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .dragWindow(window) { onToggleMaximize() },
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        WindowButton(onClick = onMinimize) { c ->
            strokeLine(c, Offset(size.width * 0.15f, size.height / 2f), Offset(size.width * 0.85f, size.height / 2f), 1.6f)
        }
        WindowButton(onClick = onToggleMaximize) { c ->
            if (isMaximized) {
                drawRect(c, Offset(size.width * 0.08f, size.height * 0.3f), Size(size.width * 0.58f, size.height * 0.58f), style = Stroke(1.5f))
                drawRect(c, Offset(size.width * 0.3f, size.height * 0.08f), Size(size.width * 0.58f, size.height * 0.58f), style = Stroke(1.5f))
            } else {
                drawRect(c, Offset(size.width * 0.15f, size.height * 0.15f), Size(size.width * 0.7f, size.height * 0.7f), style = Stroke(1.5f))
            }
        }
        WindowButton(onClick = onClose, isClose = true) { c ->
            strokeLine(c, Offset(size.width * 0.22f, size.height * 0.22f), Offset(size.width * 0.78f, size.height * 0.78f), 1.6f)
            strokeLine(c, Offset(size.width * 0.78f, size.height * 0.22f), Offset(size.width * 0.22f, size.height * 0.78f), 1.6f)
        }
    }
}

/**
 * 拖动标题栏移动窗口；双击标题栏切换最大化。
 *
 * 位移必须换算成「真实屏幕指针位置」再定位窗口：
 * 直接用 detectDragGestures 的 dragAmount 累加 window.location 会形成正反馈
 * （窗口一动，指针在窗口内的局部坐标也跟着变，下一帧把这段位移又算一遍），
 * 结果是窗口越拖越快、飞出屏幕 —— 表现就是「一拖窗口就消失，任务栏仍有图标」。
 *
 * 屏幕指针 = 窗口位置 + 节点在窗口内的偏移 + 事件局部坐标，
 * 这个值不受我们移动窗口的影响，因此没有反馈环。
 */
@Composable
private fun Modifier.dragWindow(
    window: java.awt.Window,
    onDoubleClick: () -> Unit,
): Modifier {
    val offsetInWindow = remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { offsetInWindow.value = it.positionInWindow() }
        .pointerInput(window) {
            detectTapGestures(onDoubleTap = { onDoubleClick() })
        }
        .pointerInput(window) {
            var grabX = 0f
            var grabY = 0f
            detectDragGestures(
                onDragStart = { pos ->
                    // 指针相对窗口左上角的偏移，拖拽过程中保持不变
                    grabX = offsetInWindow.value.x + pos.x
                    grabY = offsetInWindow.value.y + pos.y
                },
                onDrag = { change, _ ->
                    change.consume()
                    val screenX = window.x + offsetInWindow.value.x + change.position.x
                    val screenY = window.y + offsetInWindow.value.y + change.position.y
                    val (nx, ny) = clampToScreen(
                        window,
                        (screenX - grabX).toInt(),
                        (screenY - grabY).toInt(),
                    )
                    window.setLocation(nx, ny)
                },
            )
        }
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
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        hovered && isClose -> Color(0xFFE5484D)
        hovered -> MiuixTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    val stroke = if (hovered && isClose) Color.White else MiuixTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(11.dp)) {
            draw(stroke)
        }
    }
}

/** 线条两端圆头。 */
private fun DrawScope.strokeLine(color: Color, start: Offset, end: Offset, width: Float) {
    drawLine(color = color, start = start, end = end, strokeWidth = width, cap = StrokeCap.Round)
}

// ---------------- 托盘右键菜单 ----------------

@Composable
private fun TrayMenu(
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(vertical = 6.dp),
    ) {
        TrayMenuItem("打开面板", onOpen)
        TrayMenuItem("立即刷新", onRefresh)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(1.dp)
                .background(MiuixTheme.colorScheme.dividerLine),
        )
        TrayMenuItem("退出", onExit)
    }
}

@Composable
private fun TrayMenuItem(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (hovered) MiuixTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
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

/** 给无边框窗口加 Windows 11 圆角（失败不影响功能）。 */
private fun applyRoundedCorners(window: java.awt.Window) {
    val hwnd = com.sun.jna.Native.getWindowPointer(window) ?: return
    val dwm = com.sun.jna.Native.load("dwmapi", DwmApi::class.java)
    val pref = com.sun.jna.ptr.IntByReference(2) // DWMWCP_ROUND
    dwm.DwmSetWindowAttribute(hwnd, 33, pref, 4) // DWMWA_WINDOW_CORNER_PREFERENCE
}
