/**
 * 应用颜色常量
 * 统一的颜色管理
 */

/**
 * 主色调
 */
export const PRIMARY_COLOR = '#6366F1'
export const PRIMARY_DARK = '#4F46E5'
export const PRIMARY_LIGHT = '#818CF8'

/**
 * 背景色
 */
export const BG_PRIMARY = '#0F172A'
export const BG_CARD = '#1E293B'
export const BG_ELEVATED = '#334155'
export const BG_ELEMENT = '#475569'

/**
 * 文字颜色
 */
export const TEXT_PRIMARY = '#F8FAFC'
export const TEXT_SECONDARY = '#CBD5E1'
export const TEXT_TERTIARY = '#94A3B8'
export const TEXT_DISABLED = '#64748B'

/**
 * 状态颜色
 */
export const STATUS_SUCCESS = '#10B981'
export const STATUS_WARNING = '#F59E0B'
export const STATUS_ERROR = '#EF4444'
export const STATUS_INFO = '#3B82F6'

/**
 * 连接状态颜色
 */
export const CONNECTED_COLOR = '#10B981'
export const DISCONNECTED_COLOR = '#64748B'
export const CONNECTING_COLOR = '#F59E0B'

/**
 * 播放状态颜色
 */
export const PLAYING_COLOR = '#10B981'
export const PAUSED_COLOR = '#F59E0B'

/**
 * 辅助颜色（别名）
 */
export const SUCCESS = STATUS_SUCCESS
export const NEUTRAL = TEXT_DISABLED
export const WARNING = STATUS_WARNING
export const ERROR = STATUS_ERROR

/**
 * AppColors 接口定义
 */
export interface AppColorsType {
  PRIMARY: string
  PRIMARY_DARK: string
  PRIMARY_LIGHT: string
  BG_PRIMARY: string
  BG_CARD: string
  BG_ELEVATED: string
  BG_ELEMENT: string
  TEXT_PRIMARY: string
  TEXT_SECONDARY: string
  TEXT_TERTIARY: string
  TEXT_DISABLED: string
  STATUS_SUCCESS: string
  STATUS_WARNING: string
  STATUS_ERROR: string
  STATUS_INFO: string
  CONNECTED_COLOR: string
  DISCONNECTED_COLOR: string
  CONNECTING_COLOR: string
  PLAYING_COLOR: string
  PAUSED_COLOR: string
  SUCCESS: string
  NEUTRAL: string
  WARNING: string
  ERROR: string
}

/**
 * AppColors 命名空间
 * 提供对象式访问方式
 */
export const AppColors: AppColorsType = {
  PRIMARY: PRIMARY_COLOR,
  PRIMARY_DARK: PRIMARY_DARK,
  PRIMARY_LIGHT: PRIMARY_LIGHT,
  BG_PRIMARY: BG_PRIMARY,
  BG_CARD: BG_CARD,
  BG_ELEVATED: BG_ELEVATED,
  BG_ELEMENT: BG_ELEMENT,
  TEXT_PRIMARY: TEXT_PRIMARY,
  TEXT_SECONDARY: TEXT_SECONDARY,
  TEXT_TERTIARY: TEXT_TERTIARY,
  TEXT_DISABLED: TEXT_DISABLED,
  STATUS_SUCCESS: STATUS_SUCCESS,
  STATUS_WARNING: STATUS_WARNING,
  STATUS_ERROR: STATUS_ERROR,
  STATUS_INFO: STATUS_INFO,
  CONNECTED_COLOR: CONNECTED_COLOR,
  DISCONNECTED_COLOR: DISCONNECTED_COLOR,
  CONNECTING_COLOR: CONNECTING_COLOR,
  PLAYING_COLOR: PLAYING_COLOR,
  PAUSED_COLOR: PAUSED_COLOR,
  SUCCESS: SUCCESS,
  NEUTRAL: NEUTRAL,
  WARNING: WARNING,
  ERROR: ERROR
}
