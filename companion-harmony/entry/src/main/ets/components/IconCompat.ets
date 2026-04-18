/**
 * 兼容性图标组件
 * 支持 SymbolGlyph 和文本/Unicode 图标作为备选
 */
import { hilog } from '@kit.PerformanceAnalysisKit'

const TAG = 'IconCompat'
const DOMAIN = 0x0000

// 图标类型定义
export enum IconType {
  SYMBOL = 'symbol',       // SymbolGlyph（系统图标）
  TEXT = 'text',           // 文本/Unicode 图标
  IMAGE = 'image'          // 图片图标
}

// Unicode 图标映射
const UNICODE_ICONS: Record<string, string> = {
  // 播放控制
  'PLAY': '▶',
  'PAUSE': '⏸',
  'STOP': '⏹',
  'SKIP_BACK': '⏪',
  'SKIP_FORWARD': '⏩',
  'REFRESH': '↻',

  // 音量
  'VOLUME_UP': '🔊',
  'VOLUME_DOWN': '🔉',
  'VOLUME_MUTE': '🔇',

  // 网络与连接
  'WIFI': '📶',
  'CONNECTED': '🔗',
  'DISCONNECTED': '❌',

  // 设置
  'SETTINGS': '⚙',
  'CONFIG': '🎚',

  // 文件操作
  'DOWNLOAD': '⬇',
  'UPLOAD': '⬆',
  'COPY': '📋',
  'SAVE': '💾',
  'DELETE': '🗑',

  // 导航
  'HOME': '🏠',
  'BACK': '◀',
  'FORWARD': '▶',
  'UP': '▲',
  'DOWN': '▼',
  'LEFT': '◀',
  'RIGHT': '▶',
  'EXPAND': '⤢',
  'COLLAPSE': '⤡',

  // 显示
  'FULLSCREEN': '⛶',
  'DISPLAY': '🖥',

  // 状态
  'SUCCESS': '✓',
  'WARNING': '⚠',
  'ERROR': '✕',
  'INFO': 'ℹ',
  'LOADING': '⋯',

  // 编辑
  'CLOSE': '✕',
  'CHECK': '✓',
  'EDIT': '✏',

  // 其他
  'VIDEO': '🎬',
  'MUSIC_NOTE': '♫',
  'EYE': '👁',
  'STAR': '★',
  'HEART': '♥',
  'SEARCH': '🔍',
}

@Component
export struct IconCompat {
  // 图标名称
  @Prop iconKey: string = ''

  // 图标类型
  @Prop iconType: IconType = IconType.SYMBOL

  // 大小
  @Prop fontSize: number = 24

  // 颜色（使用字符串而不是资源引用）
  @Prop fontColor: string = '#94A3B8'

  // SymbolGlyph 的资源名称（可选）
  @Prop symbolName: string = ''

  build() {
    if (this.iconType === IconType.SYMBOL && this.symbolName) {
      // 尝试使用 SymbolGlyph
      SymbolGlyph($r(`sys.symbol.${this.symbolName}`))
        .fontSize(this.fontSize)
        .fontColor([this.fontColor])
    } else if (this.iconType === IconType.TEXT) {
      // 使用 Unicode 图标作为备选
      Text(this.getUnicodeIcon(this.iconKey))
        .fontSize(this.fontSize)
        .fontColor(this.fontColor)
    } else {
      // 默认使用 Unicode 图标
      Text(this.getUnicodeIcon(this.iconKey))
        .fontSize(this.fontSize)
        .fontColor(this.fontColor)
    }
  }

  /**
   * 获取 Unicode 图标
   */
  private getUnicodeIcon(key: string): string {
    // 直接查找
    if (UNICODE_ICONS[key]) {
      return UNICODE_ICONS[key]
    }

    // 尝试移除前缀后查找
    const simpleKey = key.replace('ohos_', '').replace('_fill', '').toUpperCase()
    if (UNICODE_ICONS[simpleKey]) {
      return UNICODE_ICONS[simpleKey]
    }

    // 默认返回问号
    hilog.warn(DOMAIN, TAG, `未找到图标: ${key}，使用默认图标`)
    return '?'
  }
}

/**
 * 创建 SymbolGlyph 图标
 */
@Component
export struct SymbolIcon {
  @Prop symbolName: string = ''
  @Prop fontSize: number = 24
  @Prop fontColor: string = '#94A3B8'

  build() {
    SymbolGlyph($r(`sys.symbol.${this.symbolName}`))
      .fontSize(this.fontSize)
      .fontColor([this.fontColor])
  }
}

/**
 * 创建文本图标（备选方案）
 */
@Component
export struct TextIcon {
  @Prop iconKey: string = ''
  @Prop fontSize: number = 24
  @Prop fontColor: string = '#94A3B8'

  build() {
    Text(this.getUnicodeIcon(this.iconKey))
      .fontSize(this.fontSize)
      .fontColor(this.fontColor)
  }

  private getUnicodeIcon(key: string): string {
    if (UNICODE_ICONS[key]) {
      return UNICODE_ICONS[key]
    }
    const simpleKey = key.replace('ohos_', '').replace('_fill', '').toUpperCase()
    if (UNICODE_ICONS[simpleKey]) {
      return UNICODE_ICONS[simpleKey]
    }
    return '•'
  }
}
