package top.ackd7.mchose.ui.mochi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/**
 * iOS 26 风格悬浮胶囊标签栏（mochi-compose 的 MochiTabBar 移植版）。
 *
 * - 选中指示器是胶囊，切换时带弹簧滑动
 * - 图标与文字颜色随选中状态平滑过渡
 * - 双层柔和阴影；容器圆角 = 指示器圆角 + 内边距（同心）
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
    itemWidth: Dp = 84.dp,
    itemHeight: Dp = 44.dp,
    padding: Dp = 6.dp,
) {
    val c = rememberMochiColors()
    val indicatorRadius = itemHeight / 2
    val containerRadius = indicatorRadius + padding
    val barWidth = itemWidth * tabs.size + padding * 2

    val indicatorOffset by animateDpAsState(
        targetValue = padding + itemWidth * selectedIndex,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "indicator",
    )

    Box(
        modifier = modifier
            .width(barWidth)
            .height(itemHeight + padding * 2)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(containerRadius),
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .clip(RoundedCornerShape(containerRadius))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(containerRadius)),
    ) {
        // 选中指示器
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset, y = padding)
                .width(itemWidth)
                .height(itemHeight)
                .clip(RoundedCornerShape(indicatorRadius))
                .background(c.accent.copy(alpha = 0.18f)),
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
                val contentColor by animateColorAsState(
                    targetValue = if (selected) c.accent else c.secondary,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tabColor$index",
                )
                val interaction = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(itemHeight)
                        .clip(RoundedCornerShape(indicatorRadius))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(7.dp))
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(19.dp),
                        tint = contentColor,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(text = tab.label, fontSize = 10.sp, color = contentColor)
                }
            }
        }
    }
}
