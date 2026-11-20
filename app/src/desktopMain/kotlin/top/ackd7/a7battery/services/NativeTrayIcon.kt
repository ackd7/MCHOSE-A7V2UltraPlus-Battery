package top.ackd7.a7battery.services

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage

/**
 * 原生托盘图标（基于 JDK 内置 java.awt.SystemTray）。
 *
 * 说明：右键菜单不再使用 AWT 的 PopupMenu —— 在 jpackage 裁剪过的 JRE 里
 * AWT 菜单会回落到不含中文字形的原生字体，中文会显示成方块（实测 getFont() 为 null）。
 * 这里只负责图标与点击事件，右键菜单由 Compose 自绘窗口实现（Skia 渲染中文正常）。
 */
class NativeTrayIcon : AutoCloseable {

    private val trayIcon: TrayIcon
    private var disposed = false

    var accent: Color = Color(0x34, 0x82, 0xFF)

    var onOpen: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    var onRefresh: (() -> Unit)? = null
    var onExit: (() -> Unit)? = null

    init {
        if (!SystemTray.isSupported()) {
            throw UnsupportedOperationException("系统托盘不可用")
        }
        trayIcon = TrayIcon(createIcon(null, false))
        trayIcon.isImageAutoSize = true
        trayIcon.toolTip = "A7 电量助手"

        trayIcon.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                when (e.button) {
                    MouseEvent.BUTTON1 -> onOpen?.invoke()
                    MouseEvent.BUTTON3 -> onMenu?.invoke()
                }
            }
        })

        SystemTray.getSystemTray().add(trayIcon)
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

        g.color = Color.WHITE
        if (percent != null) {
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 13)
            drawCentered(g, percent.toString(), 1, 5, 30, 22)
            if (charging) {
                // 手绘小闪电，避免依赖符号字体（裁剪 JRE 可能缺字形）
                g.fillPolygon(
                    intArrayOf(26, 22, 25, 24, 28, 25),
                    intArrayOf(8, 15, 15, 21, 13, 13),
                    6,
                )
            }
        } else {
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 10)
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
