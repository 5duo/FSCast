/**
 * 设计令牌系统（简化版 - 适配 ArkTS 限制）
 * 统一管理应用的设计规范
 */
export class DesignTokens {
  // ==================== 颜色系统 ====================
  // OLED 优化的深色主题，高对比度，护眼

  // 主色调
  static readonly COLOR_PRIMARY = '#E11D48'      // 玫瑰红
  static readonly COLOR_SECONDARY = '#FB7185'    // 浅玫瑰
  static readonly COLOR_ACCENT = '#2563EB'       // 蓝色 CTA

  // 背景色
  static readonly COLOR_BG_PRIMARY = '#0F172A'    // 深蓝灰（主背景）
  static readonly COLOR_BG_SECONDARY = '#1E293B'  // 中蓝灰（卡片背景）
  static readonly COLOR_BG_TERTIARY = '#334155'   // 浅蓝灰（分割线）

  // 文本色
  static readonly COLOR_TEXT_PRIMARY = '#F1F5F9'   // 主要文本
  static readonly COLOR_TEXT_SECONDARY = '#94A3B8' // 次要文本
  static readonly COLOR_TEXT_TERTIARY = '#64748B'  // 辅助文本
  static readonly COLOR_TEXT_DISABLED = '#475569'  // 禁用文本

  // 状态色
  static readonly COLOR_SUCCESS = '#10B981'       // 绿色
  static readonly COLOR_WARNING = '#F59E0B'       // 橙色
  static readonly COLOR_ERROR = '#EF4444'         // 红色
  static readonly COLOR_INFO = '#3B82F6'          // 蓝色

  // ==================== 尺寸系统 ====================
  static readonly SPACING_XS = 4
  static readonly SPACING_SM = 8
  static readonly SPACING_MD = 12
  static readonly SPACING_LG = 16
  static readonly SPACING_XL = 24
  static readonly SPACING_XXL = 32

  // ==================== 圆角系统 ====================
  static readonly RADIUS_SM = 8
  static readonly RADIUS_MD = 12
  static readonly RADIUS_LG = 16
  static readonly RADIUS_XL = 20
  static readonly RADIUS_FULL = 9999

  // ==================== 图标尺寸 ====================
  static readonly ICON_SIZE_XS = 16
  static readonly ICON_SIZE_SM = 20
  static readonly ICON_SIZE_MD = 24
  static readonly ICON_SIZE_LG = 32
  static readonly ICON_SIZE_XL = 48

  // ==================== 按钮尺寸 ====================
  static readonly BUTTON_HEIGHT_SM = 36
  static readonly BUTTON_HEIGHT_MD = 44
  static readonly BUTTON_HEIGHT_LG = 52

  // ==================== 动画时长 ====================
  static readonly DURATION_FAST = 150
  static readonly DURATION_NORMAL = 200
  static readonly DURATION_SLOW = 300

  // ==================== 辅助方法 ====================
  /**
   * 获取状态对应的颜色
   */
  static getStatusColor(status: string): string {
    const colorMap: Record<string, string> = {
      'connected': DesignTokens.COLOR_SUCCESS,
      'connecting': DesignTokens.COLOR_WARNING,
      'disconnected': DesignTokens.COLOR_ERROR,
      'playing': DesignTokens.COLOR_SUCCESS,
      'paused': DesignTokens.COLOR_WARNING,
      'stopped': DesignTokens.COLOR_TEXT_DISABLED
    }
    return colorMap[status] || DesignTokens.COLOR_TEXT_DISABLED
  }
}
