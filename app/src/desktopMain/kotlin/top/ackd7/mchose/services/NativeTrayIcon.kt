package top.ackd7.mchose.services

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * 原生托盘图标（基于 JDK 内置 java.awt.SystemTray）。
 *
 * 视觉风格参考 HyperOS 透明胶囊电池：
 * - 外壳为全圆角胶囊描边 + 右侧微小正极凸起，未填充区域完全透明（透出深/浅任务栏）
 * - 左侧按实时电量百分比比例填充状态色（高电量蓝 / 充电绿 / 中电量琥珀 / 低电量红）
 * - 自动读取 Windows 任务栏主题（SystemUsesLightTheme）适配描边与交界反色文字
 * - 休眠 / 未连接状态显示透明灰胶囊与 `Zzz`
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

        val lightTaskbar = isLightTaskbar()
        val sleeping = percent == null

        // 外壳几何（64x64 透明画布居中横置全圆角胶囊 + 右侧电池正极凸起）
        val bodyX = 3.0
        val bodyY = 14.0
        val bodyW = 52.0
        val bodyH = 36.0
        val outerCapsule = RoundRectangle2D.Double(bodyX, bodyY, bodyW, bodyH, bodyH, bodyH)

        val outlineColor = when {
            sleeping && lightTaskbar -> Color(0x64, 0x74, 0x8B)
            sleeping -> Color(0x94, 0xA3, 0xB8)
            lightTaskbar -> Color(0x27, 0x2E, 0x3A)
            else -> Color(0xEE, 0xF2, 0xF8)
        }

        // 1. 先画右侧电池正极小凸起
        g.color = outlineColor
        g.fill(RoundRectangle2D.Double(56.5, 25.0, 5.0, 14.0, 4.0, 4.0))

        // 2. 按真实电量百分比填充内部透明胶囊区域（未填充部分保持完全透明）
        val inset = 4.2
        val innerX = bodyX + inset
        val innerY = bodyY + inset
        val innerW = bodyW - inset * 2.0
        val innerH = bodyH - inset * 2.0
        val innerCapsule = RoundRectangle2D.Double(innerX, innerY, innerW, innerH, innerH, innerH)

        var fillRightX = innerX
        if (percent != null) {
            val pct = percent.coerceIn(0, 100)
            val fillColor = when {
                charging -> Color(0x22, 0xC5, 0x5E) // 充电中：翠绿
                pct >= 50 -> accent                 // 高电量：科技蓝
                pct >= 20 -> Color(0xF5, 0x9E, 0x0B) // 中电量：琥珀黄
                else -> Color(0xEF, 0x44, 0x44)      // 低电量：珊瑚红
            }
            val fillW = innerW * (pct / 100.0)
            fillRightX = innerX + fillW
            if (fillW > 0.5) {
                val oldClip: Shape? = g.clip
                g.clip(innerCapsule)
                g.color = fillColor
                g.fill(Rectangle2D.Double(innerX, innerY, fillW, innerH))
                g.clip = oldClip
            }
        }

        // 3. 画外层纤细胶囊描边
        g.color = outlineColor
        g.stroke = BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(outerCapsule)

        // 4. 绘制文字与闪电（浅色任务栏下在填充区/透明区交界处自动反色，深色任务栏统一纯白）
        if (percent != null) {
            val pct = percent.coerceIn(0, 100)
            val label = pct.toString()
            val fontSize = when {
                pct >= 100 && charging -> 17
                pct >= 100 -> 19
                charging -> 21
                else -> 24
            }
            g.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize)
            val fm = g.fontMetrics
            val textW = fm.stringWidth(label)
            val boltW = if (charging) 11 else 0
            val gap = if (charging) 2 else 0
            val totalW = textW + gap + boltW
            val startX = (bodyX + (bodyW - totalW) / 2.0).toInt()
            val baselineY = (bodyY + (bodyH - fm.height) / 2.0 + fm.ascent).toInt()

            fun drawGlyphs(color: Color) {
                g.color = color
                g.drawString(label, startX, baselineY)
                if (charging) {
                    val bx = startX + textW + gap
                    val by = (bodyY + (bodyH - 18.0) / 2.0).toInt()
                    g.fillPolygon(
                        intArrayOf(bx + 7, bx + 1, bx + 5, bx + 4, bx + 10, bx + 6),
                        intArrayOf(by, by + 10, by + 10, by + 18, by + 8, by + 8),
                        6,
                    )
                }
            }

            if (lightTaskbar) {
                val oldClip: Shape? = g.clip
                // 左侧已填充区域用白字
                g.clip(Rectangle2D.Double(0.0, 0.0, fillRightX, size.toDouble()))
                drawGlyphs(Color.WHITE)
                // 右侧透明区域（透出浅色任务栏）用深炭灰字
                g.clip = oldClip
                g.clip(Rectangle2D.Double(fillRightX, 0.0, size - fillRightX, size.toDouble()))
                drawGlyphs(Color(0x1E, 0x24, 0x30))
                g.clip = oldClip
            } else {
                drawGlyphs(Color.WHITE)
            }
        } else {
            // 休眠 / 未连接：透明胶囊居中显示 Zzz
            g.color = outlineColor
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 19)
            val fm = g.fontMetrics
            val label = "Zzz"
            val tx = (bodyX + (bodyW - fm.stringWidth(label)) / 2.0).toInt()
            val ty = (bodyY + (bodyH - fm.height) / 2.0 + fm.ascent).toInt()
            g.drawString(label, tx, ty)
        }

        g.dispose()
        return img
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
