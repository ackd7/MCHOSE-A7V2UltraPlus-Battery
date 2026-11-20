package top.ackd7.a7battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

const val APP_VERSION = "2.0.4"

/** 轻量日志：写入 %LOCALAPPDATA%\A7Battery\app.log，便于排查启动异常。 */
internal fun appLog(msg: String) {
    runCatching {
        val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        val dir = java.io.File(base, "A7Battery").apply { mkdirs() }
        java.io.File(dir, "app.log").appendText("[${java.time.LocalDateTime.now()}] $msg\n")
    }
}

fun main(args: Array<String>) {
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

        val windowState = rememberWindowState(size = DpSize(420.dp, 720.dp))
        val menuState = rememberWindowState(size = DpSize(180.dp, 148.dp))

        // 所有副作用只做一次
        LaunchedEffect(Unit) {
            try {
                val t = NativeTrayIcon()
                t.accent = java.awt.Color(0x34, 0x82, 0xFF)
                t.onOpen = { menuVisible = false; isVisible = true }
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
            monitor.onReadingChanged = { info ->
                battery = info
                val brief = "ok=${info.success} pct=${info.percent} err=${info.error}"
                if (brief != lastLogged) {
                    lastLogged = brief
                    appLog("读数: $brief")
                }
                val t = tray
                if (t != null) {
                    javax.swing.SwingUtilities.invokeLater {
                        if (settings.showPercentInTray && info.success && info.percent >= 0) {
                            t.setIcon(
                                info.percent,
                                info.charging,
                                "迈从 A7 V2 Ultra+  ${info.percent}%  ${if (info.charging) "充电中" else "放电中"}",
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
            visible = isVisible,
            undecorated = true,
        ) {
            val win = window
            var isMax by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                runCatching { applyRoundedCorners(win) }
                // 去掉 Swing 默认边框，避免无边框窗口右/下缘出现一条细线
                runCatching { win.rootPane.border = null }
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
                        onMinimize = { win.isMinimized = true },
                        onToggleMaximize = {
                            isMax = !isMax
                            win.extendedState =
                                if (isMax) java.awt.Frame.NORMAL else java.awt.Frame.MAXIMIZED_BOTH
                        },
                        onClose = { isVisible = false },
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

/** 拖动标题栏移动窗口；双击切换最大化。 */
private fun Modifier.dragWindow(
    window: java.awt.Window,
    onDoubleClick: () -> Unit,
): Modifier = this
    .pointerInput(window) {
        detectTapGestures(onDoubleTap = { onDoubleClick() })
    }
    .pointerInput(window) {
        detectDragGestures { change, drag ->
            change.consume()
            window.setLocation(window.x + drag.x.toInt(), window.y + drag.y.toInt())
        }
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
            .padding(horizontal = 2.dp)
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
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

/** 给无边框窗口加 Windows 11 圆角（失败不影响功能）。 */
private fun applyRoundedCorners(window: java.awt.Window) {
    val hwnd = com.sun.jna.Native.getWindowPointer(window) ?: return
    val dwm = com.sun.jna.Native.load("dwmapi", DwmApi::class.java)
    val pref = com.sun.jna.ptr.IntByReference(2) // DWMWCP_ROUND
    dwm.DwmSetWindowAttribute(hwnd, 33, pref, 4) // DWMWA_WINDOW_CORNER_PREFERENCE
}
