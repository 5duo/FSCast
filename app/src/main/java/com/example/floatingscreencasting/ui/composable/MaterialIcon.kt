package com.example.floatingscreencasting.ui.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.floatingscreencasting.ui.theme.*

/**
 * Material Design Icon 辅助函数
 * 用于统一显示Material Icons
 *
 * @param imageVector Material Icons ImageVector
 * @param contentDescription 内容描述（用于可访问性）
 * @param modifier 修饰符
 * @param tint 着色颜色（默认使用GoldOnSurface）
 * @param iconSize 图标大小（默认24.dp）
 */
@Composable
fun MaterialIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = GoldOnSurface,
    iconSize: Dp = 24.dp
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

/**
 * 图标大小规范
 */
object MaterialIconSizes {
    val STATUS_UNIT = 20.dp      // 状态单元图标
    val BUTTON_LARGE = 18.dp      // 大按钮图标
    val BUTTON_SMALL = 16.dp      // 小按钮图标
    val LIST_ITEM = 14.dp         // 列表图标
    val STATUS_INDICATOR = 8.dp   // 状态指示灯
}

/**
 * 图标着色规范
 */
object MaterialIconTints {
    val NORMAL = GoldOnSurface           // 正常状态
    val ACTIVE = GoldPrimary             // 激活/选中状态
    val DISABLED = GoldOnSurfaceVariant  // 禁用/次要状态
    val SUCCESS = Success                // 成功（活跃状态）
    val ERROR = Error                    // 错误状态
}
