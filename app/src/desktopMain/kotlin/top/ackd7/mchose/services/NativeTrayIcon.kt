package top.ackd7.mchose.services

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.AbstractMultiResolutionImage
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * 原生托盘图标（基于 JDK 内置 java.awt.SystemTray）。
 *
 * 4K / 高 DPI 清晰度关键优化：
 * JDK 的 WTrayIconPeer.updateNativeImage 会把普通图像按默认最近邻算法硬缩放到
 * `round(16 * scale)`（例如 4K 175% 屏为 28×28），导致 64×64 图片被严重压糊。
 * 这里使用 [AbstractMultiResolutionImage] 并按真实物理像素 1:1 精确绘制，
 * 让 WTrayIconPeer 执行零缩放像素直拷，同时收紧外边距并放大字号，在 4K 屏下锐利饱满。
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
        trayIcon = TrayIcon(createTrayImage(null, false))
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

    private var iconInitialized = false
    private var lastPercent: Int? = null
    private var lastCharging: Boolean = false
    private var lastLightTaskbar: Boolean = false
    private var lastNativeSize: Int = -1
    private var lastAccent: Color = accent

    /** 托盘气泡通知（低电量提醒等）。 */
    fun notify(title: String, text: String) {
        runCatching { trayIcon.displayMessage(title, text, TrayIcon.MessageType.WARNING) }
    }

    fun setIcon(percent: Int?, charging: Boolean, tip: String) {
        val lightTaskbar = isLightTaskbar()
        val nativeSize = nativeTrayPixelSize()
        val curAccent = accent
        if (!iconInitialized ||
            percent != lastPercent ||
            charging != lastCharging ||
            lightTaskbar != lastLightTaskbar ||
            nativeSize != lastNativeSize ||
            curAccent != lastAccent
        ) {
            iconInitialized = true
            lastPercent = percent
            lastCharging = charging
            lastLightTaskbar = lightTaskbar
            lastNativeSize = nativeSize
            lastAccent = curAccent
            trayIcon.image = createTrayImage(percent, charging, lightTaskbar, nativeSize)
        }
        if (trayIcon.toolTip != tip) {
            trayIcon.toolTip = tip
        }
    }

    private fun isLightTaskbar(): Boolean = runCatching {
        Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "SystemUsesLightTheme",
        ) != 0
    }.getOrDefault(false)

    /** 计算当前主屏托盘图标的真实物理像素尺寸（与 JDK WTrayIconPeer 完全一致）。 */
    private fun nativeTrayPixelSize(): Int = runCatching {
        val tx = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.defaultTransform
        val scale = maxOf(tx.scaleX, tx.scaleY).coerceAtLeast(1.0)
        (16.0 * scale).roundToInt().coerceIn(16, 64)
    }.getOrDefault(32)

    private fun createTrayImage(
        percent: Int?,
        charging: Boolean,
        lightTaskbar: Boolean = isLightTaskbar(),
        nativeSize: Int = nativeTrayPixelSize(),
    ): Image {
        val accentCopy = accent
        return object : AbstractMultiResolutionImage() {
            private val cache = HashMap<Int, BufferedImage>(2)

            override fun getBaseImage(): Image =
                cache.getOrPut(nativeSize) { renderAtSize(nativeSize, percent, charging, lightTaskbar, accentCopy) }

            override fun getResolutionVariant(destImageWidth: Double, destImageHeight: Double): Image {
                val target = maxOf(destImageWidth, destImageHeight).roundToInt().coerceIn(16, 128)
                return cache.getOrPut(target) { renderAtSize(target, percent, charging, lightTaskbar, accentCopy) }
            }

            override fun getResolutionVariants(): List<Image> = listOf(baseImage)
        }
    }

    override fun close() {
        if (disposed) return
        disposed = true
        try {
            SystemTray.getSystemTray().remove(trayIcon)
        } catch (_: Exception) {
        }
    }

    companion object {
        private val preferredFontFamily: String by lazy {
            val available = runCatching {
                GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
            }.getOrDefault(emptySet())
            when {
                "Segoe UI Variable Display" in available -> "Segoe UI Variable Display"
                "Segoe UI" in available -> "Segoe UI"
                else -> Font.SANS_SERIF
            }
        }

        /**
         * 按指定物理像素尺寸 [size] 1:1 精确渲染圆角正方形透明边框 + 超大号数字托盘图标。
         */
        internal fun renderAtSize(
            size: Int,
            percent: Int?,
            charging: Boolean,
            lightTaskbar: Boolean,
            accent: Color = Color(0x34, 0x82, 0xFF),
        ): BufferedImage {
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

            val s = size.toDouble()
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
                lightTaskbar -> Color(0x11, 0x18, 0x27)
                else -> Color.WHITE
            }

            // 1. 紧贴物理像素边缘的圆角正方形透明边框（最大化利用 1:1 托盘格子）
            val strokePx = (s * 0.082).roundToInt().coerceAtLeast(2).toFloat()
            val halfStroke = strokePx / 2.0
            val inset = halfStroke + 0.25
            val side = s - inset * 2.0
            val cornerArc = s * 0.30
            val frame = RoundRectangle2D.Double(inset, inset, side, side, cornerArc, cornerArc)
            g.color = borderColor
            g.stroke = BasicStroke(strokePx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(frame)

            // 内部可用净宽高：留出充足的内边距呼吸感，避免数字贴近边框
            val innerMargin = strokePx + (s * 0.145)
            val maxTextW = s - innerMargin * 2.0
            val maxTextH = s - innerMargin * 2.0

            // 2. 自适应清晰数字（或 Zzz），与四周边框保持舒适间距
            if (percent != null) {
                val pct = percent.coerceIn(0, 100)
                val label = pct.toString()
                val targetHeightRatio = if (pct >= 100) 0.56 else 0.72
                val tracking = if (pct >= 100) -0.04f else -0.01f
                val font = fitFont(g, label, maxTextW, maxTextH * targetHeightRatio, tracking)

                val shiftX = if (charging) -s * 0.035 else 0.0
                val shiftY = if (charging) s * 0.035 else 0.0
                drawVisuallyCentered(g, label, font, textColor, s / 2.0 + shiftX, s / 2.0 + shiftY)

                // 3. 充电状态：右上角紧凑翠绿圆形闪电角标（带透明隔离切口，零挤压数字）
                if (charging) {
                    val badgeR = (s * 0.175).coerceAtLeast(3.0)
                    val cutR = badgeR + (s * 0.035).coerceAtLeast(0.8)
                    val cx = s - badgeR - 0.3
                    val cy = badgeR + 0.3

                    val oldComp = g.composite
                    g.composite = AlphaComposite.Clear
                    g.fill(Ellipse2D.Double(cx - cutR, cy - cutR, cutR * 2, cutR * 2))
                    g.composite = oldComp

                    g.color = Color(0x22, 0xC5, 0x5E)
                    g.fill(Ellipse2D.Double(cx - badgeR, cy - badgeR, badgeR * 2, badgeR * 2))

                    // 按角标半径比例绘制清晰闪电
                    val r = badgeR * 0.68
                    val bx = doubleArrayOf(
                        cx + r * 0.15,
                        cx - r * 0.55,
                        cx - r * 0.02,
                        cx - r * 0.18,
                        cx + r * 0.58,
                        cx + r * 0.04,
                    )
                    val by = doubleArrayOf(
                        cy - r * 0.95,
                        cy + r * 0.08,
                        cy + r * 0.08,
                        cy + r * 0.95,
                        cy - r * 0.08,
                        cy - r * 0.08,
                    )
                    g.color = if (lightTaskbar) Color.WHITE else Color(0x0F, 0x17, 0x2A)
                    g.fillPolygon(
                        bx.map { it.roundToInt() }.toIntArray(),
                        by.map { it.roundToInt() }.toIntArray(),
                        6,
                    )
                }
            } else {
                val font = fitFont(g, "Zzz", maxTextW, maxTextH * 0.54, -0.04f)
                drawVisuallyCentered(g, "Zzz", font, textColor, s / 2.0, s / 2.0)
            }

            g.dispose()
            return img
        }

        /** 按目标视觉外接框精确计算最大可用粗体字号，保证大而不拥挤、不裁边。 */
        private fun fitFont(
            g: Graphics2D,
            text: String,
            maxW: Double,
            maxH: Double,
            tracking: Float,
        ): Font {
            val attrs = mapOf(
                TextAttribute.FAMILY to preferredFontFamily,
                TextAttribute.WEIGHT to TextAttribute.WEIGHT_BOLD,
                TextAttribute.TRACKING to tracking,
            )
            var sizePx = (maxH * 1.45).toFloat().coerceAtLeast(8f)
            while (sizePx > 7f) {
                val f = Font(attrs).deriveFont(sizePx)
                val vb = f.createGlyphVector(g.fontRenderContext, text).visualBounds
                if (vb.width <= maxW && vb.height <= maxH) return f
                sizePx -= 0.5f
            }
            return Font(attrs).deriveFont(7f)
        }

        /** 使用字形真实视觉外接框居中绘制，消除基线偏移。 */
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
    }
}
