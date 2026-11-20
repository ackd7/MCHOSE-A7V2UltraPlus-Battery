package top.ackd7.a7battery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import top.ackd7.a7battery.services.AppSettings
import top.ackd7.a7battery.services.BatteryMonitor
import top.ackd7.a7battery.services.NativeTrayIcon
import top.ackd7.a7battery.services.UpdateChecker
import top.ackd7.a7battery.services.UpdateInfo
import top.ackd7.a7battery.ui.App
import java.awt.Window

const val APP_VERSION = "2.0.0"

fun main(args: Array<String>) {
    val settings = AppSettings.load()
    val monitor = BatteryMonitor()
    val startMinimized = args.contains("--minimized")

    application {
        var tray: NativeTrayIcon? = null
        var updateInfo by mutableStateOf(UpdateInfo())
        // 保存主窗口引用，供托盘回调重新显示窗口
        var mainWindow: Window? = null

        val windowState = rememberWindowState(size = DpSize(420.dp, 720.dp))

        Window(
            onCloseRequest = {
                // 关闭按钮只隐藏窗口；完全退出用托盘右键「退出」
                this.window.isVisible = false
            },
            title = "A7 电量助手",
            state = windowState,
            visible = !startMinimized,
        ) {
            // 捕获当前 ComposeWindow 引用（AWT Window 子类）
            mainWindow = this.window

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

        // 监控回调 → 刷新托盘（托盘图标绘制需在 EDT 上执行）
        monitor.onReadingChanged = { info ->
            val t = tray ?: return@onReadingChanged
            javax.swing.SwingUtilities.invokeLater {
                val showPercent = settings.showPercentInTray
                if (showPercent && info.success && info.percent >= 0) {
                    t.setIcon(
                        info.percent,
                        info.charging,
                        "迈从 A7 V2 Ultra+  ${info.percent}%  ${if (info.charging) "充电中" else "放电中"}"
                    )
                } else {
                    t.setIcon(null, false, "A7 电量助手（设备休眠/未连接）")
                }
            }
        }
        monitor.start(settings.refreshSeconds)

        // 创建托盘
        try {
            tray = NativeTrayIcon().apply {
                accent = java.awt.Color(0x34, 0x82, 0xFF)
                onOpen = {
                    javax.swing.SwingUtilities.invokeLater {
                        mainWindow?.isVisible = true
                        mainWindow?.toFront()
                    }
                }
                onRefresh = { monitor.refresh() }
                onExit = {
                    monitor.stop()
                    javax.swing.SwingUtilities.invokeLater { exitApplication() }
                }
            }
            tray?.setIcon(null, false, "A7 电量助手")
        } catch (_: Exception) {
            // 托盘失败不影响主程序
        }

        // 自动更新检查（每天首次启动）
        if (settings.checkUpdates) {
            val today = java.time.LocalDate.now().toString()
            if (today != settings.lastUpdateCheck) {
                try {
                    val info = UpdateChecker().check(APP_VERSION)
                    settings.lastUpdateCheck = today
                    settings.save()
                    updateInfo = info
                } catch (_: Exception) {
                }
            }
        }
    }
}
