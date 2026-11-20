package top.ackd7.mchose.ui.mochi

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * mochi-compose 风格的设计 token（移植版）。
 *
 * 原库是 Android 专用（androidx.compose），无法在 Compose Desktop 直接依赖，
 * 这里按它的规范重绘：iOS 式 inset-grouped 列表 + 发丝分隔线 + 连续圆角卡片。
 */
data class MochiColors(
    val card: Color,
    val cardBorder: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val divider: Color,
    val fill: Color,
    val danger: Color,
)

@Composable
fun rememberMochiColors(): MochiColors {
    val scheme = MiuixTheme.colorScheme
    return MochiColors(
        // 注意：miuix 的 surfaceContainer / surfaceContainerHigh 在暗色下等于背景色，
        // 用 surfaceContainerHighest 才有层次；再叠一层发丝描边保证可辨识。
        card = scheme.surfaceContainerHighest,
        cardBorder = scheme.onSurface.copy(alpha = 0.07f),
        text = scheme.onSurface,
        secondary = scheme.onSurfaceVariantSummary,
        accent = scheme.primary,
        divider = scheme.dividerLine,
        fill = scheme.onSurface.copy(alpha = 0.10f),
        danger = Color(0xFFE5484D),
    )
}

private val CardShape = RoundedCornerShape(20.dp)

/** 分组卡片：可选 header（卡片上方小标题）与 footer（卡片下方说明）。 */
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
                fontSize = 13.sp,
                color = c.secondary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(c.card)
                .border(1.dp, c.cardBorder, CardShape),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                fontSize = 12.sp,
                color = c.secondary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
        }
    }
}

/** 一行：标题 / 副标题 / 右侧值或控件；底部发丝线（从文字处起）。 */
@Composable
fun MochiRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    divider: Boolean = true,
    accent: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val clickable = onClick != null && enabled

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (pressed && clickable) c.fill else Color.Transparent)
                .then(
                    if (clickable) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick!!,
                        )
                    } else {
                        Modifier
                    },
                )
                .heightIn(min = 52.dp)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = when {
                        !enabled -> c.secondary
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
                Text(text = value, fontSize = 14.sp, color = c.secondary)
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (chevron) {
                Spacer(Modifier.width(8.dp))
                Chevron(color = c.secondary.copy(alpha = 0.75f))
            }
        }

        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .height(1.dp)
                    .background(c.divider),
            )
        }
    }
}

/** 居中的文字按钮行（如「退出程序」）。 */
@Composable
fun MochiButtonRow(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val c = rememberMochiColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(if (pressed) c.fill else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = if (danger) c.danger else c.accent,
            textAlign = TextAlign.Center,
        )
    }
}

/** iOS 风格开关：44×26 胶囊 + 22dp 白色圆钮。 */
@Composable
fun MochiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = rememberMochiColors()
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "track",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "knob",
    )

    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(lerpColor(c.fill, c.accent, progress))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(22.dp)
                .shadow(1.5.dp, RoundedCornerShape(11.dp))
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

/** 下拉选择行：点击后在行下方弹出选项面板。 */
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
                alignment = Alignment.TopStart,
                offset = IntOffset(0, rowHeightPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                // 弹出动画：缩放 + 淡入 + 轻微下落，末端带弹簧
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
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .width(180.dp)
                        .shadow(18.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.card)
                        .padding(vertical = 6.dp),
                ) {
                    options.forEachIndexed { index, label ->
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .background(
                                    if (pressed) c.fill else Color.Transparent,
                                )
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = {
                                        onSelect(index)
                                        expanded = false
                                    },
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                color = if (index == selectedIndex) c.accent else c.text,
                                modifier = Modifier.weight(1f),
                            )
                            if (index == selectedIndex) {
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
    Canvas(modifier = modifier.size(width = 8.dp, height = 13.dp)) {
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.12f),
            end = Offset(size.width * 0.8f, size.height * 0.5f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.8f, size.height * 0.5f),
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
