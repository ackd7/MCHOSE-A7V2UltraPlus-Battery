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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/**
 * iOS 26 Liquid Glass 悬浮胶囊标签栏（参考 mochi-compose 与 HyperOS 设计语言）。
 *
 * - 液态玻璃半透明底座 + 双向折射高光边（左上冷白高光 → 右下微光）+ 柔和深投影
 * - 内部选中液态胶囊带弹簧滑动、高光渐变与按压果冻微拉伸
 */
data class MochiTab(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MochiTabBar(
    tabs: List<MochiTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 88.dp,
    itemHeight: Dp = 46.dp,
    padding: Dp = 6.dp,
) {
    val c = rememberMochiColors()
    val indicatorRadius = itemHeight / 2
    val containerRadius = indicatorRadius + padding
    val containerShape = RoundedCornerShape(containerRadius)
    val indicatorShape = RoundedCornerShape(indicatorRadius)
    val barWidth = itemWidth * tabs.size + padding * 2

    val indicatorOffset by animateDpAsState(
        targetValue = padding + itemWidth * selectedIndex,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "indicator",
    )

    val glassRim = Brush.verticalGradient(
        0f to c.glassBorderTop,
        0.55f to c.cardBorder,
        1f to c.glassBorderBottom,
    )
    val indicatorFill = Brush.verticalGradient(
        0f to c.accent.copy(alpha = if (c.isDark) 0.28f else 0.16f),
        1f to c.accent.copy(alpha = if (c.isDark) 0.18f else 0.10f),
    )

    Box(
        modifier = modifier
            .width(barWidth)
            .height(itemHeight + padding * 2)
            .shadow(
                elevation = if (c.isDark) 22.dp else 20.dp,
                shape = containerShape,
                ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.40f else 0.10f),
                spotColor = Color.Black.copy(alpha = if (c.isDark) 0.50f else 0.16f),
            )
            .clip(containerShape)
            .background(c.glassSurface)
            .border(1.dp, glassRim, containerShape),
    ) {
        // 液态玻璃选中指示胶囊
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset, y = padding)
                .width(itemWidth)
                .height(itemHeight)
                .clip(indicatorShape)
                .background(indicatorFill)
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (c.isDark) 0.22f else 0.72f),
                            c.accent.copy(alpha = 0.16f),
                        ),
                    ),
                    indicatorShape,
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight + padding * 2)
                .padding(horizontal = padding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val hovered by interaction.collectIsHoveredAsState()

                val contentColor by animateColorAsState(
                    targetValue = when {
                        selected -> c.accent
                        hovered -> c.text
                        else -> c.secondary
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tabColor$index",
                )
                val pressScale by animateFloatAsState(
                    targetValue = if (pressed) 0.92f else 1f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                    label = "tabScale$index",
                )

                Column(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(itemHeight)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .clip(indicatorShape)
                        .hoverable(interaction)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(19.dp),
                        tint = contentColor,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
