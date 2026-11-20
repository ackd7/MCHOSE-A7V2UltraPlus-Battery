package top.ackd7.a7battery.services

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage

/**
 * 原生托盘图标（基于 JDK 内置 java.awt.SystemTray）。
 *
 * 相比手写 Shell_NotifyIcon（JNA），SystemTray 由 JVM 维护消息循环，
 * 与 Compose Desktop 的 AWT 事件线程天然集成，稳定可靠。
 * 支持动态绘制电量百分比、左键回调、右键菜单回调。
 */
class NativeTrayIcon : AutoCloseable {

    private val trayIcon: TrayIcon
    private var disposed = false

    var accent: Color = Color(0x34, 0x82, 0xFF)

    var onOpen: (() -> Unit)? = null
    var onRefresh: (() -> Unit)? = null
    var onExit: (() -> Unit)? = null

    init {
        if (!SystemTray.isSupported()) {
            throw UnsupportedOperationException("系统托盘不可用")
        }
        trayIcon = TrayIcon(createIcon(null, false))
        trayIcon.isImageAutoSize = true
        trayIcon.toolTip = "A7 电量助手"

        // 左键点击打开面板
        trayIcon.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) {
                    onOpen?.invoke()
                }
            }
        })

        // 右键菜单
        // 注意：AWT 的 PopupMenu 默认字体（getFont() == null）在裁剪过的 JRE 里
        // 会回落到不含中文字形的原生字体，菜单中文会显示成方块。
        // 这里显式指定一个能显示中文的字体。
        val menuFont = cjkFont(12)
        val menu = PopupMenu().apply { font = menuFont }
        val itemOpen = MenuItem("打开面板").apply { font = menuFont; addActionListener { onOpen?.invoke() } }
        val itemRefresh = MenuItem("立即刷新").apply { font = menuFont; addActionListener { onRefresh?.invoke() } }
        val itemExit = MenuItem("退出").apply { font = menuFont; addActionListener { onExit?.invoke() } }
        menu.add(itemOpen)
        menu.add(itemRefresh)
        menu.addSeparator()
        menu.add(itemExit)
        trayIcon.popupMenu = menu

        SystemTray.getSystemTray().add(trayIcon)
    }

    /** 选一个能显示中文的字体（优先微软雅黑）。 */
    private fun cjkFont(size: Int): Font {
        val candidates = listOf("Microsoft YaHei UI", "Microsoft YaHei", "SimHei", "SimSun", "Dialog")
        val available = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        }.getOrDefault(emptySet())
        for (name in candidates) {
            if (name != "Dialog" && name !in available) continue
            val f = Font(name, Font.PLAIN, size)
            if (f.canDisplay('打')) return f
        }
        return Font(Font.DIALOG, Font.PLAIN, size)
    }

    fun setIcon(percent: Int?, charging: Boolean, tip: String) {
        trayIcon.image = createIcon(percent, charging)
        trayIcon.toolTip = tip
    }

    private fun createIcon(percent: Int?, charging: Boolean): BufferedImage {
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val color = when {
            percent == null -> Color(120, 140, 160)
            percent >= 50 -> accent
            percent >= 20 -> Color(255, 200, 61)
            else -> Color(255, 92, 92)
        }

        g.color = color
        g.fillRoundRect(1, 5, 30, 22, 10, 10)

        if (percent != null) {
            g.color = Color.WHITE
            g.font = Font("Segoe UI", Font.BOLD, 13)
            drawCentered(g, percent.toString(), 1, 5, 30, 22)
            if (charging) {
                g.font = Font("Segoe UI Symbol", Font.BOLD, 9)
                g.color = Color.YELLOW
                g.drawString("⚡", 22, 16)
            }
        } else {
            g.color = Color.WHITE
            g.font = Font("Segoe UI", Font.BOLD, 10)
            drawCentered(g, "A7", 1, 5, 30, 22)
        }
        g.dispose()
        return img
    }

    private fun drawCentered(g: Graphics2D, text: String, x: Int, y: Int, w: Int, h: Int) {
        val fm = g.fontMetrics
        val tx = x + (w - fm.stringWidth(text)) / 2
        val ty = y + (h - fm.ascent) / 2 + fm.ascent - fm.descent / 2
        g.drawString(text, tx, ty)
    }

    override fun close() {
        if (disposed) return
        disposed = true
        try {
            SystemTray.getSystemTray().remove(trayIcon)
        } catch (_: Exception) {
        }
    }
}
