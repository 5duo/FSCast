/**
 * HarmonyOS 系统图标资源映射
 * 使用官方系统图标库：https://developer.huawei.com/consumer/cn/design/harmonyos-symbol/
 *
 * 使用方法：
 * SymbolGlyph($r('sys.symbol.ohos_ICON_NAME'))
 *   .fontSize(24)
 *   .fontColor([Color.White])
 */

export class SysIcons {
  // ==================== 播放控制 ====================
  static readonly PLAY = 'ohos_play_fill'              // 播放
  static readonly PAUSE = 'ohos_pause'                  // 暂停
  static readonly STOP = 'ohos_stop_fill'               // 停止
  static readonly SKIP_BACK = 'ohos_backward_fill'      // 后退
  static readonly SKIP_FORWARD = 'ohos_forward_fill'    // 前进
  static readonly REPLAY = 'ohos_arrow_clockwise'       // 重播

  // ==================== 音量控制 ====================
  static readonly VOLUME_UP = 'ohos_speaker_wave_3'     // 音量加
  static readonly VOLUME_DOWN = 'ohos_speaker_wave_1'   // 音量减
  static readonly VOLUME_MUTE = 'ohos_speaker_slash'    // 静音
  static readonly VOLUME_OFF = 'ohos_speaker_slash_fill' // 关闭音量

  // ==================== 网络与连接 ====================
  static readonly WIFI = 'ohos_wifi'                    // WiFi
  static readonly WIFI_OFF = 'ohos_wifi_slash'          // WiFi断开
  static readonly CONNECTED = 'ohos_link'               // 已连接
  static readonly DISCONNECTED = 'ohos_link_badge'      // 未连接
  static readonly BROADCAST = 'ohos_antenna_radiowaves_left_and_right'  // 广播

  // ==================== 设置与配置 ====================
  static readonly SETTINGS = 'ohos_gearshape'           // 设置
  static readonly SETTINGS_FILLED = 'ohos_gearshape_fill' // 设置（填充）
  static readonly CONFIG = 'ohos_slider_horizontal_3'   // 配置

  // ==================== 文件与媒体 ====================
  static readonly DOWNLOAD = 'ohos_arrow_down_circle'   // 下载
  static readonly UPLOAD = 'ohos_arrow_up_circle'       // 上传
  static readonly COPY = 'ohos_doc_on_doc'              // 复制
  static readonly SAVE = 'ohos_square_and_arrow_down'   // 保存
  static readonly FOLDER = 'ohos_folder'                // 文件夹
  static readonly DOCUMENT = 'ohos_doc'                 // 文档

  // ==================== 编辑操作 ====================
  static readonly CLOSE = 'ohos_xmark_circle'           // 关闭
  static readonly CLOSE_FILLED = 'ohos_xmark_circle_fill' // 关闭（填充）
  static readonly DELETE = 'ohos_trash'                 // 删除
  static readonly EDIT = 'ohos_pencil'                  // 编辑
  static readonly CHECK = 'ohos_checkmark_circle'       // 确认
  static readonly CHECK_FILLED = 'ohos_checkmark_circle_fill'  // 确认（填充）

  // ==================== 导航与视图 ====================
  static readonly HOME = 'ohos_house'                   // 主页
  static readonly BACK = 'ohos_chevron_left'            // 返回
  static readonly FORWARD = 'ohos_chevron_right'        // 前进
  static readonly UP = 'ohos_chevron_up'                // 向上
  static readonly DOWN = 'ohos_chevron_down'            // 向下
  static readonly LEFT = 'ohos_chevron_left'            // 向左
  static readonly RIGHT = 'ohos_chevron_right'          // 向右
  static readonly EXPAND = 'ohos_arrow_up_right_square' // 展开
  static readonly COLLAPSE = 'ohos_arrow_down_left_square' // 收起

  // ==================== 显示与布局 ====================
  static readonly FULLSCREEN = 'ohos_arrow_up_left_and_arrow_down_right'  // 全屏
  static readonly MINIMIZE = 'ohos_arrow_down_right'   // 最小化
  static readonly LAYER = 'ohos_square_stack_3d'       // 图层
  static readonly DISPLAY = 'ohos_desktopcomputer'      // 显示器

  // ==================== 状态指示 ====================
  static readonly SUCCESS = 'ohos_checkmark'            // 成功
  static readonly WARNING = 'ohos_exclamationmark'      // 警告
  static readonly ERROR = 'ohos_xmark'                  // 错误
  static readonly INFO = 'ohos_info_circle'             // 信息
  static readonly LOADING = 'ohos_ellipsis'              // 加载中

  // ==================== 时间相关 ====================
  static readonly CLOCK = 'ohos_clock'                  // 时钟
  static readonly TIMER = 'ohos_timer'                  // 计时器
  static readonly CALENDAR = 'ohos_calendar'            // 日历

  // ==================== 搜索与刷新 ====================
  static readonly SEARCH = 'ohos_magnifyingglass'       // 搜索
  static readonly REFRESH = 'ohos_arrow_clockwise'      // 刷新
  static readonly SYNC = 'ohos_arrow_trianglehead_2_clockwise'  // 同步

  // ==================== 用户与账户 ====================
  static readonly USER = 'ohos_person'                  // 用户
  static readonly USERS = 'ohos_person_2'               // 多用户
  static readonly SETTINGS_USER = 'ohos_gearshape_2'    // 用户设置

  // ==================== 设备与硬件 ====================
  static readonly PHONE = 'ohos_phone'                  // 手机
  static readonly TABLET = 'ohos_ipad'                  // 平板
  static readonly CAR = 'ohos_car'                      // 汽车

  // ==================== 媒体控制 ====================
  static readonly PLAYLIST = 'ohos_list_bullet'         // 播放列表
  static readonly SHUFFLE = 'ohos_shuffle'              // 随机播放
  static readonly REPEAT = 'ohos_repeat'                // 重复播放

  // ==================== 其他常用图标 ====================
  static readonly LINK = 'ohos_link'                    // 链接
  static readonly CHAIN = 'ohos_link'                   // 链条（同link）
  static readonly EYE = 'ohos_eye'                      // 显示
  static readonly EYE_OFF = 'ohos_eye_slash'            // 隐藏
  static readonly STAR = 'ohos_star'                    // 星标
  static readonly STAR_FILLED = 'ohos_star_fill'        // 星标（填充）
  static readonly HEART = 'ohos_heart'                  // 喜欢
  static readonly HEART_FILLED = 'ohos_heart_fill'      // 喜欢（填充）
  static readonly SHARE = 'ohos_square_and_arrow_up'    // 分享

  // ==================== 视频相关 ====================
  static readonly VIDEO = 'ohos_video'                  // 视频
  static readonly VIDEO_FILLED = 'ohos_video_fill'      // 视频（填充）
  static readonly CLAPPERBOARD = 'ohos_clapperboard'    // 拍摄板
  static readonly FILM = 'ohos_film'                    // 胶片

  // ==================== 音频相关 ====================
  static readonly MUSIC_NOTE = 'ohos_music_note'        // 音符
  static readonly MUSIC_NOTES = 'ohos_music_note_list'  // 音符列表
  static readonly HEADPHONES = 'ohos_headphones'        // 耳机

  // ==================== 对齐与布局 ====================
  static readonly ALIGN_LEFT = 'ohos_text_alignleft'    // 左对齐
  static readonly ALIGN_CENTER = 'ohos_text_aligncenter' // 居中对齐
  static readonly ALIGN_RIGHT = 'ohos_text_alignright'  // 右对齐
  static readonly VERTICALLY = 'ohos_text_alignleft'    // 垂直对齐（可用布局图标）
}
