package top.ackd7.mchose.ui.mochi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix × Mochi 设计语言 token：
 * 结合 HyperOS 柔和克制的界面分层与 iOS 26 液态玻璃折射边、语义状态色。
 */
data class MochiColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardHover: Color,
    val cardBorder: Color,
    val glassSurface: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val segmentTrack: Color,
    val segmentThumb: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val divider: Color,
    val fill: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

@Composable
fun rememberMochiColors(): MochiColors {
    val scheme = MiuixTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.35f
    return MochiColors(
        isDark = isDark,
        background = scheme.background,
        card = if (isDark) Color(0xFF1C1F26) else Color(0xFFFFFFFF),
        cardHover = if (isDark) Color(0xFF252A34) else Color(0xFFF3F6FA),
        cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
        glassSurface = if (isDark) Color(0xE01F232C) else Color(0xEBFFFFFF),
        glassBorderTop = if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.92f),
        glassBorderBottom = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.08f),
        segmentTrack = if (isDark) Color(0xFF13161C) else Color(0xFFEAEFF6),
        segmentThumb = if (isDark) Color(0xFF2B313E) else Color(0xFFFFFFFF),
        text = scheme.onSurface,
        secondary = scheme.onSurfaceVariantSummary,
        accent = Color(0xFF3482FF),
        divider = if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f),
        fill = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.055f),
        success = Color(0xFF22C55E),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFEF4444),
    )
}

/** 分组玻璃卡片：G2 连续超椭圆 + 顶沿高光折射边 + 可选 header/footer。 */
@Composable
fun MochiSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = rememberMochiColors()
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                text = header,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = c.secondary,
                modifier = Modifier.padding(start = 14.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .mochiGlassSurface(colors = c, cornerRadius = 22.dp, elevation = if (c.isDark) 8.dp else 10.dp),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                fontSize = 11.5.sp,
                color = c.secondary,
                modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp),
            )
        }
    }
}

/** 一行设置/信息：标题 / 副标题 / 右侧值或控件；带悬停柔光与底部发丝线。 */
@Composable
fun MochiRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    divider: Boolean = true,
    accent: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val clickable = onClick != null && enabled

    val rowBg by animateColorAsState(
        targetValue = when {
            pressed && clickable -> c.fill
            hovered && clickable -> c.cardHover
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rowBg",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
                .then(
                    if (onClick != null && enabled) {
                        Modifier
                            .hoverable(interaction)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = onClick,
                            )
                    } else {
                        Modifier
                    },
                )
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal,
                    color = when {
                        !enabled -> c.secondary.copy(alpha = 0.6f)
                        accent -> c.accent
                        else -> c.text
                    },
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = subtitle, fontSize = 12.sp, color = c.secondary)
                }
            }

            if (value != null) {
                Text(text = value, fontSize = 13.5.sp, color = c.secondary)
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (chevron) {
                Spacer(Modifier.width(8.dp))
                Chevron(color = c.secondary.copy(alpha = 0.72f))
            }
        }

        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(c.divider),
            )
        }
    }
}

/** 居中的轻量文字按钮行（如「立即刷新」或「退出程序」）。 */
@Composable
fun MochiButtonRow(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = when {
            pressed && enabled -> if (danger) c.danger.copy(alpha = 0.14f) else c.fill
            hovered && enabled -> if (danger) c.danger.copy(alpha = 0.08f) else c.cardHover
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "btnRowBg",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(bg)
            .then(
                if (enabled) {
                    Modifier
                        .hoverable(interaction)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                !enabled -> c.secondary.copy(alpha = 0.55f)
                danger -> c.danger
                else -> c.accent
            },
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 醒目的 G2 超椭圆胶囊主操作按钮（带液态渐变高光与果冻回弹按压手感）。
 */
@Composable
fun MochiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color? = null,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val base = accentColor ?: c.accent
    val shape = G2RoundedShape(14.dp)

    val bgBrush = if (enabled) {
        val top = if (hovered) lerpColor(base, Color.White, 0.12f) else lerpColor(base, Color.White, 0.05f)
        val bottom = lerpColor(base, Color.Black, 0.08f)
        Brush.verticalGradient(listOf(top, bottom))
    } else {
        Brush.verticalGradient(listOf(c.fill, c.fill))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .mochiPressScale(interaction, enabled = enabled, pressedScale = 0.97f)
            .then(
                if (enabled) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = base.copy(alpha = 0.28f),
                        spotColor = base.copy(alpha = 0.34f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(bgBrush)
            .border(
                1.dp,
                if (enabled) Color.White.copy(alpha = 0.25f) else c.cardBorder,
                shape,
            )
            .then(
                if (enabled) {
                    Modifier
                        .hoverable(interaction)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else c.secondary.copy(alpha = 0.65f),
        )
    }
}

/**
 * iOS 26 / Mochi 物理液态开关：
 * 48×28 胶囊轨道 + 按住时圆钮沿轨道方向弹性拉伸（22dp → 26dp），松开弹簧回弹。
 */
@Composable
fun MochiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "track",
    )
    val knobWidth by animateDpAsState(
        targetValue = if (pressed) 27.dp else 22.dp,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "knobW",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) (46.dp - knobWidth - 3.dp) else 3.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "knobX",
    )

    val trackOff = if (c.isDark) Color(0xFF323846) else Color(0xFFD5DCE6)
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(lerpColor(trackOff, c.accent, progress))
            .border(1.dp, Color.White.copy(alpha = if (checked) 0.16f else 0.06f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(width = knobWidth, height = 22.dp)
                .shadow(2.5.dp, RoundedCornerShape(11.dp))
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White),
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** 液态玻璃下拉选择行：点击后从触发位置弹簧弹出 G2 超椭圆玻璃菜单（支持滚动）。 */
@Composable
fun MochiDropdownRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    divider: Boolean = true,
) {
    val c = rememberMochiColors()
    var expanded by remember { mutableStateOf(false) }
    val rowHeightPx = with(LocalDensity.current) { 52.dp.roundToPx() }
    val menuShape = G2RoundedShape(18.dp)

    Box {
        MochiRow(
            title = title,
            value = options.getOrNull(selectedIndex),
            chevron = true,
            divider = divider,
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(-12, rowHeightPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                val scale by animateFloatAsState(
                    targetValue = if (shown) 1f else 0.88f,
                    animationSpec = spring(
                        dampingRatio = 0.62f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "menuScale",
                )
                val alpha by animateFloatAsState(
                    targetValue = if (shown) 1f else 0f,
                    animationSpec = tween(durationMillis = 120),
                    label = "menuAlpha",
                )
                val rise by animateDpAsState(
                    targetValue = if (shown) 0.dp else (-10).dp,
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "menuRise",
                )

                Column(
                    modifier = Modifier
                        .offset(y = rise)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            transformOrigin = TransformOrigin(0.85f, 0f)
                        }
                        .width(196.dp)
                        .heightIn(max = 264.dp)
                        .shadow(22.dp, menuShape, ambientColor = Color.Black.copy(alpha = 0.18f))
                        .clip(menuShape)
                        .background(c.glassSurface)
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(c.glassBorderTop, c.glassBorderBottom)),
                            menuShape,
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                ) {
                    options.forEachIndexed { index, label ->
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val hovered by interaction.collectIsHoveredAsState()
                        val selected = index == selectedIndex
                        val itemShape = G2RoundedShape(11.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 38.dp)
                                .clip(itemShape)
                                .background(
                                    when {
                                        pressed -> c.fill
                                        selected -> c.accent.copy(alpha = if (c.isDark) 0.18f else 0.10f)
                                        hovered -> c.cardHover
                                        else -> Color.Transparent
                                    },
                                )
                                .hoverable(interaction)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = {
                                        onSelect(index)
                                        expanded = false
                                    },
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) c.accent else c.text,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Check(color = c.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 右向箭头（Canvas 绘制，避免字体缺字形）。 */
@Composable
fun Chevron(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 7.dp, height = 12.dp)) {
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.12f),
            end = Offset(size.width * 0.82f, size.height * 0.5f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.82f, size.height * 0.5f),
            end = Offset(size.width * 0.15f, size.height * 0.88f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round,
        )
    }
}

/** 选中对勾（Canvas 绘制）。 */
@Composable
fun Check(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        drawLine(
            color = color,
            start = Offset(size.width * 0.12f, size.height * 0.55f),
            end = Offset(size.width * 0.42f, size.height * 0.82f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.42f, size.height * 0.82f),
            end = Offset(size.width * 0.9f, size.height * 0.2f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round,
        )
    }
}
