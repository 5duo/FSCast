package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.floatingscreencasting.ui.theme.*

/**
 * IconPark图标辅助函数
 * 用于统一加载和着色IconPark SVG图标
 *
 * @param resId SVG图标资源ID
 * @param contentDescription 内容描述（用于可访问性）
 * @param modifier 修饰符
 * @param tint 着色颜色（默认使用GoldOnSurface）
 * @param iconSize 图标大小（默认24.dp）
 */
@Composable
fun IconParkIcon(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = GoldOnSurface,
    iconSize: Dp = 24.dp
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier.size(iconSize),
        colorFilter = ColorFilter.tint(tint)
    )
}

/**
 * 快捷图标组件（使用预定义图标）
 *
 * @param iconId 图标资源ID
 * @param contentDescription 内容描述
 * @param modifier 修饰符
 * @param tint 着色颜色
 * @param iconSize 图标大小
 */
@Composable
fun IconParkIconQuick(
    iconId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = GoldOnSurface,
    iconSize: Dp = 24.dp
) {
    IconParkIcon(
        resId = iconId,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        iconSize = iconSize
    )
}

/**
 * 图标大小规范
 */
object IconParkSizes {
    val STATUS_UNIT = 20.dp      // 状态单元图标
    val BUTTON_LARGE = 18.dp      // 大按钮图标
    val BUTTON_SMALL = 16.dp      // 小按钮图标
    val LIST_ITEM = 14.dp         // 列表图标
    val STATUS_INDICATOR = 8.dp   // 状态指示灯
}

/**
 * 图标着色规范
 */
object IconParkTints {
    val NORMAL = GoldOnSurface           // 正常状态
    val ACTIVE = GoldPrimary             // 激活/选中状态
    val DISABLED = GoldOnSurfaceVariant  // 禁用/次要状态
    val SUCCESS = Success                // 成功（活跃状态）
    val ERROR = Error                    // 错误状态
}

