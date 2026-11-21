package top.ackd7.mchose.ui.mochi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text

/**
 * G2 连续超椭圆圆角形状（参考 Apple iOS / HyperOS miuix / mochi-compose）。
 *
 * 与标准圆弧圆角不同，超椭圆曲线在直边到圆角过渡处曲率连续，视觉更圆润顺滑，
 * 且直接实现 Compose [Shape]，可同时用于 clip、shadow 与液态玻璃渐变描边。
 */
class G2RoundedShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Generic(Path())

        val r = with(density) { cornerRadius.toPx() }.coerceIn(0f, minOf(w, h) / 2f)
        // 平滑延展系数：让直边到角点的曲率平滑衔接
        val s = (r * 1.24f).coerceAtMost(minOf(w, h) / 2f)
        val c1 = r * 0.42f
        val c2 = r * 0.14f

        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(w - s, 0f)
            // 右上角
            cubicTo(w - c1, 0f, w, c2, w, s)
            lineTo(w, h - s)
            // 右下角
            cubicTo(w, h - c2, w - c1, h, w - s, h)
            lineTo(s, h)
            // 左下角
            cubicTo(c1, h, 0f, h - c2, 0f, h - s)
            lineTo(0f, s)
            // 左上角
            cubicTo(0f, c2, c1, 0f, s, 0f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is G2RoundedShape && other.cornerRadius == cornerRadius

    override fun hashCode(): Int = cornerRadius.hashCode()
}

/**
 * iOS 26 / Mochi 物理按压缩放动效：按下时轻柔回缩，松开时低阻尼弹簧回弹。
 */
fun Modifier.mochiPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "mochiPressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Liquid Glass 液态玻璃卡片容器修饰符：
 * G2 超椭圆 + 柔和层次投影 + 卡片底色 + 上亮下微的双向高光折射边框。
 */
fun Modifier.mochiGlassSurface(
    colors: MochiColors,
    cornerRadius: Dp = 22.dp,
    elevation: Dp = 10.dp,
    backgroundColor: Color = colors.card,
): Modifier {
    val shape = G2RoundedShape(cornerRadius)
    val rimBrush = Brush.verticalGradient(
        0f to colors.glassBorderTop,
        0.45f to colors.cardBorder,
        1f to colors.glassBorderBottom,
    )
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.06f),
            spotColor = Color.Black.copy(alpha = if (colors.isDark) 0.36f else 0.09f),
        )
        .clip(shape)
        .background(backgroundColor)
        .border(1.dp, rimBrush, shape)
}

/**
 * 精致微胶囊状态标签（用于连接方式、充电中、休眠 Zzz 等指示）。
 */
@Composable
fun MochiStatusPill(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val c = rememberMochiColors()
    val bg = if (emphasized) dotColor.copy(alpha = if (c.isDark) 0.20f else 0.12f) else c.fill
    val borderColor = if (emphasized) dotColor.copy(alpha = 0.28f) else c.cardBorder
    val textColor = if (emphasized) dotColor else c.secondary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

/**
 * HyperOS × iOS 26 风格分段胶囊控制器：
 * 内部滑块采用低阻尼弹簧滑动，支持果冻按压与悬停反馈。
 */
@Composable
fun MochiSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
) {
    if (options.isEmpty()) return
    val c = rememberMochiColors()
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val trackPadding = 3.dp
    val trackShape = G2RoundedShape(14.dp)
    val thumbShape = G2RoundedShape(11.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(trackShape)
            .background(c.segmentTrack)
            .border(1.dp, c.cardBorder, trackShape)
            .padding(trackPadding),
    ) {
        val segWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = segWidth * safeIndex,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "segmentThumb",
        )

        // 悬浮液态滑块
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = if (c.isDark) 4.dp else 6.dp,
                    shape = thumbShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.14f),
                )
                .clip(thumbShape)
                .background(c.segmentThumb)
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(c.glassBorderTop, c.cardBorder)),
                    thumbShape,
                ),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { idx, label ->
                val selected = idx == safeIndex
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val textColor by animateColorAsState(
                    targetValue = when {
                        selected -> c.text
                        hovered -> c.text.copy(alpha = 0.85f)
                        else -> c.secondary
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "segText$idx",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .mochiPressScale(interaction, pressedScale = 0.96f)
                        .hoverable(interaction)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(idx) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                    )
                }
            }
        }
    }
}
