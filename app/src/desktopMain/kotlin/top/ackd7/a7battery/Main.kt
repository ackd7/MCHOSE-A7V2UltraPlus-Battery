package top.ackd7.a7battery

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.ackd7.a7battery.services.AppSettings
import top.ackd7.a7battery.services.BatteryMonitor
import top.ackd7.a7battery.services.NativeTrayIcon
import top.ackd7.a7battery.services.UpdateChecker
import top.ackd7.a7battery.services.UpdateInfo
import top.ackd7.a7battery.ui.App

const val APP_VERSION = "2.0.2"

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

    application {
        var tray by remember { mutableStateOf<NativeTrayIcon?>(null) }
        var updateInfo by remember { mutableStateOf(UpdateInfo()) }
        // 控制窗口显示/隐藏的状态
        var isVisible by remember { mutableStateOf(!startMinimized) }

        val windowState = rememberWindowState(size = DpSize(420.dp, 720.dp))

        // 所有副作用只做一次（不能直接写在 application 块里，否则每次重组都会重跑）
        LaunchedEffect(Unit) {
            try {
                val t = NativeTrayIcon()
                t.accent = java.awt.Color(0x34, 0x82, 0xFF)
                t.onOpen = { isVisible = true }
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
                val brief = "ok=${info.success} pct=${info.percent} charging=${info.charging} mode=${info.mode} err=${info.error}"
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

            // 每天首次启动检查更新（网络请求放到 IO 线程，避免卡住 UI）
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
            onCloseRequest = {
                // 关闭按钮只隐藏窗口；完全退出用托盘右键「退出」
                isVisible = false
            },
            title = "A7 电量助手",
            state = windowState,
            visible = isVisible,
        ) {
            App(
                settings = settings,
                monitor = monitor,
                updateInfo = updateInfo,
                onExitRequested = {
                    tray?.close()
                    monitor.stop()
                    exitApplication()
                },
            )
        }
    }
}
