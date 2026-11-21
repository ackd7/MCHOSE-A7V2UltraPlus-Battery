package top.ackd7.mchose.services

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * 原生托盘图标（基于 JDK 内置 java.awt.SystemTray）。
 *
 * 视觉样式：1:1 圆角正方形透明边框 + 超大号数字，充分利用 Windows 托盘图标格子空间：
 * - 透明内底透出任务栏，边框按状态变色（高电量蓝 / 充电绿 / 中电量琥珀 / 低电量红 / 休眠灰）
 * - 充电时右上角显示翠绿圆形闪电角标，不挤占中央数字空间
 * - 自动读取 Windows 任务栏主题（SystemUsesLightTheme）切换深浅文字颜色
 * - 休眠 / 未连接时居中显示 `Zzz`
 *
 * 右键菜单由 Compose 自绘窗口实现，规避 jpackage 裁剪 JRE 下 AWT 菜单中文变方块的问题。
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
        trayIcon.toolTip = top.ackd7.mchose.APP_NAME

        var lastOpenAt = 0L
        fun triggerOpen() {
            val now = System.currentTimeMillis()
            if (now - lastOpenAt < 180L) return
            lastOpenAt = now
            onOpen?.invoke()
        }

        // 用 mouseReleased 替代 mouseClicked：Windows 托盘图标点击稍有抖动就会丢弃 mouseClicked
        trayIcon.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                when (e.button) {
                    MouseEvent.BUTTON1 -> triggerOpen()
                    MouseEvent.BUTTON3 -> onMenu?.invoke()
                }
            }
        })
        // 兼容双击或键盘激活托盘图标
        trayIcon.addActionListener { triggerOpen() }

        SystemTray.getSystemTray().add(trayIcon)
    }

    /** 托盘气泡通知（低电量提醒等）。 */
    fun notify(title: String, text: String) {
        runCatching { trayIcon.displayMessage(title, text, TrayIcon.MessageType.WARNING) }
    }

    fun setIcon(percent: Int?, charging: Boolean, tip: String) {
        trayIcon.image = createIcon(percent, charging)
        trayIcon.toolTip = tip
    }

    private fun isLightTaskbar(): Boolean = runCatching {
        Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "SystemUsesLightTheme",
        ) != 0
    }.getOrDefault(false)

    private fun createIcon(percent: Int?, charging: Boolean): BufferedImage {
        val size = 64
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

        val lightTaskbar = isLightTaskbar()
        val sleeping = percent == null

        val borderColor = when {
            sleeping && lightTaskbar -> Color(0x64, 0x74, 0x8B)
            sleeping -> Color(0x94, 0xA3, 0xB8)
            charging -> Color(0x22, 0xC5, 0x5E)
            percent >= 50 -> accent
            percent >= 20 -> Color(0xF5, 0x9E, 0x0B)
            else -> Color(0xEF, 0x44, 0x44)
        }
        val textColor = when {
            sleeping -> borderColor
            lightTaskbar -> Color(0x1E, 0x24, 0x30)
            else -> Color.WHITE
        }

        // 1. 撑满 1:1 格子的圆角正方形透明边框
        val inset = 4.5
        val side = size - inset * 2.0
        val cornerArc = 18.0
        val frame = RoundRectangle2D.Double(inset, inset, side, side, cornerArc, cornerArc)
        g.color = borderColor
        g.stroke = BasicStroke(5.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(frame)

        // 2. 超大号视觉居中文字（数字或 Zzz）
        if (percent != null) {
            val pct = percent.coerceIn(0, 100)
            val label = pct.toString()
            val fontSize = if (pct >= 100) 24 else 32
            // 充电角标在右上角时，数字略向左下微偏 1px 视觉更平衡
            val shiftX = if (charging) -1.2 else 0.0
            val shiftY = if (charging) 1.2 else 0.0
            drawVisuallyCentered(g, label, Font(Font.SANS_SERIF, Font.BOLD, fontSize), textColor, size / 2.0 + shiftX, size / 2.0 + shiftY)

            // 3. 充电状态：右上角紧凑翠绿圆形闪电角标（带透明隔离切口，不挤占数字）
            if (charging) {
                val cx = 52.0
                val cy = 12.0
                val cutR = 13.0
                val badgeR = 10.5

                val oldComp = g.composite
                g.composite = AlphaComposite.Clear
                g.fill(Ellipse2D.Double(cx - cutR, cy - cutR, cutR * 2, cutR * 2))
                g.composite = oldComp

                g.color = Color(0x22, 0xC5, 0x5E)
                g.fill(Ellipse2D.Double(cx - badgeR, cy - badgeR, badgeR * 2, badgeR * 2))

                g.color = if (lightTaskbar) Color.WHITE else Color(0x11, 0x18, 0x27)
                g.fillPolygon(
                    intArrayOf(53, 47, 51, 50, 57, 53),
                    intArrayOf(5, 13, 13, 19, 11, 11),
                    6,
                )
            }
        } else {
            drawVisuallyCentered(g, "Zzz", Font(Font.SANS_SERIF, Font.BOLD, 24), textColor, size / 2.0, size / 2.0)
        }

        g.dispose()
        return img
    }

    /** 使用字形真实视觉外接框居中绘制，避免基线偏移导致数字看起来偏低。 */
    private fun drawVisuallyCentered(
        g: Graphics2D,
        text: String,
        font: Font,
        color: Color,
        centerX: Double,
        centerY: Double,
    ) {
        g.font = font
        g.color = color
        val gv = font.createGlyphVector(g.fontRenderContext, text)
        val vb = gv.visualBounds
        val x = (centerX - vb.width / 2.0 - vb.x).toFloat()
        val y = (centerY - vb.height / 2.0 - vb.y).toFloat()
        g.drawGlyphVector(gv, x, y)
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
